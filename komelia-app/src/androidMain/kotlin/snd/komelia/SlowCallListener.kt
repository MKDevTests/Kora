package snd.komelia

import io.github.oshai.kotlinlogging.KotlinLogging
import okhttp3.Call
import okhttp3.Connection
import okhttp3.EventListener
import okhttp3.Response
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy

private val logger = KotlinLogging.logger("KoraHttp")

/**
 * Splits a slow request into the three things it can be waiting on.
 *
 * A timing taken around the suspend call cannot tell them apart, and that is
 * exactly what stalled the series-screen investigation on 2026-08-21: the book
 * list was measured at 10 414ms in the app while the same query answered curl
 * in 1 154ms on the same server, seconds apart, with the app's own log silent
 * throughout. Three explanations fit that and only one is true:
 *
 *   queue   callStart -> connection acquired   waiting for a free slot or a
 *                                              new connection to be opened
 *   server  request sent -> response headers   the server thinking
 *   body    headers -> call end                download and parse
 *
 * Only calls over [SLOW_MILLIS] are logged. Per-request logging is what
 * produced a 43MB log the last time, so the quiet case stays quiet.
 */
private const val SLOW_MILLIS = 2_000L

class SlowCallListener : EventListener() {

    private var callStart = 0L
    private var connectionAcquired = 0L
    private var requestSent = 0L
    private var headersEnd = 0L

    override fun callStart(call: Call) {
        callStart = System.currentTimeMillis()
    }

    override fun connectionAcquired(call: Call, connection: Connection) {
        connectionAcquired = System.currentTimeMillis()
    }

    override fun requestHeadersEnd(call: Call, request: okhttp3.Request) {
        requestSent = System.currentTimeMillis()
    }

    override fun requestBodyEnd(call: Call, byteCount: Long) {
        requestSent = System.currentTimeMillis()
    }

    override fun responseHeadersEnd(call: Call, response: Response) {
        headersEnd = System.currentTimeMillis()
    }

    override fun callEnd(call: Call) = report(call, null)

    override fun callFailed(call: Call, ioe: IOException) = report(call, ioe)

    private fun report(call: Call, failure: IOException?) {
        val end = System.currentTimeMillis()
        val total = end - callStart
        if (total < SLOW_MILLIS && failure == null) return

        // A path is enough to tell the requests apart and keeps ids out of the
        // log; the query string carries the page size and sort, which matter.
        val url = call.request().url
        val queue = if (connectionAcquired > 0) connectionAcquired - callStart else -1
        val server = if (headersEnd > 0 && requestSent > 0) headersEnd - requestSent else -1
        val body = if (headersEnd > 0) end - headersEnd else -1

        logger.info {
            "${call.request().method} ${url.encodedPath}${url.query?.let { "?$it" } ?: ""} " +
                "total=${total}ms queue=${queue}ms server=${server}ms body=${body}ms" +
                (failure?.let { " FAILED ${it::class.simpleName}: ${it.message}" } ?: "")
        }
    }

    companion object Factory : EventListener.Factory {
        override fun create(call: Call): EventListener = SlowCallListener()
    }
}
