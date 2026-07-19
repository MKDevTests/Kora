package snd.komelia.perf

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.time.Clock

/**
 * Coarse-grained timing for the operations the user actually waits on.
 *
 * Everything goes through the single `KoraPerf` logger, so one grep collects a
 * whole session's measurements:
 *
 *     adb logcat | Select-String "KoraPerf"
 *
 * Deliberately INFO (present in release builds) and deliberately coarse: this
 * measures whole user-visible operations — opening a library, resolving a home
 * shelf — not individual calls. Per-page and per-event tracing is what filled
 * the log with 43MB of noise; do not add anything here that fires on every page
 * or every SSE event.
 */
object PerfTrace {
    private val logger = KotlinLogging.logger("KoraPerf")

    /**
     * Runs [block], logging how long it took and, when [count] is provided,
     * how many items came back — a slow call returning 3000 rows is a
     * different problem from a slow call returning 20.
     */
    suspend fun <T> measure(label: String, count: (T) -> Int? = { null }, block: suspend () -> T): T {
        val start = Clock.System.now()
        try {
            val result = block()
            val ms = (Clock.System.now() - start).inWholeMilliseconds
            val n = count(result)
            logger.info { if (n != null) "$label took ${ms}ms ($n items)" else "$label took ${ms}ms" }
            return result
        } catch (t: Throwable) {
            val ms = (Clock.System.now() - start).inWholeMilliseconds
            // Failures are timings too: a 30s failure is a timeout, a 20ms one
            // is a rejected request, and the distinction matters.
            logger.info { "$label FAILED after ${ms}ms (${t::class.simpleName})" }
            throw t
        }
    }
}
