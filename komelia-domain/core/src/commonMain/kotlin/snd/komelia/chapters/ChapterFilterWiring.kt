package snd.komelia.chapters

import kotlinx.coroutines.flow.StateFlow
import snd.komelia.komga.api.KomgaApi
import snd.komelia.komga.api.KomgaSeriesApi
import snd.komelia.settings.model.ChapterSeriesFilter
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
    val filtered = ChapterFilteringSeriesApi(seriesApi, filter)
    return object : KomgaApi by this {
        override val seriesApi: KomgaSeriesApi = filtered
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
