package snd.komelia.image

/**
 * Takes back the repetition the decoder invented.
 *
 * A beam-1 decoder sometimes falls into a loop and emits the same word over and
 * over. Measured over the 1 646 Japanese bubbles of the bench corpus, it does so
 * on 9 of them, and the loop does not only produce noise -- twice it ruins a
 * sentence that was otherwise fine:
 *
 *     そういやハクは呪いに効く温泉を探してたっけ
 *       -> "Non, non, non, non. Haku cherchait une source chaude..."
 *     やだなあお客様にそんなふうに言われるなんて…
 *       -> "Non, non, non, non. Je ne peux pas dire ça à mes clients..."
 *
 * So the repair is to collapse the run rather than to reject the translation:
 * rejecting would throw away the half that is right.
 *
 * ## Why the source has to be consulted
 *
 * "Collapse a word repeated three times or more" is the obvious rule and it is
 * wrong. Run over the 1 753 English bubbles it changes 12, and all 12 are
 * losses, because in comics the repetition is usually IN THE ART:
 *
 *     "Mwa ha ha ha ha ha!"                    -> "Mwa ha !"
 *     "Whoa, whoa, whoa! Put that away."       -> "Whoa ! Put that away."
 *     "The 100 Girlfriends Who Really, Really, Really, Really, REALLY Love You"
 *
 * The last one is the title of a series. A letterer who writes a word five times
 * means it five times, and the translator that repeats it is doing its job.
 *
 * What separates the two cases is not the output but whether the SOURCE
 * repeated. With that condition the same rule changes 9 Japanese bubbles and
 * zero English ones.
 *
 * A single character repeated -- ええええ, "aaaah" -- is a drawn-out sound, not a
 * repetition, so it does not count as one; that is what keeps だめえ～♡ eligible.
 * But 待て待て does repeat a segment, and its "Attends, attends, attends..." is
 * left alone. That is the price of the condition and it is worth paying at 12
 * losses avoided.
 *
 * ## Not covered by the bench
 *
 * VolumeReplayTest stops at the sentence handed to the engine, so it never sees
 * this stage. The tests in this file are the only automated cover; the numbers
 * above come from running the shipped pivot over the corpus by hand.
 */
object TranslationOutputRepair {

    /**
     * [source] is the sentence that was sent to the engine, in the original
     * language, and [translated] is what came back.
     */
    fun collapseInventedRepeats(source: String, translated: String): String {
        if (translated.isEmpty()) return translated
        // The source already repeats something, so the output repeating it is
        // very likely faithful. Nothing to take back.
        if (sourceRepeats(source)) return translated
        return collapse(translated)
    }

    /**
     * Deliberately not a regex.
     *
     * The first version of this was one, it passed against a Python prototype
     * over both corpora, and it did nothing in the app: `\w` is Unicode in
     * Python and `[a-zA-Z0-9_]` in Kotlin, so neither 待て nor a French word
     * with an accent was ever a "word". Splitting on separators has no such
     * gap, and reads as what it does.
     */
    private fun collapse(text: String): String {
        val tokens = tokenise(text)
        if (tokens.size < 3) return text
        val out = StringBuilder()
        var i = 0
        var changed = false
        while (i < tokens.size) {
            var run = 1
            while (i + run < tokens.size && sameWord(tokens[i], tokens[i + run])) run++
            // Three, not two, because French repeats a word legitimately:
            // collapsing at two turned "jusqu'à ce que nous nous retrouvions"
            // into "jusqu'à ce que nous retrouvions", and did the same to every
            // reflexive "vous vous".
            if (run >= 3) {
                // The word comes from the FIRST of the run and the punctuation
                // from the LAST: "Non, non, non. Haku" has its capital on the
                // first and its full stop on the last, and taking either whole
                // gives "non. Haku" or "Non, Haku".
                val last = tokens[i + run - 1]
                out.append(tokens[i].word)
                out.append(last.raw.substring(last.word.length))
                changed = true
            } else {
                for (k in 0 until run) out.append(tokens[i + k].raw)
            }
            i += run
        }
        return if (changed) out.toString().trim() else text
    }

    /**
     * A segment of two or more characters repeated back to back (待て待て), or a
     * whole word repeated with separators between it ("ha ha ha", "Really,
     * Really"). Deliberately NOT a single repeated character, which is an
     * elongated sound rather than a repetition -- that is what keeps だめえ～
     * eligible while 待て待て is protected.
     */
    private fun sourceRepeats(source: String): Boolean {
        val tokens = tokenise(source)
        for (i in 0 until tokens.size - 1) {
            if (sameWord(tokens[i], tokens[i + 1])) return true
        }
        val bare = source.filterNot { it.isWhitespace() || it in SEPARATORS }
        for (len in 2..bare.length / 2) {
            for (at in 0..bare.length - 2 * len) {
                if (bare.regionMatches(at, bare, at + len, len)) return true
            }
        }
        return false
    }

    private class Token(val raw: String, val word: String)

    private fun sameWord(a: Token, b: Token) =
        a.word.isNotEmpty() && a.word.equals(b.word, ignoreCase = true)

    /** Each token keeps its trailing separators, so rebuilding is exact. */
    private fun tokenise(text: String): List<Token> {
        val out = ArrayList<Token>()
        var i = 0
        while (i < text.length) {
            val start = i
            while (i < text.length && !text[i].isWhitespace() && text[i] !in SEPARATORS) i++
            val word = text.substring(start, i)
            while (i < text.length && (text[i].isWhitespace() || text[i] in SEPARATORS)) i++
            out.add(Token(text.substring(start, i), word))
        }
        return out
    }

    private const val SEPARATORS = ",;.!?:…。、"
}
