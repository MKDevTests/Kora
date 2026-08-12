package snd.komelia.ui.settings.chapters

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import snd.komelia.AppNotifications
import snd.komelia.chapters.CHAPTER_MATCH_AUTO_SCORE
import snd.komelia.chapters.CHAPTER_MATCH_FLOOR_SCORE
import snd.komelia.chapters.CHAPTER_TITLE_SUFFIX
import snd.komelia.chapters.isChapterSeriesTitle
import snd.komelia.chapters.strippedChapterTitle
import snd.komelia.chapters.titleMatchScore
import snd.komelia.komga.api.KomgaLibraryApi
import snd.komelia.komga.api.KomgaSeriesApi
import snd.komelia.links.KoraSharedLinks
import snd.komelia.links.SeriesLinksRepository
import snd.komelia.links.SeriesRelationType
import snd.komelia.settings.CommonSettingsRepository
import snd.komelia.ui.series.SeriesLinksChanges
import snd.komga.client.common.KomgaPageRequest
import snd.komga.client.library.KomgaLibrary
import snd.komga.client.library.KomgaLibraryId
import snd.komga.client.search.allOfSeries
import snd.komga.client.series.KomgaSeries
import snd.komga.client.user.KomgaUser
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

private const val LOAD_PAGE_SIZE = 500
private const val LOAD_MAX_PAGES = 10

/** A title prefix can legitimately match a handful of editions; keep them all. */
private const val PREFIX_PAGE_SIZE = 20
private const val FUZZY_PAGE_SIZE = 50

/** Matches in flight during a bulk run. Four, like every other Komga fan-out. */
private const val MATCH_CONCURRENCY = 4

