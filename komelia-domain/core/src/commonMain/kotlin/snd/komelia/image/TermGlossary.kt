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
 * capital travelled, the name did not. Over 200 real bubbles run through both
 * engines, a name left in the sentence comes back intact 91% of the time on
 * Bergamot and 88% on ML Kit, and comes back duplicated in twelve of them —
 * which puts it somewhere the sentence never had it. A placeholder loses two.
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
         * An unpronounceable pseudo-name. Chosen by measurement, not by taste.
         *
         * 200 real bubbles containing a name, seven candidate shapes, all 1400
         * lines through both engines — Bergamot on the bench and ML Kit on the
         * tablet, because shipping a shape that only one of them respects would
         * be no protection at all. Terms surviving, out of 428:
         *
         *     Xqz0     99.5% / 99.5%      2 lost,  0 duplicated
         *     ⟦0⟧     100.0% / 97.2%      4 lost,  2 duplicated
         *     «0»      94.9% / 98.1%     15 lost
         *     #0#, @0@                        ML Kit spaces them out: "# 0 #"
         *     the name itself 91.1% / 87.9%  33 lost, 12 duplicated
         *
         * The bracket forms look better until you read what a miss costs: ML
         * Kit drops the brackets and keeps the digit ("I had come to ⟦0⟧" ->
         * "j'étais venue à 0"), leaving an orphan number on the page that
         * cannot be cleaned up without risking a real one. A mangled Xqz0
         * leaves a word-shaped residue instead, and most misses are not misses
         * at all — ML Kit upper-cases it to XQZ0, which is why restoring is
         * case-insensitive below.
         */
        internal fun placeholder(index: Int): String = "Xqz$index"

        /**
         * Matches a placeholder and captures its index.
         *
         * One pass over the whole string rather than one search per term, which
         * also settles two things for free: Xqz1 can never match the front of
         * Xqz11, and an index the engine invented is caught rather than
         * restored.
         */
        internal val PLACEHOLDER = Regex("Xqz(\\d+)", RegexOption.IGNORE_CASE)
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
     * Rewrites what goes to the translator, keeping the terms to put back.
     *
     * For steps that have to run on protected text: the Japanese katakana
     * rewrite must not reach a term the reader chose to keep, and the
     * placeholders are the only thing that guarantees it cannot.
     */
    fun mapText(transform: (String) -> String): ProtectedText =
        ProtectedText(transform(text), terms)

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
        val used = mutableSetOf<Int>()
        var dropped = false
        val result = TermGlossary.PLACEHOLDER.replace(translated) { match ->
            val index = match.groupValues[1].toIntOrNull()
            if (index != null && index in terms.indices && used.add(index)) terms[index]
            else {
                // A second copy of one already placed, or an index never
                // issued. Dropped rather than restored: a name missing is a
                // missing vocative, a name in the wrong place changes what the
                // sentence says.
                dropped = true
                ""
            }
        }
        // Only when something was removed. An untouched sentence must come out
        // of here byte for byte, or every bubble pays for a case that measured
        // zero occurrences on either engine.
        return if (!dropped) result
        else SPACE_BEFORE_PUNCTUATION.replace(COLLAPSE_SPACES.replace(result, " "), "$1").trim()
    }

    private companion object {
        /** A removed placeholder can leave a double gap where it sat. */
        val COLLAPSE_SPACES = Regex("\\s{2,}")

        /** …or a gap in front of the punctuation that followed it. */
        val SPACE_BEFORE_PUNCTUATION = Regex("\\s+([,.;:!?])")
    }
}
