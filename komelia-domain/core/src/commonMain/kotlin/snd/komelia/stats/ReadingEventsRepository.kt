package snd.komelia.stats

import snd.komga.client.book.KomgaBookId
import kotlin.time.Instant

/**
 * Read/write access to the [ReadingEvent] log.
 *
 * Writes are idempotent: re-recording the same (bookId, type) pair
 * does not produce duplicates. This is important because completion can
 * be triggered from several places (reader end-of-book, offline sync
 * flush, manual mark-as-read) and a user re-reading a book shouldn't
 * inflate stats.
 *
 * All queries are designed to leverage the partial indexes created in
 * the V62 migration on (event_type, timestamp).
 */
interface ReadingEventsRepository {

    /** Idempotent insert. No-op if an event for (bookId, type) already exists. */
    suspend fun record(bookId: KomgaBookId, type: ReadingEvent.Type, at: Instant)

    /** Count of events of [type] whose timestamp is >= [since]. */
    suspend fun countSince(type: ReadingEvent.Type, since: Instant): Int

    /** Distinct local calendar dates with at least one event of [type], newest first, capped. */
    suspend fun distinctDates(type: ReadingEvent.Type, limit: Int): List<String>

    /**
     * Bucket counts of [type] grouped by year-month ("YYYY-MM"), from [since] onward.
     * Returns one entry per month that actually has events (no zero-filling — caller
     * fills the gaps for the chart axis).
     */
    suspend fun monthlyBuckets(type: ReadingEvent.Type, since: Instant): Map<String, Int>

    /** Total number of distinct books ever associated with at least one event of [type]. */
    suspend fun lifetimeDistinctBooks(type: ReadingEvent.Type): Int

    /** Remove every event of [type]. Used by the "clear my reading history" toggle. */
    suspend fun clear(type: ReadingEvent.Type)
}
