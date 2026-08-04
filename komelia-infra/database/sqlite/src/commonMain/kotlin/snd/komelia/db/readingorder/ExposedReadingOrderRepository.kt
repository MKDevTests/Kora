package snd.komelia.db.readingorder

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert
import snd.komelia.db.ExposedRepository
import snd.komelia.db.JsonDbDefault
import snd.komelia.db.tables.SeriesReadingOrderCacheTable
import snd.komelia.db.tables.SeriesReadingOrderOriginalTable
import snd.komelia.readingorder.ReadingOrderGraph
import snd.komelia.readingorder.ReadingOrderRepository
import snd.komga.client.series.KomgaSeriesId
import kotlin.time.Clock

class ExposedReadingOrderRepository(
    database: Database,
) : ExposedRepository(database), ReadingOrderRepository {

    override suspend fun isOriginal(seriesId: KomgaSeriesId): Boolean = transaction {
        SeriesReadingOrderOriginalTable.selectAll()
            .where { SeriesReadingOrderOriginalTable.seriesId eq seriesId.value }
            .any()
    }

    override suspend fun originals(): Set<String> = transaction {
        SeriesReadingOrderOriginalTable.selectAll()
            .mapTo(mutableSetOf()) { it[SeriesReadingOrderOriginalTable.seriesId] }
    }

    override suspend fun setOriginal(seriesId: KomgaSeriesId, isOriginal: Boolean) {
        transaction {
            if (isOriginal) {
                SeriesReadingOrderOriginalTable.insertIgnore {
                    it[SeriesReadingOrderOriginalTable.seriesId] = seriesId.value
                }
            } else {
                SeriesReadingOrderOriginalTable.deleteWhere {
                    SeriesReadingOrderOriginalTable.seriesId eq seriesId.value
                }
            }
        }
    }

    override suspend fun getCached(originalSeriesId: KomgaSeriesId): ReadingOrderGraph? = transaction {
        SeriesReadingOrderCacheTable.selectAll()
            .where { SeriesReadingOrderCacheTable.originalSeriesId eq originalSeriesId.value }
            .firstOrNull()
            ?.let { row ->
                // A row we can't read is one recompute away from being fixed;
                // treat it as a miss rather than failing the screen.
                runCatching {
                    JsonDbDefault.decodeFromString<ReadingOrderGraph>(row[SeriesReadingOrderCacheTable.graph])
                }.getOrNull()
            }
    }

    override suspend fun putCached(graph: ReadingOrderGraph) {
        transaction {
            SeriesReadingOrderCacheTable.upsert {
                it[originalSeriesId] = graph.originalSeriesId
                it[SeriesReadingOrderCacheTable.graph] = JsonDbDefault.encodeToString(graph)
                it[builtAt] = Clock.System.now().toEpochMilliseconds().toString()
            }
        }
    }

    override suspend fun invalidateAll() {
        transaction { SeriesReadingOrderCacheTable.deleteAll() }
    }
}
