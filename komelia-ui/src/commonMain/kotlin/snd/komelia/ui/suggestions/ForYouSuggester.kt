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
    /** The liked series this pick was extrapolated from, strongest first. */
    val becauseOf: List<snd.komelia.similarity.SourceAttribution> = emptyList(),
)

/**
 * How the user met a series the profile was built from — the difference between
 * "you liked it" and "you read it", which a heading must not blur.
 */
enum class ForYouSourceKind { LIKED, READ, READING }

/** A profile series that can head a section: its name and what it is to the user. */
data class ForYouSource(
    val name: String,
    val kind: ForYouSourceKind,
)

/** What a run produced, plus how many series the taste profile was built from. */
data class ForYouSuggestions(
    val results: List<ForYouResult> = emptyList(),
    val profileSize: Int = 0,
    /** Series referenced by [ForYouResult.becauseOf], for the headings. */
    val sources: Map<String, ForYouSource> = emptyMap(),
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
    private val feedbackRepository: snd.komelia.similarity.SuggestionFeedbackRepository,
) {

    /**
     * Drops the cached runs — a dismissal must not come back on the next open,
     * and the cache is what would bring it back for half an hour.
     */
    fun invalidateCache() = ForYouCache.invalidate()

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
        val cacheKey = "${libraryId.value}:$includeRead:$limit"
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
        val dismissed = feedbackRepository.dismissed()

        // Ratings and favourites are cross-library and local; only the ones that
        // belong to this library can contribute terms this index holds.
        val evidence = (read + inProgress + favorites.filter { it in indexedIds } +
            ratings.keys.filter { it in indexedIds } + dismissed.filter { it in indexedIds })
            .distinct()
            .map { id ->
                SeriesEvidence(
                    seriesId = id,
                    read = id in read,
                    inProgress = id in inProgress,
                    isFavorite = id in favorites,
                    stars = ratings[id],
                    dismissed = id in dismissed,
                )
            }
        val affinities = tasteAffinities(evidence)
        if (affinities.isEmpty()) return ForYouSuggestions(profileSize = 0)

        // A discovery surface: what is FINISHED is dropped unless asked for.
        // Series in progress stay — proposing one back is a nudge to resume it.
        // "Not interested" is final: the series never comes back, whatever the
        // Show-read toggle says.
        val exclude = excludedSeriesIds.first() + dismissed + (if (includeRead) emptySet() else read)

        val engine = SimilarityEngine(entries.toIndexedSeries())
        val scored = engine.recommend(affinities, limit = limit, exclude = exclude)
        val results = resolve(scored)
        return ForYouSuggestions(
            results = results,
            profileSize = affinities.size,
            sources = resolveSources(sectionSources(results, evidence), evidence),
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
    private suspend fun resolve(scored: List<snd.komelia.similarity.SimilarSeries>): List<ForYouResult> {
        if (scored.isEmpty()) return emptyList()
        val limit = Semaphore(4)
        return coroutineScope {
            scored.map { suggestion ->
                async {
                    limit.withPermit {
                        try {
                            ForYouResult(
                                series = seriesApi.getOneSeries(KomgaSeriesId(suggestion.seriesId)),
                                reasons = suggestion.reasons,
                                becauseOf = suggestion.becauseOf,
                            )
                        } catch (t: Throwable) {
                            currentCoroutineContext().ensureActive()
                            logger.debug { "For-you suggestion ${suggestion.seriesId} dropped: ${t::class.simpleName}" }
                            null
                        }
                    }
                }
            }.awaitAll().filterNotNull()
        }
    }

    /**
     * The few profile series worth a "Because you liked X" heading.
     *
     * Capped hard on purpose: naming a source costs one request, and every
     * suggestion carries up to three of them — resolving them all would mean a
     * hundred extra round-trips against a server that answers in seconds. Only
     * sources that would actually head a section get looked up.
     */
    private fun sectionSources(results: List<ForYouResult>, evidence: List<SeriesEvidence>): List<String> {
        val kinds = evidence.associate { it.seriesId to kindOf(it) }
        fun isLiked(id: String) = kinds[id] == ForYouSourceKind.LIKED
        return results
            // The first attribution only: a card belongs to the series that
            // explains it most, not to every series it brushes against. On a
            // profile of three hundred series no single source ever accounts for
            // a quarter of a score, so a threshold on the share would simply
            // remove every section.
            .mapNotNull { result -> result.becauseOf.firstOrNull()?.seriesId }
            .groupingBy { it }.eachCount()
            // A rated or favourited series earns a heading on two picks; one the
            // user merely finished needs three. Rating a series is the user
            // telling us what they want more of — a profile full of unrated
            // reads used to crowd those out of the four available headings,
            // which is what made a fresh 5-star rating look ignored.
            .entries
            .filter { it.value >= if (isLiked(it.key)) MIN_LIKED_SECTION_SIZE else MIN_SECTION_SIZE }
            .sortedWith(
                compareByDescending<Map.Entry<String, Int>> { isLiked(it.key) }
                    .thenByDescending { it.value }
                    .thenBy { it.key }
            )
            .take(MAX_SECTIONS)
            .map { it.key }
    }

    /**
     * Names the profile series a suggestion was attributed to, and records what
     * the user actually did with each — a series that was only read must not be
     * announced as one they liked. A failure here only costs a heading; the
     * suggestion itself is already resolved.
     */
    private suspend fun resolveSources(
        ids: List<String>,
        evidence: List<SeriesEvidence>,
    ): Map<String, ForYouSource> {
        if (ids.isEmpty()) return emptyMap()
        val byId = evidence.associateBy { it.seriesId }
        val limit = Semaphore(4)
        return coroutineScope {
            ids.map { id ->
                async {
                    limit.withPermit {
                        try {
                            val name = seriesApi.getOneSeries(KomgaSeriesId(id)).metadata.title
                            id to ForYouSource(name, kindOf(byId[id]))
                        } catch (t: Throwable) {
                            currentCoroutineContext().ensureActive()
                            null
                        }
                    }
                }
            }.awaitAll().filterNotNull().toMap()
        }
    }

    /** Same precedence as [tasteAffinities]: an explicit judgement wins. */
    private fun kindOf(evidence: SeriesEvidence?): ForYouSourceKind {
        if (evidence == null) return ForYouSourceKind.READ
        val stars = evidence.stars
        return when {
            stars != null -> if (stars >= LIKED_STARS) ForYouSourceKind.LIKED else ForYouSourceKind.READ
            evidence.isFavorite -> ForYouSourceKind.LIKED
            evidence.read -> ForYouSourceKind.READ
            else -> ForYouSourceKind.READING
        }
    }
}

/** From four stars up, the user said they liked it; below, they only read it. */
private const val LIKED_STARS = 4

/** Below this a "Because you read X" heading is noise, not structure. */
private const val MIN_SECTION_SIZE = 3

/** A series the user rated or favourited earns its heading sooner. */
private const val MIN_LIKED_SECTION_SIZE = 2
private const val MAX_SECTIONS = 4
private const val PROFILE_PAGE_SIZE = 200
private const val MAX_PROFILE_PAGES = 5
