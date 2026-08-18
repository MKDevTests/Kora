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
    fun lookup(text: String): String? {
        val key = normalise(text)
        return ENTRIES[key] ?: bulk[key]
    }

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
