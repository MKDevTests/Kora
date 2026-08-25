package snd.komelia.ui.home

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.createDirectories
import io.github.vinceglb.filekit.div
import io.github.vinceglb.filekit.filesDir
import io.github.vinceglb.filekit.readBytes
import io.github.vinceglb.filekit.write
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonElement
import snd.komelia.ui.common.encodeNullReadingDirectionAsBlank
import snd.komelia.ui.common.komgaCacheJson
import snd.komelia.perf.PerfTrace
import snd.komelia.ui.common.onDisk
import snd.komelia.homefilters.BooksHomeScreenFilter
import snd.komelia.homefilters.HomeScreenFilter
import snd.komelia.homefilters.SeriesHomeScreenFilter
import snd.komelia.komga.api.model.KomeliaBook
import snd.komga.client.series.KomgaSeries

private val logger = KotlinLogging.logger { }

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

    private val json = komgaCacheJson

    /** Stable per-shelf key. Both fields are scalars owned by Kora. */
    fun shelfKey(filter: HomeScreenFilter): String = "${filter.order}:${filter.label}"

    /**
     * Last known shelves, keyed by [shelfKey]. Null if absent or unreadable.
     * A miss is never fatal, but it MUST be visible: a silently disabled cache is
     * indistinguishable from a slow server.
     */
    suspend fun load(): Map<String, PersistedShelf>? = PerfTrace.measure("home.cacheLoad", { it?.size }) {
        runCatching {
            val bytes = cacheFile().readBytes()
            json.decodeFromString(ListSerializer(PersistedShelf.serializer()), bytes.decodeToString())
                .associateBy { it.key }
        }.onFailure { logger.warn(it) { "Home shelf snapshot unreadable; falling back to a network load" } }.getOrNull()
    }

    /** Best-effort write — logged loudly, because a silent failure costs every cold start. */
    suspend fun save(data: List<HomeFilterData>) {
        PerfTrace.measure("home.cacheSave") {
            onDisk {
                runCatching {
                    cacheDir().createDirectories()
                    val shelves = data.map { d ->
                        when (d) {
                            is SeriesFilterData -> PersistedShelf(shelfKey(d.filter), series = d.series)
                            is BookFilterData -> PersistedShelf(shelfKey(d.filter), books = d.books)
                        }
                    }
                    // See encodeNullReadingDirectionAsBlank: a null readingDirection is
                    // written by komga-client in a form it cannot read back.
                    val tree = json.encodeToJsonElement(ListSerializer(PersistedShelf.serializer()), shelves)
                        .encodeNullReadingDirectionAsBlank()
                    val encoded = json.encodeToString(JsonElement.serializer(), tree)
                    cacheFile().write(encoded.encodeToByteArray())
                }.onFailure { logger.warn(it) { "Home shelf snapshot write failed; next cold start will hit the network" } }
            }
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
