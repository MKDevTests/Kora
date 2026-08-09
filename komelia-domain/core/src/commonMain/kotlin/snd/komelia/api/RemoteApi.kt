package snd.komelia.api

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
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
                    try {
                        session = komgaClientFactory.sseSession()
                        session.incoming.collect {
                            // A received event proves the connection is healthy. A later
                            // disconnect should retry promptly instead of inheriting an old delay.
                            attempt = 0
                            incoming.emit(it)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: ClientRequestException) {
                        if (e.response.status == HttpStatusCode.Unauthorized ||
                            e.response.status == HttpStatusCode.Forbidden
                        ) {
                            logger.warn { "SSE authentication rejected (${e.response.status}); waiting for new credentials" }
                            return@launch
                        }
                        logger.warn(e) { "SSE request failed (${e.response.status})" }
                    } catch (e: Exception) {
                        logger.warn(e) { "SSE connection ended unexpectedly" }
                    } finally {
                        session?.cancel()
                    }

                    val reconnectDelay = reconnectDelayMillis(attempt)
                    logger.info { "Reconnecting SSE in ${reconnectDelay}ms (attempt ${attempt + 1})" }
                    delay(reconnectDelay)
                    attempt++
                }
            }

            coroutineScope.launch {
                offlineEvents.collect { incoming.emit(it) }
            }
        }

        private fun reconnectDelayMillis(attempt: Int): Long {
            val exponent = attempt.coerceIn(0, MAX_BACKOFF_EXPONENT)
            val baseDelay = (INITIAL_RECONNECT_DELAY_MS shl exponent)
                .coerceAtMost(MAX_RECONNECT_DELAY_MS)
            val maxJitter = minOf(baseDelay / 4, MAX_RECONNECT_DELAY_MS - baseDelay)
            return baseDelay + Random.nextLong(maxJitter + 1)
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
