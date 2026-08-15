package snd.komelia.image

/**
 * Joins the bubbles of one utterance so the translator sees a sentence.
 *
 * A comic sentence is routinely lettered across two or three balloons, and each
 * balloon reached the translator on its own. Measured over Ramen Aka Neko 167,
 * 15 pages: of 142 blocks, 52 were two words or shorter and 27 carried a
 * continuation ellipsis. Handing "…It's not quite time yet..." to a translator
 * with nothing around it is most of why the output read as nonsense, and no
 * amount of tuning the engine fixes it.
 *
 * The signal is the lettering convention itself: an utterance that continues
 * ends its balloon with an ellipsis and opens the next with one. That is a
 * convention, not a guarantee, so the rules below stay narrow — a group that
 * should have been one bubble is a missed improvement, while a group that
 * welds two speakers together is a regression, and the two are not worth the
 * same.
 */
object BubbleAssembler {

    /**
     * Groups block indices into utterances, preserving order.
     *
     * Every index appears exactly once, so a caller can rebuild the full set
     * from the result without checking.
     */
    fun group(texts: List<String>): List<List<Int>> {
        val groups = mutableListOf<MutableList<Int>>()
        for (index in texts.indices) {
            val previous = groups.lastOrNull()
            val joins = previous != null &&
                    previous.last() == index - 1 &&
                    // Capped because the convention is a sentence spilling over,
                    // not a monologue: past three balloons a run of ellipses is
                    // far more likely to be several speakers trailing off.
                    previous.size < MAX_BUBBLES_PER_UTTERANCE &&
                    continues(texts[index - 1], texts[index])
            if (joins) previous.add(index) else groups.add(mutableListOf(index))
        }
        return groups
    }

    /**
     * Whether [next] carries on from [current].
     *
     * Both sides must agree. An ellipsis at the end of one balloon alone is
     * ordinary trailing off, and an ellipsis opening one alone is ordinary
     * hesitation; it is the pair that means the sentence was cut.
     */
    private fun continues(current: String, next: String): Boolean {
        val left = current.trimEnd()
        val right = next.trimStart()
        if (left.isEmpty() || right.isEmpty()) return false
        if (!ELLIPSIS_END.containsMatchIn(left)) return false
        if (!ELLIPSIS_START.containsMatchIn(right)) return false
        // A balloon that ends in a question or an exclamation has finished its
        // sentence whatever it trails into. "Really...?" then "...I see" is two
        // utterances, and joining them produced a question with an answer
        // welded onto it.
        return !left.dropLastWhile { it in ELLIPSIS_CHARS }.endsWith('?') &&
                !left.dropLastWhile { it in ELLIPSIS_CHARS }.endsWith('!')
    }

    /** The text handed to the translator for one group. */
    fun join(parts: List<String>): String =
        parts.mapIndexed { index, part ->
            var text = part.trim()
            // The ellipses were the seam, not punctuation the reader wants back
            // in the middle of a sentence.
            if (index > 0) text = text.trimStart { it in ELLIPSIS_CHARS }.trimStart()
            if (index < parts.lastIndex) text = text.trimEnd { it in ELLIPSIS_CHARS }.trimEnd()
            text
        }.filter { it.isNotEmpty() }.joinToString(" ")

    /**
     * Spreads one translation back over the balloons it came from.
     *
     * Word for word alignment is not available and is not worth building: what
     * matters is that each balloon holds roughly its share of the sentence, so
     * the reader's eye travels the same way it would have. Split at word
     * boundaries in proportion to how much of the source each balloon held.
     *
     * The ellipses come back, because on the page they are what tells the
     * reader the sentence carries into the next balloon.
     */
    fun distribute(translated: String, sources: List<String>): List<String> {
        if (sources.size == 1) return listOf(translated)
        val words = translated.trim().split(WHITESPACE).filter { it.isNotEmpty() }
        if (words.size < sources.size) {
            // Fewer words than balloons: any split would leave one empty, which
            // reads as a bubble the translator swallowed. Keep it whole on the
            // first and blank the rest — the overlay draws nothing for those.
            return List(sources.size) { if (it == 0) translated else "" }
        }

        val weights = sources.map { it.trim().length.coerceAtLeast(1) }
        val total = weights.sum()
        val out = mutableListOf<String>()
        var taken = 0
        for ((index, weight) in weights.withIndex()) {
            val remaining = sources.size - index - 1
            val share = if (index == weights.lastIndex) words.size - taken
            else ((words.size * weight.toDouble() / total).toInt())
                .coerceIn(1, words.size - taken - remaining)
            var piece = words.subList(taken, taken + share).joinToString(" ")
            if (index > 0) piece = "…$piece"
            if (index < weights.lastIndex) piece = "$piece…"
            out.add(piece)
            taken += share
        }
        return out
    }

    private const val MAX_BUBBLES_PER_UTTERANCE = 3
    private const val ELLIPSIS_CHARS = "….·"
    private val ELLIPSIS_END = Regex("[….]\\s*$")
    private val ELLIPSIS_START = Regex("^\\s*[….]")
    private val WHITESPACE = Regex("\\s+")
}
