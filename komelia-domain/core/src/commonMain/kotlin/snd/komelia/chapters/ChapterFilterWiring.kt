package snd.komelia.chapters

import kotlinx.coroutines.flow.StateFlow
import snd.komelia.komga.api.KomgaApi
import snd.komelia.komga.api.KomgaSeriesApi
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
 * ends with [CHAPTER_TITLE_SUFFIX] are dropped from every list response. When
 * [hideChapters] is false every endpoint passes through unchanged.
 *
 * This is the net that catches the home shelves, search and upcoming releases —
 * surfaces that span every library and have no condition builder to push the
 * rule into. The library grid asks the server directly (see
 * `LibrarySeriesTabState`), because only the server can keep the page sizes and
 * the total count honest; by the time a page reaches here it has already been
 * counted. Filtering the same series twice costs nothing.
 *
 * Single-item endpoints are deliberately NOT filtered: a chapter series opened
 * from a link, a bookmark or the volumes it belongs to must still open.
 */
fun KomgaApi.withChapterFilter(hideChapters: StateFlow<Boolean>): KomgaApi {
    val filtered = ChapterFilteringSeriesApi(seriesApi, hideChapters)
    return object : KomgaApi by this {
        override val seriesApi: KomgaSeriesApi = filtered
    }
}

/** Whether [title] names a chapter release. */
fun isChapterSeriesTitle(title: String): Boolean = title.trimEnd().endsWith(CHAPTER_TITLE_SUFFIX)

private class ChapterFilteringSeriesApi(
    private val delegate: KomgaSeriesApi,
    private val hideChapters: StateFlow<Boolean>,
) : KomgaSeriesApi by delegate {

    private fun Page<KomgaSeries>.filtered(): Page<KomgaSeries> =
        if (!hideChapters.value) this
        else copy(content = content.filterNot { isChapterSeriesTitle(it.metadata.title) })

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
