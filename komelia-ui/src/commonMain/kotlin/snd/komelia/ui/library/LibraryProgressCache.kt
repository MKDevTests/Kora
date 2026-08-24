package snd.komelia.ui.library

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.createDirectories
import io.github.vinceglb.filekit.div
import io.github.vinceglb.filekit.filesDir
import io.github.vinceglb.filekit.readBytes
import io.github.vinceglb.filekit.write
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import snd.komga.client.collection.KomgaCollectionId
import snd.komga.client.readlist.KomgaReadListId
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

private val logger = KotlinLogging.logger { }

/**
 * Disk memo for the progress bars on the Collections and Read Lists tiles.
 *
 * Those bars are decoration, and they were the most expensive thing on either
 * screen. Each one needs the whole membership of its collection — Komga exposes
 * no aggregate for it, `seriesIds` carries no `booksReadCount` — so the tile
 * fetched it unpaged. Measured on the Mangas library (64 collections, 9 read
 * lists), one visit to each tab:
 *
 * ```
 * GET /collections/{id}/series?unpaged=true   12 requests, 2193-7540 ms, queue to 5717 ms
 * GET /readlists/{id}/books?unpaged=true       9 requests, up to 15284 ms, of which 12433 queued
 * ```
 *
 * Twelve seconds of waiting for a connection slot: those requests were
 * saturating the pool on their own. And it is per *visible tile*, so scrolling
 * the full 64 collections means 64 complete downloads.
 *
 * The cost cannot be made smaller, only rarer — hence this memo. A bar can be
 * up to [TTL] out of date, which for a bar that says "you are about halfway
 * through this collection" is a trade worth taking; pulling to refresh clears
 * it, which is the deliberate way to ask for exact bars.
 *
 * [gate] is the other half. It bounds how many of these bulk fetches run at
 * once, so a first visit no longer starves everything else on the screen. Note
 * what this is NOT: the global in-flight cap that was built, measured and
 * rejected (it made each request faster and the total identical, while making
 * an interactive tap wait 2924 ms behind background work). This bounds work
 * already known to be bulk, and leaves every other request alone — which is
 * precisely the distinction that refutation surfaced.
 */
object LibraryProgressCache {
    private val TTL = 24.hours

    /**
     * At most this many membership fetches in flight. Two, not one: the tiles
     * are painted in a grid and a single permit would make the bars appear in a
     * visibly serial trickle.
     */
    val gate = Semaphore(2)

    private fun cacheDir() = FileKit.filesDir / "collection_progress"
    private fun cacheFile() = cacheDir() / "progress.json"

    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Mutex()
    private var entries: MutableMap<String, Entry>? = null

    private const val COLLECTION_PREFIX = "c:"
    private const val READ_LIST_PREFIX = "r:"

    fun collectionKey(id: KomgaCollectionId) = "$COLLECTION_PREFIX${id.value}"
    fun readListKey(id: KomgaReadListId) = "$READ_LIST_PREFIX${id.value}"

    /** The memoed ratio, or null when absent or older than [TTL]. */
    suspend fun get(key: String): Float? {
        val entry = lock.withLock { loaded()[key] } ?: return null
        val age = Clock.System.now().toEpochMilliseconds() - entry.atMillis
        // A negative age means the clock moved backwards. Treat it as stale
        // rather than as freshness that never expires.
        if (age < 0 || age > TTL.inWholeMilliseconds) return null
        return entry.progress
    }

    suspend fun put(key: String, progress: Float) {
        val snapshot = lock.withLock {
            val map = loaded()
            map[key] = Entry(key, progress, Clock.System.now().toEpochMilliseconds())
            map.values.toList()
        }
        write(snapshot)
    }

    /**
     * Drops the memos for one tab. Called by an explicit refresh, never by an
     * SSE event.
     *
     * Scoped per tab on purpose: refreshing Collections used to wipe the Read
     * Lists bars too, so the next visit there re-paid a cost the user never
     * asked to refresh.
     */
    suspend fun clearCollections() = clearPrefix(COLLECTION_PREFIX)

    suspend fun clearReadLists() = clearPrefix(READ_LIST_PREFIX)

    private suspend fun clearPrefix(prefix: String) {
        val snapshot = lock.withLock {
            val map = loaded()
            map.keys.filter { it.startsWith(prefix) }.forEach { map.remove(it) }
            map.values.toList()
        }
        write(snapshot)
    }

    private suspend fun loaded(): MutableMap<String, Entry> {
        entries?.let { return it }
        val fromDisk = runCatching {
            val bytes = cacheFile().readBytes()
            json.decodeFromString(ListSerializer(Entry.serializer()), bytes.decodeToString())
                .associateByTo(mutableMapOf()) { it.key }
        }.getOrElse {
            // Absent on a first run, unreadable after a bad write — both end here
            // and both are survivable. Logged all the same: a silently dead memo
            // looks exactly like a slow server.
            logger.debug(it) { "Collection progress memo not read; the bars will be fetched" }
            mutableMapOf()
        }
        entries = fromDisk
        return fromDisk
    }

    private suspend fun write(snapshot: List<Entry>) {
        runCatching {
            cacheDir().createDirectories()
            val encoded = json.encodeToString(ListSerializer(Entry.serializer()), snapshot)
            cacheFile().write(encoded.encodeToByteArray())
        }.onFailure { logger.warn(it) { "Collection progress memo write failed; the bars will be refetched" } }
    }

    @Serializable
    data class Entry(
        val key: String,
        val progress: Float,
        val atMillis: Long,
    )
}
