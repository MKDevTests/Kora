package snd.komelia.ui.home

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.createDirectories
import io.github.vinceglb.filekit.div
import io.github.vinceglb.filekit.filesDir
import io.github.vinceglb.filekit.readBytes
import io.github.vinceglb.filekit.write
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import snd.komelia.homefilters.BooksHomeScreenFilter
import snd.komelia.homefilters.HomeScreenFilter
import snd.komelia.homefilters.SeriesHomeScreenFilter
import snd.komelia.komga.api.model.KomeliaBook
import snd.komga.client.series.KomgaSeries

/**
 * Disk snapshot of the last successfully loaded Home shelves.
 *
 * Home costs one Komga query per shelf and waits for ALL of them before painting
 * anything. Measured on the user's server: 11 shelves, 7.3s total — with 9 of
 * them already done at 3.2s. None of that was cached across a process restart
 * (RandomShelfCache is memory-only and covers random shelves only), so every
 * cold start re-paid the full 7.3s in front of an empty screen.
 *
 * With this snapshot the last known shelves paint immediately while the network
 * refresh runs silently behind them. Mirrors the genre catalog and
 * [snd.komelia.ui.nextreleases.NextReleasesCache].
 *
 * Shelves are keyed by [shelfKey] (order + label) rather than by the filter
 * object: nested KomgaPageRequest / KomgaSearchCondition instances aren't
 * reliably `equals()`-comparable, so two loads of the same shelf would otherwise
 * miss each other — the same trap RandomShelfCache's key documents.
 */
object HomeShelfCache {
    private fun cacheDir() = FileKit.filesDir / "home_shelves"
    private fun cacheFile() = cacheDir() / "shelves.json"

    /** Stable per-shelf key. Both fields are scalars owned by Kora. */
    fun shelfKey(filter: HomeScreenFilter): String = "${filter.order}:${filter.label}"

    /** Last known shelves, keyed by [shelfKey]. Null if absent or unreadable. */
    suspend fun load(): Map<String, PersistedShelf>? = runCatching {
        val json = cacheFile().readBytes().decodeToString()
        Json.decodeFromString(ListSerializer(PersistedShelf.serializer()), json)
            .associateBy { it.key }
    }.getOrNull()

    /** Best-effort write — a failure only costs the next cold start its instant paint. */
    suspend fun save(data: List<HomeFilterData>) {
        runCatching {
            cacheDir().createDirectories()
            val shelves = data.map { d ->
                when (d) {
                    is SeriesFilterData -> PersistedShelf(shelfKey(d.filter), series = d.series)
                    is BookFilterData -> PersistedShelf(shelfKey(d.filter), books = d.books)
                }
            }
            cacheFile().write(
                Json.encodeToString(ListSerializer(PersistedShelf.serializer()), shelves).encodeToByteArray()
            )
        }
    }

    /**
     * Re-pair a persisted shelf with its live filter. The filter itself is never
     * persisted here — it is re-read from the database on every load, so a shelf
     * the user has since edited or reordered simply won't match and is skipped.
     */
    fun toFilterData(shelf: PersistedShelf, filter: HomeScreenFilter): HomeFilterData? = when (filter) {
        is SeriesHomeScreenFilter -> shelf.series?.let { SeriesFilterData(it, filter) }
        is BooksHomeScreenFilter -> shelf.books?.let { BookFilterData(it, filter) }
    }
}

@Serializable
data class PersistedShelf(
    val key: String,
    val series: List<KomgaSeries>? = null,
    val books: List<KomeliaBook>? = null,
)
