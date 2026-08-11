package snd.komelia

import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import snd.komelia.komga.api.KomgaApi
import snd.komelia.komga.api.KomgaLibraryApi
import snd.komga.client.book.KomgaBookId
import snd.komga.client.collection.KomgaCollectionId
import snd.komga.client.readlist.KomgaReadListId
import snd.komga.client.series.KomgaSeriesId
import snd.komga.client.sse.KomgaEvent
import snd.komga.client.sse.KomgaEvent.LibraryEvent
import snd.komga.client.sse.KomgaEvent.TaskQueueStatus
import snd.komga.client.sse.KomgaEvent.ThumbnailBookAdded
import snd.komga.client.sse.KomgaEvent.ThumbnailBookDeleted
import snd.komga.client.sse.KomgaEvent.ThumbnailCollectionEvent
import snd.komga.client.sse.KomgaEvent.ThumbnailReadListEvent
import snd.komga.client.sse.KomgaEvent.ThumbnailSeriesAdded
import snd.komga.client.sse.KomgaEvent.ThumbnailSeriesDeleted
import snd.komga.client.sse.KomgaSSESession
import kotlin.concurrent.Volatile

private val logger = KotlinLogging.logger {}

class ManagedKomgaEvents(
    komgaApi: Flow<KomgaApi>,
    private val libraryApi: Flow<KomgaLibraryApi>,
    private val komgaSharedState: KomgaAuthenticationState,

    private val memoryCache: MemoryCache?,
    private val diskCache: DiskCache?,
) {
    private val manageScope = CoroutineScope(Dispatchers.Default.limitedParallelism(1) + SupervisorJob())
    private val broadcastScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @Volatile
    private var session: KomgaSSESession? = null

    /**
     * Whether a live SSE session should exist right now. Follows the app being
     * in front of the user, but only after [BACKGROUND_GRACE_MS] — switching
     * apps for a few seconds must not tear the connection down and rebuild it,
     * which would cost more than leaving it open.
     */
    private val sseWanted = MutableStateFlow(true)

    init {
        manageScope.launch {
            AppForegroundState.isForeground.collectLatest { foreground ->
                if (foreground) {
                    sseWanted.value = true
                } else {
                    // collectLatest cancels this block if the app comes back
                    // before the grace period is up, so a quick app switch
                    // never reaches the assignment below.
                    delay(BACKGROUND_GRACE_MS)
                    sseWanted.value = false
                }
            }
        }

        komgaSharedState.authenticatedUser
            .combine(komgaApi) { user, komgaApi -> user to komgaApi }
            .combine(sseWanted) { (user, komgaApi), wanted -> Triple(user, komgaApi, wanted) }
            .onEach { (newUser, komgaApi, wanted) ->
                broadcastScope.coroutineContext.cancelChildren()
                session?.cancel()
                session = null

                try {
                    if (newUser != null && wanted) {
                        val newSession = komgaApi.createSSESession()
                        session = newSession
                        startBroadcast(newSession.incoming)
                    }
                } catch (e: Exception) {
                    logger.catching(e)
                    currentCoroutineContext().ensureActive()
                }
            }.launchIn(manageScope)
    }

    private companion object {
        /** How long the app must stay in the background before the SSE session is dropped. */
        const val BACKGROUND_GRACE_MS = 60_000L
    }

    private val _events = MutableSharedFlow<KomgaEvent>()
    val events: SharedFlow<KomgaEvent> = _events

    /** Last queue status forwarded — see the dedupe in [startBroadcast]. */
    private var lastTaskQueueStatus: TaskQueueStatus? = null

    private fun startBroadcast(events: Flow<KomgaEvent>) {
        events.onEach { event ->
            logger.debug { event }

            when (event) {
                // Komga re-sends the queue status every ~10s even when nothing
                // changed. Re-broadcasting it woke every subscriber in the app
                // (MainScreenViewModel, Home, offline) six times a minute for a
                // value that is almost always the same. Only forward changes —
                // the queue indicator sees the exact same sequence of values.
                is TaskQueueStatus -> {
                    if (event == lastTaskQueueStatus) return@onEach
                    lastTaskQueueStatus = event
                }

                is ThumbnailBookAdded -> removeBookThumbnailCache(event.bookId)
                is ThumbnailBookDeleted -> removeBookThumbnailCache(event.bookId)

                is ThumbnailSeriesAdded -> removeSeriesThumbnailCache(event.seriesId)
                is ThumbnailSeriesDeleted -> removeSeriesThumbnailCache(event.seriesId)

                is ThumbnailCollectionEvent -> removeCollectionThumbnailCache(event.collectionId)
                is ThumbnailReadListEvent -> removeReadListThumbnailCache(event.readListId)

                is LibraryEvent -> updateLibraries()

                else -> {}
            }

            _events.emit(event)
        }.launchIn(broadcastScope)
    }

    /**
     * Stops the manager before tearing the session down, so it cannot publish a
     * replacement while the current one is being closed. Without this, swapping
     * servers left the previous graph's manager alive on its own scope: still
     * collecting, still reconnecting, still holding a client.
     */
    suspend fun close() {
        manageScope.coroutineContext[Job]?.cancelAndJoin()
        session?.cancel()
        session = null
        broadcastScope.coroutineContext[Job]?.cancelAndJoin()
    }

    private fun updateLibraries() {
        manageScope.launch {
            komgaSharedState.updateLibraries(libraryApi.first().getLibraries())
        }
    }

    private fun removeSeriesThumbnailCache(seriesId: KomgaSeriesId) {
        removeMemCacheValues(seriesId.value)
        diskCache?.remove((seriesId.value))
    }

    private fun removeBookThumbnailCache(bookId: KomgaBookId) {
        removeMemCacheValues(bookId.value)
        diskCache?.remove((bookId.value))
    }

    private fun removeCollectionThumbnailCache(collectionId: KomgaCollectionId) {
        removeMemCacheValues(collectionId.value)
        diskCache?.remove((collectionId.value))
    }

    private fun removeReadListThumbnailCache(readListId: KomgaReadListId) {
        removeMemCacheValues(readListId.value)
        diskCache?.remove((readListId.value))

    }

    private fun removeMemCacheValues(key: String) {
        memoryCache?.remove(MemoryCache.Key(key))
        memoryCache?.remove(MemoryCache.Key(key, mapOf("scale" to "Fit")))
        memoryCache?.remove(MemoryCache.Key(key, mapOf("scale" to "Crop")))
        memoryCache?.remove(MemoryCache.Key(key, mapOf("scale" to "")))

    }
}