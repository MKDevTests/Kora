package snd.komelia.db.stats

import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert
import snd.komelia.db.ExposedRepository
import snd.komelia.db.tables.ReadingEventsTable
import snd.komelia.stats.ReadingEvent
import snd.komelia.stats.LifetimeBooksBaseline
import snd.komelia.stats.ReadingEventsRepository
import snd.komga.client.book.KomgaBookId
import snd.komga.client.user.KomgaUserId
import kotlin.time.Instant

/**
 * SQLite-backed implementation of [ReadingEventsRepository].
 *
 * Design notes:
 * - Timestamps are stored as epoch-millis [Long] (same convention as the
 *   other tables in this module: audio_position, book_annotations…).
 * - Date-bucket queries (distinct dates, monthly buckets) intentionally
 *   fetch raw timestamps and aggregate in Kotlin rather than relying on
 *   SQLite-specific `strftime` / `date` functions. The event volume stays
 *   small (a handful per day at most; capped at ~365 distinct dates),
 *   and this keeps the repository backend-agnostic.
 * - All time-window queries use the (event_type, timestamp) index from
 *   V62 so they scan few rows even on libraries with tens of thousands
 *   of books.
 */
class ExposedReadingEventsRepository(
    database: Database,
    /**
     * Current Komga user id, polled at write time so each new event row
     * is tagged with whoever is signed in right now. Null when no
     * authenticated session yet — those rows are tagged later by
     * [snd.komelia.UserScopeBackfillJob] on the first successful auth.
     */
    private val currentUserId: StateFlow<KomgaUserId?>,
) : ExposedRepository(database), ReadingEventsRepository {

    override suspend fun record(
        bookId: KomgaBookId,
        type: ReadingEvent.Type,
        at: Instant,
        pageCount: Int?,
    ) {
        val userId = currentUserId.value?.value
        transaction {
            ReadingEventsTable.insertIgnore {
                it[ReadingEventsTable.bookId] = bookId.value
                it[ReadingEventsTable.eventType] = type.name
                it[ReadingEventsTable.timestamp] = at.toEpochMilliseconds()
                it[ReadingEventsTable.pageCount] = pageCount
                it[ReadingEventsTable.komgaUserId] = userId
            }
        }
    }

    override suspend fun backfillNullUserIds(userId: KomgaUserId): Int {
        return transaction {
            ReadingEventsTable.update(
                where = { ReadingEventsTable.komgaUserId.isNull() },
            ) {
                it[ReadingEventsTable.komgaUserId] = userId.value
            }
        }
    }

    override suspend fun listAllByUser(): Map<KomgaUserId?, List<ReadingEvent>> {
        return transaction {
            ReadingEventsTable
                .selectAll()
                .map { row ->
                    val userIdValue = row[ReadingEventsTable.komgaUserId]
                    val key: KomgaUserId? = userIdValue?.let { KomgaUserId(it) }
                    key to ReadingEvent(
                        bookId = KomgaBookId(row[ReadingEventsTable.bookId]),
                        type = readType(row[ReadingEventsTable.eventType]) ?: return@map null,
                        timestamp = Instant.fromEpochMilliseconds(row[ReadingEventsTable.timestamp]),
                        pageCount = row[ReadingEventsTable.pageCount],
                    )
                }
                .filterNotNull()
                .groupBy({ it.first }, { it.second })
        }
    }

    override suspend fun upsertAllForUser(userId: KomgaUserId, events: List<ReadingEvent>): Int {
        if (events.isEmpty()) return 0
        transaction {
            events.forEach { event ->
                ReadingEventsTable.upsert {
                    it[ReadingEventsTable.bookId] = event.bookId.value
                    it[ReadingEventsTable.eventType] = event.type.name
                    it[ReadingEventsTable.timestamp] = event.timestamp.toEpochMilliseconds()
                    it[ReadingEventsTable.pageCount] = event.pageCount
                    it[ReadingEventsTable.komgaUserId] = userId.value
                }
            }
        }
        return events.size
    }

    override suspend fun sumPagesLifetimeCarryover(): Long {
        return transaction {
            ReadingEventsTable
                .selectAll()
                .where { ReadingEventsTable.eventType eq ReadingEvent.Type.LIFETIME_CARRYOVER.name }
                .sumOf { (it[ReadingEventsTable.pageCount] ?: 0).toLong() }
        }
    }

    override suspend fun upsertLifetimeCarryover(userId: KomgaUserId, pages: Long) {
        // Sentinel row uniqueness: bookId encodes the user so the current
        // PK (book_id, event_type) gives one carryover row per user
        // without changing the PK shape.
        val bookId = "_carryover_${userId.value}"
        transaction {
            if (pages <= 0L) {
                ReadingEventsTable.deleteWhere {
                    ReadingEventsTable.bookId.eq(bookId)
                        .and(ReadingEventsTable.eventType.eq(ReadingEvent.Type.LIFETIME_CARRYOVER.name))
                }
                return@transaction
            }
            ReadingEventsTable.upsert {
                it[ReadingEventsTable.bookId] = bookId
                it[ReadingEventsTable.eventType] = ReadingEvent.Type.LIFETIME_CARRYOVER.name
                it[ReadingEventsTable.timestamp] = 0L
                // pageCount is INTEGER (32-bit) in the schema. Long fits up
                // to ~2B pages — comfortably above any realistic lifetime
                // sum. Truncate to Int with a guard for paranoia.
                it[ReadingEventsTable.pageCount] = pages.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                it[ReadingEventsTable.komgaUserId] = userId.value
            }
        }
    }

    override suspend fun sumPagesSince(type: ReadingEvent.Type, since: Instant): Long {
        return transaction {
            ReadingEventsTable
                .selectAll()
                .where {
                    ReadingEventsTable.eventType.eq(type.name)
                        .and(ReadingEventsTable.timestamp.greaterEq(since.toEpochMilliseconds()))
                }
                // Aggregate in Kotlin rather than via Exposed's Sum<Int?>: the
                // event volume is tiny (a few completions per day), and this
                // avoids Exposed-version-specific aggregator wiring while
                // keeping NULL → 0 semantics explicit.
                .sumOf { (it[ReadingEventsTable.pageCount] ?: 0).toLong() }
        }
    }

    override suspend fun sumPagesLifetime(type: ReadingEvent.Type): Long {
        return transaction {
            ReadingEventsTable
                .selectAll()
                .where { ReadingEventsTable.eventType eq type.name }
                .sumOf { (it[ReadingEventsTable.pageCount] ?: 0).toLong() }
        }
    }

    override suspend fun countSince(type: ReadingEvent.Type, since: Instant): Int {
        return transaction {
            ReadingEventsTable
                .selectAll()
                .where {
                    ReadingEventsTable.eventType.eq(type.name)
                        .and(ReadingEventsTable.timestamp.greaterEq(since.toEpochMilliseconds()))
                }
                .count()
                .toInt()
        }
    }

    override suspend fun distinctDates(type: ReadingEvent.Type, limit: Int): List<String> {
        if (limit <= 0) return emptyList()
        val tz = TimeZone.currentSystemDefault()
        return transaction {
            ReadingEventsTable
                .selectAll()
                .where { ReadingEventsTable.eventType eq type.name }
                .orderBy(ReadingEventsTable.timestamp, SortOrder.DESC)
                .asSequence()
                .map {
                    Instant.fromEpochMilliseconds(it[ReadingEventsTable.timestamp])
                        .toLocalDateTime(tz)
                        .date
                        .toString()
                }
                .distinct()
                .take(limit)
                .toList()
        }
    }

    override suspend fun monthlyBuckets(type: ReadingEvent.Type, since: Instant): Map<String, Int> {
        val tz = TimeZone.currentSystemDefault()
        return transaction {
            ReadingEventsTable
                .selectAll()
                .where {
                    ReadingEventsTable.eventType.eq(type.name)
                        .and(ReadingEventsTable.timestamp.greaterEq(since.toEpochMilliseconds()))
                }
                .map {
                    val date = Instant.fromEpochMilliseconds(it[ReadingEventsTable.timestamp])
                        .toLocalDateTime(tz)
                        .date
                    "%04d-%02d".format(date.year, date.monthNumber)
                }
                .groupingBy { it }
                .eachCount()
        }
    }

    override suspend fun dailyBuckets(type: ReadingEvent.Type, since: Instant): Map<String, Int> {
        val tz = TimeZone.currentSystemDefault()
        return transaction {
            ReadingEventsTable
                .selectAll()
                .where {
                    ReadingEventsTable.eventType.eq(type.name)
                        .and(ReadingEventsTable.timestamp.greaterEq(since.toEpochMilliseconds()))
                }
                .map {
                    val date = Instant.fromEpochMilliseconds(it[ReadingEventsTable.timestamp])
                        .toLocalDateTime(tz)
                        .date
                    "%04d-%02d-%02d".format(date.year, date.monthNumber, date.dayOfMonth)
                }
                .groupingBy { it }
                .eachCount()
        }
    }

    override suspend fun lifetimeDistinctBooks(type: ReadingEvent.Type): Int {
        return transaction {
            ReadingEventsTable
                .selectAll()
                .where { ReadingEventsTable.eventType eq type.name }
                .map { it[ReadingEventsTable.bookId] }
                .distinct()
                .size
        }
    }

    override suspend fun getLifetimeBooksBaseline(): LifetimeBooksBaseline? {
        val userId = currentUserId.value ?: return null
        val rowId = booksBaselineBookId(userId)
        return transaction {
            ReadingEventsTable
                .selectAll()
                .where {
                    ReadingEventsTable.bookId.eq(rowId)
                        .and(ReadingEventsTable.eventType.eq(ReadingEvent.Type.LIFETIME_BOOKS_BASELINE.name))
                }
                .firstOrNull()
                ?.let { row ->
                    LifetimeBooksBaseline(
                        count = row[ReadingEventsTable.pageCount] ?: 0,
                        takenAt = Instant.fromEpochMilliseconds(row[ReadingEventsTable.timestamp]),
                    )
                }
        }
    }

    override suspend fun upsertLifetimeBooksBaseline(count: Int, at: Instant, userId: KomgaUserId?) {
        val owner = userId ?: currentUserId.value ?: return
        val rowId = booksBaselineBookId(owner)
        transaction {
            ReadingEventsTable.upsert {
                it[ReadingEventsTable.bookId] = rowId
                it[ReadingEventsTable.eventType] = ReadingEvent.Type.LIFETIME_BOOKS_BASELINE.name
                it[ReadingEventsTable.timestamp] = at.toEpochMilliseconds()
                it[ReadingEventsTable.pageCount] = count.coerceAtLeast(0)
                it[ReadingEventsTable.komgaUserId] = owner.value
            }
        }
    }

    /**
     * Reads a stored event type, or null when the string names nothing this
     * build knows.
     *
     * The column holds `Type.name`, so a row written by a newer Kora — one that
     * has a type this build predates — would otherwise throw
     * IllegalArgumentException out of `valueOf` and take the whole listing with
     * it. Skipping the row loses one event; throwing loses the backup.
     */
    private fun readType(stored: String): ReadingEvent.Type? =
        ReadingEvent.Type.entries.firstOrNull { it.name == stored }

    override suspend fun clear(type: ReadingEvent.Type) {
        transaction {
            ReadingEventsTable.deleteWhere { eventType eq type.name }
        }
    }

    companion object {
        /**
         * Sentinel row uniqueness, same trick as the carryover row: the book id
         * encodes the user, so the (book_id, event_type) primary key yields one
         * baseline per user without changing the key shape.
         */
        internal fun booksBaselineBookId(userId: KomgaUserId) = "_booksbaseline_${userId.value}"
    }
}
