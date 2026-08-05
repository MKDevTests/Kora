package snd.komelia.db.library

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert
import snd.komelia.db.ExposedRepository
import snd.komelia.db.tables.SeriesBooksCacheTable
import snd.komelia.komga.api.model.KomeliaBook
import snd.komelia.library.CachedSeriesBooks
import snd.komelia.library.SeriesBooksCacheRepository
import kotlin.time.Clock

class ExposedSeriesBooksCacheRepository(
    database: Database,
) : ExposedRepository(database), SeriesBooksCacheRepository {

    /** Lenient: a cache of a server answer, never a source of truth. */
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(KomeliaBook.serializer())

    override suspend fun get(seriesId: String): CachedSeriesBooks? {
        val row = transaction {
            SeriesBooksCacheTable
                .selectAll()
                .where { SeriesBooksCacheTable.seriesId eq seriesId }
                .firstOrNull()
                ?.let {
                    Triple(
                        it[SeriesBooksCacheTable.booksJson],
                        it[SeriesBooksCacheTable.pageSize],
                        it[SeriesBooksCacheTable.totalPages],
                    )
                }
        } ?: return null

        val books = runCatching { json.decodeFromString(serializer, row.first) }.getOrNull()
        return if (books.isNullOrEmpty()) null
        else CachedSeriesBooks(books = books, pageSize = row.second, totalPages = row.third)
    }

    override suspend fun put(seriesId: String, books: CachedSeriesBooks) {
        val encoded = runCatching { json.encodeToString(serializer, books.books) }.getOrNull() ?: return
        transaction {
            SeriesBooksCacheTable.upsert {
                it[SeriesBooksCacheTable.seriesId] = seriesId
                it[booksJson] = encoded
                it[pageSize] = books.pageSize
                it[totalPages] = books.totalPages
                it[updatedAt] = Clock.System.now().toEpochMilliseconds().toString()
            }
        }
    }
}
