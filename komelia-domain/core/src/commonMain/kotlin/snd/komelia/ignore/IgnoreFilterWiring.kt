package snd.komelia.ignore

import kotlinx.coroutines.flow.StateFlow
import snd.komelia.komga.api.KomgaApi
import snd.komelia.komga.api.KomgaBookApi
import snd.komelia.komga.api.KomgaSeriesApi
import snd.komelia.komga.api.model.KomeliaBook
import snd.komga.client.book.KomgaBookSearch
import snd.komga.client.common.KomgaPageRequest
import snd.komga.client.common.Page
import snd.komga.client.library.KomgaLibraryId
import snd.komga.client.search.BookConditionBuilder
import snd.komga.client.search.SeriesConditionBuilder
import snd.komga.client.series.KomgaSeries
import snd.komga.client.series.KomgaSeriesSearch

/**
 * Returns a [KomgaApi] identical to the receiver except that the locally ignored
 * series (and the books belonging to them) are dropped from every list response.
 * Filtering is client-side only — nothing is sent to the server. When
 * [ignoredSeriesIds] is empty (feature off or nothing ignored) every endpoint
 * passes through unchanged.
 *
 * Single-item endpoints (getOneSeries / getOne) are intentionally NOT filtered,
 * so the Ignore List management screen can still resolve an ignored series by id.
 */
fun KomgaApi.withIgnoreFilter(ignoredSeriesIds: StateFlow<Set<String>>): KomgaApi {
    val filteredSeries = IgnoreFilteringSeriesApi(seriesApi, ignoredSeriesIds)
    val filteredBooks = IgnoreFilteringBookApi(bookApi, ignoredSeriesIds)
    return object : KomgaApi by this {
        override val seriesApi: KomgaSeriesApi = filteredSeries
        override val bookApi: KomgaBookApi = filteredBooks
    }
}

private class IgnoreFilteringSeriesApi(
    private val delegate: KomgaSeriesApi,
    private val ignored: StateFlow<Set<String>>,
) : KomgaSeriesApi by delegate {

    private fun Page<KomgaSeries>.filtered(): Page<KomgaSeries> {
        val ids = ignored.value
        return if (ids.isEmpty()) this
        else copy(content = content.filterNot { it.id.value in ids })
    }

    override suspend fun getSeriesList(
        conditionBuilder: SeriesConditionBuilder,
        fulltextSearch: String?,
        pageRequest: KomgaPageRequest?,
    ): Page<KomgaSeries> = delegate.getSeriesList(conditionBuilder, fulltextSearch, pageRequest).filtered()

    override suspend fun getSeriesList(
        search: KomgaSeriesSearch,
        pageRequest: KomgaPageRequest?,
    ): Page<KomgaSeries> = delegate.getSeriesList(search, pageRequest).filtered()

    override suspend fun getNewSeries(
        libraryIds: List<KomgaLibraryId>?,
        oneshot: Boolean?,
        deleted: Boolean?,
        pageRequest: KomgaPageRequest?,
    ): Page<KomgaSeries> = delegate.getNewSeries(libraryIds, oneshot, deleted, pageRequest).filtered()

    override suspend fun getUpdatedSeries(
        libraryIds: List<KomgaLibraryId>?,
        oneshot: Boolean?,
        deleted: Boolean?,
        pageRequest: KomgaPageRequest?,
    ): Page<KomgaSeries> = delegate.getUpdatedSeries(libraryIds, oneshot, deleted, pageRequest).filtered()
}

private class IgnoreFilteringBookApi(
    private val delegate: KomgaBookApi,
    private val ignored: StateFlow<Set<String>>,
) : KomgaBookApi by delegate {

    private fun Page<KomeliaBook>.filtered(): Page<KomeliaBook> {
        val ids = ignored.value
        return if (ids.isEmpty()) this
        else copy(content = content.filterNot { it.seriesId.value in ids })
    }

    override suspend fun getBookList(
        conditionBuilder: BookConditionBuilder,
        fullTextSearch: String?,
        pageRequest: KomgaPageRequest?,
    ): Page<KomeliaBook> = delegate.getBookList(conditionBuilder, fullTextSearch, pageRequest).filtered()

    override suspend fun getBookList(
        search: KomgaBookSearch,
        pageRequest: KomgaPageRequest?,
    ): Page<KomeliaBook> = delegate.getBookList(search, pageRequest).filtered()

    override suspend fun getLatestBooks(pageRequest: KomgaPageRequest?): Page<KomeliaBook> =
        delegate.getLatestBooks(pageRequest).filtered()

    override suspend fun getBooksOnDeck(
        libraryIds: List<KomgaLibraryId>?,
        pageRequest: KomgaPageRequest?,
    ): Page<KomeliaBook> = delegate.getBooksOnDeck(libraryIds, pageRequest).filtered()

    override suspend fun getDuplicateBooks(pageRequest: KomgaPageRequest?): Page<KomeliaBook> =
        delegate.getDuplicateBooks(pageRequest).filtered()
}
