package snd.komelia.ui.settings.toolkit

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import snd.komelia.toolkit.ToolkitApi
import snd.komelia.toolkit.ToolkitFunction
import snd.komelia.toolkit.ToolkitResult
import snd.komelia.toolkit.ToolkitSource
import snd.komelia.toolkit.ToolkitStatus

/**
 * Backs the admin Toolkit screen. Owns the connectivity test; the automation
 * flow itself lives in the process-scoped [ToolkitJobRunner] so it survives
 * leaving the screen.
 */
class ToolkitViewModel(
    val api: ToolkitApi,
) : ScreenModel {

    sealed interface TestState {
        data object Idle : TestState
        data object Testing : TestState
        data class Ok(val status: ToolkitStatus) : TestState
        data class Error(val message: String) : TestState
    }

    private val _test = MutableStateFlow<TestState>(TestState.Idle)
    val test: StateFlow<TestState> = _test.asStateFlow()

    val flowState get() = ToolkitJobRunner.state

    fun testConnection() {
        _test.value = TestState.Testing
        screenModelScope.launch {
            _test.value = when (val r = api.status()) {
                is ToolkitResult.Success -> TestState.Ok(r.value)
                is ToolkitResult.NotConfigured -> TestState.Error("URL ou jeton manquant")
                is ToolkitResult.HttpError ->
                    TestState.Error(if (r.komgaReconnectNeeded) "Reconnecter Toolkit à Komga" else "Erreur ${r.code}")
                is ToolkitResult.NetworkError -> TestState.Error("Injoignable : ${r.cause.message}")
            }
        }
    }

    fun startPreview(function: ToolkitFunction, source: ToolkitSource, libraryId: String) =
        ToolkitJobRunner.startPreview(api, function, source, libraryId)

    fun startRun(function: ToolkitFunction, source: ToolkitSource, libraryId: String) =
        ToolkitJobRunner.startRun(api, function, source, libraryId)

    fun confirm() = ToolkitJobRunner.confirm(api)
    fun cancel() = ToolkitJobRunner.cancel(api)
    fun reset() = ToolkitJobRunner.reset()
}