/** Enough failures to see the pattern, few enough to stay a message and not a wall. */
private const val MAX_SHOWN_ERRORS = 5

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
    val failed: Int,
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
    private val settingsRepository: CommonSettingsRepository,
    private val authenticatedUser: StateFlow<KomgaUser?>,
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

    /** Failures from the last run, kept on screen because the toast is not readable. */
    var errors by mutableStateOf<List<String>>(emptyList())
        private set

    /**
     * Whether what this screen writes reaches the server. False means the links
     * stay on this install only, which the screen says out loud rather than
     * letting an admin pair a whole library into a local table by accident.
     */
    var sharesToServer by mutableStateOf(false)
        private set

    /**
     * Same rule as the series Links tab: publish only when sharing is on AND
     * the user is admin. Read fresh (suspending) rather than from a StateFlow,
     * whose initial `false` would silently downgrade an early write to local.
     */
    private suspend fun shareViaKomga(): Boolean =
        settingsRepository.getShareLinksViaKomga().first() &&
            authenticatedUser.value?.roleAdmin() == true

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
            sharesToServer = shareViaKomga()
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
                .map { series ->
                    // Either source counts as linked. A pairing published from
                    // another install exists only in the series' own metadata,
                    // and calling it "unlinked" would invite overwriting it.
                    val published = KoraSharedLinks.relationsOf(series)
                        .any { isChapterRelation(it.type) }
                    ChapterSeriesRow(
                        series = series,
                        linked = published || series.id.value in linkedIds,
                    )
                }
        }
        loading = false
    }

    /** Series already carrying a chapters/volumes link locally, in either direction. */
    private suspend fun chapterLinkedIds(): Set<String> =
        linksRepository.getAllRelations()
            .filter { isChapterRelation(it.type) }
            .flatMap { listOf(it.from.value, it.to.value) }
            .toSet()

    private fun isChapterRelation(type: SeriesRelationType) =
        type == SeriesRelationType.CHAPTERS || type == SeriesRelationType.VOLUMES


    /** Looks for the volumes of one row and links it when the answer is unambiguous. */
    fun onFindMatch(row: ChapterSeriesRow) {
        screenModelScope.launch {
            matching = true
            errors = emptyList()
            runMatch(row)
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
            errors = emptyList()
            try {
                // Bounded fan-out rather than one series after another: each match
                // is a server round-trip, and fifty of them in a row is a minute
                // of staring at a spinner.
                val limit = Semaphore(MATCH_CONCURRENCY)
                val results = coroutineScope {
                    targets.map { row -> async { limit.withPermit { runMatch(row) } } }.awaitAll()
                }
                lastOutcome = MatchOutcome(
                    linked = results.count { it == MatchResult.LINKED },
                    ambiguous = results.count { it == MatchResult.AMBIGUOUS },
                    notFound = results.count { it == MatchResult.NOT_FOUND },
                    failed = results.count { it == MatchResult.FAILED },
                )
                selectedIds = emptySet()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // Whatever broke outside a single match — the batch itself. Same
                // treatment: on screen and in the log, not a toast that is gone
                // before it can be read.
                logger.error(e) { "Chapter bulk match failed" }
                errors = errors + describe(e)
            }
            matching = false
        }
    }

    private enum class MatchResult { LINKED, AMBIGUOUS, NOT_FOUND, FAILED }

    /**
     * Run one match without letting it end the batch.
     *
     * The app's error notifications are a toast: by the time you have read which
     * series failed, it is gone. These stay on screen until the next run, and go
     * to the log with their stack trace. A failure also has to stay local to its
     * own series — inside a coroutineScope, one uncaught throw cancels every
     * sibling still in flight.
     */
    private suspend fun runMatch(row: ChapterSeriesRow): MatchResult =
        try {
            matchOne(row)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logger.error(e) { "Chapter match failed for '${row.series.metadata.title}'" }
            errors = (errors + "${row.series.metadata.title}: ${describe(e)}").takeLast(MAX_SHOWN_ERRORS)
            MatchResult.FAILED
        }

    /** Message + type, because a bare "null" message says nothing about what broke. */
    private fun describe(e: Throwable): String {
        val message = e.message ?: e.cause?.message
        return if (message.isNullOrBlank()) e::class.simpleName ?: "Unknown error"
        else "$message (${e::class.simpleName})"
    }

    /**
     * One match attempt, cheap half first.
     *
     * A title prefix is served by an index; the full-text search is not, and it
     * is what made this screen slow. "Berserk (Chap)" looks for titles starting
     * with "Berserk", which is one indexed range scan and finds the volumes of
     * nearly every chapter series here — they are named after them.
     *
     * Not `title { isEqualTo(...) }`, which cannot be serialised at all: the
     * Title condition holds a StringOp, `Is` is generic, and its value ends up
     * on a polymorphic Any serializer that has no entry for String. It throws
     * before a request is ever sent. The prefix operators are concrete String
     * classes and have no such problem.
     *
     * Scoring still applies to the prefix hits — not to rank them, but because
     * two series with the same title must come out as duplicates, and [settle]
     * is the one place that decides.
     */
    private suspend fun matchOne(row: ChapterSeriesRow): MatchResult {
        val libraryId = selectedLibrary?.id ?: return MatchResult.NOT_FOUND
        val stripped = strippedChapterTitle(row.series.metadata.title)
        if (stripped.isBlank()) return MatchResult.NOT_FOUND

        val byPrefix = rawSeriesApi.getSeriesList(
            conditionBuilder = allOfSeries {
                library { isEqualTo(libraryId) }
                title { beginsWith(stripped) }
            },
            fulltextSearch = null,
            pageRequest = KomgaPageRequest(size = PREFIX_PAGE_SIZE, pageIndex = 0),
        ).content.scoredAgainst(row, stripped)
        if (byPrefix.isNotEmpty()) return settle(row, byPrefix)

        // The volumes are not named after the chapters: fall back to the
        // index-free search, where an accent, a colon or a leading article is
        // what stands between the two.
        val fuzzy = rawSeriesApi.getSeriesList(
            conditionBuilder = allOfSeries { library { isEqualTo(libraryId) } },
            fulltextSearch = stripped,
            pageRequest = KomgaPageRequest(size = FUZZY_PAGE_SIZE, pageIndex = 0),
        ).content.scoredAgainst(row, stripped)
        return settle(row, fuzzy)
    }

    /** Drop what can never be the answer, score the rest, best first. */
    private fun List<KomgaSeries>.scoredAgainst(
        row: ChapterSeriesRow,
        stripped: String,
    ): List<ChapterCandidate> = asSequence()
        // Never propose a chapter series as another one's volumes, and never
        // propose the row itself.
        .filter { it.id != row.series.id }
        .filterNot { isChapterSeriesTitle(it.metadata.title) }
        .map { ChapterCandidate(it, titleMatchScore(stripped, it.metadata.title)) }
        .filter { it.score >= CHAPTER_MATCH_FLOOR_SCORE }
        .sortedByDescending { it.score }
        .toList()

    /**
     * Apply, ask, or give up.
     *
     * A single candidate at or above [CHAPTER_MATCH_AUTO_SCORE] is applied. Two
     * candidates that high never are, however confident each looks on its own:
     * a library holding two series called "Berserk" gives no ground to prefer
     * either, and a wrong link is silent once written.
     */
    private suspend fun settle(row: ChapterSeriesRow, candidates: List<ChapterCandidate>): MatchResult {
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
        logger.info {
            "Chapter link: '${row.series.metadata.title}' -> '${volumes.metadata.title}' " +
                "(${row.series.id.value} -> ${volumes.id.value})"
        }
        // Local always, server too when allowed — the exact rule the Links tab
        // applies, so a pair made here is the same pair made there.
        if (shareViaKomga()) {
            KoraSharedLinks.link(
                seriesApi = rawSeriesApi,
                from = row.series.id,
                to = volumes.id,
                type = SeriesRelationType.VOLUMES,
            )
        }
        updateRow(row.series.id.value) {
            it.copy(linked = true, candidates = emptyList(), searched = true)
        }
        // Without this a series screen already on the back stack keeps the state
        // it loaded before, so the link looks like it was never made.
        SeriesLinksChanges.notifyChanged()
    }

    /** Links [row] to a candidate the user picked among several. */
    fun onPickCandidate(row: ChapterSeriesRow, candidate: ChapterCandidate) {
        screenModelScope.launch {
            logger.info { "Chapter link picked by hand: ${candidate.series.metadata.title}" }
            errors = emptyList()
            try {
                link(row, candidate.series)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                logger.error(e) { "Chapter link failed for '${row.series.metadata.title}'" }
                errors = listOf("${row.series.metadata.title}: ${describe(e)}")
            }
        }
    }

    /** Removes the chapters/volumes pairing, for a link put on the wrong series. */
    fun onUnlink(row: ChapterSeriesRow) {
        screenModelScope.launch {
            notifications.runCatchingToNotifications {
                // The pairing can live locally, on the server, or both. Take the
                // target from whichever holds it, and clear both — removing only
                // the local copy left the published link to come back on reload.
                val target = linksRepository.relationsOf(row.series.id)
                    .firstOrNull { isChapterRelation(it.type) }?.series
                    ?: KoraSharedLinks.relationsOf(rawSeriesApi.getOneSeries(row.series.id))
                        .firstOrNull { isChapterRelation(it.type) }?.series
                    ?: return@runCatchingToNotifications

                linksRepository.unlinkRelation(row.series.id, target)
                if (shareViaKomga()) KoraSharedLinks.unlink(rawSeriesApi, row.series.id, target)
                updateRow(row.series.id.value) {
                    it.copy(linked = false, candidates = emptyList(), searched = false)
                }
                SeriesLinksChanges.notifyChanged()
            }
        }
    }

    private fun updateRow(seriesId: String, transform: (ChapterSeriesRow) -> ChapterSeriesRow) {
        rows = rows.map { if (it.series.id.value == seriesId) transform(it) else it }
    }
}
