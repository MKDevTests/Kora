package snd.komelia.ui.series

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import snd.komelia.komga.api.KomgaSeriesApi
import snd.komelia.links.KoraLinkCodec
import snd.komelia.links.SeriesLinksRepository
import snd.komelia.links.SeriesRelationEdge
import snd.komelia.readingorder.ReadingOrderGraph
import snd.komelia.readingorder.ReadingOrderRepository
import snd.komelia.readingorder.buildReadingOrder
import snd.komga.client.series.KomgaSeriesId

private val logger = KotlinLogging.logger {}

/**
 * Shared links are readable one series at a time; cap the crawl.
 *
 * Unlike the node caps in the graph builder, this one IS paid in requests: one
 * getOneSeries per series, four in flight. Twelve was exactly the size of a real
 * franchise (Fairy Tail), so the last series' own links were never read and the
 * picture was cut for want of one more wave. Twenty-four leaves room; the result
 * is cached and the previous picture stays on screen while it runs.
 */
private const val MAX_SHARED_WALK = 24

/**
 * State for the "Reading order" picture at the top of the series Links tab.
 *
 * The graph is always drawn from the franchise's ORIGINAL series, never from
 * the series being viewed: that is what makes the picture the same wherever you
 * open it from, and what answers "where do I start". The user designates the
 * original; until they do, the head of the sequel chain is used.
 */
class ReadingOrderState(
    private val series: StateFlow<snd.komga.client.series.KomgaSeries?>,
    private val linksRepository: SeriesLinksRepository,
    private val repository: ReadingOrderRepository,
    private val seriesApi: KomgaSeriesApi,
    private val settingsRepository: snd.komelia.settings.CommonSettingsRepository,
    private val screenModelScope: CoroutineScope,
) {
    var graph by mutableStateOf<ReadingOrderGraph?>(null)
        private set

    var isLoading by mutableStateOf(false)
        private set

    /** True when the series being viewed is the designated original. */
    var currentIsOriginal by mutableStateOf(false)
        private set

    private var started = false

    fun onOpened() {
        if (started) return
        started = true
        load(forceRefresh = false)
        // Any link change anywhere reshapes franchises, including this one.
        SeriesLinksChanges.changes.onEach {
            repository.invalidateAll()
            load(forceRefresh = true)
        }.launchIn(screenModelScope)
    }

    /** Explicit rebuild — the escape hatch when the picture looks wrong. */
    fun refresh() = load(forceRefresh = true)

    /** Designates (or clears) the current series as the franchise's original. */
    fun toggleOriginal() {
        val current = series.value ?: return
        screenModelScope.launch {
            val next = !currentIsOriginal
            repository.setOriginal(current.id, next)
            // The whole franchise is drawn from that decision, so every cached
            // picture is stale, not just this one.
            repository.invalidateAll()
            currentIsOriginal = next
            load(forceRefresh = true)
        }
    }

    private fun load(forceRefresh: Boolean) {
        screenModelScope.launch {
            isLoading = true
            try {
                val current = series.filterNotNull().first()

                // Draw the graph we already know for this series while it is
                // recomputed. Finding its root needs the shared-link walk —
                // up to twelve requests — and until now nothing was shown
                // during it, even though the answer was already on disk.
                if (!forceRefresh && graph == null) {
                    val remembered = snd.komelia.perf.PerfTrace.measure(
                        label = "series.readingOrder.remembered",
                        count = { it: snd.komelia.readingorder.ReadingOrderGraph? -> it?.nodes?.size },
                    ) { repository.getCachedContaining(current.id) }
                    if (remembered != null && remembered.isWorthShowing) {
                        graph = remembered
                        isLoading = false
                    }
                }

                val localRelations = linksRepository.getAllRelations()
                val versions = linksRepository.getAllVersions()
                currentIsOriginal = repository.isOriginal(current.id)

                // Links can live in TWO places, and reading only one of them is
                // what made the graph work in debug and fail in release: the
                // local table is per app install, while links shared through
                // Komga travel with the server. The Links tab has always merged
                // both; the graph now does too.
                val fetched = mutableMapOf<String, snd.komga.client.series.KomgaSeries>()
                val sharedRelations =
                    if (settingsRepository.getShareLinksViaKomga().first()) {
                        walkSharedLinks(current, fetched)
                    } else emptyList()

                val relations = (localRelations + sharedRelations).distinct()
                val component = franchiseOf(current.id.value, relations, versions)
                if (component.size <= 1) {
                    graph = null
                    return@launch
                }
                val originalId = KomgaSeriesId(pickOriginal(current.id.value, component, repository.originals()))

                if (!forceRefresh) {
                    val cached = repository.getCached(originalId)
                    if (cached != null) {
                        graph = cached.takeIf { it.isWorthShowing }
                        return@launch
                    }
                }

                val titles = resolveTitles(component, fetched)
                val built = buildReadingOrder(originalId, relations, versions, titles)
                // A one-box graph is never worth caching: it would be served
                // back as "nothing to show" forever, even after the links that
                // would have filled it were added.
                if (built.isWorthShowing) repository.putCached(built)
                graph = built.takeIf { it.isWorthShowing }
            } catch (t: Throwable) {
                currentCoroutineContext().ensureActive()
                logger.warn(t) { "Reading order graph failed" }
                graph = null
            } finally {
                isLoading = false
            }
        }
    }

    /**
     * Follows the Kora links written in Komga metadata, breadth-first from the
     * current series.
     *
     * Shared links are only readable series by series, so this is a bounded walk
     * — [MAX_SHARED_WALK] fetches, four in flight — and every series it fetches
     * is kept for the title pass, which would otherwise fetch them all again.
     */
    private suspend fun walkSharedLinks(
        start: snd.komga.client.series.KomgaSeries,
        fetched: MutableMap<String, snd.komga.client.series.KomgaSeries>,
    ): List<SeriesRelationEdge> {
        val edges = mutableListOf<SeriesRelationEdge>()
        val seen = mutableSetOf(start.id.value)
        var frontier = listOf(start)
        fetched[start.id.value] = start

        while (frontier.isNotEmpty() && fetched.size < MAX_SHARED_WALK) {
            val nextIds = mutableSetOf<String>()
            for (series in frontier) {
                series.metadata.links.mapNotNull { KoraLinkCodec.parse(it) }.forEach { parsed ->
                    edges += SeriesRelationEdge(series.id, parsed.target, parsed.type)
                    if (seen.add(parsed.target.value)) nextIds += parsed.target.value
                }
            }
            if (nextIds.isEmpty()) break
            val limit = Semaphore(4)
            frontier = coroutineScope {
                nextIds.take(MAX_SHARED_WALK - fetched.size).map { id ->
                    async {
                        limit.withPermit {
                            try {
                                seriesApi.getOneSeries(KomgaSeriesId(id))
                            } catch (t: Throwable) {
                                currentCoroutineContext().ensureActive()
                                null
                            }
                        }
                    }
                }.awaitAll().filterNotNull()
            }
            frontier.forEach { fetched[it.id.value] = it }
        }
        return edges
    }

    private suspend fun resolveTitles(
        seriesIds: Set<String>,
        known: Map<String, snd.komga.client.series.KomgaSeries>,
    ): Map<String, String> {
        val alreadyKnown = known.filterKeys { it in seriesIds }.mapValues { it.value.metadata.title }
        val missing = seriesIds - alreadyKnown.keys
        val limit = Semaphore(4)
        return alreadyKnown + coroutineScope {
            missing.map { id ->
                async {
                    limit.withPermit {
                        val title = try {
                            seriesApi.getOneSeries(KomgaSeriesId(id)).metadata.title
                        } catch (t: Throwable) {
                            currentCoroutineContext().ensureActive()
                            null
                        }
                        title?.let { id to it }
                    }
                }
            }.awaitAll().filterNotNull().toMap()
        }
    }
}

