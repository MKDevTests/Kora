package snd.komelia

import io.github.oshai.kotlinlogging.KotlinLogging
import okhttp3.Interceptor
import okhttp3.Response
import java.io.File
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger("perf.budget")

/**
 * One ceiling on how many *query* requests Kora will have in flight at once,
 * across the whole app.
 *
 * Why this exists. Measured on the tablet against an idle Komga, the same
 * request costs wildly different amounts depending only on what else we sent
 * alongside it:
 *
 * ```
 * POST /api/v1/books/list?size=20&sort=readProgress.readDate,desc
 *     in a wave of four:  total=5197ms queue=2ms server=5197ms
 *     seconds later, alone: total=1191ms queue=2ms server=1158ms
 * ```
 *
 * `queue` is near zero in both, so nothing is waiting for a slot on our side:
 * all four are at the server at once, and the server hands each of them a
 * quarter of a much worse deal. Twenty-one uncoordinated `Semaphore(4)` in the
 * codebase each bound their own fan-out; none of them knows about the others,
 * and the only global bound is OkHttp's eight-per-host.
 *
 * The bet this makes is that serialising is better on both axes — the first
 * shelf appears at ~1.2 s instead of ~5.2 s, and the last one no later than it
 * does today. That bet is unproven at two data points, which is why the limit
 * is readable at runtime (see [readLimit]) rather than compiled in: it is a
 * measuring instrument first.
 *
 * Two safety properties, because a global gate is exactly the shape of thing
 * that deadlocks an app:
 *
 *  - It is a **whitelist**. Only the endpoints measured as expensive are held.
 *    Page images, thumbnails, and anything not named here — an event stream
 *    above all — pass straight through, so turning a page never waits behind a
 *    shelf and no long-lived response can hold a permit.
 *  - It never blocks forever. If a permit does not arrive within
 *    [MAX_WAIT_SECONDS] the request proceeds anyway, which is today's
 *    behaviour. The worst case is that the gate does nothing.
 *
 * The wait lands in the `queue` bucket of [SlowCallListener], because
 * application interceptors run before the connection is acquired. So
 * `server=` becomes the server's real cost and `queue=` becomes ours.
 */
class RequestBudgetInterceptor(permits: Int) : Interceptor {
    private val gate = Semaphore(permits, true)

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!isHeldBack(request.url.encodedPath)) return chain.proceed(request)

        val acquired = gate.tryAcquire(MAX_WAIT_SECONDS, TimeUnit.SECONDS)
        if (!acquired) {
            logger.warn { "budget wait expired, proceeding ungated: ${request.url.encodedPath}" }
            return chain.proceed(request)
        }
        try {
            return chain.proceed(request)
        } finally {
            gate.release()
        }
    }

    private fun isHeldBack(path: String): Boolean = HELD_BACK.any { path.endsWith(it) }

    companion object {
        /**
         * The endpoints measured at seconds each, and only those. Suffix
         * matches, so `/api/v1/books/{id}/pages/7` and every `/thumbnail`
         * fall outside by construction.
         */
        private val HELD_BACK = listOf(
            "/books/list",
            "/series/list",
            "/books/ondeck",
            "/series/new",
            "/series/updated",
            "/authors",
            "/tags",
            "/tags/series",
            "/genres",
            "/publishers",
            "/languages",
            "/age-ratings",
            "/series/release-dates",
            "/collections",
            "/readlists",
        )

        private const val MAX_WAIT_SECONDS = 30L

        /** Today's behaviour: OkHttp already caps us at eight per host. */
        const val DEFAULT_PERMITS = 8

        /**
         * Reads the ceiling from `filesDir/perf_gate.txt` so one build can be
         * swept across values without a UI:
         *
         * ```
         * adb shell "run-as io.github.mkdevtests.kora.debug sh -c 'echo 2 > files/perf_gate.txt'"
         * ```
         *
         * Absent, unreadable or out of range falls back to [DEFAULT_PERMITS].
         * Delete the file and the knob to hardcode the answer once it is known.
         */
        fun readLimit(filesDir: File): Int {
            val value = runCatching { File(filesDir, "perf_gate.txt").readText().trim().toInt() }.getOrNull()
            return if (value != null && value in 1..64) value else DEFAULT_PERMITS
        }
    }
}
