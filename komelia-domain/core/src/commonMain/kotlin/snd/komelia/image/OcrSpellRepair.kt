package snd.komelia.image

/**
 * Repairs the strokes the recogniser confuses, before the text is translated.
 *
 * One inserted stroke can invert a sentence. Measured end to end on Ramen Aka
 * Neko 164 page 13: at the size the page is scanned, PP-OCRv6 read UNTRAINED as
 * UINTRAINED, and Bergamot returned "un chat entraîné" — the opposite of what
 * the balloon said. Handed the same sentence spelled right, the same engine
 * returns "un chat non entraîné". Neither the detector nor the translator was
 * at fault, and no amount of tuning either one fixes this.
 *
 * The confusion is one family: 'u' drawn in comic lettering comes back as 'li',
 * 'ui', 'ii' or 'll'. Over two full chapters it produced PUIDDING, UINIQUE,
 * AMATEUIR, UISUALLY, MLICH and LIMM — roughly three a chapter, every one of
 * them a word the translator then had to guess at. 'rn' for 'm' and 'cl' for
 * 'd' are kept as the two classics; they fired on nothing in the sample, which
 * is a reason to keep them cheap, not a reason to drop them.
 *
 * What was measured and thrown out matters as much: 'l' for 'i' and the digit
 * confusions (0/o, 1/l, 5/s, 8/b) produced only false repairs — WEL became
 * "wei", a bare M became "rn" — and no true one. They are not in the table.
 *
 * Two rules keep it honest. A token already in the lexicon is never touched, so
 * real words cannot be damaged by construction. A token with two possible
 * repairs is left alone, because choosing between them is how a repair starts
 * inventing words of its own. Across 28 pages that gave 7 repairs and no
 * damage.
 */
object OcrSpellRepair {

    /**
     * The shipped word list, loaded once from a resource the way [PhraseBook]
     * is. Empty until then, and an empty lexicon repairs nothing rather than
     * repairing wrongly.
     */
    private var lexicon: Set<String> = emptySet()

    val isLoaded: Boolean get() = lexicon.isNotEmpty()

    fun load(words: Set<String>) {
        if (lexicon.isEmpty()) lexicon = words
    }

    /**
     * Rewrites the tokens of [text] that are misreads, leaving everything else
     * exactly as it was — including spacing and punctuation, which the caller's
     * later stages depend on.
     */
    fun apply(text: String): String {
        if (lexicon.isEmpty() || text.isEmpty()) return text
        return TOKEN.replace(text) { match ->
            repair(match.value) ?: match.value
        }
    }

    /**
     * The repaired spelling of one token, or null to leave it alone.
     *
     * The answer carries the token's own casing back: comic lettering is full
     * caps, and [TranslationTextUtils.toSentenceCase] decides what to lower by
     * looking for a lowercase letter. Returning "untrained" into an all-caps
     * balloon would tell it the balloon was mixed case and stop it lowering the
     * rest, which costs more than the repair gains.
     */
    internal fun repair(token: String): String? {
        if (token.length < MIN_LENGTH) return null
        val lowered = token.lowercase()
        if (lowered in lexicon) return null
        var answer: String? = null
        for (candidate in candidates(lowered)) {
            if (candidate.length < MIN_LENGTH || candidate !in lexicon) continue
            // A second reading means abstain, not pick.
            if (answer != null && answer != candidate) return null
            answer = candidate
        }
        return answer?.let { matchCase(token, it) }
    }

    /** Every single-substitution rewrite of [word], in both directions. */
    private fun candidates(word: String): Set<String> {
        val out = mutableSetOf<String>()
        for ((a, b) in CONFUSIONS) {
            for ((from, to) in listOf(a to b, b to a)) {
                var start = 0
                while (true) {
                    val at = word.indexOf(from, start)
                    if (at < 0) break
                    out += word.substring(0, at) + to + word.substring(at + from.length)
                    start = at + 1
                }
            }
        }
        out -= word
        return out
    }

    /** All caps in, all caps out; anything else keeps the lexicon's spelling. */
    private fun matchCase(original: String, repaired: String): String {
        val letters = original.filter { it.isLetter() }
        return if (letters.isNotEmpty() && letters.none { it.isLowerCase() }) repaired.uppercase()
        else repaired
    }

    /**
     * Below three characters there is not enough left of the word to tell a
     * misread from a real short word, and the sample proved it: a bare "M"
     * became "rn" and "0e" became "oe".
     */
    private const val MIN_LENGTH = 3

    private val CONFUSIONS = listOf(
        "li" to "u", "ui" to "u", "ii" to "u", "iu" to "u", "ll" to "u",
        "rn" to "m", "cl" to "d",
    )

    /** Letters, digits and the apostrophe: "don't" is one token, not two. */
    private val TOKEN = Regex("[\\p{L}\\p{N}']+")
}
