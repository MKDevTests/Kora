package snd.komelia.ui.series

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import snd.komelia.AppNotifications
import snd.komelia.komga.api.KomgaSeriesApi
import snd.komelia.links.SeriesLinksRepository
import snd.komelia.links.SeriesRelationType
import snd.komelia.ui.LoadState
import snd.komga.client.common.KomgaPageRequest
import snd.komga.client.search.KomgaSearchCondition
import snd.komga.client.search.allOfSeries
import snd.komga.client.series.KomgaSeries
import snd.komga.client.series.KomgaSeriesId

/**
 * Process-wide "a link changed" signal so every open series-Links screen
 * refreshes after a link/unlink anywhere — both sides of a relation update
 * live, even when the other series' screen is already on the back stack.
 */
private object SeriesLinksChanges {
    val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    fun notifyChanged() {
        changes.tryEmit(Unit)
    }
}

/**
 * State for the series "Links" tab: the current series' other versions
 * (language/edition) and its typed related series (sequel/prequel/spin-off/
 * related), resolved to full [KomgaSeries] for display. Backed by the local
 * [SeriesLinksRepository]; links are created/removed here too.
 */
class SeriesLinksState(
    val series: StateFlow<KomgaSeries?>,
    private val notifications: AppNotifications,
    private val seriesApi: KomgaSeriesApi,
    private val linksRepository: SeriesLinksRepository,
    private val screenModelScope: CoroutineScope,
    val cardWidth: StateFlow<Dp>,
) {
    private val mutableState = MutableStateFlow<LoadState<Unit>>(LoadState.Uninitialized)
    val state = mutableState.asStateFlow()

    var versions by mutableStateOf<List<KomgaSeries>>(emptyList())
        private set

    /** Related series grouped by relation type (from this series' perspective). */
    var relations by mutableStateOf<Map<SeriesRelationType, List<KomgaSeries>>>(emptyMap())
        private set

    suspend fun initialize() {
        if (mutableState.value != LoadState.Uninitialized) return
        load()
        // Refresh whenever any link changes (this series or any other).
        SeriesLinksChanges.changes.onEach { load() }.launchIn(screenModelScope)
    }

    fun reload() {
        screenModelScope.launch { load() }
    }

    private suspend fun load() {
        notifications.runCatchingToNotifications {
            mutableState.value = LoadState.Loading
            val current = series.filterNotNull().first()
            versions = linksRepository.versionsOf(current.id).mapNotNull { resolve(it) }
            relations = linksRepository.relationsOf(current.id)
                .groupBy { it.type }
                .mapValues { (_, rels) -> rels.mapNotNull { resolve(it.series) } }
                .filterValues { it.isNotEmpty() }
            mutableState.value = LoadState.Success(Unit)
        }.onFailure { mutableState.value = LoadState.Error(it) }
    }

    /** Resolve a linked id to a series; null if it no longer exists on the server. */
    private suspend fun resolve(id: KomgaSeriesId): KomgaSeries? =
        runCatching { seriesApi.getOneSeries(id) }.getOrNull()

    fun linkVersion(other: KomgaSeriesId) = act { current -> linksRepository.linkVersion(current, other) }
    fun unlinkVersion(other: KomgaSeriesId) = act { linksRepository.unlinkVersion(other) }
    fun linkRelation(other: KomgaSeriesId, type: SeriesRelationType) =
        act { current -> linksRepository.linkRelation(current, other, type) }
    fun unlinkRelation(other: KomgaSeriesId) = act { current -> linksRepository.unlinkRelation(current, other) }

    private fun act(block: suspend (current: KomgaSeriesId) -> Unit) {
        val current = series.value?.id ?: return
        screenModelScope.launch {
            notifications.runCatchingToNotifications { block(current) }
            // Drives a reload on this screen AND every other open Links screen.
            SeriesLinksChanges.notifyChanged()
        }
    }

    /** Search series by text for the "add link" picker (excludes the current series). */
    suspend fun search(query: String): List<KomgaSeries> {
        if (query.isBlank()) return emptyList()
        val currentId = series.value?.id?.value
        return notifications.runCatchingToNotifications {
            seriesApi.getSeriesList(
                conditionBuilder = allOfSeries {},
                fulltextSearch = query,
                pageRequest = KomgaPageRequest(size = 30),
            ).content
        }.getOrDefault(emptyList()).filter { it.id.value != currentId }
    }

    /**
     * Candidate links found from the current series' authors (same writer/artist)
     * and title similarity. The user confirms each — nothing is linked
     * automatically. Already-linked series and the series itself are excluded.
     */
    suspend fun suggestions(): List<KomgaSeries> {
        val current = series.value ?: return emptyList()
        val title = current.metadata.title
        return notifications.runCatchingToNotifications {
            val byName = seriesApi.getSeriesList(
                conditionBuilder = allOfSeries {},
                fulltextSearch = significantQuery(title),
                pageRequest = KomgaPageRequest(size = 20),
            ).content
            val authorNames = current.booksMetadata.authors.map { it.name }.distinct().take(3)
            val byAuthor = authorNames.flatMap { name ->
                runCatching {
                    seriesApi.getSeriesList(
                        conditionBuilder = allOfSeries {
                            author { isEqualTo(KomgaSearchCondition.AuthorMatch(name, null)) }
                        },
                        fulltextSearch = null,
                        pageRequest = KomgaPageRequest(size = 20),
                    ).content
                }.getOrDefault(emptyList())
            }
            val excluded = excludedIds() + current.id.value
            (byName + byAuthor)
                .distinctBy { it.id.value }
                .filter { it.id.value !in excluded }
                .sortedByDescending { nameSimilarity(title, it.metadata.title) }
                .take(15)
        }.getOrDefault(emptyList())
    }

    private fun excludedIds(): Set<String> =
        (versions.map { it.id.value } + relations.values.flatten().map { it.id.value }).toSet()

    private fun significantQuery(title: String): String {
        val base = title.substringBefore(":").trim()
        val words = base.split(" ").filter { it.isNotBlank() }
        return if (words.size <= 2) base else words.take(2).joinToString(" ")
    }

    private fun tokenize(s: String): Set<String> =
        s.lowercase().split(Regex("[^a-z0-9]+")).filter { it.length > 1 }.toSet()

    private fun nameSimilarity(a: String, b: String): Double {
        val ta = tokenize(a)
        val tb = tokenize(b)
        if (ta.isEmpty() || tb.isEmpty()) return 0.0
        val intersection = ta.intersect(tb).size.toDouble()
        val union = (ta + tb).size.toDouble()
        return intersection / union
    }
}
