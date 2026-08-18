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

    /*
     * There was an isAllCaps() here, used to letter the translation back in
     * capitals because that is how the untranslated balloons around it are
     * drawn. Tried on the tablet and rejected: a full balloon of French
     * capitals is harder to read than the English it replaced, and the effect
     * it was reaching for — the overlay looking like lettering rather than a
     * caption — comes from the weight, not the case. TranslationOverlay draws
     * bold instead. Do not bring the uppercase back.
     */

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
        val cased = lonePronounI.replace(sb.toString(), "I")
        val named = namedByHonorific.replace(cased) { it.value.replaceFirstChar(Char::uppercaseChar) }
        val addressed = addressedByName.replace(named) { m ->
            if (OcrSpellRepair.knows(m.value)) m.value
            else m.value.replaceFirstChar(Char::uppercaseChar)
        }
        return surnameAfterGivenName.replace(addressed) { m ->
            val (given, family) = m.destructured
            if (OcrSpellRepair.knows(given) || OcrSpellRepair.knows(family)) m.value
            else given + " " + family.replaceFirstChar(Char::uppercaseChar)
        }
    }

    /**
     * A name in front of an honorific, which lowering the balloon just took
     * the capital off.
     *
     * Measured: "BUT RITSU-KUN IS RIGHT" came out "but ritsu-kun is right",
     * and the translator read the name as a common noun and gave it an
     * article -- "Mais le ritsu-kun a raison". Only the first word of a
     * balloon gets its capital back otherwise, and a name rarely stands there.
     *
     * Built from the honorific list rather than spelled out again, so the two
     * cannot drift apart, and from the narrow half of it: "sun-tan" is not a
     * person.
     */
    /**
     * Somebody addressed by name, after the balloon was lowered.
     *
     * A comic is lettered in capitals, so the whole balloon is lowered before
     * translating, and a character's name goes down with it. The translator
     * then reads it as a common noun: "about women, rankin?" came back "sur les
     * femmes, le Rankin ?", article and all, and "Dammit, Fingerman, I thought
     * you were top five" lost the name entirely to "Bourdonne, je croyais".
     *
     * Only after a comma, which is where a name being spoken to sits, and only
     * for a word the shipped lexicon does not know. Both halves of that were
     * measured, on the 1208 balloons of the bench:
     *
     *  - anywhere in the sentence: 202 balloons changed, roughly twelve gains
     *    against six losses. Ordinary English the 37166-word list happens not
     *    to carry stopped being translated -- "weren't Halfhard firing" kept
     *    "Halfhard", "its Turbid waters" kept "Turbid" -- and the capitals
     *    leaked into the French, where "You Wot!" came back "Vous Avez Woot !".
     *  - after a comma only: 35 balloons, about twenty gains against two, and
     *    no leaked capitals. Olto seven times, Fingerman four, Kovacs and
     *    Takeshi three each.
     *
     * The two it still costs are a real word the list is missing, sitting where
     * a name would: "cleric, Swordfighter, etc." and "junkie, Bonebag gums".
     * That is the price of the rule and it is worth paying at ten to one.
     */
    private val addressedByName = Regex("(?<=, )[a-z]{3,}")

    /**
     * A family name behind a given name.
     *
     * Lowering the balloon leaves a capital on the first word of a sentence
     * only, so "TAKESHI KOVACS!" comes out "Takeshi kovacs!" and the
     * translator reads the surname as a common noun. Two words the shipped
     * lexicon knows neither of, one behind the other, are a person.
     */
    private val surnameAfterGivenName = Regex("""\b([A-Z][a-z]{2,})\s+([a-z]{3,})\b""")

    private val namedByHonorific = Regex(
        "\\b[a-z][a-z]*(?=-(?:" +
            OcrSpellRepair.NAME_HONORIFICS.joinToString("|") +
            ")\\b)"
    )

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
