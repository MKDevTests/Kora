package snd.komelia.ratings

import kotlinx.coroutines.flow.Flow
import snd.komga.client.series.KomgaSeriesId

/**
 * Read/write for the local series-ratings table. Writes upsert by
 * series id — re-rating replaces the previous value.
 *
 * [observe] returns a reactive Flow keyed by series id so screens can
 * update their star UI as soon as the user picks a rating from the
 * quick-actions menu without an explicit reload.
 */
interface SeriesRatingsRepository {

    suspend fun get(seriesId: KomgaSeriesId): SeriesRating?

    /** Reactive — emits null when no rating exists yet. */
    fun observe(seriesId: KomgaSeriesId): Flow<SeriesRating?>

    suspend fun put(seriesId: KomgaSeriesId, stars: Int)

    suspend fun delete(seriesId: KomgaSeriesId)

    /** All ratings, newest first. Used by the optional "Tes top-rated" shelf. */
    suspend fun listAllByStarsDesc(limit: Int): List<SeriesRating>

    /**
     * Every rating, no order, no limit. Used by the JSON backup exporter
     * to snapshot the entire table in one shot. The result set is expected
     * to be small (typically <100 rows even for power users), so paging
     * is not exposed here.
     */
    suspend fun listAll(): List<SeriesRating>

    /**
     * Atomically wipe the table and re-insert [ratings], preserving each
     * row's original [SeriesRating.ratedAt]. Used by the JSON backup
     * importer so a restored backup recreates exactly the snapshot the
     * user exported — not a snapshot stamped at restore time.
     *
     * Observers of both removed and re-inserted series ids are notified
     * after the transaction commits.
     */
    suspend fun replaceAll(ratings: List<SeriesRating>)
}
