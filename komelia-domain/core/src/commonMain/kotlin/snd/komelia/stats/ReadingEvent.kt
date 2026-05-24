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
    /**
     * Page count of the book at the moment the event was recorded.
     * Null for rows inserted before v1.0.10 (no backfill — Komga doesn't
     * timestamp historical completions) and for the rare case where the
     * book's media metadata couldn't be fetched at record time.
     */
    val pageCount: Int? = null,
) {
    enum class Type {
        /** The user finished reading a book (any code path). */
        COMPLETED,

        /**
         * Sentinel row carrying the aggregate pages-read total from events
         * older than the backup's rolling window (v1.0.10+). One per user:
         * `bookId = "_carryover_<userId>"`, `timestamp = 0`, `pageCount =
         * accumulated pages from trimmed events`. Excluded from all
         * time-windowed and per-type stat queries (they filter on
         * `COMPLETED`), included only in the lifetime pages sum.
         */
        LIFETIME_CARRYOVER,
    }
}
