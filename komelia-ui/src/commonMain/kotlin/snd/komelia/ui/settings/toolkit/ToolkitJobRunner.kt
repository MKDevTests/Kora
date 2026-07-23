package snd.komelia.ui.settings.toolkit

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import snd.komelia.toolkit.ToolkitApi
import snd.komelia.toolkit.ToolkitFunction
import snd.komelia.toolkit.ToolkitJob
import snd.komelia.toolkit.ToolkitResult
import snd.komelia.toolkit.ToolkitSource
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

private val logger = KotlinLogging.logger("KoraToolkit")

/**
 * Drives one Toolkit automation flow (preview → confirm) in a PROCESS-scoped
 * coroutine so the user can leave the screen and come back — the scan can take
 * minutes and Kora must never block on it. Screens observe [state]; they don't
 * own the work.
 *
 * A single flow at a time: the user starts one function, reviews it, applies or
 * cancels. Starting a new one replaces the current.
 */
object ToolkitJobRunner {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pollJob: Job? = null

    private val _state = MutableStateFlow<ToolkitFlowState>(ToolkitFlowState.Idle)
    val state: StateFlow<ToolkitFlowState> = _state.asStateFlow()

    /** Launches a preview and polls it in the background. Returns immediately. */
    fun startPreview(api: ToolkitApi, function: ToolkitFunction, source: ToolkitSource, libraryId: String) {
        pollJob?.cancel()
        _state.value = ToolkitFlowState.Working(function, source, Phase.PREVIEW, 0, 0, "")
        pollJob = scope.launch {
            when (val started = api.preview(function, source, libraryId)) {
                is ToolkitResult.Success -> pollUntilDone(api, function, source, Phase.PREVIEW, started.value.id)
                else -> _state.value = errorState(function, source, started)
            }
        }
    }

    /**
     * Fire-and-apply (/run): analyses AND writes in one job. Used by
     * next-releases (no preview exists). The caller shows a confirmation dialog
     * BEFORE calling this — once launched there is nothing more to validate.
     */
    fun startRun(api: ToolkitApi, function: ToolkitFunction, source: ToolkitSource, libraryId: String) {
        pollJob?.cancel()
        _state.value = ToolkitFlowState.Working(function, source, Phase.APPLY, 0, 0, "")
        pollJob = scope.launch {
            when (val started = api.run(function, source, libraryId)) {
                is ToolkitResult.Success -> pollUntilDone(api, function, source, Phase.APPLY, started.value.id)
                else -> _state.value = errorState(function, source, started)
            }
        }
    }

    /** Confirms the current preview (must be in [ToolkitFlowState.PreviewReady]). */
    fun confirm(api: ToolkitApi) {
        val ready = _state.value as? ToolkitFlowState.PreviewReady ?: return
        pollJob?.cancel()
        _state.value = ToolkitFlowState.Working(ready.function, ready.source, Phase.APPLY, 0, 0, "")
        pollJob = scope.launch {
            when (val started = api.confirm(ready.function, ready.source, ready.previewJob.id)) {
                is ToolkitResult.Success -> pollUntilDone(api, ready.function, ready.source, Phase.APPLY, started.value.id)
                else -> _state.value = errorState(ready.function, ready.source, started)
            }
        }
    }

    /** Cancels the running job on the server and resets. Best-effort. */
    fun cancel(api: ToolkitApi) {
        val current = _state.value
        val jobId = (current as? ToolkitFlowState.Working)?.jobId
        pollJob?.cancel()
        pollJob = null
        if (jobId != null) scope.launch { runCatching { api.cancel(jobId) } }
        _state.value = ToolkitFlowState.Idle
    }

    /** Dismisses a terminal state back to Idle (leaving the summary screen). */
    fun reset() {
        pollJob?.cancel()
        pollJob = null
        _state.value = ToolkitFlowState.Idle
    }

    private suspend fun pollUntilDone(
        api: ToolkitApi,
        function: ToolkitFunction,
        source: ToolkitSource,
        phase: Phase,
        jobId: String,
    ) {
        val deadline = Clock.System.now() + MAX_WAIT
        while (Clock.System.now() < deadline) {
            when (val r = api.job(jobId)) {
                is ToolkitResult.Success -> {
                    val job = r.value
                    if (job.isTerminal) {
                        _state.value = terminalState(function, source, phase, job)
                        return
                    }
                    _state.value = ToolkitFlowState.Working(function, source, phase, job.current, job.total, job.message, jobId)
                }
                // A transient error mid-poll isn't fatal: keep the working state
                // and try again until the deadline (home-server wifi drops).
                is ToolkitResult.NetworkError -> logger.warn { "poll transient: ${r.cause.message}" }
                else -> {
                    _state.value = errorState(function, source, r)
                    return
                }
            }
            delay(POLL_INTERVAL)
        }
        _state.value = ToolkitFlowState.Failed(function, source, "Délai dépassé (${MAX_WAIT.inWholeMinutes} min)")
    }

    private fun terminalState(function: ToolkitFunction, source: ToolkitSource, phase: Phase, job: ToolkitJob): ToolkitFlowState =
        when {
            !job.isCompleted -> ToolkitFlowState.Failed(function, source, job.error.ifBlank { "Échec de la tâche" })
            phase == Phase.PREVIEW -> ToolkitFlowState.PreviewReady(function, source, job)
            else -> ToolkitFlowState.Applied(function, source, job)
        }

    private fun errorState(function: ToolkitFunction, source: ToolkitSource, r: ToolkitResult<*>): ToolkitFlowState =
        when (r) {
            is ToolkitResult.NotConfigured -> ToolkitFlowState.Failed(function, source, "Toolkit non configuré")
            is ToolkitResult.HttpError -> ToolkitFlowState.Failed(
                function, source,
                if (r.komgaReconnectNeeded) "Reconnecter Toolkit à Komga"
                else "Erreur ${r.code}${if (r.body.isNotBlank()) " : ${r.body.take(200)}" else ""}",
            )
            is ToolkitResult.NetworkError -> ToolkitFlowState.Failed(function, source, "Réseau : ${r.cause.message}")
            is ToolkitResult.Success -> ToolkitFlowState.Idle // unreachable
        }

    private val POLL_INTERVAL = 1500.milliseconds
    // Toolkit scans can exceed 30 min on a large library; give generous headroom.
    private val MAX_WAIT = 45.minutes
}

/** Which step of the two-step flow a job belongs to. */
enum class Phase { PREVIEW, APPLY }

sealed interface ToolkitFlowState {
    data object Idle : ToolkitFlowState

    /** A job is queued/running; drives the progress screen. */
    data class Working(
        val function: ToolkitFunction,
        val source: ToolkitSource,
        val phase: Phase,
        val current: Int,
        val total: Int,
        val message: String,
        val jobId: String? = null,
    ) : ToolkitFlowState

    /** Preview finished; [previewJob].result holds counters + rows to review. */
    data class PreviewReady(
        val function: ToolkitFunction,
        val source: ToolkitSource,
        val previewJob: ToolkitJob,
    ) : ToolkitFlowState

    /** Apply finished; [applyJob].applyResult() holds the write summary. */
    data class Applied(
        val function: ToolkitFunction,
        val source: ToolkitSource,
        val applyJob: ToolkitJob,
    ) : ToolkitFlowState

    data class Failed(
        val function: ToolkitFunction,
        val source: ToolkitSource,
        val message: String,
    ) : ToolkitFlowState
}
