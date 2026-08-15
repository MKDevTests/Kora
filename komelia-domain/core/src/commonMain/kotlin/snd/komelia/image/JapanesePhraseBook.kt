package snd.komelia.image

/**
 * The Japanese counterpart of [PhraseBook]: set utterances answered before the
 * engine sees them.
 *
 * Same rule, and for the same reason — whole balloon, exact match. Substituting
 * a phrase found inside a longer sentence is what turns a table like this into
 * a corruption engine, and Japanese makes it worse than English does: with no
 * spaces between words, a "contains" search matches across word boundaries that
 * are not there.
 *
 * Measured before shipping it, on 474 merged balloons of a real volume: as a
 * whole-balloon table it fired on none of them, and as a prefix it would have
 * fired on 2% of the sentences. That is not a reason to leave it out — the
 * volume is a yakuza title with very little everyday conversation, and the
 * table is 6000 entries of exactly that. It is a reason not to loosen the rule
 * to chase a number: every match it does make is one the engine would have got
 * wrong.
 */
object JapanesePhraseBook {

    private var entries: Map<String, String> = emptyMap()

    val isLoaded: Boolean get() = entries.isNotEmpty()

    val size: Int get() = entries.size

    /** Idempotent, first call wins. Reference data, fixed for the session. */
    fun load(table: Map<String, String>) {
        if (entries.isEmpty()) {
            entries = table.entries.associate { normalise(it.key) to it.value }
        }
    }

    internal fun resetForTest() {
        entries = emptyMap()
    }

    fun lookup(text: String): String? = entries[normalise(text)]

    /**
     * Strips what does not change which utterance this is.
     *
     * Terminal punctuation only, and both widths of it: the file was built with
     * it removed, the page has it, and manga lettering varies it freely (だよ,
     * だよ！, だよ！？ are one line). Nothing inside the sentence is touched —
     * a 、 in the middle separates clauses and belongs to the phrase.
     *
     * Whitespace goes too, because the merge joins a balloon's columns with no
     * separator but the OCR can still leave one at either end.
     */
    internal fun normalise(text: String): String =
        text.trim().trimEnd(*TERMINAL).trim()

    private val TERMINAL = charArrayOf(
        '。', '、', '！', '？', '…', '・', '♪', '♡',
        '.', ',', '!', '?', ' ', '　',
    )
}
