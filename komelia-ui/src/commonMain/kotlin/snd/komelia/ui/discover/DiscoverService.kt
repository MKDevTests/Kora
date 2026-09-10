package snd.komelia.ui.discover

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
import snd.komelia.discover.DiscoverRepository
import snd.komelia.discover.DiscoverSourceLink
import snd.komelia.discover.DiscoverSuggestion
import snd.komelia.discover.MangaUpdatesClient
import snd.komelia.discover.MangaUpdatesSeries
import snd.komelia.discover.SOURCE_MANGAUPDATES
import snd.komelia.discover.catalogueKey
import snd.komelia.discover.localTitleVariants
import snd.komelia.discover.trackerTitle
import snd.komelia.komga.api.KomgaSeriesApi
import snd.komelia.ratings.SeriesRatingsRepository
import snd.komelia.similarity.SeriesEvidence
import snd.komelia.similarity.SimilarityIndexRepository
import snd.komelia.similarity.tasteAffinities
import snd.komga.client.book.KomgaReadStatus
import snd.komga.client.common.KomgaWebLink
import snd.komga.client.common.KomgaPageRequest
import snd.komga.client.common.KomgaSort.KomgaSeriesSort
import snd.komga.client.library.KomgaLibraryId
import snd.komga.client.search.allOfSeries
import snd.komga.client.series.KomgaSeriesId
import kotlin.time.Clock

private val logger = KotlinLogging.logger {}

/**
 * Builds the Discover results: series the user does NOT own, from what they
 * already like.
 *
 * Everything else in the app suggests from the Komga catalogue, which caps it
 * at what is already on the shelf. This asks MangaUpdates instead — whose
 * per-series recommendations are voted by readers, so they work on the third of
 * this catalogue that carries no genre, tag, publisher or author.
 *
 * The whole design is about the request budget. A pass costs roughly one
 * request per seed series and never runs while the user is looking at a screen:
 * [DiscoverScanner] owns it, out of any composition, at most once a week. The
 * tab itself reads the table and makes no network request at all.
 */
