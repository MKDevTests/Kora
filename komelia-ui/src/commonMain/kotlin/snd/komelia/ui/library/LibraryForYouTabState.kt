package snd.komelia.ui.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import snd.komelia.AppNotifications
import snd.komelia.komga.api.KomgaSeriesApi
import snd.komelia.ratings.SeriesRatingsRepository
import snd.komelia.similarity.Feature
import snd.komelia.similarity.SeriesEvidence
import snd.komelia.similarity.SimilarityEngine
import snd.komelia.similarity.SimilarityIndexBuilder
import snd.komelia.similarity.SimilarityIndexRepository
import snd.komelia.similarity.TermFamily
import snd.komelia.similarity.tasteAffinities
import snd.komelia.similarity.toIndexedSeries
import snd.komga.client.common.KomgaPageRequest
import snd.komga.client.common.KomgaSort.KomgaSeriesSort
import snd.komga.client.common.KomgaReadStatus
import snd.komga.client.library.KomgaLibrary
import snd.komga.client.library.KomgaLibraryId
import snd.komga.client.search.allOfSeries
import snd.komga.client.series.KomgaSeries
import snd.komga.client.series.KomgaSeriesId

private val logger = KotlinLogging.logger {}

/** One recommendation: the series to show, and the terms that earned it. */
data class ForYouSuggestion(
    val series: KomgaSeries,
    val reasons: List<String>,
)

/**
 * State for the library's "For you" tab: what to read next, from what the user
 * has already read and rated in THIS library.
 *
 * The taste profile is a weighted sum of the term vectors of the series the user
 * has met (see [tasteAffinities]), so a bad rating pushes its terms down rather
 * than banning a whole genre. Scoring itself is the same local engine as the
 * series "Similar" tab and costs milliseconds; the expensive part is the library
 * index, which is shared with that tab and built at most once.
 */
