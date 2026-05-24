package snd.komelia.db.ratings

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert
import snd.komelia.db.ExposedRepository
import snd.komelia.db.tables.SeriesRatingsTable
import snd.komelia.ratings.SeriesRating
import snd.komelia.ratings.SeriesRatingsRepository
import snd.komga.client.series.KomgaSeriesId
import snd.komga.client.user.KomgaUserId
import kotlin.time.Clock
import kotlin.time.Instant

class ExposedSeriesRatingsRepository(
    database: Database,
    /**
     * Current Komga user id, polled at write time so each upsert is tagged
     * with whoever is signed in right now. Null when no authenticated
     * session yet — backfilled by [snd.komelia.UserScopeBackfillJob].
     */
    private val currentUserId: StateFlow<KomgaUserId?>,
) : ExposedRepository(database), SeriesRatingsRepository {

    /**
     * In-process broadcast of write events so [observe] callers can
     * refresh without polling. Replay=0: only future writes matter; the
     * initial value comes from a `onStart { emit current }`.
     */
    private val writeEvents = MutableSharedFlow<KomgaSeriesId>(replay = 0, extraBufferCapacity = 8)

    override suspend fun get(seriesId: KomgaSeriesId): SeriesRating? {
        return transaction {
            SeriesRatingsTable
                .selectAll()
                .where { SeriesRatingsTable.seriesId.eq(seriesId.value) }
                .firstOrNull()
                ?.toModel()
        }
    }

    override fun observe(seriesId: KomgaSeriesId): Flow<SeriesRating?> {
        return writeEvents
            .filter { it == seriesId }
            .map { get(seriesId) }
            .onStart { emit(get(seriesId)) }
    }

    override suspend fun put(seriesId: KomgaSeriesId, stars: Int) {
        require(stars in 1..5) { "stars must be 1..5, got $stars" }
        val userId = currentUserId.value?.value
        transaction {
            SeriesRatingsTable.upsert {
                it[SeriesRatingsTable.seriesId] = seriesId.value
                it[SeriesRatingsTable.stars] = stars
                it[ratedAt] = Clock.System.now().toEpochMilliseconds()
                it[komgaUserId] = userId
            }
        }
        writeEvents.emit(seriesId)
    }

    override suspend fun delete(seriesId: KomgaSeriesId) {
        transaction {
            SeriesRatingsTable.deleteWhere { SeriesRatingsTable.seriesId.eq(seriesId.value) }
        }
        writeEvents.emit(seriesId)
    }

    override suspend fun listAllByStarsDesc(limit: Int): List<SeriesRating> {
        return transaction {
            SeriesRatingsTable
                .selectAll()
                .orderBy(
                    SeriesRatingsTable.stars to SortOrder.DESC,
                    SeriesRatingsTable.ratedAt to SortOrder.DESC,
                )
                .limit(limit)
                .map { it.toModel() }
        }
    }

    override suspend fun listAll(): List<SeriesRating> {
        return transaction {
            SeriesRatingsTable
                .selectAll()
                .map { it.toModel() }
        }
    }

    override suspend fun replaceAll(ratings: List<SeriesRating>) {
        ratings.forEach {
            require(it.stars in 1..5) { "stars must be 1..5, got ${it.stars}" }
        }
        // Snapshot existing ids *before* the wipe so observers of removed
        // ratings get notified too — not just the ones we're restoring.
        // Both the delete and the inserts happen in a single transaction
        // so a crash mid-loop can't leave the table half-empty.
        val userId = currentUserId.value?.value
        val toEmit: Set<KomgaSeriesId> = transaction {
            val previousIds = SeriesRatingsTable
                .selectAll()
                .map { KomgaSeriesId(it[SeriesRatingsTable.seriesId]) }
                .toSet()
            SeriesRatingsTable.deleteAll()
            ratings.forEach { rating ->
                SeriesRatingsTable.insert {
                    it[seriesId] = rating.seriesId.value
                    it[stars] = rating.stars
                    it[ratedAt] = rating.ratedAt.toEpochMilliseconds()
                    it[komgaUserId] = userId
                }
            }
            previousIds + ratings.map { it.seriesId }
        }
        toEmit.forEach { writeEvents.emit(it) }
    }

    override suspend fun backfillNullUserIds(userId: KomgaUserId): Int {
        return transaction {
            SeriesRatingsTable.update(
                where = { SeriesRatingsTable.komgaUserId.isNull() },
            ) {
                it[SeriesRatingsTable.komgaUserId] = userId.value
            }
        }
    }

    override suspend fun listAllByUser(): Map<KomgaUserId?, List<SeriesRating>> {
        return transaction {
            SeriesRatingsTable
                .selectAll()
                .map { row ->
                    val userIdValue = row[SeriesRatingsTable.komgaUserId]
                    val key: KomgaUserId? = userIdValue?.let { KomgaUserId(it) }
                    key to row.toModel()
                }
                .groupBy({ it.first }, { it.second })
        }
    }

    override suspend fun replaceAllForUser(userId: KomgaUserId, ratings: List<SeriesRating>) {
        ratings.forEach {
            require(it.stars in 1..5) { "stars must be 1..5, got ${it.stars}" }
        }
        // Snapshot the user's existing slice so observers get notified for
        // rows we remove as well as the ones we (re-)insert.
        val toEmit: Set<KomgaSeriesId> = transaction {
            val previousIds = SeriesRatingsTable
                .selectAll()
                .where { SeriesRatingsTable.komgaUserId.eq(userId.value) }
                .map { KomgaSeriesId(it[SeriesRatingsTable.seriesId]) }
                .toSet()
            SeriesRatingsTable.deleteWhere { SeriesRatingsTable.komgaUserId.eq(userId.value) }
            ratings.forEach { rating ->
                SeriesRatingsTable.insert {
                    it[seriesId] = rating.seriesId.value
                    it[stars] = rating.stars
                    it[ratedAt] = rating.ratedAt.toEpochMilliseconds()
                    it[komgaUserId] = userId.value
                }
            }
            previousIds + ratings.map { it.seriesId }
        }
        toEmit.forEach { writeEvents.emit(it) }
    }

    private fun org.jetbrains.exposed.v1.core.ResultRow.toModel(): SeriesRating {
        return SeriesRating(
            seriesId = KomgaSeriesId(get(SeriesRatingsTable.seriesId)),
            stars = get(SeriesRatingsTable.stars),
            ratedAt = Instant.fromEpochMilliseconds(get(SeriesRatingsTable.ratedAt)),
        )
    }
}
