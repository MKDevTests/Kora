package snd.komelia.ui.suggestions

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import snd.komelia.komga.api.KomgaSeriesApi
import snd.komelia.ratings.SeriesRatingsRepository
import snd.komelia.similarity.Feature
import snd.komelia.similarity.SeriesEvidence
import snd.komelia.similarity.SimilarityEngine
import snd.komelia.similarity.SimilarityIndexBuilder
import snd.komelia.similarity.SimilarityIndexRepository
import snd.komelia.similarity.tasteAffinities
import snd.komelia.similarity.toIndexedSeries
import snd.komga.client.book.KomgaReadStatus
import snd.komga.client.common.KomgaPageRequest
import snd.komga.client.common.KomgaSort.KomgaSeriesSort
import snd.komga.client.library.KomgaLibraryId
import snd.komga.client.search.allOfSeries
import snd.komga.client.series.KomgaSeries
import snd.komga.client.series.KomgaSeriesId
import kotlin.time.Clock

private val logger = KotlinLogging.logger {}

/** One recommendation, with the terms that earned it. */
data class ForYouResult(
    val series: KomgaSeries,
    val reasons: List<Feature>,
)

/** What a run produced, plus how many series the taste profile was built from. */
data class ForYouSuggestions(
    val results: List<ForYouResult> = emptyList(),
    val profileSize: Int = 0,
)

/**
 * Process-wide cache of computed suggestions.
 *
 * Building a taste profile costs up to ten paged queries PER LIBRARY (read and
 * in-progress series), and this server answers a list in about two seconds.
 * That is affordable when the user opens the For-you tab; it is not affordable
 * on every Home load, where it made the shelf arrive minutes late — or never,
 * when one of those requests timed out. Computed once, then served from here.
 */
private object ForYouCache {
    private data class Entry(val suggestions: ForYouSuggestions, val atMillis: Long)

    private val byKey = mutableMapOf<String, Entry>()

    fun get(key: String): ForYouSuggestions? {
        val entry = byKey[key] ?: return null
        val age = Clock.System.now().toEpochMilliseconds() - entry.atMillis
        return if (age < TTL_MILLIS) entry.suggestions else null
    }

    fun put(key: String, suggestions: ForYouSuggestions) {
        byKey[key] = Entry(suggestions, Clock.System.now().toEpochMilliseconds())
    }

    fun invalidate() = byKey.clear()

    /** Long enough to cover a session's Home loads, short enough to follow a rating. */
    private const val TTL_MILLIS = 30 * 60 * 1000L
}

/**
 * Computes "what to read next" for one library, from what the user has read and
 * rated there.
 *
 * Extracted from the library tab so the Home shelf runs the SAME pipeline: two
 * implementations of a taste profile would drift, and the one on Home — the
 * screen people actually look at — would be the one nobody checked.
 */
class ForYouSuggester(
    private val seriesApi: KomgaSeriesApi,
    private val repository: SimilarityIndexRepository,
    private val indexBuilder: SimilarityIndexBuilder?,
    private val ratingsRepository: SeriesRatingsRepository,
    private val favoriteSeriesIds: Flow<Set<String>>,
    private val excludedSeriesIds: Flow<Set<String>>,
) {

    /**
     * [onIndexProgress] reports the one-off library indexing (about a request per
     * hundred series); it never fires again once the index exists.
     */
    suspend fun suggest(
        libraryId: KomgaLibraryId,
        limit: Int,
        includeRead: Boolean = false,
        /**
         * False on Home: indexing a library is a burst of requests the user did
         * not ask for by scrolling past a shelf. The shelf stays empty until the
         * library's own tab has indexed once.
         */
        indexIfMissing: Boolean = true,
        /** Recompute even if a fresh result is cached (the tab's refresh). */
        force: Boolean = false,
        onIndexProgress: (Float?) -> Unit = {},
    ): ForYouSuggestions {
        val cacheKey = "${'$'}{libraryId.value}:${'$'}includeRead:${'$'}limit"
        if (!force) ForYouCache.get(cacheKey)?.let { return it }

        var entries = repository.entriesOf(libraryId.value)
        if (entries.isEmpty()) {
            if (!indexIfMissing) return ForYouSuggestions()
            val builder = indexBuilder ?: return ForYouSuggestions()
            onIndexProgress(0f)
            try {
                builder.build(libraryId) { indexed, total ->
                    onIndexProgress(if (total > 0) indexed.toFloat() / total else 0f)
                }
            } finally {
                onIndexProgress(null)
            }
            entries = repository.entriesOf(libraryId.value)
        }
        if (entries.isEmpty()) return ForYouSuggestions()

        val indexedIds = entries.mapTo(HashSet(entries.size)) { it.seriesId }
        val read = seriesIdsWith(KomgaReadStatus.READ, libraryId)
        val inProgress = seriesIdsWith(KomgaReadStatus.IN_PROGRESS, libraryId)
        val favorites = favoriteSeriesIds.first()
        val ratings = ratingsRepository.listAll().associate { it.seriesId.value to it.stars }

        // Ratings and favourites are cross-library and local; only the ones that
        // belong to this library can contribute terms this index holds.
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
        if (affinities.isEmpty()) return ForYouSuggestions(profileSize = 0)

        // A discovery surface: what is FINISHED is dropped unless asked for.
        // Series in progress stay — proposing one back is a nudge to resume it.
        val exclude = excludedSeriesIds.first() + (if (includeRead) emptySet() else read)

        val engine = SimilarityEngine(entries.toIndexedSeries())
        val scored = engine.recommend(affinities, limit = limit, exclude = exclude)
        return ForYouSuggestions(
            results = resolve(scored.map { it.seriesId to it.reasons }),
            profileSize = affinities.size,
        ).also { ForYouCache.put(cacheKey, it) }
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
    private suspend fun resolve(scored: List<Pair<String, List<Feature>>>): List<ForYouResult> {
        if (scored.isEmpty()) return emptyList()
        val limit = Semaphore(4)
        return coroutineScope {
            scored.map { (id, reasons) ->
                async {
                    limit.withPermit {
                        try {
                            ForYouResult(seriesApi.getOneSeries(KomgaSeriesId(id)), reasons)
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

private const val PROFILE_PAGE_SIZE = 200
private const val MAX_PROFILE_PAGES = 5
