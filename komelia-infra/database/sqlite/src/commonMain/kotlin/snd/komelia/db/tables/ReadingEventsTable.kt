package snd.komelia.db.tables

import org.jetbrains.exposed.v1.core.Table

/**
 * Append-only log of reading events used to compute time-bounded stats
 * (last 7/30 days, streak, monthly chart). See V62 migration for details.
 */
object ReadingEventsTable : Table("reading_events") {
    val bookId = text("book_id")
    val eventType = text("event_type")
    val timestamp = long("timestamp")
    /** Page count of the book at completion time. Nullable — see V65 migration. */
    val pageCount = integer("page_count").nullable()
    /**
     * Owning Komga user (server-issued UUID). Nullable on legacy rows
     * inserted before v1.0.10; a post-upgrade one-shot job backfills
     * NULLs with the current user's id on first authenticated session.
     * See V66 migration.
     */
    val komgaUserId = text("komga_user_id").nullable()
    override val primaryKey = PrimaryKey(bookId, eventType)
}