class LibraryForYouTabState(
    private val library: StateFlow<KomgaLibrary?>,
    private val notifications: AppNotifications,
    private val seriesApi: KomgaSeriesApi,
    private val repository: SimilarityIndexRepository,
    private val indexBuilder: SimilarityIndexBuilder?,
    private val ratingsRepository: SeriesRatingsRepository,
    private val favoriteSeriesIds: Flow<Set<String>>,
    private val excludedSeriesIds: Flow<Set<String>>,
    private val screenModelScope: CoroutineScope,
    val cardWidth: StateFlow<Dp>,
) {
    var suggestions by mutableStateOf<List<ForYouSuggestion>>(emptyList())
        private set

    /** 0f..1f while the library index is being built, null otherwise. */
    var buildProgress by mutableStateOf<Float?>(null)
        private set

    var isLoading by mutableStateOf(false)
        private set

    var failed by mutableStateOf(false)
        private set

    /** How many series fed the taste profile — shown so an empty tab explains itself. */
    var profileSize by mutableStateOf(0)
        private set

    /**
     * When false (the default) the tab only proposes series the user has never
     * finished: it is a discovery surface, unlike the series "Similar" tab where
     * seeing a title you already read is the point.
     */
    var includeRead by mutableStateOf(false)
        private set

    private var started = false

    fun onOpened() {
        if (started) return
        started = true
        reload()
    }

    fun reload() {
        screenModelScope.launch { load() }
    }

    fun toggleIncludeRead() {
        includeRead = !includeRead
        reload()
    }

    private suspend fun load() {
        notifications.runCatchingToNotifications {
            isLoading = true
            failed = false
            val currentLibrary = library.filterNotNull().first()
            val libraryId = currentLibrary.id

            var entries = repository.entriesOf(libraryId.value)
            if (entries.isEmpty()) {
                val builder = indexBuilder
                if (builder == null) {
                    isLoading = false
                    return@runCatchingToNotifications
                }
                buildProgress = 0f
                try {
                    builder.build(libraryId) { indexed, total ->
                        buildProgress = if (total > 0) indexed.toFloat() / total else 0f
                    }
                } finally {
                    buildProgress = null
                }
                entries = repository.entriesOf(libraryId.value)
            }

            val indexedIds = entries.mapTo(HashSet(entries.size)) { it.seriesId }
            val read = seriesIdsWith(KomgaReadStatus.READ, libraryId)
            val inProgress = seriesIdsWith(KomgaReadStatus.IN_PROGRESS, libraryId)
            val favorites = favoriteSeriesIds.first()
            val ratings = ratingsRepository.listAll().associate { it.seriesId.value to it.stars }

            // Ratings and favourites are cross-library and local; keep only what
            // belongs to the library being looked at, or the profile would be
            // built from terms this index doesn't even hold.
            val evidence = (read + inProgress + favorites.filter { it in indexedIds } + ratings.keys.filter { it in indexedIds })
                .distinct()
                .map { id ->
                    SeriesEvidence(
                        seriesId = id,
                        read = id in read,
                        inProgress = id in inProgress,
                        isFavorite = id in favorites,
                        stars = ratings[id],
                    )
                }
            val affinities = tasteAffinities(evidence)
            profileSize = affinities.size

            // A discovery surface: what is FINISHED is dropped unless the user
            // asks for it. Series in progress stay — proposing one back is a
            // nudge to resume it. Hidden/ignored are dropped as everywhere else.
            val exclude = excludedSeriesIds.first() + (if (includeRead) emptySet() else read)

            val engine = SimilarityEngine(entries.toIndexedSeries())
            val scored = engine.recommend(affinities, limit = MAX_SUGGESTIONS, exclude = exclude)
            suggestions = resolve(scored.map { it.seriesId to it.reasons })
            isLoading = false
        }.onFailure {
            logger.error(it) { "For-you tab failed" }
            isLoading = false
            failed = true
        }
    }

    /**
     * Ids of the library's series in a given read state, newest first and capped:
     * the profile is an average of tastes, and the thousandth series read adds
     * nothing a hundred pages of requests could justify.
     */
    private suspend fun seriesIdsWith(status: KomgaReadStatus, libraryId: KomgaLibraryId): Set<String> {
        val ids = LinkedHashSet<String>()
        var pageIndex = 0
        while (pageIndex < MAX_PROFILE_PAGES) {
            val page = seriesApi.getSeriesList(
                conditionBuilder = allOfSeries {
                    library { isEqualTo(libraryId) }
                    readStatus { isEqualTo(status) }
                },
                fulltextSearch = null,
                pageRequest = KomgaPageRequest(
                    size = PROFILE_PAGE_SIZE,
                    pageIndex = pageIndex,
                    sort = KomgaSeriesSort.byLastModifiedDateDesc(),
                ),
            )
            page.content.forEach { ids += it.id.value }
            if (page.content.isEmpty() || pageIndex >= page.totalPages - 1) break
            pageIndex++
        }
        return ids
    }

    /** Ids -> series, four in flight. A failed lookup drops its own suggestion. */
    private suspend fun resolve(scored: List<Pair<String, List<Feature>>>): List<ForYouSuggestion> {
        if (scored.isEmpty()) return emptyList()
        val limit = Semaphore(4)
        return coroutineScope {
            scored.map { (id, reasons) ->
                async {
                    limit.withPermit {
                        try {
                            ForYouSuggestion(seriesApi.getOneSeries(KomgaSeriesId(id)), reasons.map { it.label() })
                        } catch (t: Throwable) {
                            currentCoroutineContext().ensureActive()
                            logger.debug { "For-you suggestion $id dropped: ${t::class.simpleName}" }
                            null
                        }
                    }
                }
            }.awaitAll().filterNotNull()
        }
    }
}

/** Same wording as the series "Similar" tab, so a reason reads the same everywhere. */
private fun Feature.label(): String = when (family) {
    TermFamily.AUTHOR -> "Author: $value"
    TermFamily.GENRE -> GenreLabels.label(value)
    TermFamily.TAG -> value.removePrefix("kora:tag:").replaceFirstChar { it.uppercaseChar() }
    TermFamily.BOOK_TAG -> value.replaceFirstChar { it.uppercaseChar() }
    TermFamily.PUBLISHER -> "Publisher: ${value.replaceFirstChar { it.uppercaseChar() }}"
}

private const val MAX_SUGGESTIONS = 40
private const val PROFILE_PAGE_SIZE = 200
private const val MAX_PROFILE_PAGES = 5
