package snd.komelia.db.libraryfilters

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert
import snd.komelia.db.ExposedRepository
import snd.komelia.db.tables.LibrarySeriesFiltersTable
import snd.komelia.libraryfilters.LibrarySeriesFiltersRepository
import snd.komga.client.library.KomgaLibraryId

class ExposedLibrarySeriesFiltersRepository(
    database: Database,
) : ExposedRepository(database), LibrarySeriesFiltersRepository {

    override suspend fun get(libraryId: KomgaLibraryId): String? {
        return transaction {
            LibrarySeriesFiltersTable.selectAll()
                .where { LibrarySeriesFiltersTable.libraryId eq libraryId.value }
                .firstOrNull()
                ?.get(LibrarySeriesFiltersTable.filterJson)
        }
    }

    override suspend fun put(libraryId: KomgaLibraryId, json: String) {
        transaction {
            LibrarySeriesFiltersTable.upsert {
                it[LibrarySeriesFiltersTable.libraryId] = libraryId.value
                it[filterJson] = json
            }
        }
    }

    override suspend fun delete(libraryId: KomgaLibraryId) {
        transaction {
            LibrarySeriesFiltersTable.deleteWhere { LibrarySeriesFiltersTable.libraryId eq libraryId.value }
        }
    }

    override suspend fun getAll(): Map<KomgaLibraryId, String> {
        return transaction {
            LibrarySeriesFiltersTable.selectAll()
                .associate {
                    KomgaLibraryId(it[LibrarySeriesFiltersTable.libraryId]) to it[LibrarySeriesFiltersTable.filterJson]
                }
        }
    }

    override suspend fun deleteAll() {
        transaction { LibrarySeriesFiltersTable.deleteAll() }
    }
}
