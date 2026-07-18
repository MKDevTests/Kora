package snd.komelia.ui.nextreleases

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import snd.komga.client.library.KomgaLibrary

private val logger = KotlinLogging.logger {}

/**
 * Owns the upcoming-releases scan, outside of any composition.
 *
 * The scan used to be launched from a `LaunchedEffect` in the home card and
 * from the screen's model scope. Both are tied to a composition, so navigating
 * away cancelled the scan — and a scan is one Komga query per `nextrelease:`
 * tag, which on a well-tagged library takes long enough that it essentially
 * never survived to completion. The logs were full of
 * `JobCancellationException: Job was cancelled` and the calendar stayed empty.
 *
 * Here the scan runs in a process-scoped coroutine: leaving the screen no
 * longer kills it, and the result lands in [NextReleasesCache] whether or not
 * anyone is still watching. Surfaces observe [releases] instead of driving the
 * work themselves.
 */
object NextReleasesScanner {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    private val _releases = MutableStateFlow<List<NextReleasesService.UpcomingRelease>?>(null)

    /** Latest known list: null until the disk cache is read or a scan lands. */
    val releases: StateFlow<List<NextReleasesService.UpcomingRelease>?> = _releases.asStateFlow()

    /** True while a scan is running, so surfaces can show progress. */
    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    /** Seeds from the disk snapshot. Cheap and idempotent. */
    suspend fun primeFromDisk() {
        if (_releases.value != null) return
        NextReleasesCache.releases?.let { _releases.value = it; return }
        NextReleasesCache.loadPersisted()?.let {
            NextReleasesCache.releases = it
            _releases.value = it
        }
    }

    /**
     * Starts a scan unless one is already running, or a recent successful scan
     * makes it pointless. [force] bypasses the staleness check (the calendar
     * screen opening = an explicit ask for fresh data).
     */
    fun ensureFresh(
        service: NextReleasesService,
        libraries: List<KomgaLibrary>,
        force: Boolean = false,
    ) {
        if (libraries.isEmpty()) return
        if (job?.isActive == true) return
        if (!force && !NextReleasesCache.isStale()) return

        job = scope.launch {
            _scanning.value = true
            try {
                val scan = service.compute(libraries)
                NextReleasesCache.update(scan)
                // update() may refuse an empty result from an incomplete scan,
                // so publish what the cache actually holds.
                _releases.value = NextReleasesCache.releases
                if (!scan.complete) {
                    logger.warn { "Incomplete next-releases scan (${scan.releases.size} resolved)" }
                }
            } catch (t: Throwable) {
                logger.error(t) { "Next-releases scan failed" }
            } finally {
                _scanning.value = false
            }
        }
    }
}
