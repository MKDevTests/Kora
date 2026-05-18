package snd.komelia.db.reader

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert
import snd.komelia.db.ExposedRepository
import snd.komelia.db.tables.SeriesReaderOverridesTable
import snd.komelia.reader.SeriesReaderOverridesRepository
import snd.komga.client.series.KomgaSeriesId

class ExposedSeriesReaderOverridesRepository(
    database: Database,
) : ExposedRepository(database), SeriesReaderOverridesRepository {

    override suspend fun getReadingDirection(seriesId: KomgaSeriesId): String? {
        return transaction {
            SeriesReaderOverridesTable.selectAll()
                .where { SeriesReaderOverridesTable.seriesId eq seriesId.value }
                .firstOrNull()
                ?.get(SeriesReaderOverridesTable.readingDirection)
        }
    }

    override suspend fun putReadingDirection(seriesId: KomgaSeriesId, direction: String) {
        transaction {
            SeriesReaderOverridesTable.upsert {
                it[SeriesReaderOverridesTable.seriesId] = seriesId.value
                it[readingDirection] = direction
            }
        }
    }

    override suspend fun delete(seriesId: KomgaSeriesId) {
        transaction {
            SeriesReaderOverridesTable.deleteWhere {
                SeriesReaderOverridesTable.seriesId eq seriesId.value
            }
        }
    }

    override suspend fun getAll(): Map<KomgaSeriesId, String> {
        return transaction {
            SeriesReaderOverridesTable.selectAll()
                .associate {
                    KomgaSeriesId(it[SeriesReaderOverridesTable.seriesId]) to it[SeriesReaderOverridesTable.readingDirection]
                }
        }
    }

    override suspend fun deleteAll() {
        transaction { SeriesReaderOverridesTable.deleteAll() }
    }
}
