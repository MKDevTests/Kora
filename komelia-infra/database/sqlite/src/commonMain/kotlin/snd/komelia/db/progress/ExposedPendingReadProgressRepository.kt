package snd.komelia.db.progress

import kotlin.time.Instant
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert
import snd.komelia.db.ExposedRepository
import snd.komelia.db.tables.PendingReadProgressTable
import snd.komelia.progress.PendingReadProgress
import snd.komelia.progress.PendingReadProgressRepository

class ExposedPendingReadProgressRepository(
    database: Database,
) : ExposedRepository(database), PendingReadProgressRepository {

    override suspend fun getAll(): List<PendingReadProgress> = transaction {
        PendingReadProgressTable.selectAll().map {
            PendingReadProgress(
                bookId = it[PendingReadProgressTable.bookId],
                page = it[PendingReadProgressTable.page],
                totalPages = it[PendingReadProgressTable.totalPages],
                modified = Instant.parse(it[PendingReadProgressTable.modified]),
            )
        }
    }

    override suspend fun put(progress: PendingReadProgress) = transaction {
        PendingReadProgressTable.upsert {
            it[bookId] = progress.bookId
            it[page] = progress.page
            it[totalPages] = progress.totalPages
            it[modified] = progress.modified.toString()
        }
        Unit
    }

    override suspend fun delete(bookId: String) = transaction {
        PendingReadProgressTable.deleteWhere { PendingReadProgressTable.bookId eq bookId }
        Unit
    }
}
