package snd.komelia.api

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import snd.komelia.NetworkState
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import snd.komelia.komga.api.KomgaApi
import snd.komga.client.KomgaClientFactory
import snd.komga.client.sse.KomgaEvent
import snd.komga.client.sse.KomgaSSESession
import kotlin.random.Random

data class RemoteApi(
    override val actuatorApi: RemoteActuatorApi,
    override val announcementsApi: RemoteAnnouncementsApi,
    override val bookApi: RemoteBookApi,
    override val collectionsApi: RemoteCollectionsApi,
    override val fileSystemApi: RemoteFileSystemApi,
    override val libraryApi: RemoteLibraryApi,
    override val readListApi: RemoteReadListApi,
    override val referentialApi: RemoteReferentialApi,
    override val seriesApi: RemoteSeriesApi,
    override val settingsApi: RemoteSettingsApi,
    override val tasksApi: RemoteTaskApi,
    override val userApi: RemoteUserApi,
    private val komgaClientFactory: KomgaClientFactory,
    private val offlineEvents: SharedFlow<KomgaEvent>
) : KomgaApi {
    override suspend fun createSSESession(): KomgaSSESession {
        return CombinedSSESession(komgaClientFactory, offlineEvents)
    }

    private class CombinedSSESession(
        private val komgaClientFactory: KomgaClientFactory,
        offlineEvents: SharedFlow<KomgaEvent>,
    ) : KomgaSSESession {
        override val incoming: MutableSharedFlow<KomgaEvent> = MutableSharedFlow()
        private val logger = KotlinLogging.logger { }
        private val coroutineScope = CoroutineScope(
            Dispatchers.Default + SupervisorJob() +
                    CoroutineExceptionHandler { _, exception -> logger.catching(exception) })


        init {
            // it might take a long time for the sse connection to be established
            // and for the server to respond with at least single event so that ktor could transform response body to sse session
            // launch the connection in separate coroutine to prevent blocking offline events
            coroutineScope.launch {
                var attempt = 0

                while (currentCoroutineContext().isActive) {
                    var session: KomgaSSESession? = null
                    var droppedOnComeback = false
                    try {
                        session = komgaClientFactory.sseSession()
                        val live = session
                        // A stream opened on a link that has since died does
                        // not fail by itself: it sits on the socket until the
                        // 60 s timeout ("total=299571ms … Socket closed" in
                        // the logs). A network comeback or an address switch
                        // drops it here and reopens at once on the new route.
                        val comebacksAtOpen = NetworkState.comebacks.value
                        coroutineScope {
                            val collecting = launch {
                                live.incoming.collect { event ->
                                    // Receiving an event proves the connection works, so a
                                    // later drop retries promptly instead of inheriting the
                                    // backoff from whatever went wrong before it.
                                    attempt = 0
                                    incoming.emit(event)
                                }
                            }
                            val watcher = launch {
                                NetworkState.comebacks.first { it != comebacksAtOpen }
                                logger.info { "SSE: network or server address changed, dropping the stream" }
                                droppedOnComeback = true
                                collecting.cancel()
                            }
                            collecting.join()
                            watcher.cancel()
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        logger.warn(e) { "SSE connection ended, reconnecting" }
                    } finally {
                        session?.cancel()
                    }
                    if (droppedOnComeback) {
                        attempt = 0
                        continue
                    }

                    val delayMillis = reconnectDelayMillis(attempt)
                    logger.info { "Reconnecting SSE in ${delayMillis}ms (attempt ${attempt + 1})" }
                    // A network coming back, or the active address changing
                    // (failover), is a new situation: reconnect at once and
                    // from a clean backoff, rather than sit out up to two
                    // minutes earned against a link that no longer exists.
                    val comebacks = NetworkState.comebacks.value
                    val wokenEarly = withTimeoutOrNull(delayMillis) {
                        NetworkState.comebacks.first { it != comebacks }
                    } != null
                    if (wokenEarly) {
                        logger.info { "SSE: network or server address changed, reconnecting now" }
                        attempt = 0
                    } else {
                        attempt++
                    }
                }
            }

            coroutineScope.launch {
                offlineEvents.collect { incoming.emit(it) }
            }
        }


        /**
         * Exponential backoff with jitter, one second to two minutes.
         *
         * The old loop retried every ten seconds forever. With a server that is
         * simply off, that is six connection attempts a minute for as long as
         * the app is open — each one waking the radio.
         *
         * Deliberately no special case for 401/403: Komga's session cookie can
         * expire while the app runs, and the client re-authenticates on the next
         * request. Giving up on an auth failure would leave the stream dead until
         * the app restarted, with nothing on screen to say so. Backing off to two
         * minutes is cheap enough to just keep trying.
         */
        private fun reconnectDelayMillis(attempt: Int): Long {
            val exponent = attempt.coerceIn(0, MAX_BACKOFF_EXPONENT)
            val base = (INITIAL_RECONNECT_DELAY_MS shl exponent).coerceAtMost(MAX_RECONNECT_DELAY_MS)
            val maxJitter = minOf(base / 4, MAX_RECONNECT_DELAY_MS - base)
            return base + Random.nextLong(maxJitter + 1)
        }

        override fun cancel() {
            // sse session should have this scope as its coroutine context
            coroutineScope.cancel()
        }

        private companion object {
            const val INITIAL_RECONNECT_DELAY_MS = 1_000L
            const val MAX_RECONNECT_DELAY_MS = 120_000L
            const val MAX_BACKOFF_EXPONENT = 7
        }
    }
}
