package snd.komelia.image

import coil3.disk.DiskCache
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.plugins.ServerResponseException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import snd.komelia.NetworkState
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Semaphore
import okio.FileSystem
import okio.Path.Companion.toPath
import snd.komelia.isTransientNetworkFailure
import snd.komelia.komga.api.KomgaBookApi
import snd.komelia.komga.api.LocalFileApiProvider
import snd.komelia.offline.book.repository.OfflineBookRepository
import snd.komga.client.book.KomgaBookId

private val logger = KotlinLogging.logger {}

/** See [BookImageLoader.sharedLane] for the measurement behind the numbers. */
private const val SHARED_PAGE_DOWNLOADS = 3
private const val URGENT_PAGE_DOWNLOADS = 1

/**
 * Pauses between attempts at a page whose download failed on the network path,
 * in seconds. See [BookImageLoader.fetchFromServer].
 *
 * The sum is 90 s. Measured on the tablet on 2026-09-13, twice: from the first
 * request after waking to the Wi-Fi coming back took 63 s and 65 s. Each
 * attempt itself waits up to the client's connect timeout on top of these.
 */
private val NETWORK_RETRY_DELAYS_S = listOf(2L, 4L, 8L, 16L, 30L, 30L)

/**
 * Pauses between attempts at a page the server refused with a 5xx. Two
 * only: a server that is busy (Komga's read-only SQLite pool has one
 * connection; under a burst it answers 500 and 40 s per request, seen on
 * 2026-08) recovers in seconds, a server that is broken does not, and a
 * bad file comes back the same every time.
 */
private val SERVER_RETRY_DELAYS_S = listOf(3L, 6L)
private val RETRYABLE_SERVER_STATUS = setOf(500, 502, 503, 504)

/**
 * Thrown into a page download when every physical network is gone. The
 * request would otherwise sit on a dead socket for the whole socket
 * timeout — measured 46 s on 2026-09-14 — holding a download permit, and
 * only fail once the link was back. Treated as a network failure: the
 * retry loop takes over and waits for the comeback.
 */
private class NetworkLostException : RuntimeException("network lost")

/**
 * A page download waiting to be tried again after a network failure. Shown
 * under the page's spinner, which otherwise says "Downloading" for up to three
 * minutes with nothing to explain the wait.
 */
data class PageRetry(
    val attempt: Int,
    val maxAttempts: Int,
    /** Wall-clock millis of the next attempt; the display counts down to it. */
    val nextAttemptAtMillis: Long,
    /** True when the server answered 5xx, false when the network path failed. */
    val serverBusy: Boolean = false,
    /**
     * True while the device has no physical network at all: no attempt is
     * being counted, the page waits for the comeback and [nextAttemptAtMillis]
     * means nothing.
     */
    val waitingForNetwork: Boolean = false,
)

