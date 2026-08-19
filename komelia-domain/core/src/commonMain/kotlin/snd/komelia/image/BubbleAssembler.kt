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
     * Why each boundary between two consecutive blocks was or was not a seam.
     *
     * [group] answers what happened; this answers why, which is the question
     * that matters. A group welded from two speakers shows up on screen and
     * gets reported. A sentence that should have been joined and was not looks
     * exactly like an ordinary short balloon, so nobody reports it -- and
     * measured on four series, only one group ever reached the three-balloon
     * cap, meaning the cap is not where the losses are. The refusals are.
     *
     * Returns one decision per boundary, so element `i` describes the seam
     * between block `i` and block `i + 1` and the list is one shorter than
     * [texts]. Empty for fewer than two blocks.
     *
     * Costs a second pass over the page's text, which is why it is separate
     * from [group] rather than folded into it: the reader calls [group] on
     * every page and this only when the diagnostic log is being collected.
     */
    fun explain(texts: List<String>): List<Seam> {
        if (texts.size < 2) return emptyList()
        val sizes = IntArray(texts.size) { 1 }
        return (0 until texts.lastIndex).map { index ->
            val left = texts[index].trimEnd()
            val right = texts[index + 1].trimStart()
            val reason = when {
                left.isEmpty() || right.isEmpty() -> Seam.EMPTY
                !ELLIPSIS_END.containsMatchIn(left) && !ELLIPSIS_START.containsMatchIn(right) ->
                    Seam.NO_ELLIPSIS
                !ELLIPSIS_END.containsMatchIn(left) -> Seam.NO_ELLIPSIS_END
                !ELLIPSIS_START.containsMatchIn(right) -> Seam.NO_ELLIPSIS_START
                left.dropLastWhile { it in ELLIPSIS_CHARS }.endsWith('?') ||
                        left.dropLastWhile { it in ELLIPSIS_CHARS }.endsWith('!') ->
                    Seam.SENTENCE_ENDED
                sizes[index] >= MAX_BUBBLES_PER_UTTERANCE -> Seam.CAP_REACHED
                else -> Seam.JOINED
            }
            // Carries the running group size forward so CAP_REACHED can be told
            // apart from an ordinary refusal, which is the whole point of
            // knowing whether the cap costs anything.
            if (reason == Seam.JOINED) sizes[index + 1] = sizes[index] + 1
            reason
        }
    }

    /**
     * What one boundary between two blocks was.
     *
     * [NO_ELLIPSIS] is the overwhelming majority and is not interesting on its
     * own -- two unrelated balloons look exactly like that. The ones worth
     * counting are [NO_ELLIPSIS_END] and [NO_ELLIPSIS_START], where the
     * lettering agreed on one side only, and [CAP_REACHED], which is the
     * refusal the design documents keep proposing to relax.
     */
    enum class Seam {
        /** Joined into one utterance. */
        JOINED,

        /** Neither side carries the continuation ellipsis. */
        NO_ELLIPSIS,

        /** The next balloon opens with an ellipsis; this one does not end with one. */
        NO_ELLIPSIS_END,

        /** This balloon ends with an ellipsis; the next does not open with one. */
        NO_ELLIPSIS_START,

        /** The ellipsis was there, but the balloon had finished its sentence. */
        SENTENCE_ENDED,

        /** Both sides agreed and the three-balloon cap refused anyway. */
        CAP_REACHED,

        /** One of the two blocks held no text. */
        EMPTY,
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
     *
     * [avoidEndingOn] holds the words a balloon must not end on. A cut chosen
     * purely by proportion lands wherever the arithmetic falls, and measured
     * over the twenty-four multi-balloon groups in the bench corpus, eleven of
     * them ended a balloon on a preposition or an article: "il s'agit du rythme
     * du | jeu", "je n'ai pas d'infos sur | lui". The reader gets a balloon
     * that stops mid-phrase and has to hold it until the next one. Nudging the
     * cut by a word fixed all eleven and moved nothing else.
     *
     * The list is French because French is what the reader translates into.
     * Pass an empty set for another target rather than letting French function
     * words decide where German gets cut.
     */
    fun distribute(
        translated: String,
        sources: List<String>,
        avoidEndingOn: Set<String> = FRENCH_FUNCTION_WORDS,
    ): List<String> {
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
            else {
                val ceiling = words.size - taken - remaining
                val proportional = ((words.size * weight.toDouble() / total).toInt())
                    .coerceIn(1, ceiling)
                readableCut(proportional, words, taken, ceiling, avoidEndingOn)
            }
            var piece = words.subList(taken, taken + share).joinToString(" ")
            if (index > 0) piece = "…$piece"
            if (index < weights.lastIndex) piece = "$piece…"
            out.add(piece)
            taken += share
        }
        return out
    }

    /**
     * The nearest cut that does not leave a balloon hanging on a function word.
     *
     * Tried one word either side before two, so the balloons stay as close to
     * their proportional share as the sentence allows -- the share is what
     * keeps the reader's eye moving at the rate the lettering intended, and it
     * is only worth spending when the alternative is unreadable. Gives up and
     * returns [share] when every candidate is as bad, which is what happens to
     * a run of short function words.
     */
    private fun readableCut(
        share: Int,
        words: List<String>,
        taken: Int,
        ceiling: Int,
        avoidEndingOn: Set<String>,
    ): Int {
        if (avoidEndingOn.isEmpty() || !hangs(words, taken, share, avoidEndingOn)) return share
        for (delta in intArrayOf(-1, 1, -2, 2)) {
            val candidate = share + delta
            if (candidate < 1 || candidate > ceiling) continue
            if (!hangs(words, taken, candidate, avoidEndingOn)) return candidate
        }
        return share
    }

    /** Whether cutting after [share] words leaves the balloon on a function word. */
    private fun hangs(
        words: List<String>,
        taken: Int,
        share: Int,
        avoidEndingOn: Set<String>,
    ): Boolean {
        val last = words[taken + share - 1]
            .lowercase()
            .filter { it.isLetter() || it == '\'' }
        return last in avoidEndingOn
    }

    /**
     * French words that cannot end a readable fragment.
     *
     * Articles, prepositions, conjunctions, pronouns and the two auxiliaries --
     * the words that announce something and are meaningless without it. Not a
     * general stop-word list: "jamais", "rien" and "beaucoup" are frequent and
     * end a balloon perfectly well.
     */
    val FRENCH_FUNCTION_WORDS = setOf(
        "a", "à", "au", "aux", "de", "des", "du", "le", "la", "les", "l", "un", "une",
        "et", "ou", "où", "ni", "mais", "donc", "or", "car", "que", "qui", "quoi", "dont",
        "ce", "cet", "cette", "ces", "mon", "ton", "son", "ma", "ta", "sa",
        "mes", "tes", "ses", "notre", "votre", "leur", "leurs",
        "je", "tu", "il", "elle", "on", "nous", "vous", "ils", "elles",
        "me", "te", "se", "lui", "ne", "pas", "plus", "si", "très",
        "tout", "toute", "tous", "toutes",
        "pour", "par", "sans", "sous", "sur", "dans", "avec", "chez", "vers",
        "entre", "depuis", "pendant", "en", "y",
        "est", "sont", "était", "étaient", "été", "avoir", "être",
    )

    private const val MAX_BUBBLES_PER_UTTERANCE = 3
    private const val ELLIPSIS_CHARS = "….·"

    /**
     * An ellipsis, and nothing else. The character, or two or more full stops
     * standing in for it.
     *
     * These read `[….]` at first, which matches a single full stop — so every
     * balloon that simply ended its sentence was treated as trailing off. On a
     * real page that welded two speakers together: "Tetra-chan used to pretend
     * she was an untrained cat." went to the translator joined to "...With that
     * strange meowing just now?" from another panel, came back as one sentence,
     * and was then split across both balloons — the second one reading
     * "…formé avec cet étrange…" because "non entraîné" had been cut in half.
     *
     * A full stop ends a sentence. It is the ellipsis that says it did not.
     *
     * The surrounding `[^\w]*` is for what the recogniser does to a lettered
     * ellipsis, which is to read one dot of it as its own character and drop it
     * outside the run: ".….ARE LETTING UP!" and ".…SO MY STRENGTH..." both
     * carry the ellipsis, one stray full stop ahead of it. Anchoring on
     * whitespace alone missed those, and their balloons went to the translator
     * as sentence fragments -- "Neither of them..." and "...are letting up!"
     * translated apart. Measured over the eight bench volumes, 1 927
     * boundaries: five recovered, none lost. Still not `[….]`, which was the
     * founding mistake -- a single full stop must not open the run.
     */
    private val ELLIPSIS_END = Regex("(…|\\.{2,})[^\\w]*$")
    private val ELLIPSIS_START = Regex("^[^\\w]*(…|\\.{2,})")
    private val WHITESPACE = Regex("\\s+")
}