class DiscoverService(
    private val seriesApi: KomgaSeriesApi,
    private val similarityIndex: SimilarityIndexRepository,
    private val ratingsRepository: SeriesRatingsRepository,
    private val favoriteSeriesIds: Flow<Set<String>>,
    /** Libraries the seed is drawn from; empty means every library. */
    private val seedLibraryIds: Flow<Set<String>>,
    private val repository: DiscoverRepository,
    private val mangaUpdates: MangaUpdatesClient,
) {

    /**
     * Runs a full pass and replaces the stored results. Returns how many
     * suggestions it produced.
     *
     * [onProgress] is 0..1 over the seed series, for the tab's refresh button.
     */
    suspend fun scan(
        libraries: List<KomgaLibraryId>,
        onProgress: (Float) -> Unit = {},
    ): Int {
        val seed = buildSeed(libraries)
        if (seed.isEmpty()) {
            logger.debug { "Discover: empty taste profile, nothing to ask" }
            return 0
        }

        val links = resolveLinks(seed.keys)
        val owned = ownedTitleKeys()
        val dismissed = repository.dismissedIds()

        val accumulator = mutableMapOf<String, Accumulated>()
        val done = Semaphore(NETWORK_CONCURRENCY)
        var handled = 0

        coroutineScope {
            seed.entries
                .filter { links[it.key]?.externalId != null }
                .map { (seriesId, affinity) ->
                    val externalId = requireNotNull(links[seriesId]?.externalId)
                    async {
                        done.withPermit { mangaUpdates.series(externalId) }?.let { seriesId to it }
                    }
                }
                .awaitAll()
                .filterNotNull()
                .forEach { (seriesId, source) ->
                    handled++
                    onProgress(handled.toFloat() / seed.size)
                    accumulate(accumulator, source, seriesId, seed.getValue(seriesId), owned, dismissed)
                }
        }

        val suggestions = enrich(
            accumulator.values.sortedByDescending { it.score }.take(MAX_STORED),
            owned,
        )

        repository.replaceSuggestions(suggestions)
        logger.info { "Discover: ${suggestions.size} suggestions from ${seed.size} seed series" }
        return suggestions.size
    }

    /**
     * Fills each suggestion in with what the source knows about it, and drops
     * the ones that turn out to be series the user already has.
     *
     * Both come from the same request, which is why it is worth making. Until
     * it existed a card carried a cover, a title and nothing else — no year, no
     * rating, no genre, no author, no summary — because a recommendation entry
     * carries none of that.
     *
     * And the duplicate check has no other way to work: the shelf says
     * "Parasite" where the source says "Kiseijuu", "FullMetal Alchemist" where
     * it says "Hagane no Renkinjutsushi". Only the series' list of alternative
     * names bridges the two, and it only comes back from here. Measured over 54
     * candidates against the 12775 indexed series: 11 duplicates caught that the
     * title alone could not see.
     *
     * Costs one request each, measured at 0.31s — about thirteen seconds for a
     * full page, once a week, in the background.
     */
    private suspend fun enrich(
        candidates: List<Accumulated>,
        owned: Set<String>,
    ): List<DiscoverSuggestion> {
        if (candidates.isEmpty()) return emptyList()
        val now = Clock.System.now()
        val gate = Semaphore(NETWORK_CONCURRENCY)

        return coroutineScope {
            candidates.map { candidate ->
                async {
                    val details = gate.withPermit { mangaUpdates.series(candidate.externalId) }
                    // A lookup that fails leaves the card thin rather than
                    // absent: the recommendation itself is still a real one.
                    if (details == null) return@async candidate.toSuggestion(now)

                    // Out of scope, and not a small share: 9 of 54 candidates
                    // measured on the real profile were novels. The type is only
                    // known here — a recommendation entry does not carry it.
                    if (details.isNovel) return@async null

                    val names = (listOf(details.title) + details.associated.map { it.title })
                        .filter { it.isNotBlank() }
                    if (names.any { catalogueKey(it) in owned }) return@async null

                    candidate.toSuggestion(now, details)
                }
            }.awaitAll().filterNotNull()
        }
    }

    /**
     * The seed: the series the user liked most, strongest first, capped.
     *
     * Deliberately the SAME profile the For-you tab is built on — two notions
     * of "what this user likes" would drift, and the one nobody re-checked
     * would be the one shipping suggestions. The cap is the request budget:
     * beyond a few dozen seeds the recommendations start repeating anyway,
     * because popular series recommend each other.
     *
     * Restricted to the chosen libraries, which is the difference between a
     * seed that can produce something and one that cannot: on the first real
     * pass, 37 of the 43 seed slots went to franco-belgian comics that
     * MangaUpdates does not carry, leaving six manga to generate every
     * suggestion. Ratings and favourites are filtered too — a five-star comic
     * is a real preference, but not one this source can answer.
     */
    private suspend fun buildSeed(libraries: List<KomgaLibraryId>): Map<String, Double> {
        val chosen = seedLibraryIds.first()
        val seedLibraries = if (chosen.isEmpty()) libraries
        else libraries.filter { it.value in chosen }
        if (seedLibraries.isEmpty()) return emptyMap()

        val favorites = favoriteSeriesIds.first()
        val ratings = ratingsRepository.listAll().associate { it.seriesId.value to it.stars }

        val read = mutableSetOf<String>()
        val inProgress = mutableSetOf<String>()
        seedLibraries.forEach { libraryId ->
            read += seriesIdsWith(KomgaReadStatus.READ, libraryId)
            inProgress += seriesIdsWith(KomgaReadStatus.IN_PROGRESS, libraryId)
        }

        // Ratings and favourites are cross-library and local, so unlike the read
        // states above they carry no library of their own. The term index does,
        // and answers ids-only — no JSON blob is decoded to run this filter.
        val inScope: Set<String>? = if (chosen.isEmpty()) null
        else seedLibraries.flatMapTo(mutableSetOf()) { similarityIndex.seriesIdsOf(it.value) }

        fun allowed(id: String) = inScope == null || id in inScope

        val evidence = (read + inProgress + favorites.filter(::allowed) + ratings.keys.filter(::allowed))
            .distinct()
            .map { id ->
                SeriesEvidence(
                    seriesId = id,
                    read = id in read,
                    inProgress = id in inProgress,
                    isFavorite = id in favorites,
                    stars = ratings[id],
                    dismissed = false,
                )
            }

        return tasteAffinities(evidence)
            .filterValues { it > 0.0 }
            .entries
            .sortedByDescending { it.value }
            .take(SEED_LIMIT)
            .associate { it.key to it.value }
    }

    /**
     * Ids of the library's series in a given read state, newest first and
     * capped — same shape as the For-you profile, two pages instead of five:
     * only the top few dozen affinities survive the seed cap, so paging deeper
     * would buy requests and no seeds.
     */
    private suspend fun seriesIdsWith(status: KomgaReadStatus, libraryId: KomgaLibraryId): Set<String> {
        val ids = LinkedHashSet<String>()
        var pageIndex = 0
        while (pageIndex < MAX_PROFILE_PAGES) {
            val page = try {
                seriesApi.getSeriesList(
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
            } catch (t: Throwable) {
                currentCoroutineContext().ensureActive()
                logger.debug { "Discover: profile page failed for ${libraryId.value}: ${t::class.simpleName}" }
                break
            }
            page.content.forEach { ids += it.id.value }
            if (page.content.isEmpty() || pageIndex >= page.totalPages - 1) break
            pageIndex++
        }
        return ids
    }

    /**
     * Local series id -> MangaUpdates id, resolved once and stored.
     *
     * Three ways in, cheapest first, and each one only runs when the one
     * before it found nothing:
     *
     *  1. an explicit mangaupdates.com link in the Komga metadata — free,
     *     certain, and rare in this library;
     *  2. a Nautiljon or Anime-Planet link, whose slug IS the romanised or
     *     English title (see [trackerTitle]) — still free, and it asks the
     *     search the question it can actually answer, since these shelves are
     *     named in French while MangaUpdates indexes romaji;
     *  3. the Komga title itself, stripped of its shelf decorations.
     *
     * Every search is checked by exact title equality before it counts, so a
     * step that finds nothing convincing falls through to the next instead of
     * inventing a match. A series nothing resolves is recorded as a miss, so
     * the next pass does not pay for it again.
     */
    private suspend fun resolveLinks(seriesIds: Collection<String>): Map<String, DiscoverSourceLink> {
        val known = repository.linksOf(seriesIds)
        val unresolved = seriesIds.filter { known[it]?.resolvedAt == null }
        if (unresolved.isEmpty()) return known

        val gate = Semaphore(NETWORK_CONCURRENCY)
        val resolved = coroutineScope {
            unresolved.map { seriesId ->
                async {
                    try {
                        val series = seriesApi.getOneSeries(KomgaSeriesId(seriesId))
                        val links = series.metadata.links
                        val explicit = links.firstNotNullOfOrNull { MangaUpdatesClient.idFromUrl(it.url) }
                        val externalId = explicit ?: searchQueriesFor(series.metadata.title, links)
                            .firstNotNullOfOrNull { query ->
                                gate.withPermit { mangaUpdates.searchOne(query) }
                                    ?.seriesId
                                    ?.takeIf { it > 0 }
                                    ?.toString()
                            }
                        DiscoverSourceLink(
                            seriesId = seriesId,
                            source = SOURCE_MANGAUPDATES,
                            externalId = externalId,
                            resolvedAt = Clock.System.now(),
                        )
                    } catch (t: Throwable) {
                        currentCoroutineContext().ensureActive()
                        logger.debug { "Discover: could not resolve $seriesId: ${t::class.simpleName}" }
                        // No row written: an unreachable server is not a miss,
                        // and recording it as one would blank this seed for a
                        // week over a transient failure.
                        null
                    }
                }
            }.awaitAll().filterNotNull()
        }

        repository.putLinks(resolved)
        return known + resolved.associateBy { it.seriesId }
    }

    /**
     * The queries worth trying for a series, in order, without duplicates.
     *
     * Tracker slugs come first because they are already in the form the
     * search indexes; the shelf name is the fallback. Each is tried only
     * until one produces a match that passes the equality check.
     */
    private fun searchQueriesFor(title: String, links: List<KomgaWebLink>): List<String> =
        (links.mapNotNull { trackerTitle(it.url) } + localTitleVariants(title))
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

    /**
     * Adds one source series' recommendations to the running scores.
     *
     * Weights are normalised per source before being scaled by affinity, so a
     * seed contributes at most its own affinity however popular it is. Raw
     * vote counts would let one blockbuster outvote every other seed and turn
     * the tab into a bestseller list.
     */
    private fun accumulate(
        into: MutableMap<String, Accumulated>,
        source: MangaUpdatesSeries,
        sourceSeriesId: String,
        affinity: Double,
        owned: Set<String>,
        dismissed: Set<String>,
    ) {
        val recommendations = source.allRecommendations
        val maxWeight = recommendations.maxOfOrNull { it.weight }?.takeIf { it > 0 } ?: return
        val categoryIds = source.categoryRecommendations.mapTo(mutableSetOf()) { it.seriesId }

        recommendations.forEach { recommendation ->
            val externalId = recommendation.seriesId.takeIf { it > 0 }?.toString() ?: return@forEach
            if (externalId in dismissed) return@forEach
            // Cheap first pass on the name we already have; the real check
            // happens in [enrich], where the alternative names are known.
            if (catalogueKey(recommendation.seriesName) in owned) return@forEach

            val weight = recommendation.weight.toDouble() / maxWeight
            val discount = if (recommendation.seriesId in categoryIds) CATEGORY_DISCOUNT else 1.0
            val entry = into.getOrPut(externalId) {
                Accumulated(
                    externalId = externalId,
                    title = recommendation.seriesName,
                    url = recommendation.seriesUrl,
                    imageUrl = recommendation.seriesImage?.url?.original.orEmpty(),
                )
            }
            entry.score += weight * discount * affinity
            entry.becauseOf[sourceSeriesId] = maxOf(
                entry.becauseOf[sourceSeriesId] ?: 0.0,
                weight * discount * affinity,
            )
        }
    }

    /**
     * Normalised titles of everything indexed, for "do I already own this".
     *
     * Read from the local term index rather than from Komga: it already holds
     * every series' title, so the check costs one query instead of a crawl —
     * and a library that has never been indexed simply contributes nothing,
     * which is the honest answer rather than a wrong one.
     */
    private suspend fun ownedTitleKeys(): Set<String> =
        similarityIndex.allTitles().mapTo(mutableSetOf()) { catalogueKey(it.titleSort) }

    private class Accumulated(
        val externalId: String,
        val title: String,
        val url: String,
        val imageUrl: String,
    ) {
        var score: Double = 0.0
        val becauseOf: MutableMap<String, Double> = mutableMapOf()

        fun toSuggestion(now: kotlin.time.Instant, details: MangaUpdatesSeries? = null) = DiscoverSuggestion(
            externalId = externalId,
            source = SOURCE_MANGAUPDATES,
            // The detailed title wins: the recommendation entry sometimes
            // carries an abbreviated form of the same name.
            title = details?.title?.takeIf { it.isNotBlank() } ?: title,
            url = details?.url?.takeIf { it.isNotBlank() } ?: url,
            imageUrl = details?.image?.url?.original?.takeIf { it.isNotBlank() } ?: imageUrl,
            year = details?.year.orEmpty(),
            rating = details?.rating ?: 0.0,
            ratingVotes = details?.ratingVotes ?: 0,
            status = details?.status.orEmpty(),
            description = details?.description.orEmpty(),
            genres = details?.genres?.map { it.genre }?.filter { it.isNotBlank() } ?: emptyList(),
            authors = details?.authors
                ?.filter { it.name.isNotBlank() }
                ?.map { if (it.type.isBlank()) it.name else "${it.name} (${it.type})" }
                ?: emptyList(),
            publishers = details?.publishers?.map { it.publisherName }?.filter { it.isNotBlank() } ?: emptyList(),
            score = score,
            becauseOf = becauseOf.entries
                .sortedByDescending { it.value }
                .take(MAX_ATTRIBUTIONS)
                .map { it.key },
            updatedAt = now,
        )
    }
}

/** Category votes are a weaker signal than a reader's explicit link. */
private const val CATEGORY_DISCOUNT = 0.5

/** Two requests in flight. This is somebody's free service. */
private const val NETWORK_CONCURRENCY = 2

private const val SEED_LIMIT = 40

/**
 * How many suggestions survive to be stored — and therefore how many extra
 * requests a pass makes, one each. Two hundred would be twice the pass's whole
 * budget for rows nobody scrolls to.
 */
private const val MAX_STORED = 40
private const val MAX_ATTRIBUTIONS = 3
private const val PROFILE_PAGE_SIZE = 200
private const val MAX_PROFILE_PAGES = 2
