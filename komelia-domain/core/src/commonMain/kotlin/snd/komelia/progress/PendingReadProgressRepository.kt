package snd.komelia.progress

import kotlin.time.Instant

/**
 * A read position the server has not acknowledged.
 *
 * [modified] is when the page was reached, not when the push is retried: it
 * is sent as the progression's timestamp so that a position set later on
 * another device is not overwritten by a stale one delivered late.
 */
data class PendingReadProgress(
    val bookId: String,
    val page: Int,
    val totalPages: Int,
    val modified: Instant,
)

/**
 * Read progress that could not be delivered, kept until it is.
 *
 * Measured 2026-09-13: a push that failed while the Wi-Fi was down was logged
 * and forgotten. If the reader stopped on that page, Komga kept the previous
 * position. See [PendingReadProgressPusher] for the delivery.
 */
interface PendingReadProgressRepository {
    suspend fun getAll(): List<PendingReadProgress>
    suspend fun put(progress: PendingReadProgress)
    suspend fun delete(bookId: String)
}
