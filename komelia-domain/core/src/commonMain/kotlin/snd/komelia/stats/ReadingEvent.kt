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

        /**
         * Sentinel row carrying the server's count of READ books at the moment
         * it was last asked for, plus the moment itself (v1.8.3+). One per
         * user: `bookId = "_booksbaseline_<userId>"`, `timestamp = when the
         * count was taken`, `pageCount = the count`.
         *
         * Exists because asking Komga was measured at 5558 and 7474 ms — 80 to
         * 89% of the whole statistics card — for a number that changes by one
         * when you finish a book. The lifetime figure is now this baseline plus
         * the COMPLETED events recorded since its timestamp, which is exact,
         * and the server is asked again only when the baseline goes stale.
         *
         * Excluded from every time-windowed and per-type query (they filter on
         * COMPLETED), like [LIFETIME_CARRYOVER].
         */
        LIFETIME_BOOKS_BASELINE,
    }
}
