package snd.komelia.image

/**
 * Drops the blocks that are not text before anything tries to translate them.
 *
 * Recognition over artwork returns confident nonsense rather than nothing:
 * "0e 000 200 200 20" off a bowl of soup, "Tay to" off a shop sign, "akaneg
 * JEWEL" off a logo. Each one is then translated, drawn as an opaque panel over
 * the drawing it came from, and costs the reader a bubble that says nothing.
 *
 * Deliberately conservative. Real lettering that gets dropped is worse than
 * noise that gets through, because the reader can ignore a nonsense bubble but
 * cannot recover a missing one. Every rule here therefore has to be something
 * no English sentence can look like.
 */
object EnglishTextCleaner {

    /** Whether the block carries something worth translating. */
    fun isTranslatable(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false

        val letters = trimmed.count { it.isLetter() }
        // Mostly digits and punctuation: a price, a phone number, or noise off
        // the artwork. Never a line of dialogue.
        if (letters * 2 < trimmed.count { !it.isWhitespace() }) return false

        // A long run of consonants with no vowel in it is not English. Short
        // ones are: "Mmm!", "Hmm", "Shh", "Tsk" are all real lettering, and the
        // first draft of this rule threw them away. Four letters is the cut --
        // the interjections are three or fewer, the invented runs over hatching
        // are longer.
        if (letters >= MIN_LETTERS_FOR_VOWEL && trimmed.none { it.lowercaseChar() in VOWELS }) {
            return false
        }

        return true
    }

    private const val VOWELS = "aeiouy"
    private const val MIN_LETTERS_FOR_VOWEL = 4
}
