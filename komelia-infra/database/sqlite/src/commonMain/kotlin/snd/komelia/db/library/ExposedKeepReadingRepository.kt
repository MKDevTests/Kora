package snd.komelia.db.library

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert
import snd.komelia.db.ExposedRepository
import snd.komelia.db.tables.KeepReadingCacheTable
import snd.komelia.komga.api.model.KomeliaBook
import snd.komelia.library.KeepReadingRepository
import kotlin.time.Clock

class ExposedKeepReadingRepository(
    database: Database,
) : ExposedRepository(database), KeepReadingRepository {

    /**
     * Lenient on purpose: this is a cache of a server answer, and a book model
     * that gained a field between two app versions must degrade to an empty row
     * — one silent request — not to a crash on a screen the user just opened.
     */
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(KomeliaBook.serializer())

    override suspend fun get(libraryId: String): List<KomeliaBook> {
        val stored = transaction {
            KeepReadingCacheTable
                .selectAll()
                .where { KeepReadingCacheTable.libraryId eq libraryId }
                .firstOrNull()
                ?.get(KeepReadingCacheTable.booksJson)
        } ?: return emptyList()

        // An unreadable row means one silent refresh, which is what the caller
        // does anyway when the cache comes back empty.
        return runCatching { json.decodeFromString(serializer, stored) }.getOrElse { emptyList() }
    }

    override suspend fun put(libraryId: String, books: List<KomeliaBook>) {
        val encoded = runCatching { json.encodeToString(serializer, books) }.getOrNull() ?: return
        transaction {
            KeepReadingCacheTable.upsert {
                it[KeepReadingCacheTable.libraryId] = libraryId
                it[booksJson] = encoded
                it[updatedAt] = Clock.System.now().toEpochMilliseconds().toString()
            }
        }
    }
}
