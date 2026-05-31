package snd.komelia.ui.settings.diagnostics

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Backing model for the Diagnostics v2 sections. Loads cache / offline /
 * background-task data from the platform [DiagnosticsDataSource] on entry and
 * after a cache clear. The v1 read-only rows (version / mode / server) stay in
 * the screen, read from composition Locals.
 */
class DiagnosticsViewModel(
    private val source: DiagnosticsDataSource,
) : ScreenModel {

    val isSupported: Boolean = source.isSupported

    private val _cache = MutableStateFlow<CacheUsage?>(null)
    val cache: StateFlow<CacheUsage?> = _cache.asStateFlow()

    private val _offline = MutableStateFlow<OfflineUsage?>(null)
    val offline: StateFlow<OfflineUsage?> = _offline.asStateFlow()

    private val _tasks = MutableStateFlow<List<BackgroundTask>>(emptyList())
    val tasks: StateFlow<List<BackgroundTask>> = _tasks.asStateFlow()

    private val _clearing = MutableStateFlow(false)
    val clearing: StateFlow<Boolean> = _clearing.asStateFlow()

    private val _logInfo = MutableStateFlow<LogInfo?>(null)
    val logInfo: StateFlow<LogInfo?> = _logInfo.asStateFlow()

    init {
        if (isSupported) refresh()
    }

    fun refresh() {
        screenModelScope.launch {
            _cache.value = source.cacheUsage()
            _offline.value = source.offlineUsage()
            _tasks.value = source.backgroundTasks()
            _logInfo.value = source.logInfo()
        }
    }

    /** Persist a new log size cap (applied on next app start) and refresh the readout. */
    fun setLogCap(cap: LogSizeCap) {
        source.setLogCap(cap)
        screenModelScope.launch { _logInfo.value = source.logInfo() }
    }

    /** Tail of the app logs for the in-app viewer dialog. */
    suspend fun readRecentLogs(): String = source.readRecentLogs()

    /** Redacted full log text for the SAF export. */
    suspend fun buildLogExport(): String = source.exportLogs()

    fun clearImageCache() {
        if (_clearing.value) return
        screenModelScope.launch {
            _clearing.value = true
            try {
                source.clearImageCache()
                _cache.value = source.cacheUsage()
            } finally {
                _clearing.value = false
            }
        }
    }
}
