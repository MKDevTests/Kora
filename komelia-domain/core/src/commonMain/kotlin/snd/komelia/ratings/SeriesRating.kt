package snd.komelia.ratings

import snd.komga.client.series.KomgaSeriesId
import kotlin.time.Instant

/**
 * User's personal star rating for a series (1..5). Kept local in Kora's
 * SQLite — Komga itself has no user-rating field. Replaced on re-rate
 * (one row per series).
 */
data class SeriesRating(
    val seriesId: KomgaSeriesId,
    val stars: Int,
    val ratedAt: Instant,
) {
    init {
        require(stars in 1..5) { "stars must be 1..5, got $stars" }
    }
}
