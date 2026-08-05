package snd.komelia.library

import snd.komelia.komga.api.model.KomeliaBook

/** The first page of a series' books, as the server last sent it. */
data class CachedSeriesBooks(
    val books: List<KomeliaBook>,
    val pageSize: Int,
    val totalPages: Int,
)

/**
 * Last known first page of books, per series.
 *
 * Entering a series left the volumes area empty for the one to three seconds
 * the request takes, every time, for a list that rarely changes in between.
 * Only the first page is kept, and only for the default view: another page or
 * another ordering is something the user asked for on purpose, and showing
 * them the wrong list would be worse than the wait.
 */
interface SeriesBooksCacheRepository {
    suspend fun get(seriesId: String): CachedSeriesBooks?
    suspend fun put(seriesId: String, books: CachedSeriesBooks)
}
