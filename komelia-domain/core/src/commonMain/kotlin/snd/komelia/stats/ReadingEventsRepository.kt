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

    /**
     * Idempotent insert. No-op if an event for (bookId, type) already exists.
     * [pageCount] is the book's page count at completion time; pass null when
     * unavailable (e.g. offline reader with missing metadata).
     */
    suspend fun record(
        bookId: KomgaBookId,
        type: ReadingEvent.Type,
        at: Instant,
        pageCount: Int? = null,
    )

    /** Count of events of [type] whose timestamp is >= [since]. */
    suspend fun countSince(type: ReadingEvent.Type, since: Instant): Int

    /**
     * Sum of [ReadingEvent.pageCount] for events of [type] whose timestamp
     * is >= [since]. Rows with a null pageCount contribute 0.
     */
    suspend fun sumPagesSince(type: ReadingEvent.Type, since: Instant): Long

    /** Lifetime sum of [ReadingEvent.pageCount] for events of [type]. */
    suspend fun sumPagesLifetime(type: ReadingEvent.Type): Long

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

    /**
     * One-shot post-upgrade tagging: tag every row whose `komga_user_id` is
     * NULL with [userId]. Returns the number of rows updated so callers can
     * decide whether to log "nothing to backfill". Idempotent — re-running
     * after success affects 0 rows.
     *
     * Called by [snd.komelia.UserScopeBackfillJob] on the first successful
     * authentication after a user upgrades from a pre-v1.0.10 install.
     */
    suspend fun backfillNullUserIds(userId: snd.komga.client.user.KomgaUserId): Int

    /**
     * Snapshot every event grouped by its `komga_user_id`. Used by the
     * backup exporter to build per-user sections. The NULL key holds rows
     * recorded before the user-id scoping landed (legacy v1.0.3..v1.0.9)
     * that the backfill job hasn't tagged yet — callers typically fold
     * them into the current user's section.
     */
    suspend fun listAllByUser(): Map<snd.komga.client.user.KomgaUserId?, List<ReadingEvent>>

    /**
     * Insert / replace a batch of events tagged with [userId]. Used by the
     * backup importer for both same-user restore and admin-gated shadow
     * restore. Idempotent on (book_id, event_type) — re-importing the same
     * snapshot is a no-op.
     */
    suspend fun upsertAllForUser(userId: snd.komga.client.user.KomgaUserId, events: List<ReadingEvent>): Int

    /**
     * Aggregate pages-read total carried forward from events older than the
     * backup's rolling window. Stored as one sentinel row per user (type
     * [ReadingEvent.Type.LIFETIME_CARRYOVER], `bookId = "_carryover_<userId>"`).
     * Returns the device-wide sum across every user — caller adds to
     * `sumPagesLifetime(COMPLETED)` to get the accurate lifetime total
     * including carryover.
     */
    suspend fun sumPagesLifetimeCarryover(): Long

    /**
     * Upsert this user's [ReadingEvent.Type.LIFETIME_CARRYOVER] sentinel row.
     * Called from the backup importer; also used internally by the exporter
     * to re-roll the carryover after trimming the current event log.
     * Passing 0 deletes the sentinel row.
     */
    suspend fun upsertLifetimeCarryover(
        userId: snd.komga.client.user.KomgaUserId,
        pages: Long,
    )
}
