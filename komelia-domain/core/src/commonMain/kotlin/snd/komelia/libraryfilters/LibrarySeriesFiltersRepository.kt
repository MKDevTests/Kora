package snd.komelia.libraryfilters

import snd.komga.client.library.KomgaLibraryId

/**
 * Repository persisting per-library serialized filter state on disk so users
 * keep their selected sort/filter when switching libraries or restarting the app.
 *
 * The value is intentionally typed as [String] (JSON blob) so this domain-level
 * interface stays decoupled from any UI-layer model.
 */
interface LibrarySeriesFiltersRepository {
    suspend fun get(libraryId: KomgaLibraryId): String?
    suspend fun put(libraryId: KomgaLibraryId, json: String)
    suspend fun delete(libraryId: KomgaLibraryId)

    /** Snapshot of every persisted entry. Used by backup/restore. */
    suspend fun getAll(): Map<KomgaLibraryId, String>

    /** Drops every entry. Used before a backup import. */
    suspend fun deleteAll()
}
