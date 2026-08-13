package snd.komelia.chapters

import kotlinx.coroutines.flow.StateFlow
import snd.komelia.komga.api.KomgaApi
import snd.komelia.komga.api.KomgaBookApi
import snd.komelia.komga.api.KomgaSeriesApi
import snd.komelia.komga.api.model.KomeliaBook
import snd.komelia.settings.model.ChapterSeriesFilter
import snd.komga.client.book.KomgaBookSearch
import snd.komga.client.common.KomgaPageRequest
import snd.komga.client.common.Page
import snd.komga.client.library.KomgaLibraryId
import snd.komga.client.search.SeriesConditionBuilder
import snd.komga.client.series.KomgaSeries
import snd.komga.client.series.KomgaSeriesSearch

/**
 * Marks a series as a chapter release rather than a collected edition. Matched
 * on the end of the title, case-sensitively, exactly as the user types it into
 * Komga — a loose match would swallow a series legitimately called
 * "Chapitres de la Rose".
 */
const val CHAPTER_TITLE_SUFFIX = "(Chap)"

/**
 * Returns a [KomgaApi] identical to the receiver except that series whose title
 * ends with [CHAPTER_TITLE_SUFFIX] are dropped from every list response. On
 * [ChapterSeriesFilter.ANY] every endpoint passes through unchanged.
 *
 * This catches every list in the app — the library grid, the home shelves,
 * search, upcoming releases. Asking the server instead was tried and reverted:
 * a title matched on its ending is a leading-wildcard LIKE that no index can
 * serve, and Komga scanned the whole series table twice per page for it. See
 * the note in `LibrarySeriesTabState.getAllSeries`.
 *
 * The price of filtering here is that pages come back a few short and the total
 * still counts what was removed — the page was counted before it reached us.
 *
 * Single-item endpoints are deliberately NOT filtered: a chapter series opened
 * from a link, a bookmark or the volumes it belongs to must still open.
 */
fun KomgaApi.withChapterFilter(filter: StateFlow<ChapterSeriesFilter>): KomgaApi {
    val filteredSeries = ChapterFilteringSeriesApi(seriesApi, filter)
    val filteredBooks = ChapterFilteringBookApi(bookApi, filter)
    return object : KomgaApi by this {
        override val seriesApi: KomgaSeriesApi = filteredSeries
        override val bookApi: KomgaBookApi = filteredBooks
    }
}

/** Whether [title] names a chapter release. */
fun isChapterSeriesTitle(title: String): Boolean = title.trimEnd().endsWith(CHAPTER_TITLE_SUFFIX)

private class ChapterFilteringSeriesApi(
    private val delegate: KomgaSeriesApi,
    private val filter: StateFlow<ChapterSeriesFilter>,
) : KomgaSeriesApi by delegate {

    private fun Page<KomgaSeries>.filtered(): Page<KomgaSeries> = when (filter.value) {
        ChapterSeriesFilter.ANY -> this
        ChapterSeriesFilter.HIDE_CHAPTERS ->
            copy(content = content.filterNot { isChapterSeriesTitle(it.metadata.title) })
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

/**
 * Same idea one level down: hiding a chapter series from every series list did
 * nothing for the shelves made of BOOKS. "On deck", "Keep reading", "Forgotten"
 * and book search all query books directly, so a volume belonging to a `(Chap)`
 * series kept showing up on the home screen with the filter on.
 *
 * Which lists get filtered is decided by the overload, and that is not an
 * accident:
 *
 *  - `getBookList(search = ...)`, `getBooksOnDeck`, `getLatestBooks` are the
 *    BROWSE queries — "what should I read", search results. Filtered.
 *  - `getBookList(conditionBuilder = ...)` is the SCOPED query: every caller
 *    narrows it to one series (the series book list, the reader's siblings, the
 *    book screen's siblings, oneshots) or is an admin tool. Filtering it would
 *    empty a chapter series the moment it was opened, and — worse — break
 *    continuous reading inside one, since the reader finds the next volume
 *    through it. Left alone, exactly like the single-series endpoints above.
 *
 * A browse query that has to go through the condition builder should be moved
 * to the search overload rather than filtered here; the library "Keep reading"
 * row was moved for this reason.
 */
private class ChapterFilteringBookApi(
    private val delegate: KomgaBookApi,
    private val filter: StateFlow<ChapterSeriesFilter>,
) : KomgaBookApi by delegate {

    private fun Page<KomeliaBook>.filtered(): Page<KomeliaBook> = when (filter.value) {
        ChapterSeriesFilter.ANY -> this
        ChapterSeriesFilter.HIDE_CHAPTERS ->
            copy(content = content.filterNot { isChapterSeriesTitle(it.seriesTitle) })
    }

    override suspend fun getBookList(
        search: KomgaBookSearch,
        pageRequest: KomgaPageRequest?,
    ): Page<KomeliaBook> = delegate.getBookList(search, pageRequest).filtered()

    override suspend fun getBooksOnDeck(
        libraryIds: List<KomgaLibraryId>?,
        pageRequest: KomgaPageRequest?,
    ): Page<KomeliaBook> = delegate.getBooksOnDeck(libraryIds, pageRequest).filtered()

    override suspend fun getLatestBooks(pageRequest: KomgaPageRequest?): Page<KomeliaBook> =
        delegate.getLatestBooks(pageRequest).filtered()
}
