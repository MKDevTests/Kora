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
 * Both are fixed the same way: the term leaves as something the engine has no
 * translation for, and the chosen wording is put back on the way out. That
 * works whichever engine sits in between, which is the point.
 *
 * Putting the capital back and hoping was tried first, and measured not to be
 * enough. The tablet sent "My name is Meryl Strife, and I represent the
 * bernardelli insurance society" and got back "Mon nom de Meryl Conflife": the
 * capital travelled, the name did not. Over 200 real bubbles through Bergamot,
 * a name left in place survives 75.7% of the time — and comes back duplicated
 * in six of them, which would put it somewhere the sentence never had it.
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
     * Swaps every known term for a placeholder, ready to be translated.
     *
     * Returns the terms alongside the text because restoring them needs to know
     * which placeholder stood for what: a bubble can hold two of them, and one
     * that cannot tell them apart is no use.
     */
    fun protect(text: String): ProtectedText {
        if (entries.isEmpty()) return ProtectedText(text, emptyList())
        val restored = mutableListOf<String>()
        val protectedText = entries.fold(text) { current, entry ->
            entry.pattern.replace(current) {
                val token = placeholder(restored.size)
                restored += entry.target
                token
            }
        }
        return ProtectedText(protectedText, restored)
    }

    companion object {
        val EMPTY = TermGlossary(emptyMap())

        /**
         * Mathematical white square brackets, U+27E6/U+27E7.
         *
         * Chosen by measurement, not by taste: 200 real bubbles containing a
         * name, seven candidate shapes, every one of them through Bergamot.
         * This one came back whole 200 times out of 200. `Xqz0` lost one,
         * `@0@` two, `#0#` five, a longer invented name eleven, and leaving the
         * name itself in place lost forty-six. Nothing in the training data
         * looks like these glyphs, which is exactly why they survive.
         */
        internal fun placeholder(index: Int): String = "\u27E6$index\u27E7"

        /**
         * Matches a placeholder of any index, for cleaning up after an engine
         * that mangled one.
         */
        private val PLACEHOLDER = Regex("\\s*\u27E6\\d+\u27E7\\s*")

        internal fun stripPlaceholders(text: String): String =
            PLACEHOLDER.replace(text, " ").trim()
    }
}

/**
 * A sentence whose glossary terms have been swapped out, and the terms needed
 * to put them back.
 */
class ProtectedText internal constructor(
    /** What to hand the translator. */
    val text: String,
    private val terms: List<String>,
) {
    val isProtected: Boolean get() = terms.isNotEmpty()

    /**
     * Puts the terms back into a translation.
     *
     * A placeholder the engine dropped simply leaves the term out of the
     * sentence, which is a missing vocative at worst — measurably better than
     * the alternative, where the name comes back as a common noun and the line
     * stops meaning anything. A placeholder it duplicated is worse than either,
     * so only the first occurrence is restored and the rest are removed: better
     * a name missing than a name somewhere the sentence never had it.
     */
    fun restore(translated: String): String {
        if (terms.isEmpty()) return translated
        var result = translated
        terms.forEachIndexed { index, term ->
            val token = TermGlossary.placeholder(index)
            val at = result.indexOf(token)
            if (at >= 0) {
                result = result.substring(0, at) + term + result.substring(at + token.length)
            }
        }
        // Anything still shaped like a placeholder is a duplicate the engine
        // invented, or one whose index we never issued. Either way it must not
        // reach the page.
        return TermGlossary.stripPlaceholders(result)
    }
}
