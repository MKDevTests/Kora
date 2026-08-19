package snd.komelia.image

/**
 * Answers the utterances a small NMT model reliably gets wrong, before it sees
 * them.
 *
 * Bergamot and ML Kit both translate an idiom word for word. Measured on real
 * pages: "Something the matter?" came back "Quelque chose la matière ?",
 * "it's concerning" became "c'est en ce qui concerne", "Pout" became "Tacaud".
 * These are not tuning problems — the models have no idiomatic reading of the
 * phrase to reach for, and a bigger model on a tablet is not the answer.
 *
 * Deliberately whole-utterance and exact. Substituting a phrase found inside a
 * longer sentence is the tempting next step and is how this kind of table
 * starts corrupting ordinary text: "give me a break" is "tu plaisantes",
 * "fous-moi la paix" or "laisse-moi souffler" depending on what surrounds it,
 * and nothing here can tell which. A balloon that *is* the phrase has no such
 * ambiguity, and comic lettering puts idioms in balloons of their own more
 * often than prose does.
 *
 * Structured so a Wiktionary-derived dataset can be poured in later without
 * changing callers: this is the curated tier of the document's
 * "User TM → curated TM → engine" order.
 */
object PhraseBook {

    /**
     * The bulk table, installed once from the shipped resource.
     *
     * Kept out of the source and read from a file because there are two
     * thousand of them: a `mapOf` literal that size does not survive the
     * 255-argument limit, which has already cost builds on the string
     * catalogue. Empty until [load] runs, and the curated table below works on
     * its own until then -- a reader who opens a page before the file is read
     * gets slightly worse translations, not a crash.
     */
    private var bulk: Map<String, String> = emptyMap()

    /**
     * Installs the shipped table. Idempotent, and the first call wins: this is
     * reference data that does not change while the app is running.
     */
    fun load(entries: Map<String, String>) {
        if (bulk.isEmpty()) bulk = entries
    }

    /** Whether [load] has run, so a caller can avoid re-reading the file. */
    val isLoaded: Boolean get() = bulk.isNotEmpty()

    /**
     * The French for [text], or null to let the engine do its job.
     *
     * The curated table wins. Its entries were each written against a page
     * where the engine was seen failing, and one of them disagreeing with the
     * general list means the general list is wrong for comics.
     */
    fun lookup(text: String): String? = lookupTraced(text)?.french

    /**
     * The same answer as [lookup], with the key that matched and the tier that
     * held it.
     *
     * Exists because the bench cannot see this table -- it replays the Kotlin
     * pipeline up to the engine, and [lookup] is called past that point, in the
     * reader. A run of the bench reporting "0 changed" after a batch of entries
     * was added is therefore meaningless, and nearly cost a batch that was
     * correcting twenty-two balloons on the tablet. Until the bench is wired
     * end-to-end, the log is the only place the table's effect is visible, and
     * a hit without its key cannot be told from the engine happening to agree.
     */
    fun lookupTraced(text: String): Answer? {
        val key = normalise(text)
        ENTRIES[key]?.let { return Answer(dressed(text, it), key, Tier.CURATED) }
        bulk[key]?.let { return Answer(dressed(text, it), key, Tier.BULK) }
        return null
    }

    /** Gives the answer the capital and the ending the balloon was lettered with. */
    private fun dressed(balloon: String, answer: String): String =
        matchClosingPunctuation(balloon, matchOpeningCase(balloon, answer))

    /** A phrase book hit: what it answered, on which key, from which tier. */
    data class Answer(val french: String, val key: String, val tier: Tier)

    /**
     * Which table answered.
     *
     * Worth separating in the log because the two are maintained differently:
     * [CURATED] entries were each written against a page where the engine was
     * seen failing, so a bad one is a mistake to fix, while [BULK] comes from a
     * general expression list and a bad one is a candidate for removal.
     */
    enum class Tier { CURATED, BULK }

