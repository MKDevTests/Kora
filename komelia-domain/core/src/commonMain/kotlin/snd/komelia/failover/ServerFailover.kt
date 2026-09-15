package snd.komelia.failover

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.plugins.api.Send
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import snd.komelia.AppNotification
import snd.komelia.AppNotifications
import snd.komelia.NetworkState
import snd.komelia.isTransientNetworkFailure
import snd.komelia.settings.CommonSettingsRepository
import kotlin.concurrent.Volatile
import kotlin.time.Clock

private val logger = KotlinLogging.logger {}

/**
 * Switches the active server address to one of the alternates when the
 * active one stops answering at the network level.
 *
 * The case, measured on the tablet on 2026-09-13: when the Wi-Fi drops, the
 * VPN becomes the default route (`from /100.92.4.x` in the logs) and every
 * request still goes to the LAN address, which that route cannot reach. The
 * user has both addresses on file already, as "alternate URLs" — switching
 * between them was a manual trip to the settings.
 *
 * How it works: [plugin] is installed in the HTTP client's configuration,
 * so every client derived from it (the Komga client is a `config {}` copy)
 * carries it. A request to the active host that fails on the network path
 * ([isTransientNetworkFailure]) while the device does have a network
 * triggers one probe pass: each alternate is asked for `/api/v1/libraries`
 * with a short timeout, and ANY HTTP answer, 401 included, proves the host
 * is reachable. The first one that answers becomes the active address (the
 * old one joins the alternates, so the way back is the same mechanism), the
 * session cookies are carried over, the failed request is re-sent there, and
 * a notification says so. The client reads the base URL per request, so
 * nothing is rebuilt and the reader stays open.
 *
 * Symmetric on purpose: when home again, the VPN address may keep working
 * and the app stays on it until it fails. That costs nothing noticeable on
 * a LAN-to-LAN VPN, and it keeps the rule to one sentence.
 *
 * Bounded: one pass at a time, at most one every [PROBE_COOLDOWN_MS]. Inert
 * until [configure] has run — the client is built before the settings are.
 */
class ServerFailover {

    class Config(
        val settings: CommonSettingsRepository,
        val activeUrl: StateFlow<String>,
        val notifications: AppNotifications,
        /** Cookies follow the switch: the session is server-side, not host-side. */
        val onSwitched: suspend (from: Url, to: Url) -> Unit,
        /** What "switched to" reads as in the notification. */
        val switchedMessage: (String) -> String,
    )

    @Volatile
    private var config: Config? = null
    private val passMutex = Mutex()
    private var lastPassAtMillis = 0L

    fun configure(config: Config) {
        this.config = config
    }

    val plugin = createClientPlugin("ServerFailover") {
        on(Send) { request ->
            try {
                proceed(request)
            } catch (e: Throwable) {
                val newBase = onFailure(request, e) ?: throw e
                request.rebase(newBase)
                proceed(request)
            }
        }
    }

    /**
     * Returns the new base URL if a switch happened for this failure, else
     * null and the caller rethrows.
     */
    private suspend fun onFailure(request: HttpRequestBuilder, e: Throwable): Url? {
        val config = config ?: return null
        if (!isTransientNetworkFailure(e)) return null
        if (!NetworkState.isAvailable.value) return null
        val active = runCatching { Url(config.activeUrl.value) }.getOrNull() ?: return null
        if (!request.url.build().sameHostAs(active)) return null

        return passMutex.withLock {
            // Another request may have switched while this one waited.
            val nowActive = runCatching { Url(config.activeUrl.value) }.getOrNull() ?: return null
            if (!nowActive.sameHostAs(active)) return nowActive

            val now = Clock.System.now().toEpochMilliseconds()
            if (now - lastPassAtMillis < PROBE_COOLDOWN_MS) return null
            lastPassAtMillis = now

            val from = config.activeUrl.value.trim().trimEnd('/')
            val alternates = config.settings.getAlternateServerUrls().first()
                .map { it.trim().trimEnd('/') }
                .filter { it.isNotBlank() && it != from }
            if (alternates.isEmpty()) return null

            val reachable = alternates.firstOrNull { probe(it) } ?: run {
                logger.warn { "failover: ${active.host} unreachable, none of ${alternates.size} alternate(s) answered" }
                return null
            }
            config.settings.putServerUrl(reachable)
            config.settings.putAlternateServerUrls((alternates - reachable + from).distinct())
            runCatching { config.onSwitched(Url(from), Url(reachable)) }
                .onFailure { logger.warn(it) { "failover: cookies not carried over" } }
            logger.warn { "failover: $from unreachable, switched to $reachable" }
            // Counts as a network comeback: failed pages, the pending read
            // progress, the error screens and the event stream all wait on
            // it, and a new address is exactly the moment to ask again.
            NetworkState.networkArrived()
            config.notifications.add(AppNotification.Normal(config.switchedMessage(reachable)))
            Url(reachable)
        }
    }

    private suspend fun probe(url: String): Boolean =
        ServerProbe.probe(url) is ServerProbe.Result.Reachable

    private fun HttpRequestBuilder.rebase(base: Url) {
        url.protocol = URLProtocol.createOrDefault(base.protocol.name)
        url.host = base.host
        url.port = base.port
    }

    private fun Url.sameHostAs(other: Url): Boolean =
        host.equals(other.host, ignoreCase = true) && port == other.port

    private companion object {
        const val PROBE_COOLDOWN_MS = 30_000L
    }
}
