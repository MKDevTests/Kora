package snd.komelia.db.savedfilters

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert
import snd.komelia.db.ExposedRepository
import snd.komelia.db.tables.SavedSeriesFiltersTable
import snd.komelia.savedfilters.SavedSeriesFilter
import snd.komelia.savedfilters.SavedSeriesFilterRepository

class ExposedSavedSeriesFilterRepository(
    database: Database,
) : ExposedRepository(database), SavedSeriesFilterRepository {

    override suspend fun getAll(libraryId: String): List<SavedSeriesFilter> {
        return transaction {
            SavedSeriesFiltersTable.selectAll()
                .where { SavedSeriesFiltersTable.libraryId eq libraryId }
                .map { it.toDomain() }
                .sortedBy { it.position }
        }
    }

    override suspend fun put(filter: SavedSeriesFilter) {
        transaction {
            SavedSeriesFiltersTable.upsert {
                it[id] = filter.id
                it[libraryId] = filter.libraryId
                it[name] = filter.name
                it[position] = filter.position
                it[filterJson] = filter.filterJson
            }
        }
    }

    override suspend fun delete(id: String) {
        transaction {
            SavedSeriesFiltersTable.deleteWhere { SavedSeriesFiltersTable.id eq id }
        }
    }

    override suspend fun getAll(): List<SavedSeriesFilter> {
        return transaction { SavedSeriesFiltersTable.selectAll().map { it.toDomain() } }
    }

    override suspend fun deleteAll() {
        transaction { SavedSeriesFiltersTable.deleteAll() }
    }

    private fun ResultRow.toDomain() = SavedSeriesFilter(
        id = this[SavedSeriesFiltersTable.id],
        libraryId = this[SavedSeriesFiltersTable.libraryId],
        name = this[SavedSeriesFiltersTable.name],
        position = this[SavedSeriesFiltersTable.position],
        filterJson = this[SavedSeriesFiltersTable.filterJson],
    )
}
