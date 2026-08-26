package snd.komelia.savedfilters

/**
 * One search the user named and kept.
 *
 * [filterJson] is a serialized UI-layer filter, deliberately typed as a String
 * so this domain interface stays free of any UI model — the same choice the
 * per-library filter repository makes.
 */
data class SavedSeriesFilter(
    val id: String,
    val libraryId: String,
    val name: String,
    val position: Int,
    val filterJson: String,
)

/**
 * Named searches, scoped per library.
 *
 * The library key is a plain String rather than a KomgaLibraryId because the
 * "all libraries" view has no id and still needs somewhere to keep its
 * searches — see [ALL_LIBRARIES_KEY].
 */
interface SavedSeriesFilterRepository {
    /** The library's searches, in the order the user put them. */
    suspend fun getAll(libraryId: String): List<SavedSeriesFilter>

    /** Inserts or updates one search. Position is the caller's to decide. */
    suspend fun put(filter: SavedSeriesFilter)

    suspend fun delete(id: String)

    /** Every entry, all libraries. Used by backup/restore. */
    suspend fun getAll(): List<SavedSeriesFilter>

    /** Drops every entry. Used before a backup import. */
    suspend fun deleteAll()

    companion object {
        /**
         * Bucket for the library-less "all libraries" view.
         *
         * A real Komga library id is a ULID, so no id can ever collide with
         * this. That view is where a broad search — a language, a completion
         * state — is the most useful, which is why it gets a bucket instead of
         * having the feature switched off.
         */
        const val ALL_LIBRARIES_KEY = "__all__"
    }
}
