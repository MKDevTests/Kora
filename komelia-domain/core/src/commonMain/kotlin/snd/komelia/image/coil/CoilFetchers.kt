package snd.komelia.image.coil

import coil3.decode.DataSource
import coil3.disk.DiskCache
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.Options
import coil3.size.isOriginal
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import snd.komelia.komga.api.KomgaBookApi
import snd.komelia.komga.api.KomgaCollectionsApi
import snd.komelia.komga.api.KomgaReadListApi
import snd.komelia.komga.api.KomgaSeriesApi
import snd.komelia.komga.api.LocalFileApiProvider
import snd.komelia.offline.book.repository.OfflineBookRepository
import snd.komga.client.book.KomgaBookId
import snd.komga.client.collection.KomgaCollectionId
import snd.komga.client.common.KomgaThumbnailId
import snd.komga.client.readlist.KomgaReadListId
import snd.komga.client.series.KomgaSeriesId

/**
 * Base fetcher for every Komga thumbnail, with its own disk cache.
 *
 * Coil's `diskCache` is only ever populated by its network fetcher. These
 * fetchers call the Komga API themselves and return an already-decoded
 * [ImageFetchResult] — which Coil cannot cache, since there are no bytes to
 * write. So `.diskCache { … }` in AppModule was decorative for covers and EVERY
 * cold start re-downloaded EVERY cover (measured: 65 covers, 0 from disk, on a
 * server answering in 2-9s behind a 5-connection cap). That is what the user saw
 * as "Home is slow", long after the data itself was served from a local snapshot
 * in 145ms.
 *
 * We therefore cache the raw bytes ourselves, keyed by [cacheKey], and keep
 * decoding eagerly (upstream's reason for [ImageFetchResult]: avoid copying the
 * ByteArray into an okio buffer). Reported [DataSource] is now honest — DISK vs
 * NETWORK — instead of hardcoded NETWORK.
 *
 * Invalidation: explicit thumbnails embed their thumbnailId in the key, so they
 * self-invalidate. Default thumbnails cannot (no id), so [ThumbnailCacheKeys]
 * exposes their keys for removal on Komga's thumbnail SSE events.
 */
abstract class CoilFetcher(
    private val decoder: CoilAwareDecoder,
    private val options: Options,
    private val diskCache: DiskCache? = null,
    private val cacheKey: String? = null,
) : Fetcher {

    protected abstract suspend fun fetchBytes(): ByteArray?

    override suspend fun fetch(): FetchResult? {
        readFromDisk()?.let { return decode(it, DataSource.DISK) }

        val bytes = fetchBytes() ?: return null
        writeToDisk(bytes)
        return decode(bytes, DataSource.NETWORK)
    }

    // decode right away to avoid copying bytearray into okio buffer
    private fun decode(bytes: ByteArray, dataSource: DataSource): FetchResult {
        decoder.decodeBytes(bytes, options).use { image ->
            return ImageFetchResult(
                image = image.toCoilImage(),
                isSampled = !options.size.isOriginal,
                dataSource = dataSource
            )
        }
    }

    /** A cache miss (or an unreadable entry) is never fatal — we just refetch. */
    private fun readFromDisk(): ByteArray? {
        val cache = diskCache ?: return null
        val key = cacheKey ?: return null
        return runCatching {
            cache.openSnapshot(key)?.use { snapshot ->
                cache.fileSystem.read(snapshot.data) { readByteArray() }
            }
        }.getOrNull()
    }

    /** Best-effort — a failed write only costs the next cold start this cover. */
    private fun writeToDisk(bytes: ByteArray) {
        val cache = diskCache ?: return
        val key = cacheKey ?: return
        runCatching {
            cache.openEditor(key)?.let { editor ->
                try {
                    cache.fileSystem.write(editor.data) { write(bytes) }
                    editor.commit()
                } catch (e: Throwable) {
                    editor.abort()
                    throw e
                }
            }
        }
    }
}

/**
 * Disk-cache keys for Komga thumbnails.
 *
 * **The default-thumbnail key is the raw entity id, and that is load-bearing.**
 * [snd.komelia.ManagedKomgaEvents] already invalidates on Komga's thumbnail SSE
 * events with exactly that key:
 *
 *     is ThumbnailSeriesAdded -> diskCache?.remove(seriesId.value)
 *
 * That invalidation has always been dead code — nothing ever wrote to the disk
 * cache for it to remove. Now that these fetchers do, matching its key scheme
 * makes it work with no extra wiring. Change one side and stale covers come
 * back, silently.
 *
 * Explicit thumbnails embed their thumbnailId, so a new thumbnail yields a new
 * key and the old entry just ages out — they need no invalidation, which is why
 * removing only the default key is correct.
 */
object ThumbnailCacheKeys {
    // Keep in sync with ManagedKomgaEvents.remove*ThumbnailCache.
    fun defaultBook(bookId: String) = bookId
    fun defaultSeries(seriesId: String) = seriesId
    fun defaultCollection(collectionId: String) = collectionId
    fun defaultReadList(readListId: String) = readListId

    fun book(bookId: String, thumbnailId: String) = "$bookId:$thumbnailId"
    fun series(seriesId: String, thumbnailId: String) = "$seriesId:$thumbnailId"
    fun collection(collectionId: String, thumbnailId: String) = "$collectionId:$thumbnailId"
    fun readList(readListId: String, thumbnailId: String) = "$readListId:$thumbnailId"
}

