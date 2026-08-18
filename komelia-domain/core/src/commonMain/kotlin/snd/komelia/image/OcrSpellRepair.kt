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
            repair(match.value) ?: split(match.value) ?: match.value
        }
    }

    /**
     * Two words the recogniser ran together, put back apart.
     *
     * PP-OCRv6 drops the space between words often enough on comic lettering to
     * matter. Measured on real pages: "hurt my leg" came back "hurt myleg" and
     * the translator answered "blesser myleg" — an unknown token passes through
     * untranslated and takes the sentence with it. "the old man" came back
     * "theold man" and lost "vieil" exactly the same way.
     *
     * That the recogniser is where the space goes was established by
     * elimination, not assumed. Nothing between it and here can remove one: the
     * merge step only regroups boxes and never touches their text, the elements
     * of a block are joined *with* a space, [TranslationTextUtils.rejoinLineBreaks]
     * needs a hyphen to fire, and this object works one token at a time.
     *
     * Same two rules as [repair], for the same reason. A token the lexicon
     * knows is never touched, so "cannot", "anymore" and "himself" cannot be
     * pulled apart by construction. A token that splits two ways is left alone
     * rather than guessed at.
     */
    internal fun split(token: String): String? {
        if (token.length < MIN_SPLIT_LENGTH) return null
        // A contraction is one word with a mark in it. Cutting "you're" gives
        // two fragments, not two words.
        if (APOSTROPHE in token) return null
        val lowered = token.lowercase()
        if (lowered in lexicon) return null
        var answer: String? = null
        for (at in MIN_PART..(lowered.length - MIN_PART)) {
            val head = lowered.substring(0, at)
            if (!isWholeWord(head)) continue
            val tail = lowered.substring(at)
            if (!isWholeWord(tail)) continue
            // A second reading means abstain, not pick.
            if (answer != null) return null
            answer = "$head $tail"
        }
        return answer?.let { matchCase(token, it) }
    }

    /**
     * Whether a half is a word a balloon could contain.
     *
     * Two-letter halves are checked against a closed list rather than the
     * lexicon, because the shipped word list is not one: it carries "ld", "ms",
     * "eg" and other fragments that no reader ever sees on a page. They are
     * harmless everywhere else and ruinous here — "theold" reads as both "the
     * old" and "theo ld" against the raw lexicon, so the splitter saw two
     * readings and abstained on the very case it exists for.
     */
    private fun isWholeWord(part: String): Boolean = when {
        part.length < MIN_PART -> false
        part.length == MIN_PART -> part in SHORT_WORDS
        else -> part in lexicon
    }

    /** Every two-letter word English actually uses in dialogue. */
    private val SHORT_WORDS = setOf(
        "am", "an", "as", "at", "be", "by", "do", "go", "he", "hi", "if", "in",
        "is", "it", "me", "my", "no", "of", "oh", "ok", "on", "or", "so", "to",
        "up", "us", "we",
    )

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

    /**
     * Below five characters a run-together cannot be told from a short word the
     * lexicon happens not to carry, and two-letter halves are what make it
     * work at all — "myleg" is "my leg" and the "my" is only two letters.
     * Five is the shortest length at which both halves can clear that bar.
     */
    private const val MIN_SPLIT_LENGTH = 5
    private const val MIN_PART = 2
    private const val APOSTROPHE = '\''

    private val CONFUSIONS = listOf(
        "li" to "u", "ui" to "u", "ii" to "u", "iu" to "u", "ll" to "u",
        // The same 'u' read as a single thin stroke rather than two: "I've had
        // enough" came back "I've had enoigh". Added after measuring, not on
        // the strength of the one case -- over the same 28 pages it repairs
        // ENOIGH and changes nothing else, false or true.
        "i" to "u",
        "rn" to "m", "cl" to "d",
    )

    /** Letters, digits and the apostrophe: "don't" is one token, not two. */
    private val TOKEN = Regex("[\\p{L}\\p{N}']+")
}
