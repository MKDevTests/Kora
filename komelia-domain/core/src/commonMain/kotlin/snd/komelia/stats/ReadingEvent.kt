package snd.komelia.stats

import snd.komga.client.book.KomgaBookId
import kotlin.time.Instant

/**
 * An append-only log entry recording something that happened to a book.
 * Used as the local source of truth for time-bounded reading stats
 * (last 7/30 days, streak, monthly chart).
 *
 * For now the only emitted type is [Type.COMPLETED]; the table is shaped
 * to accept additional event types (e.g. SERIES_FINISHED, BOOK_STARTED)
 * without further migration.
 */
data class ReadingEvent(
    val bookId: KomgaBookId,
    val type: Type,
    val timestamp: Instant,
) {
    enum class Type {
        /** The user finished reading a book (any code path). */
        COMPLETED,
    }
}
