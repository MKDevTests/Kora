package snd.komelia.ui.settings.chapters

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.launch
import snd.komelia.AppNotifications
import snd.komelia.chapters.CHAPTER_MATCH_AUTO_SCORE
import snd.komelia.chapters.CHAPTER_MATCH_FLOOR_SCORE
import snd.komelia.chapters.CHAPTER_TITLE_SUFFIX
import snd.komelia.chapters.isChapterSeriesTitle
import snd.komelia.chapters.strippedChapterTitle
import snd.komelia.chapters.titleMatchScore
import snd.komelia.komga.api.KomgaLibraryApi
import snd.komelia.komga.api.KomgaSeriesApi
import snd.komelia.links.SeriesLinksRepository
import snd.komelia.links.SeriesRelationType
import snd.komga.client.common.KomgaPageRequest
import snd.komga.client.library.KomgaLibrary
import snd.komga.client.library.KomgaLibraryId
import snd.komga.client.search.allOfSeries
import snd.komga.client.series.KomgaSeries

private const val LOAD_PAGE_SIZE = 500
private const val LOAD_MAX_PAGES = 10

/** A possible volumes series, with how alike its title is (0-100). */
data class ChapterCandidate(
    val series: KomgaSeries,
    val score: Int,
)

/**
 * A chapter series and what we know about its volumes.
 *
 * [candidates] is filled by a match attempt, not by the initial listing: one
 * request per series is fine on demand and would be a burst on load.
 */
data class ChapterSeriesRow(
    val series: KomgaSeries,
    val linked: Boolean,
    val candidates: List<ChapterCandidate> = emptyList(),
    val searched: Boolean = false,
)

/** Which rows the list shows. */
enum class ChapterListFilter { ALL, UNLINKED, LINKED }

/** What a match attempt concluded, for the summary shown after a bulk run. */
data class MatchOutcome(
    val linked: Int,
    val ambiguous: Int,
    val notFound: Int,
)

/**
 * Admin screen behind Settings -> Chapter management: lists the chapter series
 * of one library and links them to the volumes they belong to.
 *
 * Reads through [rawSeriesApi] on purpose. The api the rest of the app uses
 * drops chapter series when the filter is on, which is exactly the set this
 * screen exists to manage — it would list nothing.
 */
