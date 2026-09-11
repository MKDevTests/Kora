package snd.komelia.ui.discover

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import snd.komelia.discover.DiscoverRepository
import snd.komelia.discover.DiscoverSuggestion
import snd.komelia.komga.api.KomgaSeriesApi
import snd.komelia.settings.CommonSettingsRepository
import snd.komelia.ui.LoadState
import snd.komga.client.library.KomgaLibraryId
import snd.komga.client.series.KomgaSeriesId

private val logger = KotlinLogging.logger {}

/**
 * The Discover tab's state.
 *
 * It reads the results table and never talks to MangaUpdates: the pass that
 * fills that table belongs to [DiscoverScanner], which runs process-scoped and
 * at most once a week. The only thing this model can trigger is a forced pass,
 * and only because the user pressed refresh.
 */
class DiscoverViewModel(
    private val repository: DiscoverRepository,
    private val service: DiscoverService,
    private val seriesApi: KomgaSeriesApi,
    private val settingsRepository: CommonSettingsRepository,
) : StateScreenModel<LoadState<List<DiscoverSuggestion>>>(LoadState.Uninitialized) {

    /** Local series id -> title, for the "because you read X" line. */
    private val _sourceNames = MutableStateFlow<Map<String, String>>(emptyMap())
    val sourceNames: StateFlow<Map<String, String>> = _sourceNames.asStateFlow()

    val scanning: StateFlow<Boolean> = DiscoverScanner.scanning
    val progress: StateFlow<Float> = DiscoverScanner.progress

    /**
     * Hide suggestions with no English edition. Persisted, off by default: a
     * French edition is unknowable from the source, so this filter can and
     * does hide series he could read -- it is his call to turn on.
     */
    private val _hideUnlicensed = MutableStateFlow(false)
    val hideUnlicensed: StateFlow<Boolean> = _hideUnlicensed.asStateFlow()

    fun setHideUnlicensed(hide: Boolean) {
        _hideUnlicensed.value = hide
        screenModelScope.launch { settingsRepository.putDiscoverHideUnlicensed(hide) }
    }

    /** Cards the user kept, shown by the "Intéressé" tab of the same screen. */
    private val _interested = MutableStateFlow<List<DiscoverSuggestion>>(emptyList())
    val interested: StateFlow<List<DiscoverSuggestion>> = _interested.asStateFlow()

    fun initialize(libraries: List<KomgaLibraryId>) {
        if (state.value !is LoadState.Uninitialized) return
        screenModelScope.launch { _hideUnlicensed.value = settingsRepository.getDiscoverHideUnlicensed().first() }
        load()
        // A pass landing while the tab is open must show up without the user
        // having to leave and come back.
        screenModelScope.launch {
            DiscoverScanner.generation.drop(1).collect { load() }
        }
        DiscoverScanner.ensureFresh(service, repository, libraries)
    }

    fun refresh(libraries: List<KomgaLibraryId>) {
        DiscoverScanner.ensureFresh(service, repository, libraries, force = true)
    }

    fun dismiss(suggestion: DiscoverSuggestion) {
        screenModelScope.launch {
            repository.dismiss(suggestion.externalId)
            removeFromList(suggestion.externalId)
            _interested.value = _interested.value.filterNot { it.externalId == suggestion.externalId }
        }
    }

    /**
     * Keeps a suggestion, or puts it back among the others.
     *
     * The list it leaves is updated in place rather than reloaded: the gesture
     * must feel immediate, and a reload would also re-run the source-name
     * lookups for every card still on screen.
     */
    fun setInterested(suggestion: DiscoverSuggestion, interested: Boolean) {
        screenModelScope.launch {
            repository.setInterested(suggestion.externalId, interested)
            if (interested) {
                removeFromList(suggestion.externalId)
                _interested.value = listOf(suggestion.copy(interested = true)) + _interested.value
            } else {
                _interested.value = _interested.value.filterNot { it.externalId == suggestion.externalId }
                load()
            }
        }
    }

    private fun removeFromList(externalId: String) {
        val current = (state.value as? LoadState.Success)?.value ?: return
        mutableState.value = LoadState.Success(current.filterNot { it.externalId == externalId })
    }

    private fun load() {
        screenModelScope.launch {
            if (state.value is LoadState.Uninitialized) mutableState.value = LoadState.Loading
            try {
                val suggestions = repository.topSuggestions(PAGE_SIZE)
                val kept = repository.interestedSuggestions()
                mutableState.value = LoadState.Success(suggestions)
                _interested.value = kept
                resolveSourceNames(suggestions + kept)
            } catch (t: Throwable) {
                currentCoroutineContext().ensureActive()
                mutableState.value = LoadState.Error(t)
            }
        }
    }

    /**
     * Names the local series the visible suggestions were attributed to.
     *
     * Ids are what gets stored — a Komga rename must not leave a stale title on
     * a card — so the names are looked up here, once per distinct id and capped:
     * this is one Komga request each, and an uncapped list would be a burst.
     */
    private suspend fun resolveSourceNames(suggestions: List<DiscoverSuggestion>) {
        val ids = suggestions
            .flatMap { it.becauseOf }
            .distinct()
            .filterNot { it in _sourceNames.value }
            .take(MAX_RESOLVED_SOURCES)
        if (ids.isEmpty()) return

        val gate = Semaphore(4)
        val resolved = coroutineScope {
            ids.map { id ->
                async {
                    gate.withPermit {
                        try {
                            id to seriesApi.getOneSeries(KomgaSeriesId(id)).metadata.title
                        } catch (t: Throwable) {
                            currentCoroutineContext().ensureActive()
                            logger.debug { "Discover: no name for $id: ${t::class.simpleName}" }
                            null
                        }
                    }
                }
            }.awaitAll().filterNotNull()
        }
        _sourceNames.value = _sourceNames.value + resolved
    }
}

private const val PAGE_SIZE = 60

/** A card names one source; beyond this many the lookups stop being free. */
private const val MAX_RESOLVED_SOURCES = 20
