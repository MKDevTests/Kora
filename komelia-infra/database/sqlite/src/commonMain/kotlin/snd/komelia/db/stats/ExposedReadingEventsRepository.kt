package snd.komelia.db.stats

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import snd.komelia.db.ExposedRepository
import snd.komelia.db.tables.ReadingEventsTable
import snd.komelia.stats.ReadingEvent
import snd.komelia.stats.ReadingEventsRepository
import snd.komga.client.book.KomgaBookId
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
) : ExposedRepository(database), ReadingEventsRepository {

    override suspend fun record(bookId: KomgaBookId, type: ReadingEvent.Type, at: Instant) {
        transaction {
            ReadingEventsTable.insertIgnore {
                it[ReadingEventsTable.bookId] = bookId.value
                it[ReadingEventsTable.eventType] = type.name
                it[ReadingEventsTable.timestamp] = at.toEpochMilliseconds()
            }
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

    override suspend fun clear(type: ReadingEvent.Type) {
        transaction {
            ReadingEventsTable.deleteWhere { eventType eq type.name }
        }
    }
}
