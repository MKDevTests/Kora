package snd.komelia.ui.collection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import snd.komelia.AppNotifications
import snd.komelia.komga.api.KomgaCollectionsApi
import snd.komelia.komga.api.KomgaSeriesApi
import snd.komelia.ui.LoadState
import snd.komga.client.collection.KomgaCollection
import snd.komga.client.common.KomgaPageRequest
import snd.komga.client.series.KomgaSeries
import snd.komga.client.sse.KomgaEvent
import snd.komga.client.sse.KomgaEvent.CollectionEvent

class SeriesCollectionsState(
    val series: StateFlow<KomgaSeries?>,
    private val notifications: AppNotifications,
    private val seriesApi: KomgaSeriesApi,
    private val collectionApi: KomgaCollectionsApi,
    private val events: SharedFlow<KomgaEvent>,
    private val screenModelScope: CoroutineScope,
    val cardWidth: StateFlow<Dp>,
) {
    private val mutableState = MutableStateFlow<LoadState<Unit>>(LoadState.Uninitialized)
    val state = mutableState.asStateFlow()

    var collections by mutableStateOf<Map<KomgaCollection, List<KomgaSeries>>>(emptyMap())
        private set

    private val reloadEventsEnabled = MutableStateFlow(true)
    private val reloadJobsFlow = MutableSharedFlow<Unit>(1, 0, DROP_OLDEST)

    suspend fun initialize() {
        if (mutableState.value != LoadState.Uninitialized) return

        loadCollections()
        screenModelScope.launch { startKomgaEventListener() }

        reloadJobsFlow.onEach {
            reloadEventsEnabled.first { it }
            loadCollections()
        }.launchIn(screenModelScope)
    }

    fun stopKomgaEventHandler() {
        reloadEventsEnabled.value = false
    }

    fun startKomgaEventHandler() {
        reloadEventsEnabled.value = true
    }

    private suspend fun loadCollections() {
        notifications.runCatchingToNotifications {
            mutableState.value = LoadState.Loading
            val series = series.filterNotNull().first()
            // Only the cheap call runs here. It is the one the closed tab needs:
            // it decides whether the tab is shown at all. The members are the
            // expensive half — measured at 1764 ms for 270 series downloaded on
            // a screen where the tab was never opened — so they wait for
            // [ensureMembersLoaded].
            val collections = snd.komelia.perf.PerfTrace.measure(
                label = "series.collections.list",
                count = { it: List<KomgaCollection> -> it.size },
            ) { seriesApi.getAllCollectionsBySeries(series.id) }

            membersLoadedFor = null
            this.collections = collections.associateWith { emptyList() }
            mutableState.value = LoadState.Success(Unit)
        }.onFailure { mutableState.value = LoadState.Error(it) }
    }

    /** Collections whose members are in [collections], or null when none are. */
    private var membersLoadedFor: Set<KomgaCollection>? = null
    private val membersLock = Mutex()

    /**
     * Fills in the series of each collection. Called when the Collections tab
     * actually becomes visible — never on screen load, where it was the single
     * most expensive request of the series screen.
     *
     * Idempotent; a reload of the collection list resets it.
     */
    suspend fun ensureMembersLoaded() {
        val wanted = collections.keys
        if (wanted.isEmpty()) return
        membersLock.withLock {
            if (membersLoadedFor == wanted) return
            notifications.runCatchingToNotifications {
                collections = snd.komelia.perf.PerfTrace.measure(
                    label = "series.collections.members n=${wanted.size}",
                    count = { it: Map<KomgaCollection, List<KomgaSeries>> -> it.values.sumOf { s -> s.size } },
                ) {
                    wanted.associateWith { collection ->
                        collectionApi.getSeriesForCollection(
                            id = collection.id,
                            pageRequest = KomgaPageRequest(size = 500)
                        ).content
                    }
                }
                membersLoadedFor = wanted
            }
        }
    }

    private suspend fun startKomgaEventListener() {
        events.collect { event ->
            if (event is CollectionEvent && collections.keys.any { it.id == event.collectionId }) {
                reloadJobsFlow.tryEmit(Unit)
            }
        }
    }
}