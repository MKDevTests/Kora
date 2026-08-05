package snd.komelia.library

/** How many collections, read lists and genres a library holds. */
data class LibraryCounts(
    val collections: Int,
    val readLists: Int,
    val genres: Int,
)

/**
 * Last known tab counts, per library.
 *
 * Kept on disk, not in memory: they decide whether the Genres / Collections /
 * Read lists chips appear, and re-asking the server costs seconds — measured at
 * up to 6.9 s for the read lists alone. A remembered count paints the chips
 * with the screen; the refresh then happens behind them, and a chip only moves
 * if the answer actually changed.
 */
interface LibraryCountsRepository {
    suspend fun get(libraryId: String): LibraryCounts?
    suspend fun put(libraryId: String, counts: LibraryCounts)
}
