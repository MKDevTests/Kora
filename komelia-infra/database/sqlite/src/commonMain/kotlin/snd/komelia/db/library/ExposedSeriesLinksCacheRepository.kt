package snd.komelia.db.library

import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert
import snd.komelia.db.ExposedRepository
import snd.komelia.db.tables.SeriesLinksCacheTable
import snd.komelia.library.CachedSeriesLinks
import snd.komelia.library.SeriesLinksCacheRepository
import kotlin.time.Clock

class ExposedSeriesLinksCacheRepository(
    database: Database,
) : ExposedRepository(database), SeriesLinksCacheRepository {

    /** Lenient: this is a cache of a server answer, never a source of truth. */
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun get(seriesId: String): CachedSeriesLinks? {
        val stored = transaction {
            SeriesLinksCacheTable
                .selectAll()
                .where { SeriesLinksCacheTable.seriesId eq seriesId }
                .firstOrNull()
                ?.get(SeriesLinksCacheTable.linksJson)
        } ?: return null
        return runCatching { json.decodeFromString<CachedSeriesLinks>(stored) }.getOrNull()
    }

    override suspend fun put(seriesId: String, links: CachedSeriesLinks) {
        val encoded = runCatching { json.encodeToString(links) }.getOrNull() ?: return
        transaction {
            SeriesLinksCacheTable.upsert {
                it[SeriesLinksCacheTable.seriesId] = seriesId
                it[linksJson] = encoded
                it[updatedAt] = Clock.System.now().toEpochMilliseconds().toString()
            }
        }
    }
}
