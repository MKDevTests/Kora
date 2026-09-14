package snd.komelia.failover

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import kotlin.time.Clock

private val logger = KotlinLogging.logger {}

/**
 * Asks a server address whether anything answers there.
 *
 * `GET /api/v1/libraries` with a short connect timeout; ANY HTTP answer, a
 * 401 included, proves the host is reachable — reachability is the question,
 * not the session. Shared by [ServerFailover] and the "test" button on the
 * server screen, so what the button says is what the failover would do.
 */
object ServerProbe {

    sealed interface Result {
        val millis: Long

        data class Reachable(val status: Int, override val millis: Long) : Result
        data class Unreachable(val reason: String, override val millis: Long) : Result
    }

    suspend fun probe(url: String): Result {
        val base = url.trim().trimEnd('/')
        val started = Clock.System.now().toEpochMilliseconds()
        val response = runCatching {
            client.get("$base/api/v1/libraries") { timeout { connectTimeoutMillis = CONNECT_TIMEOUT_MS } }
        }
        val millis = Clock.System.now().toEpochMilliseconds() - started
        val result = response.fold(
            onSuccess = { Result.Reachable(it.status.value, millis) },
            onFailure = { Result.Unreachable(it::class.simpleName ?: "failure", millis) },
        )
        logger.info { "probe $base -> $result" }
        return result
    }

    private const val CONNECT_TIMEOUT_MS = 5_000L

    /**
     * A plain client: no failover plugin (it must not fail over while
     * failing over), no expectSuccess (a 401 is an answer).
     */
    private val client by lazy {
        HttpClient {
            expectSuccess = false
            install(HttpTimeout) { requestTimeoutMillis = 10_000 }
        }
    }
}
