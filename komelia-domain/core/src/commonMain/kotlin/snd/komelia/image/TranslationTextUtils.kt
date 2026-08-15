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

    /** Ends a spoken line. A sound effect drawn on the artwork has none of these. */
    private val sentenceEnd = setOf('.', '!', '?', '…', ':', ',', '"', '\'', '-')

    /**
     * Animal cries, which are sound effects wearing a speech balloon.
     *
     * Only ever matched as the whole utterance, so a balloon reading "Meow!" is
     * a cat and one reading "Did the cat meow?" is a question. Kept narrow for
     * the words that double as ordinary English — "purr", "growl", "hiss" and
     * "roar" are verbs, and it is the balloon holding nothing else that makes
     * them a noise here.
     */
    private val animalCries = setOf(
        "meow", "mew", "mreow", "nya", "nyaa", "nyan", "purr", "hiss",
        "woof", "arf", "bark", "bow-wow", "yip", "yap", "howl", "growl",
        "moo", "baa", "oink", "neigh", "quack", "cluck", "cock-a-doodle-doo",
        "chirp", "tweet", "caw", "hoot", "squeak", "ribbit", "croak", "roar",
    )

    /**
     * A sound effect painted onto the artwork rather than words in a bubble.
     *
     * Translating them produced nonsense — 'Ping' came back as 'Table de Ping',
     * 'Whack' as 'Couper', 'Seizure' as 'Saisie' — and each one had an opaque
     * panel painted over the drawing to say so. English sound effects read fine
     * in French, so the drawing is left alone.
     *
     * The test is bare lettering: one word, or the same word twice ('Twitch
     * twitch', 'Rattle rattle'), with nothing that ends a spoken line. Checked
     * against a full volume, that keeps every one-word line of real dialogue —
     * 'Correct.', 'Ever.', 'Quite.', 'Wait.', 'Huh?!' all carry punctuation —
     * and two-word fragments of a sentence spread over several bubbles, such as
     * 'Romantic match' or 'So happy', are left translated because their words
     * differ.
     */
    fun isSoundEffect(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false

        // An animal in a comic gets a speech balloon like anyone else, so its
        // cry arrives punctuated like dialogue and the rule below lets it
        // through: "Meow!" was translated, and a cat saying "Miaou" in French
        // is not what the balloon said. Checked before the punctuation test for
        // exactly that reason.
        val bare = trimmed.trim { !it.isLetter() }
        if (bare.isNotEmpty()) {
            val cry = bare.split(' ').filter { it.isNotBlank() }
            val single = cry.size == 1 || (cry.size == 2 && cry[0].equals(cry[1], ignoreCase = true))
            if (single && cry[0].trim { !it.isLetter() }.lowercase() in animalCries) return true
        }

        if (trimmed.last() in sentenceEnd) return false

        val words = trimmed.split(' ').filter { it.isNotBlank() }
        return when (words.size) {
            1 -> true
            2 -> words[0].equals(words[1], ignoreCase = true)
            else -> false
        }
    }

    /**
     * Whether the balloon was lettered in capitals.
     *
     * Comics are drawn in capitals throughout, so this is not about shouting —
     * it is the ordinary look of the page, and a translation in sentence case
     * sitting next to untranslated balloons in capitals reads as a different
     * book. The information is there in what the recogniser returned and was
     * being thrown away: [toSentenceCase] runs before translation on purpose,
     * because capitals measurably degrade what the engine returns.
     *
     * Needs at least two letters, so "I" and a stray "A" do not drag a whole
     * balloon into capitals. A single lowercase letter anywhere is enough to
     * say no, which is what keeps a balloon the letterer set in mixed case out
     * of this.
     */
    fun isAllCaps(text: String): Boolean {
        val letters = text.filter { it.isLetter() }
        return letters.length >= 2 && letters.none { it.isLowerCase() }
    }

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

    /**
     * "MAMA-SAN" reads badly inside a French sentence; "Mama-san" does not.
     * The honorific stays lowercase — it is a suffix, not a second name.
     */
    private fun titleCase(name: String): String = name
        .split("-")
        .mapIndexed { index, part ->
            when {
                part.isEmpty() -> part
                index == 0 -> part[0].uppercase() + part.substring(1).lowercase()
                else -> part.lowercase()
            }
        }
        .joinToString("-")

    /** Standalone "i" after lowercasing — the English pronoun, and "i'm", "i've". */
    private val lonePronounI = Regex("""\bi\b""")

    /**
     * Turns comic lettering into normally cased text before translating it.
     *
     * Bubbles are drawn in full caps. Translation models are trained on cased
     * text, so an all-caps sentence is out of distribution and comes back
     * mangled ("IT'S CONCERNING" -> "c'est en ce qui concerne"). Text that
     * already has lowercase letters is left alone — it is either a caption
     * drawn that way or a shop sign, and is already in distribution.
     */
    fun toSentenceCase(text: String): String {
        val letters = text.filter { it.isLetter() }
        if (letters.isEmpty() || letters.any { it.isLowerCase() }) return text

        val sb = StringBuilder(text.length)
        var capitalizeNext = true
        for (c in text.lowercase()) {
            when {
                capitalizeNext && c.isLetter() -> {
                    sb.append(c.uppercaseChar())
                    capitalizeNext = false
                }

                c == '.' || c == '!' || c == '?' -> {
                    sb.append(c)
                    capitalizeNext = true
                }

                else -> sb.append(c)
            }
        }
        return lonePronounI.replace(sb.toString(), "I")
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
