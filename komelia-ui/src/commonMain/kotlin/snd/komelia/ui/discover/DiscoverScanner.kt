package snd.komelia.ui.discover

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import snd.komelia.discover.DiscoverRepository
import snd.komelia.discover.DiscoverScanState
import snd.komga.client.library.KomgaLibraryId
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

private val logger = KotlinLogging.logger {}

/**
 * Owns the Discover pass, outside of any composition.
 *
 * Same reason as [snd.komelia.ui.nextreleases.NextReleasesScanner], learned the
 * hard way there: a scan launched from a `LaunchedEffect` or a screen model
 * scope dies when the user navigates away, and this pass is a minute of
 * requests — it would essentially never finish. Here it runs process-scoped and
 * lands in the database whether or not anyone is still watching.
 *
 * Never call this from a composition.
 */
object DiscoverScanner {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    private val _scanning = MutableStateFlow(false)

    /** True while a pass is running, so the tab can show progress. */
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    /** Bumped when a pass writes results, so open screens reload the table. */
    private val _generation = MutableStateFlow(0)
    val generation: StateFlow<Int> = _generation.asStateFlow()

    /**
     * Starts a pass unless one is running or the last one is recent enough.
     *
     * [force] is the tab's refresh button — an explicit ask beats the throttle.
     * Called on app foreground, never from a composition.
     */
    fun ensureFresh(
        service: DiscoverService,
        repository: DiscoverRepository,
        libraries: List<KomgaLibraryId>,
        force: Boolean = false,
    ) {
        if (libraries.isEmpty()) return
        if (job?.isActive == true) return

        job = scope.launch {
            val state = repository.scanState()
            val lastRun = state.lastRunAt
            if (!force && lastRun != null && Clock.System.now() - lastRun < REFRESH_INTERVAL) {
                logger.debug { "Discover: last pass was $lastRun, skipping" }
                return@launch
            }

            _scanning.value = true
            _progress.value = 0f
            try {
                val produced = service.scan(libraries) { _progress.value = it }
                repository.putScanState(DiscoverScanState(lastRunAt = Clock.System.now(), lastError = ""))
                _generation.value += 1
                logger.info { "Discover: pass finished, $produced suggestions" }
            } catch (t: Throwable) {
                currentCoroutineContext().ensureActive()
                logger.warn(t) { "Discover: pass failed" }
                // The timestamp is NOT updated on failure: a server that was
                // down at foreground time must be retried at the next one, not
                // in a week.
                repository.putScanState(
                    DiscoverScanState(lastRunAt = lastRun, lastError = t::class.simpleName.orEmpty())
                )
            } finally {
                _scanning.value = false
                _progress.value = 0f
            }
        }
    }
}

/** A week. Reader-voted recommendations do not move faster than that. */
private val REFRESH_INTERVAL = 7.days
