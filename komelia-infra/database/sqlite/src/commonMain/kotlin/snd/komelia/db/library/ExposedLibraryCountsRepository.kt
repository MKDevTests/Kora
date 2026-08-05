package snd.komelia.db.library

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert
import snd.komelia.db.ExposedRepository
import snd.komelia.db.tables.LibraryCountsTable
import snd.komelia.library.LibraryCounts
import snd.komelia.library.LibraryCountsRepository
import kotlin.time.Clock

class ExposedLibraryCountsRepository(
    database: Database,
) : ExposedRepository(database), LibraryCountsRepository {

    override suspend fun get(libraryId: String): LibraryCounts? = transaction {
        LibraryCountsTable
            .selectAll()
            .where { LibraryCountsTable.libraryId eq libraryId }
            .firstOrNull()
            ?.let {
                LibraryCounts(
                    collections = it[LibraryCountsTable.collections],
                    readLists = it[LibraryCountsTable.readLists],
                    genres = it[LibraryCountsTable.genres],
                )
            }
    }

    override suspend fun put(libraryId: String, counts: LibraryCounts) {
        transaction {
            LibraryCountsTable.upsert {
                it[LibraryCountsTable.libraryId] = libraryId
                it[collections] = counts.collections
                it[readLists] = counts.readLists
                it[genres] = counts.genres
                it[updatedAt] = Clock.System.now().toEpochMilliseconds().toString()
            }
        }
    }
}
