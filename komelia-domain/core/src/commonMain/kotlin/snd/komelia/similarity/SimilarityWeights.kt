package snd.komelia.similarity

import kotlin.math.ln

/**
 * How much each kind of shared term counts. Kept as data, not constants, so the
 * bench can sweep values without touching the engine — the whole point of not
 * precomputing a score matrix.
 *
 * Starting values, to be settled on real data:
 *  - authors first: sharing an author is the strongest statement of intent,
 *    especially in BD/Comics;
 *  - genres next: a fixed 22-slug taxonomy, hand-tagged, so trustworthy;
 *  - tags lower: wider vocabulary, uneven density between series;
 *  - book tags lower still: aggregated from books, noisier again;
 *  - publisher barely counts: it captures an imprint's line, not a taste.
 */
data class SimilarityWeights(
    /**
     * Settled at the bench on the real libraries. It started at 1.2 (author
     * first), but once the per-author cap actually capped, the ranking barely
     * moved between 1.2 and 0.6 — while 0.6 leaves more room for the
     * genre/tag matches under the two same-author entries.
     */
    val author: Double = 0.6,
    val genre: Double = 1.0,
    val tag: Double = 0.6,
    val bookTag: Double = 0.4,
    val publisher: Double = 0.25,
    /**
     * Per-role multipliers on top of [author]. Sharing a writer says more than
     * sharing a colourist. Unlisted roles fall back to 1.0.
     */
    val authorRoles: Map<String, Double> = mapOf(
        "writer" to 1.0,
        "penciller" to 0.9,
        "artist" to 0.9,
        "inker" to 0.5,
        "colorist" to 0.4,
        "letterer" to 0.3,
        "translator" to 0.2,
    ),
    /**
     * At most this many results from the same author. Without it "similar to X"
     * degenerates into that author's bibliography, which the author filter
     * already does better.
     */
    val maxPerAuthor: Int = 2,
) {
    fun familyWeight(feature: Feature): Double = when (feature.family) {
        TermFamily.AUTHOR -> author * (authorRoles[feature.role?.lowercase()] ?: 1.0)
        TermFamily.GENRE -> genre
        TermFamily.TAG -> tag
        TermFamily.BOOK_TAG -> bookTag
        TermFamily.PUBLISHER -> publisher
    }

    companion object {
        val Default = SimilarityWeights()
    }
}

/**
 * Rarity weighting. A genre carried by 1800 of 3000 series says almost nothing;
 * one carried by 40 says a lot. Without this every series looks alike and the
 * whole feature reads as noise — it is the single biggest quality lever here.
 *
 * Smoothed IDF: ln(1 + N / df), so a term on every series still scores > 0
 * instead of collapsing the vector.
 */
fun idf(totalSeries: Int, documentFrequency: Int): Double {
    if (totalSeries <= 0 || documentFrequency <= 0) return 0.0
    return ln(1.0 + totalSeries.toDouble() / documentFrequency.toDouble())
}
