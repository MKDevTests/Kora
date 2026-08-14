package snd.komelia.image

/**
 * Cleans up OCR text before translation, and puts back what translation should
 * not have touched.
 *
 * Both fixes come from reading actual output: a word broken across two lines
 * becomes an invented word once translated ("A BUSI- NESS CARD?" came back as
 * "une carte de bus"), and Japanese honorifics get translated as if they were
 * common nouns ("MAMA-SAN" became "Maman-san").
 */
object TranslationTextUtils {

    private val honorifics = setOf(
        "san", "chan", "kun", "sama", "dono", "senpai", "sempai", "sensei", "tan",
    )

    /** A name plus its honorific, in either the source or the translated text. */
    private val honorificName = Regex(
        """\p{L}+-(?:san|chan|kun|sama|dono|senpai|sempai|sensei|tan)\b""",
        RegexOption.IGNORE_CASE,
    )

    /** Word split across two lines: "BUSI- NESS" — the space comes from the line join. */
    private val lineBreakHyphen = Regex("""(\p{L})-\s+(\p{L}+)""")

    /**
     * Rejoins words the lettering broke across lines.
     *
     * "BUSI- NESS" is one word and must lose the hyphen; "MAMA- SAN" is a name
     * whose hyphen belongs there. The honorific list is what tells them apart —
     * without it, dropping every hyphen turns Mama-san into Mamasan.
     *
     * Repeated because a single pass cannot rejoin "A- B- C": the regex resumes
     * after the first match, past the second hyphen's leading letter.
     */
    fun rejoinLineBreaks(text: String): String {
        var result = text
        repeat(3) {
            val next = lineBreakHyphen.replace(result) { match ->
                val before = match.groupValues[1]
                val after = match.groupValues[2]
                if (after.lowercase() in honorifics) "$before-$after" else "$before$after"
            }
            if (next == result) return result
            result = next
        }
        return result
    }

    /**
     * Puts the original names back after translation.
     *
     * Honorifics survive translation as a suffix but the name in front of them
     * gets translated word-for-word. Names are matched in order of appearance,
     * which holds because translation preserves sentence order for the short
     * fragments a speech bubble contains.
     */
    fun restoreNames(source: String, translated: String): String {
        val names = honorificName.findAll(source).map { it.value }.toList()
        if (names.isEmpty()) return translated
        var index = 0
        return honorificName.replace(translated) { match ->
            if (index < names.size) titleCase(names[index++]) else match.value
        }
    }

    /** "MAMA-SAN" reads badly inside a French sentence; "Mama-san" does not. */
    private fun titleCase(name: String): String = name
        .split("-")
        .joinToString("-") { part ->
            if (part.isEmpty()) part
            else part[0].uppercase() + part.substring(1).lowercase()
        }

    /**
     * True when translating produced nothing new. Sound effects come back
     * unchanged ("MEOW!", "SLURRRP", "CLUNK"), and covering them with an opaque
     * panel hides the artwork to show the same word.
     */
    fun isUnchanged(source: String, translated: String): Boolean =
        normalise(source) == normalise(translated)

    private fun normalise(text: String) = text
        .lowercase()
        .filter { it.isLetterOrDigit() }
}