class KomgaBookDefaultThumbnailFetcher(
    private val bookApi: KomgaBookApi,
    private val bookId: KomgaBookId,
    decoder: CoilAwareDecoder,
    options: Options,
    diskCache: DiskCache? = null,
) : CoilFetcher(decoder, options, diskCache, ThumbnailCacheKeys.defaultBook(bookId.value)) {
    override suspend fun fetchBytes() = bookApi.getDefaultThumbnail(bookId)
}

class KomgaBookThumbnailFetcher(
    private val bookApi: KomgaBookApi,
    private val bookId: KomgaBookId,
    private val thumbnailId: KomgaThumbnailId,
    decoder: CoilAwareDecoder,
    options: Options,
    diskCache: DiskCache? = null,
) : CoilFetcher(decoder, options, diskCache, ThumbnailCacheKeys.book(bookId.value, thumbnailId.value)) {
    override suspend fun fetchBytes() = bookApi.getThumbnail(bookId, thumbnailId)
}

class KomgaSeriesDefaultThumbnailFetcher(
    private val seriesApi: KomgaSeriesApi,
    private val seriesId: KomgaSeriesId,
    decoder: CoilAwareDecoder,
    options: Options,
    diskCache: DiskCache? = null,
) : CoilFetcher(decoder, options, diskCache, ThumbnailCacheKeys.defaultSeries(seriesId.value)) {
    override suspend fun fetchBytes() = seriesApi.getDefaultThumbnail(seriesId)
}

class KomgaSeriesThumbnailFetcher(
    private val seriesApi: KomgaSeriesApi,
    private val seriesId: KomgaSeriesId,
    private val thumbnailId: KomgaThumbnailId,
    decoder: CoilAwareDecoder,
    options: Options,
    diskCache: DiskCache? = null,
) : CoilFetcher(decoder, options, diskCache, ThumbnailCacheKeys.series(seriesId.value, thumbnailId.value)) {
    override suspend fun fetchBytes() = seriesApi.getThumbnail(seriesId, thumbnailId)
}

class KomgaCollectionDefaultThumbnailFetcher(
    private val collectionApi: KomgaCollectionsApi,
    private val collectionId: KomgaCollectionId,
    decoder: CoilAwareDecoder,
    options: Options,
    diskCache: DiskCache? = null,
) : CoilFetcher(decoder, options, diskCache, ThumbnailCacheKeys.defaultCollection(collectionId.value)) {
    override suspend fun fetchBytes() = collectionApi.getDefaultThumbnail(collectionId)
}

class KomgaCollectionThumbnailFetcher(
    private val collectionApi: KomgaCollectionsApi,
    private val collectionId: KomgaCollectionId,
    private val thumbnailId: KomgaThumbnailId,
    decoder: CoilAwareDecoder,
    options: Options,
    diskCache: DiskCache? = null,
) : CoilFetcher(decoder, options, diskCache, ThumbnailCacheKeys.collection(collectionId.value, thumbnailId.value)) {
    override suspend fun fetchBytes() = collectionApi.getThumbnail(collectionId, thumbnailId)
}

class KomgaReadListDefaultThumbnailFetcher(
    private val readListApi: KomgaReadListApi,
    private val readListId: KomgaReadListId,
    decoder: CoilAwareDecoder,
    options: Options,
    diskCache: DiskCache? = null,
) : CoilFetcher(decoder, options, diskCache, ThumbnailCacheKeys.defaultReadList(readListId.value)) {
    override suspend fun fetchBytes() = readListApi.getDefaultThumbnail(readListId)
}

class KomgaReadListThumbnailFetcher(
    private val readListApi: KomgaReadListApi,
    private val readListId: KomgaReadListId,
    private val thumbnailId: KomgaThumbnailId,
    decoder: CoilAwareDecoder,
    options: Options,
    diskCache: DiskCache? = null,
) : CoilFetcher(decoder, options, diskCache, ThumbnailCacheKeys.readList(readListId.value, thumbnailId.value)) {
    override suspend fun fetchBytes() = readListApi.getThumbnail(readListId, thumbnailId)
}

/**
 * Deliberately NOT disk-cached: this one already resolves from local files or
 * the offline store, so a disk copy would duplicate local data for no gain.
 */
class KomgaBookPageThumbnailFetcher(
    private val bookApi: KomgaBookApi,
    private val offlineBookRepository: OfflineBookRepository?,
    private val offlineBookApi: KomgaBookApi?,
    private val localFileApiProvider: LocalFileApiProvider?,
    private val bookId: KomgaBookId,
    private val pageNumber: Int,
    decoder: CoilAwareDecoder,
    options: Options,
) : CoilFetcher(decoder, options) {

    override suspend fun fetchBytes(): ByteArray? {
        localFileApiProvider?.getApiForBook(bookId)?.let {
            return it.getPageThumbnail(bookId, pageNumber)
        }
        if (offlineBookRepository?.find(bookId) != null && offlineBookApi != null) {
            return try {
                offlineBookApi.getPageThumbnail(bookId, pageNumber)
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                bookApi.getPageThumbnail(bookId, pageNumber)
            }
        }
        return bookApi.getPageThumbnail(bookId, pageNumber)
    }
}