/** Every series reachable from [seriesId] through any link, editions included. */
private fun franchiseOf(
    seriesId: String,
    relations: List<SeriesRelationEdge>,
    versions: Map<KomgaSeriesId, String>,
): Set<String> {
    val neighbours = mutableMapOf<String, MutableSet<String>>()
    fun connect(a: String, b: String) {
        neighbours.getOrPut(a) { mutableSetOf() } += b
        neighbours.getOrPut(b) { mutableSetOf() } += a
    }
    relations.forEach { connect(it.from.value, it.to.value) }
    versions.entries.groupBy({ it.value }, { it.key.value }).values.forEach { members ->
        members.forEach { m -> members.forEach { other -> if (m != other) connect(m, other) } }
    }

    val seen = mutableSetOf<String>()
    val queue = ArrayDeque(listOf(seriesId))
    while (queue.isNotEmpty()) {
        val current = queue.removeFirst()
        if (!seen.add(current)) continue
        neighbours[current].orEmpty().forEach { if (it !in seen) queue += it }
    }
    return seen
}

/**
 * The series to draw from: the user's designated original when the franchise has
 * one, otherwise the series being viewed.
 *
 * Falling back to the viewed series is deliberate. Relations are stored in BOTH
 * directions — "Zero is the prequel of Fairy Tail" is also "Fairy Tail is the
 * sequel of Zero" — so no structural rule can tell the start of a franchise
 * from its prequel. The first attempt tried "the series nothing points to as a
 * sequel" and picked the spin-off, whose only edge leads back to the main
 * series: a one-box graph, which is why nothing was drawn. Drawing from where
 * the user stands always produces a real picture, and the chip is right there
 * to pin it down for good.
 */
private fun pickOriginal(
    viewedSeriesId: String,
    component: Set<String>,
    designated: Set<String>,
): String = component.firstOrNull { it in designated } ?: viewedSeriesId
