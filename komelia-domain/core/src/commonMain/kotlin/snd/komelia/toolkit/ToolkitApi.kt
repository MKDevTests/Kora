package snd.komelia.toolkit

import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer

/** Where to reach Toolkit + the bearer token. Read fresh per call so a settings
 *  change (or the encrypted token becoming available) takes effect immediately. */
data class ToolkitConfig(val baseUrl: String, val token: String)

/** Outcome of a Toolkit call. Keeps HTTP failures typed so the UI can map the
 *  documented codes (401/404/409/503) to messages instead of a raw exception. */
sealed interface ToolkitResult<out T> {
    data class Success<T>(val value: T) : ToolkitResult<T>
    /** Base URL / token not set yet — the settings screen isn't filled in. */
    data object NotConfigured : ToolkitResult<Nothing>
    /** A documented HTTP error. [komgaReconnectNeeded] is the 401 "Connexion Komga requise" case. */
    data class HttpError(val code: Int, val body: String, val komgaReconnectNeeded: Boolean) : ToolkitResult<Nothing>
    /** Transport failure (host down, timeout, TLS). */
    data class NetworkError(val cause: Throwable) : ToolkitResult<Nothing>
}

/**
 * Thin client for the Komga Toolkit automation API. Reuses Kora's engine (proxy,
 * timeouts, TLS) via a derived client with expectSuccess disabled so 4xx/5xx are
 * inspected, not thrown. Parses with a lenient Json (the server sends fields Kora
 * ignores). Only ever talks to the user's own Toolkit on the LAN — no scraping.
 */
class ToolkitApi(
    baseClient: HttpClient,
    private val config: () -> ToolkitConfig?,
) {
    private val client = baseClient.config { expectSuccess = false }
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun status(): ToolkitResult<ToolkitStatus> =
        request { cfg -> client.get("${cfg.baseUrl}/api/automation/status") { auth(cfg) } }

    suspend fun preview(
        function: ToolkitFunction,
        source: ToolkitSource,
        libraryId: String,
    ): ToolkitResult<ToolkitJob> =
        requestJob { cfg ->
            client.post("${cfg.baseUrl}/api/automation/${function.slug}/${source.slug}/preview") {
                auth(cfg)
                contentType(ContentType.Application.Json)
                setBody("""{"library_id":"$libraryId"}""")
            }
        }

    /** Fire-and-apply: analyses, filters, revalidates AND writes in one job.
     *  Used by next-releases (no preview) and available for tracking too. */
    suspend fun run(
        function: ToolkitFunction,
        source: ToolkitSource,
        libraryId: String,
    ): ToolkitResult<ToolkitJob> =
        requestJob { cfg ->
            client.post("${cfg.baseUrl}/api/automation/${function.slug}/${source.slug}/run") {
                auth(cfg)
                contentType(ContentType.Application.Json)
                setBody("""{"library_id":"$libraryId"}""")
            }
        }

    suspend fun job(jobId: String): ToolkitResult<ToolkitJob> =
        requestJob { cfg -> client.get("${cfg.baseUrl}/api/automation/jobs/$jobId") { auth(cfg) } }

    suspend fun confirm(
        function: ToolkitFunction,
        source: ToolkitSource,
        previewJobId: String,
    ): ToolkitResult<ToolkitJob> =
        requestJob { cfg ->
            client.post("${cfg.baseUrl}/api/automation/${function.slug}/${source.slug}/confirm") {
                auth(cfg)
                contentType(ContentType.Application.Json)
                setBody("""{"preview_job_id":"$previewJobId","confirmed":true}""")
            }
        }

    suspend fun cancel(jobId: String): ToolkitResult<Unit> {
        val cfg = config() ?: return ToolkitResult.NotConfigured
        return try {
            val resp = client.post("${cfg.baseUrl}/api/automation/jobs/$jobId/cancel") { auth(cfg) }
            if (resp.status.value in 200..299) ToolkitResult.Success(Unit) else resp.toError()
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            ToolkitResult.NetworkError(t)
        }
    }

    private fun io.ktor.client.request.HttpRequestBuilder.auth(cfg: ToolkitConfig) = bearerAuth(cfg.token)

    /** Runs a call and decodes the body to [T] on 2xx, else maps the error. */
    private suspend inline fun <reified T> request(block: suspend (ToolkitConfig) -> HttpResponse): ToolkitResult<T> {
        val cfg = config() ?: return ToolkitResult.NotConfigured
        return try {
            val resp = block(cfg)
            if (resp.status.value in 200..299) {
                @Suppress("UNCHECKED_CAST")
                val ser = serializer<T>() as KSerializer<T>
                ToolkitResult.Success(json.decodeFromString(ser, resp.bodyAsText()))
            } else resp.toError()
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            ToolkitResult.NetworkError(t)
        }
    }

    /** Preview/confirm/job all return an envelope; unwrap to the [ToolkitJob]. */
    private suspend fun requestJob(block: suspend (ToolkitConfig) -> HttpResponse): ToolkitResult<ToolkitJob> =
        when (val r = request<ToolkitJobEnvelope>(block)) {
            is ToolkitResult.Success -> ToolkitResult.Success(r.value.job)
            is ToolkitResult.NotConfigured -> r
            is ToolkitResult.HttpError -> r
            is ToolkitResult.NetworkError -> r
        }

    private suspend fun HttpResponse.toError(): ToolkitResult.HttpError {
        val body = runCatching { bodyAsText() }.getOrDefault("")
        val reconnect = status.value == 401 && body.contains("Komga", ignoreCase = true)
        return ToolkitResult.HttpError(status.value, body, reconnect)
    }
}
