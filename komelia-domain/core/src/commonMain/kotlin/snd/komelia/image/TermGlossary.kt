package snd.komelia.image

/**
 * Terms a series spells its own way, held out of the translator's hands.
 *
 * Two different problems, one mechanism:
 *
 * Names the reader flattens. Bubbles are lettered in full caps, so
 * [TranslationTextUtils.toSentenceCase] lowercases everything and capitalises
 * sentence starts — and a name in the middle of a sentence loses its capital
 * with the rest. MERYL STRIFE goes out as "Meryl strife" and comes back as
 * "Meryl discorde", because "strife" reads as a common noun. Measured over 400
 * bubbles of eight volumes: bernardelli, patricia, florence, henrietta, sabine
 * and christmas all went out lowercased.
 *
 * Terms with a settled translation. The Force is "la Force", not "la force";
 * Wayne Manor is "le Manoir Wayne". A small model has no reason to know that
 * and no reason to be consistent about it from one page to the next.
 *
 * Both are fixed by holding the term steady on the way in and putting the
 * chosen wording back on the way out, which works whichever engine sits in
 * between.
 */
class TermGlossary(terms: Map<String, String>) {

    private data class Entry(val source: String, val target: String, val pattern: Regex)

    /**
     * Longest source term first, so "Wayne Manor" is matched before "Wayne" and
     * the shorter entry cannot eat half of the longer one.
     */
    private val entries: List<Entry> = terms
        .filterKeys { it.isNotBlank() }
        .map { (source, target) ->
            Entry(
                source = source.trim(),
                target = target.trim().ifEmpty { source.trim() },
                pattern = Regex(
                    "(?<![\\p{L}\\p{N}])" + Regex.escape(source.trim()) + "(?![\\p{L}\\p{N}])",
                    RegexOption.IGNORE_CASE,
                ),
            )
        }
        .sortedByDescending { it.source.length }

    val isEmpty: Boolean get() = entries.isEmpty()

    /**
     * Restores each term's own spelling in text about to be translated.
     *
     * Runs after sentence-casing, which is what flattened the term in the first
     * place. Nothing else about the sentence is touched: the point is only that
     * the model sees "Strife" rather than "strife".
     */
    fun restoreTerms(text: String): String = entries.fold(text) { current, entry ->
        entry.pattern.replace(current) { entry.source }
    }

    /**
     * Puts the chosen wording into a translation.
     *
     * Only replaces the term where the engine left it in the source language,
     * which is the case it can actually fix. A term the engine did translate is
     * beyond reach here — that is what [restoreTerms] is for, and why it matters
     * more than this does.
     */
    fun applyTo(translated: String): String = entries.fold(translated) { current, entry ->
        if (entry.source.equals(entry.target, ignoreCase = true)) current
        else entry.pattern.replace(current) { entry.target }
    }

    companion object {
        val EMPTY = TermGlossary(emptyMap())
    }
}
