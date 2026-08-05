package snd.komelia.similarity

/**
 * How much each kind of evidence says about what the user likes.
 *
 * Data, not constants, for the same reason as [SimilarityWeights]: these are
 * meant to be swept at the bench against the real reading history.
 */
data class TasteWeights(
    /** Finished it, never rated it. Engagement, not a verdict. */
    val read: Double = 1.0,
    /** Started, not finished, not rated. Real but weaker evidence. */
    val inProgress: Double = 0.6,
    /** Favourited: an explicit "I like this", counted like a top rating. */
    val favorite: Double = 3.0,
    /**
     * Per-star affinity. 3 stars is neutral-positive, 1 and 2 are negative —
     * a disliked series should push its terms DOWN, not merely be absent, or a
     * genre the user dislikes keeps resurfacing just because they read a lot of
     * it before deciding they disliked it.
     */
    val stars: Map<Int, Double> = mapOf(
        5 to 3.0,
        4 to 2.0,
        3 to 1.0,
        2 to -1.0,
        1 to -2.0,
    ),
    /**
     * "Not interested". Negative, between a 2 and a 1 star: the user judged the
     * series without reading it, which is weaker than a rating but far from
     * neutral — and hiding the cover without learning anything would make them
     * repeat the tap on every near-identical series the same terms produce.
     */
    val dismissed: Double = -1.5,
)

/** What the app knows about one series the user has met. */
data class SeriesEvidence(
    val seriesId: String,
    val read: Boolean = false,
    val inProgress: Boolean = false,
    val isFavorite: Boolean = false,
    /** 1..5, or null when the user never rated it. */
    val stars: Int? = null,
    /** The user answered "not interested" on a suggestion. */
    val dismissed: Boolean = false,
)

/**
 * Turns what the user has read/rated into per-series affinities for
 * [SimilarityEngine.recommend].
 *
 * Two rules that are easy to get wrong and that decide whether the tab is
 * usable at all:
 *
 *  - **An unrated series is not a zero.** Most series here are never rated;
 *    treating "no rating" as 0 stars would drag the whole profile down and make
 *    the few rated ones the only thing that counts. Unrated simply falls back
 *    to the engagement weight.
 *  - **A rating counts even if the series isn't finished.** The user rates when
 *    they have an opinion, not when they reach the last volume; requiring
 *    completion would throw away the strongest signal available.
 */
fun tasteAffinities(
    evidence: Collection<SeriesEvidence>,
    weights: TasteWeights = TasteWeights(),
): Map<String, Double> = buildMap {
    for (item in evidence) {
        val stars = item.stars
        val affinity = when {
            // An explicit rating outranks everything else, including a
            // dismissal: rating a series is the stronger statement of the two.
            stars != null -> weights.stars[stars] ?: 0.0
            item.isFavorite -> weights.favorite
            item.dismissed -> weights.dismissed
            item.read -> weights.read
            item.inProgress -> weights.inProgress
            else -> 0.0
        }
        if (affinity != 0.0) put(item.seriesId, affinity)
    }
}
