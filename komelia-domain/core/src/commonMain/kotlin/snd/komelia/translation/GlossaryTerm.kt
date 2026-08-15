package snd.komelia.translation

import snd.komga.client.series.KomgaSeriesId

/**
 * One term the translator is not allowed to decide for itself.
 *
 * [target] may equal [source]: that is the common case for a name, where the
 * point is only that it survives being sentence-cased and reaches the engine
 * spelled as a name rather than as a common noun.
 */
data class GlossaryTerm(
    val seriesId: KomgaSeriesId?,
    val source: String,
    val target: String,
) {
    /** True when the entry exists only to keep the term intact. */
    val isNameOnly: Boolean get() = source.equals(target, ignoreCase = true)
}