    /**
     * Gives the answer the capital the balloon had.
     *
     * 1276 of the 2046 shipped entries are written lowercase -- "about time"
     * answers "c'est pas trop tot" -- because the table was built as a
     * dictionary of expressions rather than of lines. Read as a balloon that
     * lands mid-page in lowercase, which is what "je suis vraiment desole" and
     * "oh la la" were.
     */
    private fun matchOpeningCase(balloon: String, answer: String): String {
        val opening = balloon.firstOrNull { it.isLetter() } ?: return answer
        if (!opening.isUpperCase()) return answer
        return answer.replaceFirstChar { it.uppercaseChar() }
    }

    /**
     * Gives the answer back the shout or the question the balloon was drawn
     * with.
     *
     * Same reason as [matchOpeningCase], and found the same way. The shipped
     * table is a dictionary of expressions, so "shove off" answers "fiche le
     * camp" with no punctuation at all -- and a balloon lettered "SHOVE OFF!"
     * came out flat while the engine it replaced had kept the exclamation.
     * Measured once the bench could finally see this table: six of the
     * forty-four balloons it answers lost their ending that way.
     *
     * Only when the answer has no strong punctuation of its own. An entry that
     * ends in "?" was written that way on purpose -- "huh" answers "Hein ?" --
     * and a balloon lettered "HUH?!" must not turn it into "Hein ??!". A full
     * stop is replaced rather than kept, since the balloon disagrees with it.
     */
    private fun matchClosingPunctuation(balloon: String, answer: String): String {
        val trimmed = answer.trimEnd()
        if (trimmed.isEmpty()) return answer
        if (trimmed.last() in STRONG_PUNCTUATION) return answer
        val ending = balloon.trimEnd().takeLastWhile { it in STRONG_PUNCTUATION }
        if (ending.isEmpty()) return answer
        // French sets a space before these; the curated entries are written
        // that way, so an answer that gains one has to match them.
        return trimmed.trimEnd('.') + " " + ending
    }

    private const val STRONG_PUNCTUATION = "!?"

    /**
     * Strips everything that does not change which phrase this is.
     *
     * Comic lettering varies the punctuation freely — "Something the matter?"
     * and "Something the matter?!" are the same line — and the OCR pipeline has
     * already lowercased the text by this point, but not always.
     */
    internal fun normalise(text: String): String = text
        .lowercase()
        .replace(TYPOGRAPHIC_APOSTROPHE, '\'')
        .filter { it.isLetterOrDigit() || it == '\'' || it.isWhitespace() }
        .split(WHITESPACE)
        .filter { it.isNotEmpty() }
        .joinToString(" ")

    private const val TYPOGRAPHIC_APOSTROPHE = '’'
    private val WHITESPACE = Regex("\\s+")

