package snd.komelia.db.similarity

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.batchUpsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert
import snd.komelia.db.ExposedRepository
import snd.komelia.db.JsonDbDefault
import snd.komelia.db.tables.SeriesSimilarityIndexStateTable
import snd.komelia.db.tables.SeriesSimilarityIndexTable
import snd.komelia.similarity.SeriesTerms
import snd.komelia.similarity.SimilarityIndexEntry
import snd.komelia.similarity.SimilarityIndexRepository
import snd.komelia.similarity.SimilarityIndexState
import snd.komelia.similarity.SimilarityIndexTitle
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * SQLite-backed term index (V85). Terms are stored as one JSON blob per series;
 * see [SeriesSimilarityIndexTable] for why that beats a normalised term table.
 */
class ExposedSimilarityIndexRepository(
    database: Database,
) : ExposedRepository(database), SimilarityIndexRepository {

    override suspend fun entryOf(seriesId: String): SimilarityIndexEntry? = transaction {
        SeriesSimilarityIndexTable
            .selectAll()
            .where { SeriesSimilarityIndexTable.seriesId eq seriesId }
            .firstOrNull()
            ?.let { row ->
                val terms = runCatching {
                    JsonDbDefault.decodeFromString<SeriesTerms>(row[SeriesSimilarityIndexTable.terms])
                }.getOrNull() ?: return@let null
                SimilarityIndexEntry(
                    seriesId = row[SeriesSimilarityIndexTable.seriesId],
                    libraryId = row[SeriesSimilarityIndexTable.libraryId],
                    titleSort = row[SeriesSimilarityIndexTable.titleSort],
                    terms = terms,
                    language = row[SeriesSimilarityIndexTable.language],
                )
            }
    }

    override suspend fun entriesOf(libraryId: String): List<SimilarityIndexEntry> {
        return transaction {
            SeriesSimilarityIndexTable.selectAll()
                .where { SeriesSimilarityIndexTable.libraryId eq libraryId }
                .mapNotNull { row ->
                    // A row whose JSON can't be read is a rebuild away from being
                    // fixed; dropping it degrades one suggestion instead of
                    // failing the whole tab.
                    val terms = runCatching {
                        JsonDbDefault.decodeFromString<SeriesTerms>(row[SeriesSimilarityIndexTable.terms])
                    }.getOrNull() ?: return@mapNotNull null
                    SimilarityIndexEntry(
                        seriesId = row[SeriesSimilarityIndexTable.seriesId],
                        libraryId = row[SeriesSimilarityIndexTable.libraryId],
                        titleSort = row[SeriesSimilarityIndexTable.titleSort],
                        terms = terms,
                        language = row[SeriesSimilarityIndexTable.language],
                    )
                }
        }
    }

    override suspend fun seriesIdsOf(libraryId: String): List<String> = transaction {
        SeriesSimilarityIndexTable
            .select(SeriesSimilarityIndexTable.seriesId)
            .where { SeriesSimilarityIndexTable.libraryId eq libraryId }
            .map { it[SeriesSimilarityIndexTable.seriesId] }
    }

    override suspend fun allTitles(): List<SimilarityIndexTitle> = transaction {
        SeriesSimilarityIndexTable
            .select(
                SeriesSimilarityIndexTable.seriesId,
                SeriesSimilarityIndexTable.libraryId,
                SeriesSimilarityIndexTable.titleSort,
                SeriesSimilarityIndexTable.language,
            )
            .map {
                SimilarityIndexTitle(
                    seriesId = it[SeriesSimilarityIndexTable.seriesId],
                    libraryId = it[SeriesSimilarityIndexTable.libraryId],
                    titleSort = it[SeriesSimilarityIndexTable.titleSort],
                    language = it[SeriesSimilarityIndexTable.language],
                )
            }
    }

    override suspend fun countOf(libraryId: String): Int = transaction {
        SeriesSimilarityIndexTable
            .select(SeriesSimilarityIndexTable.seriesId)
            .where { SeriesSimilarityIndexTable.libraryId eq libraryId }
            .count()
            .toInt()
    }

    override suspend fun upsertAll(entries: List<SimilarityIndexEntry>) {
        if (entries.isEmpty()) return
        // Epoch millis rather than ISO text, like the other local timestamps here:
        // it round-trips through a TEXT column without a parser.
        val now = Clock.System.now().toEpochMilliseconds().toString()
        // Chunked: a full build hands over several thousand rows at once, and one
        // giant statement is where SQLite on a tablet starts stuttering.
        entries.chunked(WRITE_CHUNK).forEach { chunk ->
            transaction {
                SeriesSimilarityIndexTable.batchUpsert(chunk) { entry ->
                    this[SeriesSimilarityIndexTable.seriesId] = entry.seriesId
                    this[SeriesSimilarityIndexTable.libraryId] = entry.libraryId
                    this[SeriesSimilarityIndexTable.titleSort] = entry.titleSort
                    this[SeriesSimilarityIndexTable.terms] = JsonDbDefault.encodeToString(entry.terms)
                    this[SeriesSimilarityIndexTable.language] = entry.language
                    this[SeriesSimilarityIndexTable.updatedAt] = now
                }
            }
        }
    }

    override suspend fun deleteLibrary(libraryId: String) {
        transaction {
            SeriesSimilarityIndexTable.deleteWhere { SeriesSimilarityIndexTable.libraryId eq libraryId }
        }
    }

    override suspend fun deleteSeries(seriesIds: Collection<String>) {
        if (seriesIds.isEmpty()) return
        transaction {
            seriesIds.chunked(WRITE_CHUNK).forEach { chunk ->
                SeriesSimilarityIndexTable.deleteWhere { SeriesSimilarityIndexTable.seriesId.inList(chunk) }
            }
        }
    }

    override suspend fun stateOf(libraryId: String): SimilarityIndexState? {
        return transaction {
            SeriesSimilarityIndexStateTable.selectAll()
                .where { SeriesSimilarityIndexStateTable.libraryId eq libraryId }
                .firstOrNull()
                ?.let { row ->
                    SimilarityIndexState(
                        libraryId = row[SeriesSimilarityIndexStateTable.libraryId],
                        builtAt = row[SeriesSimilarityIndexStateTable.builtAt]
                            .toLongOrNull()
                            ?.let { Instant.fromEpochMilliseconds(it) },
                        seriesCount = row[SeriesSimilarityIndexStateTable.seriesCount],
                        genreCount = row[SeriesSimilarityIndexStateTable.genreCount],
                    )
                }
        }
    }

    override suspend fun putState(state: SimilarityIndexState) {
        transaction {
            SeriesSimilarityIndexStateTable.upsert {
                it[SeriesSimilarityIndexStateTable.libraryId] = state.libraryId
                it[SeriesSimilarityIndexStateTable.builtAt] =
                    state.builtAt?.toEpochMilliseconds()?.toString() ?: ""
                it[SeriesSimilarityIndexStateTable.seriesCount] = state.seriesCount
                it[SeriesSimilarityIndexStateTable.genreCount] = state.genreCount
            }
        }
    }

    override suspend fun putGenreCount(libraryId: String, count: Int) {
        transaction {
            SeriesSimilarityIndexStateTable.update(
                where = { SeriesSimilarityIndexStateTable.libraryId eq libraryId }
            ) {
                it[SeriesSimilarityIndexStateTable.genreCount] = count
            }
        }
    }
}

private const val WRITE_CHUNK = 500
