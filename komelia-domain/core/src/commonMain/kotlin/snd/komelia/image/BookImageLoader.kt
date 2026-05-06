package snd.komelia.image

import coil3.disk.DiskCache
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.StateFlow
import okio.FileSystem
import okio.Path.Companion.toPath
import snd.komelia.komga.api.KomgaBookApi
import snd.komelia.komga.api.LocalFileApiProvider
import snd.komelia.offline.book.repository.OfflineBookRepository
import snd.komga.client.book.KomgaBookId

private val logger = KotlinLogging.logger {}

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

    private suspend fun fetchPage(bookId: KomgaBookId, page: Int): ByteArray {
        localFileApiProvider?.getApiForBook(bookId)?.let { localApi ->
            return localApi.getPage(bookId, page)
        }
        if (offlineBookRepository?.find(bookId) != null && offlineBookApi != null) {
            return try {
                offlineBookApi.getPage(bookId, page)
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                logger.warn(e) { "Local page read failed for $bookId page $page, falling back to network" }
                bookClient.value.getPage(bookId, page)
            }
        }
        return bookClient.value.getPage(bookId, page)
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