package snd.komelia.ui.series

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
import snd.komelia.similarity.Feature
import snd.komelia.similarity.SimilarityEngine
import snd.komelia.similarity.SimilarityIndexBuilder
import snd.komelia.similarity.SimilarityIndexRepository
import snd.komelia.similarity.TermFamily
import snd.komelia.similarity.toIndexedSeries
import snd.komelia.ui.library.GenreLabels
import snd.komga.client.series.KomgaSeries
import snd.komga.client.series.KomgaSeriesId

private val logger = KotlinLogging.logger {}

/** One suggestion: the series to show, and why it was picked. */
data class SimilarSuggestion(
    val series: KomgaSeries,
    /** Already-readable labels ("Same author: Miura", "Fantasy"), strongest first. */
    val reasons: List<String>,
)

/**
 * State for the series "Similar" tab.
 *
 * Scoring is entirely local (see [SimilarityEngine]) and costs milliseconds; the
 * only expensive step is building the library's term index, about one request
 * per 100 series. So nothing happens until the tab is actually opened —
 * [onOpened] is the entry point, not an eager `initialize()` like the other tabs.
 * A library the user never asks suggestions for is never indexed.
 *
 * Already-read series are deliberately kept: the cards carry the read badge, and
 * dropping them would leave the tab empty on a well-read library. Hidden and
 * ignored series ARE filtered here, which is also why the index stores them —
 * hiding a series must not force a rebuild.
 */
class SimilarSeriesState(
    val series: StateFlow<KomgaSeries?>,
    private val notifications: AppNotifications,
    private val seriesApi: KomgaSeriesApi,
    private val repository: SimilarityIndexRepository,
    private val indexBuilder: SimilarityIndexBuilder?,
    /** Locally ignored + admin-hidden ids; read per load, never cached here. */
    private val excludedSeriesIds: Flow<Set<String>>,
    private val screenModelScope: CoroutineScope,
    val cardWidth: StateFlow<Dp>,
) {
    var suggestions by mutableStateOf<List<SimilarSuggestion>>(emptyList())
        private set

    /** 0f..1f while the index is being built, null the rest of the time. */
    var buildProgress by mutableStateOf<Float?>(null)
        private set

    /** Series held in the index for this library — shown next to the rebuild button. */
    var indexedCount by mutableStateOf(0)
        private set

    var isLoading by mutableStateOf(false)
        private set

    var failed by mutableStateOf(false)
        private set

    /**
     * Snapshot state rather than a LoadState flow, unlike the sibling tabs:
     * this content is emitted from the lazy grid's builder lambda, which cannot
     * call `collectAsState`.
     */
    private var started = false

    /** Called when the tab becomes visible. Idempotent. */
    fun onOpened() {
        if (started) return
        started = true
        screenModelScope.launch { load(forceRebuild = false) }
    }

    /** Explicit "rebuild the index" — the server is the authority, we are a cache. */
    fun rebuild() {
        screenModelScope.launch { load(forceRebuild = true) }
    }

    private suspend fun load(forceRebuild: Boolean) {
        notifications.runCatchingToNotifications {
            isLoading = true
            failed = false
            val current = series.filterNotNull().first()
            val libraryId = current.libraryId

            var entries = repository.entriesOf(libraryId.value)
            // Build when there is no index, when this series is missing from it
            // (added since the last build), or on explicit demand.
            val needsBuild = forceRebuild ||
                entries.isEmpty() ||
                entries.none { it.seriesId == current.id.value }
            if (needsBuild) {
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
            indexedCount = entries.size

            val engine = SimilarityEngine(entries.toIndexedSeries())
            val scored = engine.similarTo(
                seriesId = current.id.value,
                limit = MAX_SUGGESTIONS,
                // The index holds hidden/ignored series on purpose (so hiding one
                // doesn't force a rebuild); they are dropped here instead.
                exclude = excludedSeriesIds.first(),
            )
            suggestions = resolve(scored.map { it.seriesId to it.reasons })
            isLoading = false
        }.onFailure {
            logger.error(it) { "Similar-series tab failed" }
            isLoading = false
            failed = true
        }
    }

    /**
     * Ids -> series. Komga has no "series in this id list" condition (the same
     * wall the Favorites screen hit), so it is one lookup each, four in flight.
     * A lookup that fails drops its suggestion instead of the whole tab.
     */
    private suspend fun resolve(scored: List<Pair<String, List<Feature>>>): List<SimilarSuggestion> {
        if (scored.isEmpty()) return emptyList()
        val limit = Semaphore(4)
        return coroutineScope {
            scored.map { (id, reasons) ->
                async {
                    limit.withPermit {
                        try {
                            SimilarSuggestion(seriesApi.getOneSeries(KomgaSeriesId(id)), reasons.map { it.label() })
                        } catch (t: Throwable) {
                            currentCoroutineContext().ensureActive()
                            logger.debug { "Similar suggestion $id dropped: ${t::class.simpleName}" }
                            null
                        }
                    }
                }
            }.awaitAll().filterNotNull()
        }
    }
}

/**
 * Human-readable reason. The raw term is a scoring key — showing
 * "kora:tag:seinen" or a bare slug would read as a bug.
 */
private fun Feature.label(): String = when (family) {
    TermFamily.AUTHOR -> "Same author: $value"
    // Genre labels are the curated French ones the rest of the app already
    // shows (the taxonomy is hand-written in French); everything else is English
    // like the rest of the UI.
    TermFamily.GENRE -> GenreLabels.label(value)
    TermFamily.TAG -> value.removePrefix("kora:tag:").replaceFirstChar { it.uppercaseChar() }
    TermFamily.BOOK_TAG -> value.replaceFirstChar { it.uppercaseChar() }
    TermFamily.PUBLISHER -> "Publisher: ${value.replaceFirstChar { it.uppercaseChar() }}"
}

private const val MAX_SUGGESTIONS = 20
