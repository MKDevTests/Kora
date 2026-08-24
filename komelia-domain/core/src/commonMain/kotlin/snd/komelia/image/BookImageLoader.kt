package snd.komelia.image

import coil3.disk.DiskCache
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okio.FileSystem
import okio.Path.Companion.toPath
import snd.komelia.komga.api.KomgaBookApi
import snd.komelia.komga.api.LocalFileApiProvider
import snd.komelia.offline.book.repository.OfflineBookRepository
import snd.komga.client.book.KomgaBookId

private val logger = KotlinLogging.logger {}

/** See [BookImageLoader.downloadLimit] for the measurement behind the number. */
private const val MAX_CONCURRENT_PAGE_DOWNLOADS = 4

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
            // AUDIT: plain page loading has never been timed. reader.page.total
            // exists but sits inside the translation scan, so it measures the
            // OCR path and says nothing about simply turning a page. Split in
            // two because the network half and the decode half have different
            // fixes.
            val source = snd.komelia.perf.PerfTrace.measure("image.page.fetch") { doLoad(bookId, page) }
            ReaderImageResult.Success(
                snd.komelia.perf.PerfTrace.measure("image.page.decode") {
                    readerImageFactory.getImage(source, ReaderImage.PageId(bookId.value, page, halfTag))
                }
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
     * Four, the same bound the home shelves, the genre counts and the
     * next-releases scan already use. It sits under OkHttp's eight per host,
     * which leaves room for the screen's own API calls instead of letting a
     * prefetch burst crowd them out.
     */
    private val downloadLimit = Semaphore(MAX_CONCURRENT_PAGE_DOWNLOADS)

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
                downloadLimit.withPermit { bookClient.value.getPage(bookId, page) }
            }
        }
        return downloadLimit.withPermit { bookClient.value.getPage(bookId, page) }
    }

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