    /**
     * Keys are already normalised — written that way rather than run through
     * [normalise] at startup, so a malformed key is a visible mistake in the
     * table instead of a silent miss at runtime.
     *
     * Every entry here was seen failing on a real page. This is not a general
     * idiom dictionary and should not grow into one by guesswork.
     */
    private val ENTRIES: Map<String, String> = mapOf(
        // Measured failures, with what the engine actually returned.
        "something the matter" to "Un problème ?",                  // "Quelque chose la matière ?"
        "it's concerning" to "C'est inquiétant.",                   // "c'est en ce qui concerne"
        "whip one right up" to "Je vous prépare ça tout de suite.",
        "i'll whip one right up" to "Je vous prépare ça tout de suite.",
        "pout" to "Moue.",                                          // "Tacaud"
        "no news is good news" to "Pas de nouvelles, bonnes nouvelles.",
        "they say no news is good news right" to
                "On dit que pas de nouvelles, c'est bonnes nouvelles, non ?",
        // Measured 2026-08-19 on an English manga, with what Bergamot returned.
        // Second pass over the same volume, once the thirteen above were in.
        "quite" to "En effet.",                                     // "Tout assez."
        "and what's more" to "Et il y a mieux.",                    // "Et ce qui est de plus"
        "it's no trouble" to "De rien.",                            // "Ce n'est pas des ennuis."
        "always happy to help" to "Toujours ravi d'aider.",
        "get your act together" to "Ressaisis-toi !",               // "Faites votre acte ensemble"
        "dang it" to "Zut !",                                       // laisse "dang it" en anglais
        "thank you" to "Merci.",                                    // "Merci... Vous."
        "who does that" to "Qui ferait ça ?",
        "guess there's always tomorrow" to "Il y aura toujours demain.",
        "oh right" to "Ah, c'est vrai.",                            // "Oh, c'est ça."
        "head over heels in love" to "Follement amoureux.",
        "huh" to "Hein ?",                                          // "Heint ?"
        "gasp" to "Ha !",                                           // "Halez-vous !"
        "worry not" to "N'aie crainte.",                            // "Inquiète-toi non."
        "love is blind" to "L'amour est aveugle.",                  // "Amour est aveugle."
        "see you later" to "À plus tard !",                         // "On vous voit plus tard !"
        "which is it then" to "Alors, c'est quoi ?",                // "C'est qui, alors ?"
        "what's that for" to "C'est pour quoi faire ?",             // "C'est à quoi ?"
        "would you like one" to "Tu en veux une ?",                 // "Voulez-vous un?"
        "what are you trying to get at" to "Où veux-tu en venir ?", // "À quoi essayez-vous de vous rendre ?"
        "but you must beware" to "Mais prends garde.",              // "Mais vous devez vous méfie."
        "head over heels" to "Fou amoureux.",
        "in short love at first sight" to "Bref : le coup de foudre.", // "Bref: le amour à première vue."
        // Measured 2026-08-18 on an English comic, with what Bergamot returned.
        "no looking back" to "On ne regarde pas en arrière.",       // "Pas de recul."
        "we'll be fine" to "Ça ira.",                               // "Nous serons bien,"
        "new year's" to "Le Nouvel An",                             // "Nouvelles années"

        // Set phrases a reader meets constantly and that go word for word.
        "thanks for the meal" to "Merci pour le repas !",
        "thanks for waiting" to "Merci d'avoir patienté !",
        "time to dig in" to "À table !",
        "dig in" to "Bon appétit !",
        "right this way" to "Par ici, je vous prie.",
        "come on in" to "Entrez !",
        "keep the change" to "Gardez la monnaie.",
        "what brings you here" to "Qu'est-ce qui vous amène ?",
        "what's the occasion" to "Qu'est-ce qu'on fête ?",
        "that makes sense" to "Ça se tient.",
        "that does sound like him" to "Ça lui ressemble bien.",
        "don't mind me" to "Ne faites pas attention à moi.",
        "something's come up" to "Il y a un imprévu.",
        "what's up" to "Quoi de neuf ?",
        "what's the matter" to "Qu'est-ce qu'il y a ?",
        "is that so" to "Ah bon ?",
        "no way" to "Pas question !",
        "hold on" to "Attends !",
        "give me a break" to "Tu te fiches de moi ?",
        "you're kidding" to "Tu plaisantes !",
        "suit yourself" to "Comme tu veux.",
        "never mind" to "Laisse tomber.",
        "take care" to "Prends soin de toi.",
        "long time no see" to "Ça fait un bail !",
        "it's been a while" to "Ça faisait longtemps.",
        "here goes nothing" to "Advienne que pourra.",
        "i'm counting on you" to "Je compte sur toi.",
        "leave it to me" to "Laisse-moi faire.",
        "my bad" to "Au temps pour moi.",
        "not a chance" to "Aucune chance.",
        "makes no difference to me" to "Ça m'est égal.",
        "don't push your luck" to "Ne tente pas le diable.",
        "cut me some slack" to "Sois un peu indulgent.",
        "in over your head" to "Tu t'es mis dans un sacré pétrin.",
        "pull yourself together" to "Ressaisis-toi !",
    )
}