class BookImageLoader(
    private val bookClient: StateFlow<KomgaBookApi>,
    private val imageDecoder: KomeliaImageDecoder,
    private val readerImageFactory: ReaderImageFactory,
    //TODO consider non coil disk cache implementation?
    val diskCache: DiskCache?,
    private val offlineBookRepository: OfflineBookRepository? = null,
    private val offlineBookApi: KomgaBookApi? = null,
    private val localFileApiProvider: LocalFileApiProvider? = null,
) {
    val fileSystem = diskCache?.fileSystem

    suspend fun loadReaderImage(
        bookId: KomgaBookId,
        page: Int,
        halfTag: String? = null,
    ): ReaderImageResult {
        return try {
            val source = doLoad(bookId, page)
            ReaderImageResult.Success(
                readerImageFactory.getImage(source, ReaderImage.PageId(bookId.value, page, halfTag))
            )
        } catch (e: Throwable) {
            currentCoroutineContext().ensureActive()
            logger.catching(e)
            ReaderImageResult.Error(e)
        }
    }

    // TODO remove
    suspend fun loadImage(bookId: KomgaBookId, page: Int): ImageResult {
        return try {
            doLoad(bookId, page).use { source ->
                val image = when (source) {
                    is ImageSource.FilePathSource -> {
                        val fileSystem = checkNotNull(fileSystem)
                        imageDecoder.decode(fileSystem.read(source.path.toPath()) { readByteArray() })
                    }

                    is ImageSource.MemorySource -> imageDecoder.decode(source.data)
                }
                ImageResult.Success(image)
            }
        } catch (e: Throwable) {
            currentCoroutineContext().ensureActive()
            logger.catching(e)
            ImageResult.Error(e)
        }
    }

    /**
     * How many page downloads may be in flight at once, across every reader.
     *
     * Measured on 2026-08-20 against the user's own server: the same
     * /api/v1/books/ondeck answered curl in 1.6s with the app closed, and took
     * 23 152, 24 090 and 25 643ms inside the app, twice crossing the 30s socket
     * timeout. The server is not slow. We fire more than it can answer at once
     * and then time out waiting for our own queue.
     *
     * That is what made one page fail while its neighbour was fine on a healthy
     * network: nothing is wrong with page P+1, it is simply the request that
     * was starved this time. A retry button alone would not have fixed it --
     * the retry would join the same queue.
     *
     * Four in total, the same bound the home shelves, the genre counts and
     * the next-releases scan already use. It sits under OkHttp's eight per
     * host, which leaves room for the screen's own API calls instead of
     * letting a prefetch burst crowd them out.
     *
     * Split three and one since 2026-09-14. Measured the day before with the
     * Wi-Fi cut: the page on screen (644) got its error 40 s after the five
     * prefetched pages ahead of it in the queue, each of which held a permit
     * for a 10 s connect that could not succeed. The pages the reader has
     * declared [urgent][setUrgentPages] take a shared permit when one is free
     * and the reserved one otherwise, so the page being looked at never
     * queues behind read-ahead; read-ahead only ever uses the shared three.
     * On a healthy network the shared lane is rarely full, so nothing changes.
     */
    private val sharedLane = Semaphore(SHARED_PAGE_DOWNLOADS)
    private val urgentLane = Semaphore(URGENT_PAGE_DOWNLOADS)

    /** Bumped on every permit release and every urgency change; waiters re-check. */
    private val laneTick = MutableStateFlow(0L)
    private val urgentPages = MutableStateFlow<Set<ReaderImage.PageId>>(emptySet())

    /**
     * The pages the reader is showing right now, whole-page ids (no half tag).
     * Replaces the previous set: what was urgent a page ago is read-ahead now.
     */
    fun setUrgentPages(pages: Set<ReaderImage.PageId>) {
        val whole = pages.map { ReaderImage.PageId(it.bookId, it.pageNumber) }.toSet()
        if (urgentPages.value == whole) return
        urgentPages.value = whole
        laneTick.update { it + 1 }
    }

    private suspend fun <T> withDownloadLane(pageId: ReaderImage.PageId, block: suspend () -> T): T {
        while (true) {
            val seen = laneTick.value
            val lane = when {
                sharedLane.tryAcquire() -> sharedLane
                pageId in urgentPages.value && urgentLane.tryAcquire() -> urgentLane
                else -> null
            }
            if (lane != null) {
                try {
                    return block()
                } finally {
                    lane.release()
                    laneTick.update { it + 1 }
                }
            }
            // A release or an urgency change since `seen` returns at once.
            laneTick.first { it != seen }
        }
    }

    private val _retries = MutableStateFlow<Map<ReaderImage.PageId, PageRetry>>(emptyMap())

    /** Pages currently between two attempts, keyed by page. See [PageRetry]. */
    val retries: StateFlow<Map<ReaderImage.PageId, PageRetry>> = _retries

    private suspend fun fetchPage(bookId: KomgaBookId, page: Int): ByteArray {
        localFileApiProvider?.getApiForBook(bookId)?.let { localApi ->
            // Local files are not a server request and must not take a permit:
            // holding one here would let an offline book throttle the online
            // reader for no reason.
            return localApi.getPage(bookId, page)
        }
        if (offlineBookRepository?.find(bookId) != null && offlineBookApi != null) {
            return try {
                offlineBookApi.getPage(bookId, page)
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                logger.warn(e) { "Local page read failed for $bookId page $page, falling back to network" }
                fetchFromServer(bookId, page)
            }
        }
        return fetchFromServer(bookId, page)
    }

    /**
     * One page from the server, tried again after a network failure.
     *
     * Why here and not in the readers: both readers cache the result of a load,
     * including a failure, and hand it back until the user presses Reload or
     * leaves the book. That is deliberate — a request loop is the wrong answer
     * to a server in trouble — so the result they cache must already be the
     * outcome of a patient attempt. Only network-path failures are retried
     * ([isTransientNetworkFailure]): an HTTP error or a bad file comes back the
     * same every time.
     *
     * The pause is taken OUTSIDE the download permit: a page waiting for the
     * Wi-Fi to return must not hold one of the four slots while it waits, and
     * it ends early when the platform announces the link is back
     * ([NetworkState.comebacks]) — the pause is a ceiling, not a schedule.
     * Cancellation (the reader moved on, the book was closed) ends the wait —
     * the wait is cancellable and `ensureActive` is checked after each failure.
     */
    private suspend fun fetchFromServer(bookId: KomgaBookId, page: Int): ByteArray {
        val pageId = ReaderImage.PageId(bookId.value, page)
        var attempt = 0
        var serverAttempt = 0
        var waited = false
        try {
            while (true) {
                try {
                    return withDownloadLane(pageId) { getPageUnlessNetworkLost(bookId, page) }
                } catch (e: Throwable) {
                    currentCoroutineContext().ensureActive()
                    val serverBusy = e.retryableServerStatus() != null
                    // No physical network at all: an attempt cannot succeed
                    // and must not be counted. Measured 2026-09-14: without
                    // this, six "network lost" attempts went by in forty
                    // seconds and the page showed an error while the Wi-Fi
                    // was simply off. Waits for the comeback, which is the
                    // only thing that can change the outcome.
                    if (!serverBusy && !NetworkState.isAvailable.value) {
                        logger.info { "page $page of $bookId: no network, waiting for it (attempt ${attempt + 1}/${NETWORK_RETRY_DELAYS_S.size} kept)" }
                        _retries.update {
                            it + (pageId to PageRetry(attempt + 1, NETWORK_RETRY_DELAYS_S.size, nowMillis(), waitingForNetwork = true))
                        }
                        waited = true
                        NetworkState.isAvailable.first { it }
                        continue
                    }
                    val pause = when {
                        serverBusy -> SERVER_RETRY_DELAYS_S.getOrNull(serverAttempt)
                        e is NetworkLostException || isTransientNetworkFailure(e) || e.cancelledByNetworkLoss() ->
                            NETWORK_RETRY_DELAYS_S.getOrNull(attempt)
                        else -> null
                    } ?: throw e
                    val (n, max) = if (serverBusy) {
                        serverAttempt++
                        serverAttempt to SERVER_RETRY_DELAYS_S.size
                    } else {
                        attempt++
                        attempt to NETWORK_RETRY_DELAYS_S.size
                    }
                    logger.warn {
                        "page $page of $bookId: ${e::class.simpleName}: ${e.message} -- " +
                            "${if (serverBusy) "server " else ""}retry $n/$max in ${pause}s"
                    }
                    _retries.update {
                        it + (pageId to PageRetry(n, max, nowMillis() + pause * 1000, serverBusy))
                    }
                    if (serverBusy) {
                        delay(pause * 1000)
                    } else {
                        val comebacks = NetworkState.comebacks.value
                        withTimeoutOrNull(pause * 1000) { NetworkState.comebacks.first { it != comebacks } }
                    }
                }
            }
        } finally {
            if (attempt > 0 || serverAttempt > 0 || waited) _retries.update { it - pageId }
        }
    }

    /**
     * The download itself, abandoned the moment the device has no physical
     * network left (see [NetworkLostException]). The request is cancelled
     * — OkHttp closes the socket — and the lane is released with it.
     */
    private suspend fun getPageUnlessNetworkLost(bookId: KomgaBookId, page: Int): ByteArray = coroutineScope {
        val download = async { bookClient.value.getPage(bookId, page) }
        val watcher = launch {
            NetworkState.isAvailable.first { !it }
            download.cancel(CancellationException("network lost", NetworkLostException()))
        }
        try {
            download.await()
        } catch (e: CancellationException) {
            currentCoroutineContext().ensureActive()
            throw e.cause as? NetworkLostException ?: e
        } finally {
            watcher.cancel()
        }
    }

    /**
     * OkHttp reports a call cancelled by the module's network-lost hook as
     * "Canceled", the same word as a user cancellation — which is why
     * [isTransientNetworkFailure] leaves it out. Here the coroutine is still
     * alive and the network is gone, so it is the hook, and it retries.
     */
    private fun Throwable.cancelledByNetworkLoss(): Boolean {
        if (NetworkState.isAvailable.value) return false
        var t: Throwable? = this
        repeat(4) {
            val e = t ?: return false
            if (e.message == "Canceled") return true
            t = e.cause
        }
        return false
    }

    private fun Throwable.retryableServerStatus(): Int? {
        var t: Throwable? = this
        repeat(4) {
            val e = t ?: return null
            if (e is ServerResponseException && e.response.status.value in RETRYABLE_SERVER_STATUS) return e.response.status.value
            t = e.cause
        }
        return null
    }

    private fun nowMillis(): Long = kotlin.time.Clock.System.now().toEpochMilliseconds()

    private suspend fun doLoad(bookId: KomgaBookId, page: Int): ImageSource {
        val pageId = ReaderImage.PageId(bookId.value, page)
        if (diskCache == null) {
            val bytes: ByteArray = fetchPage(bookId, page)
            return ImageSource.MemorySource(bytes)
        }

        val existingSnapshot = diskCache.openSnapshot(pageId.toString())
        val fileSystem = diskCache.fileSystem
        if (existingSnapshot != null) {
            return ImageSource.FilePathSource(existingSnapshot)
        }

        val bytes = fetchPage(bookId, page)
        val newSnapshot = writeToDiskCache(
            fileSystem = fileSystem,
            cacheKey = pageId.toString(),
            bytes = bytes
        )

        return newSnapshot?.let { ImageSource.FilePathSource(it) }
            ?: ImageSource.MemorySource(bytes)
    }

    private fun writeToDiskCache(
        fileSystem: FileSystem,
        cacheKey: String,
        bytes: ByteArray,
    ): DiskCache.Snapshot? {
        val editor = diskCache?.openEditor(cacheKey) ?: return null
        try {
            fileSystem.write(editor.data) { this.write(bytes) }
            return editor.commitAndOpenSnapshot()
        } catch (e: Exception) {
            editor.abort()
            throw e
        }
    }
}

sealed interface ReaderImageResult {
    val image: ReaderImage?

    data class Success(override val image: ReaderImage) : ReaderImageResult
    data class Error(val throwable: Throwable) : ReaderImageResult {
        override val image: ReaderImage? = null
    }
}

sealed interface ImageResult {
    val image: KomeliaImage?

    data class Success(override val image: KomeliaImage) : ImageResult
    data class Error(val throwable: Throwable) : ImageResult {
        override val image: KomeliaImage? = null
    }
}

sealed interface ImageSource : AutoCloseable {
    class MemorySource(val data: ByteArray) : ImageSource {
        override fun close() = Unit
    }

    class FilePathSource(
        val path: String,
        private val cacheLock: DiskCache.Snapshot?
    ) : ImageSource {
        constructor(snapshot: DiskCache.Snapshot) : this(path = snapshot.data.toString(), cacheLock = snapshot)

        override fun close() {
            cacheLock?.close()
        }
    }
}
