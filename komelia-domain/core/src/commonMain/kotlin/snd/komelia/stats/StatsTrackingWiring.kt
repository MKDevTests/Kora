package snd.komelia.stats

import kotlinx.coroutines.flow.StateFlow
import snd.komelia.komga.api.KomgaApi
import snd.komelia.komga.api.KomgaBookApi
import snd.komelia.komga.api.LocalFileApiProvider
import snd.komga.client.book.KomgaBookId

/**
 * Returns a [KomgaApi] that behaves identically to the receiver except that
 * `bookApi.markReadProgress(..., completed = true)` also records a
 * [ReadingEvent.Type.COMPLETED] row through [readingEvents] (gated by
 * [statsEnabled]). All other endpoints pass through unchanged.
 *
 * Used at the global online/offline KomgaApi selector to cover every
 * server-backed completion path (reader end-of-book, manual mark-as-read,
 * bulk action, offline sync flush) in one shot.
 */
fun KomgaApi.withStatsTracking(
    readingEvents: ReadingEventsRepository,
    statsEnabled: StateFlow<Boolean>,
): KomgaApi {
    val originalBookApi = bookApi
    val trackedBookApi = StatsTrackingBookApi(originalBookApi, readingEvents, statsEnabled)
    return object : KomgaApi by this {
        override val bookApi: KomgaBookApi = trackedBookApi
    }
}

/**
 * Returns a [LocalFileApiProvider] that wraps every [KomgaBookApi] it
 * dispenses in [StatsTrackingBookApi], so completions from the
 * Android-intent local file viewer are logged exactly like Komga
 * server completions.
 */
fun LocalFileApiProvider.withStatsTracking(
    readingEvents: ReadingEventsRepository,
    statsEnabled: StateFlow<Boolean>,
): LocalFileApiProvider = object : LocalFileApiProvider by this {
    override fun getApiForBook(bookId: KomgaBookId): KomgaBookApi? =
        this@withStatsTracking.getApiForBook(bookId)?.let {
            StatsTrackingBookApi(it, readingEvents, statsEnabled)
        }
}