class ChapterManagementViewModel(
    private val rawSeriesApi: KomgaSeriesApi,
    private val libraryApi: KomgaLibraryApi,
    private val linksRepository: SeriesLinksRepository,
    private val notifications: AppNotifications,
) : ScreenModel {

    var libraries by mutableStateOf<List<KomgaLibrary>>(emptyList())
        private set
    var selectedLibrary by mutableStateOf<KomgaLibrary?>(null)
        private set
    var rows by mutableStateOf<List<ChapterSeriesRow>>(emptyList())
        private set

    /** Opens on the unlinked ones: they are the list the screen exists to act on. */
    var listFilter by mutableStateOf(ChapterListFilter.UNLINKED)
        private set
    var selectedIds by mutableStateOf<Set<String>>(emptySet())
        private set

    var loading by mutableStateOf(false)
        private set
    var matching by mutableStateOf(false)
        private set
    var lastOutcome by mutableStateOf<MatchOutcome?>(null)
        private set

    val visibleRows: List<ChapterSeriesRow>
        get() = when (listFilter) {
            ChapterListFilter.ALL -> rows
            ChapterListFilter.UNLINKED -> rows.filterNot { it.linked }
            ChapterListFilter.LINKED -> rows.filter { it.linked }
        }

    suspend fun initialize() {
        if (libraries.isNotEmpty()) return
        notifications.runCatchingToNotifications {
            libraries = libraryApi.getLibraries()
        }
    }

    fun onLibrarySelected(library: KomgaLibrary) {
        selectedLibrary = library
        selectedIds = emptySet()
        lastOutcome = null
        rows = emptyList()
        screenModelScope.launch { load(library.id) }
    }

    fun onListFilterChange(filter: ChapterListFilter) {
        listFilter = filter
        // Selections that are no longer on screen would still be acted on by
        // "match selected", which the user cannot see and would not expect.
        selectedIds = selectedIds.intersect(visibleRows.map { it.series.id.value }.toSet())
    }

    fun onSelectionToggle(seriesId: String) {
        selectedIds = if (seriesId in selectedIds) selectedIds - seriesId else selectedIds + seriesId
    }

    fun onSelectAll() {
        val visible = visibleRows.map { it.series.id.value }.toSet()
        selectedIds = if (selectedIds.containsAll(visible)) emptySet() else visible
    }

    /**
     * Loads the chapter series of [libraryId].
     *
     * Komga is asked for the free-text term rather than for a title ending in
     * "(Chap)": a suffix match is a leading-wildcard LIKE that no index can
     * serve, measured at 420 seconds on a real library. The full-text index
     * answers in one request and returns a superset — anything merely
     * containing the word — which the real condition then narrows down here.
     */
    private suspend fun load(libraryId: KomgaLibraryId) {
        loading = true
        notifications.runCatchingToNotifications {
            val condition = allOfSeries { library { isEqualTo(libraryId) } }
            // The full-text hits are a superset, so they can outnumber the real
            // chapter series: page through them rather than truncate silently.
            val found = buildList {
                var pageIndex = 0
                while (pageIndex < LOAD_MAX_PAGES) {
                    val page = rawSeriesApi.getSeriesList(
                        conditionBuilder = condition,
                        fulltextSearch = CHAPTER_TITLE_SUFFIX.trim('(', ')'),
                        pageRequest = KomgaPageRequest(size = LOAD_PAGE_SIZE, pageIndex = pageIndex),
                    )
                    addAll(page.content.filter { isChapterSeriesTitle(it.metadata.title) })
                    pageIndex++
                    if (pageIndex >= page.totalPages) break
                }
            }

            val linkedIds = chapterLinkedIds()
            rows = found
                .sortedBy { it.metadata.title.lowercase() }
                .map { ChapterSeriesRow(series = it, linked = it.id.value in linkedIds) }
        }
        loading = false
    }

    /** Series already carrying a chapters/volumes link, in either direction. */
    private suspend fun chapterLinkedIds(): Set<String> =
        linksRepository.getAllRelations()
            .filter {
                it.type == SeriesRelationType.CHAPTERS || it.type == SeriesRelationType.VOLUMES
            }
            .flatMap { listOf(it.from.value, it.to.value) }
            .toSet()

    /** Looks for the volumes of one row and links it when the answer is unambiguous. */
    fun onFindMatch(row: ChapterSeriesRow) {
        screenModelScope.launch {
            matching = true
            notifications.runCatchingToNotifications { matchOne(row) }
            matching = false
        }
    }

    /** Same, for every selected row, reporting what it could and could not settle. */
    fun onMatchSelected() {
        val targets = visibleRows.filter { it.series.id.value in selectedIds }
        if (targets.isEmpty()) return
        screenModelScope.launch {
            matching = true
            lastOutcome = null
            notifications.runCatchingToNotifications {
                var linked = 0
                var ambiguous = 0
                var notFound = 0
                targets.forEach { row ->
                    when (matchOne(row)) {
                        MatchResult.LINKED -> linked++
                        MatchResult.AMBIGUOUS -> ambiguous++
                        MatchResult.NOT_FOUND -> notFound++
                    }
                }
                lastOutcome = MatchOutcome(linked, ambiguous, notFound)
                selectedIds = emptySet()
            }
            matching = false
        }
    }

    private enum class MatchResult { LINKED, AMBIGUOUS, NOT_FOUND }

    /**
     * One match attempt.
     *
     * Candidates come from the full-text index on the stripped title — indexed,
     * so this stays fast in bulk — and are then scored locally. Exact equality
     * alone was not enough: the two entries are typed at different moments and
     * drift by an accent, a colon or a stray "Vol.".
     *
     * A single candidate at or above [CHAPTER_MATCH_AUTO_SCORE] is applied. Two
     * candidates that high never are, however confident each looks on its own:
     * a library holding two series called "Berserk" gives no ground to prefer
     * either, and a wrong link is silent once written.
     */
    private suspend fun matchOne(row: ChapterSeriesRow): MatchResult {
        val libraryId = selectedLibrary?.id ?: return MatchResult.NOT_FOUND
        val stripped = strippedChapterTitle(row.series.metadata.title)
        if (stripped.isBlank()) return MatchResult.NOT_FOUND

        val candidates = rawSeriesApi.getSeriesList(
            conditionBuilder = allOfSeries { library { isEqualTo(libraryId) } },
            fulltextSearch = stripped,
            pageRequest = KomgaPageRequest(size = 50, pageIndex = 0),
        ).content
            .asSequence()
            // Never propose a chapter series as another one's volumes, and never
            // propose the row itself.
            .filter { it.id != row.series.id }
            .filterNot { isChapterSeriesTitle(it.metadata.title) }
            .map { ChapterCandidate(it, titleMatchScore(stripped, it.metadata.title)) }
            .filter { it.score >= CHAPTER_MATCH_FLOOR_SCORE }
            .sortedByDescending { it.score }
            .toList()

        val confident = candidates.filter { it.score >= CHAPTER_MATCH_AUTO_SCORE }
        return when {
            candidates.isEmpty() -> {
                updateRow(row.series.id.value) { it.copy(candidates = emptyList(), searched = true) }
                MatchResult.NOT_FOUND
            }

            confident.size == 1 -> {
                link(row, confident.first().series)
                MatchResult.LINKED
            }

            else -> {
                updateRow(row.series.id.value) { it.copy(candidates = candidates, searched = true) }
                MatchResult.AMBIGUOUS
            }
        }
    }

    /**
     * Writes the pair. The chapter series is the subject: its volumes are
     * [volumes]. linkRelation writes the inverse edge too, so the volumes side
     * reads "Chapters" without a second call.
     */
    private suspend fun link(row: ChapterSeriesRow, volumes: KomgaSeries) {
        linksRepository.linkRelation(
            from = row.series.id,
            to = volumes.id,
            type = SeriesRelationType.VOLUMES,
        )
        updateRow(row.series.id.value) {
            it.copy(linked = true, candidates = emptyList(), searched = true)
        }
    }

    /** Links [row] to a candidate the user picked among several. */
    fun onPickCandidate(row: ChapterSeriesRow, candidate: ChapterCandidate) {
        screenModelScope.launch {
            notifications.runCatchingToNotifications { link(row, candidate.series) }
        }
    }

    /** Removes the chapters/volumes pairing, for a link put on the wrong series. */
    fun onUnlink(row: ChapterSeriesRow) {
        screenModelScope.launch {
            notifications.runCatchingToNotifications {
                val volumes = linksRepository.relationsOf(row.series.id)
                    .firstOrNull { it.type == SeriesRelationType.VOLUMES }
                    ?: return@runCatchingToNotifications
                linksRepository.unlinkRelation(row.series.id, volumes.series)
                updateRow(row.series.id.value) {
                    it.copy(linked = false, candidates = emptyList(), searched = false)
                }
            }
        }
    }

    private fun updateRow(seriesId: String, transform: (ChapterSeriesRow) -> ChapterSeriesRow) {
        rows = rows.map { if (it.series.id.value == seriesId) transform(it) else it }
    }
}
