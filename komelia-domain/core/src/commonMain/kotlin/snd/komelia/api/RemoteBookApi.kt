package snd.komelia.api

import io.ktor.client.statement.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import snd.komelia.komga.api.KomgaBookApi
import snd.komelia.komga.api.model.KomeliaBook
import snd.komelia.offline.book.repository.OfflineBookRepository
import snd.komelia.offline.localFilePath
import snd.komelia.offline.readChunked
import snd.komga.client.book.KomgaBook
import snd.komga.client.book.KomgaBookClient
import kotlinx.coroutines.withTimeout
import snd.komga.client.book.KomgaBookId
import snd.komga.client.book.KomgaBookMetadataUpdateRequest
import snd.komga.client.book.KomgaBookPage
import snd.komga.client.book.KomgaBookReadProgressUpdateRequest
import snd.komga.client.book.KomgaBookSearch
import snd.komga.client.book.KomgaBookThumbnail
import snd.komga.client.book.R2Positions
import snd.komga.client.book.R2Progression
import snd.komga.client.book.WPPublication
import snd.komga.client.common.KomgaPageRequest
import snd.komga.client.common.KomgaThumbnailId
import snd.komga.client.common.Page
import snd.komga.client.library.KomgaLibraryId
import snd.komga.client.readlist.KomgaReadList
import snd.komga.client.series.KomgaSeriesId
import snd.komga.client.search.BookConditionBuilder

private const val EPUB_BUFFER_SIZE: Int = 64 * 1024

private const val PROGRESS_PUSH_TIMEOUT_MS = 15_000L

