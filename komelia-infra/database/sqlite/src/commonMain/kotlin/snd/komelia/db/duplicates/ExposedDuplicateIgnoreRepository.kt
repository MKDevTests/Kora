package snd.komelia.db.duplicates

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert
import snd.komelia.db.ExposedRepository
import snd.komelia.db.tables.DuplicateIgnoredTable
import snd.komelia.duplicates.DuplicateIgnoreRepository
import kotlin.time.Clock

/**
 * SQLite-backed ignore list for the duplicate finder (V103).
 *
 * No in-memory mirror, unlike the suggestion dismissals: this list is read once
 * when an admin opens the duplicates screen and never on a hot path, and it is
 * a few dozen rows at most.
 */
class ExposedDuplicateIgnoreRepository(
    database: Database,
) : ExposedRepository(database), DuplicateIgnoreRepository {

    override suspend fun ignoredPairs(): Set<String> = transaction {
        DuplicateIgnoredTable.selectAll()
            .mapTo(mutableSetOf()) { it[DuplicateIgnoredTable.pairKey] }
    }

    override suspend fun ignore(pairKey: String) {
        transaction {
            DuplicateIgnoredTable.upsert {
                it[DuplicateIgnoredTable.pairKey] = pairKey
                it[ignoredAt] = Clock.System.now().toEpochMilliseconds().toString()
            }
        }
    }

    override suspend fun unignore(pairKey: String) {
        transaction {
            DuplicateIgnoredTable.deleteWhere { DuplicateIgnoredTable.pairKey eq pairKey }
        }
    }

    override suspend fun clear() {
        transaction { DuplicateIgnoredTable.deleteAll() }
    }
}