class RemoteBookApi(
    private val bookClient: KomgaBookClient,
    private val offlineBookRepository: OfflineBookRepository,
) : KomgaBookApi {
    override suspend fun getOne(bookId: KomgaBookId): KomeliaBook {
        val book = bookClient.getOne(bookId)
        return getKomeliaBook(book)
    }

    override suspend fun getBookList(
        conditionBuilder: BookConditionBuilder,
        fullTextSearch: String?,
        pageRequest: KomgaPageRequest?
    ): Page<KomeliaBook> {
        val booksPage = bookClient.getBookList(conditionBuilder, fullTextSearch, pageRequest)
        return getKomeliaBookPage(booksPage)
    }

    override suspend fun getBookList(
        search: KomgaBookSearch,
        pageRequest: KomgaPageRequest?
    ): Page<KomeliaBook> {
        val booksPage = bookClient.getBookList(search, pageRequest)
        return getKomeliaBookPage(booksPage)
    }

    override suspend fun getLatestBooks(pageRequest: KomgaPageRequest?): Page<KomeliaBook> {
        val booksPage = bookClient.getLatestBooks(pageRequest)
        return getKomeliaBookPage(booksPage)
    }

    override suspend fun getBooksOnDeck(
        libraryIds: List<KomgaLibraryId>?,
        pageRequest: KomgaPageRequest?
    ): Page<KomeliaBook> {
        val booksPage = bookClient.getBooksOnDeck(libraryIds, pageRequest)
        return getKomeliaBookPage(booksPage)
    }

    override suspend fun getDuplicateBooks(pageRequest: KomgaPageRequest?): Page<KomeliaBook> {
        val booksPage = bookClient.getDuplicateBooks(pageRequest)
        return getKomeliaBookPage(booksPage)
    }

    override suspend fun getBookSiblingPrevious(bookId: KomgaBookId): KomeliaBook? {
        val book = bookClient.getBookSiblingPrevious(bookId)
        return book?.let { getKomeliaBook(it) }

    }

    override suspend fun getBookSiblingNext(bookId: KomgaBookId): KomeliaBook? {
        val book = bookClient.getBookSiblingNext(bookId)
        return book?.let { getKomeliaBook(it) }
    }

    override suspend fun updateMetadata(
        bookId: KomgaBookId,
        request: KomgaBookMetadataUpdateRequest
    ) {
        bookClient.updateMetadata(bookId, request)
    }

    override suspend fun getBookPages(bookId: KomgaBookId): List<KomgaBookPage> {
        return bookClient.getBookPages(bookId)
    }

    override suspend fun analyze(bookId: KomgaBookId) {
        bookClient.analyze(bookId)
    }

    override suspend fun refreshMetadata(bookId: KomgaBookId) {
        bookClient.refreshMetadata(bookId)
    }

    override suspend fun markReadProgress(
        bookId: KomgaBookId,
        request: KomgaBookReadProgressUpdateRequest
    ) {
        bookClient.markReadProgress(bookId, request)
    }

    override suspend fun deleteReadProgress(bookId: KomgaBookId) {
        bookClient.deleteReadProgress(bookId)
    }

    override suspend fun deleteBook(bookId: KomgaBookId) {
        bookClient.deleteBook(bookId)
    }

    override suspend fun regenerateThumbnails(forBiggerResultOnly: Boolean) {
        bookClient.regenerateThumbnails(forBiggerResultOnly)
    }

    override suspend fun getDefaultThumbnail(bookId: KomgaBookId): ByteArray? {
        return bookClient.getDefaultThumbnail(bookId)
    }

    override suspend fun getThumbnail(bookId: KomgaBookId, thumbnailId: KomgaThumbnailId) =
        bookClient.getThumbnail(bookId, thumbnailId)

    override suspend fun getThumbnails(bookId: KomgaBookId): List<KomgaBookThumbnail> {
        return bookClient.getThumbnails(bookId)
    }

    override suspend fun uploadThumbnail(
        bookId: KomgaBookId,
        file: ByteArray,
        filename: String,
        selected: Boolean
    ): KomgaBookThumbnail {
        return bookClient.uploadThumbnail(
            bookId = bookId,
            file = file,
            filename = filename,
            selected = selected
        )
    }

    override suspend fun selectBookThumbnail(
        bookId: KomgaBookId,
        thumbnailId: KomgaThumbnailId
    ) {
        bookClient.selectThumbnail(bookId, thumbnailId)
    }

    override suspend fun deleteBookThumbnail(
        bookId: KomgaBookId,
        thumbnailId: KomgaThumbnailId
    ) {
        bookClient.deleteThumbnail(bookId, thumbnailId)
    }

    override suspend fun getAllReadListsByBook(bookId: KomgaBookId): List<KomgaReadList> {
        return bookClient.getAllReadListsByBook(bookId)
    }

    override suspend fun getPage(bookId: KomgaBookId, page: Int): ByteArray {
        return bookClient.getPage(bookId, page)
    }

    override suspend fun getPageThumbnail(
        bookId: KomgaBookId,
        page: Int
    ): ByteArray {
        return try {
            bookClient.getPageThumbnail(bookId, page)
        } catch (e: Exception) {
            bookClient.getPage(bookId, page)
        }
    }

    override suspend fun getReadiumProgression(bookId: KomgaBookId): R2Progression? {
        return bookClient.getReadiumProgression(bookId)
    }

    override suspend fun updateReadiumProgression(
        bookId: KomgaBookId,
        progression: R2Progression
    ) {
        // Bounded: on a dead link this POST sat on the 60 s socket timeout
        // (measured 2026-09-13, 12:37:02 -> FAILED 12:38:02) while pages
        // were already retrying. Fifteen seconds covers the 10 s connect
        // and a slow answer; a push that fails is kept for later anyway
        // (PendingReadProgress), so giving up early costs nothing.
        withTimeout(PROGRESS_PUSH_TIMEOUT_MS) {
            bookClient.updateReadiumProgression(bookId, progression)
        }
    }

    override suspend fun getReadiumPositions(bookId: KomgaBookId): R2Positions {
        return bookClient.getReadiumPositions(bookId)
    }

    override suspend fun getWebPubManifest(bookId: KomgaBookId): WPPublication {
        return bookClient.getWebPubManifest(bookId)
    }

    override suspend fun getBookEpubResource(
        bookId: KomgaBookId,
        resourceName: String
    ): ByteArray {
        return bookClient.getBookEpubResource(bookId, resourceName)
    }

    override suspend fun getBookRawFile(bookId: KomgaBookId): ByteArray {
        var bytes: ByteArray = byteArrayOf()
        bookClient.getBookFile(bookId) { response ->
            bytes = response.readBytes()
        }
        return bytes
    }

    override suspend fun downloadBookRawFile(bookId: KomgaBookId, onChunk: suspend (ByteArray) -> Unit) {
        val localFile = offlineBookRepository.find(bookId)?.fileDownloadPath
        if (localFile != null) {
            localFile.readChunked(EPUB_BUFFER_SIZE, onChunk)
            return
        }
        bookClient.getBookFile(bookId) { response ->
            val channel = response.bodyAsChannel()
            while (!channel.isClosedForRead) {
                val packet = channel.readRemaining(EPUB_BUFFER_SIZE.toLong())
                onChunk(packet.readBytes())
            }
        }
    }

    override suspend fun getDownloadedSeriesIds(seriesIds: List<KomgaSeriesId>): Set<KomgaSeriesId> {
        return offlineBookRepository.findAllBySeriesIds(seriesIds).map { it.seriesId }.toSet()
    }

    override suspend fun getBookLocalFilePath(bookId: KomgaBookId): String? {
        return offlineBookRepository.find(bookId)?.fileDownloadPath?.localFilePath()
    }

    override suspend fun hasLocalFile(bookId: KomgaBookId): Boolean {
        return offlineBookRepository.find(bookId) != null
    }

    private suspend fun getKomeliaBook(book: KomgaBook): KomeliaBook {
        val offlineBook = offlineBookRepository.find(book.id)
        return KomeliaBook(
            book = book,
            downloaded = offlineBook != null,
            localFileLastModified = offlineBook?.localFileLastModified,
            remoteFileUnavailable = offlineBook?.remoteUnavailable ?: false
        )
    }

    private suspend fun getKomeliaBookPage(bookPage: Page<KomgaBook>): Page<KomeliaBook> {
        val ids = bookPage.content.map { it.id }
        val offlineBooks = offlineBookRepository.findIn(ids).associateBy { it.id }
        val komeliaBooks = bookPage.content.map {
            val offlineBook = offlineBooks[it.id]
            KomeliaBook(
                book = it,
                downloaded = offlineBook != null,
                localFileLastModified = offlineBook?.localFileLastModified,
                remoteFileUnavailable = offlineBook?.remoteUnavailable ?: false
            )
        }
        return bookPage.toKomeliaBookPage(komeliaBooks)
    }
}

fun Page<KomgaBook>.toKomeliaBookPage(books: List<KomeliaBook>): Page<KomeliaBook> {
    return Page(
        content = books,
        pageable = this.pageable,
        totalElements = this.totalElements,
        totalPages = this.totalPages,
        last = this.last,
        number = this.number,
        sort = this.sort,
        first = this.first,
        numberOfElements = this.numberOfElements,
        size = this.size,
        empty = this.empty
    )
}
