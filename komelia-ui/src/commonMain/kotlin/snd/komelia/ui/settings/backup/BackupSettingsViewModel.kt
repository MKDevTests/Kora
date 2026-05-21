package snd.komelia.ui.settings.backup

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import io.github.vinceglb.filekit.PlatformFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import snd.komelia.backup.BackupBundle
import snd.komelia.backup.BackupService
import snd.komelia.backup.ImportResult
import snd.komelia.settings.CommonSettingsRepository
import snd.komelia.settings.model.AutobackupFrequency
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * State machine for the Backup & Restore settings screen. The screen flow is:
 *  - Idle -> ExportReady (file save dialog opens) -> Done
 *  - Idle -> PreImportConfirm -> (file pick) -> ImportPreview -> Importing -> Done
 * Any failure transitions to [BackupUiState.Error], which the user dismisses
 * back to [BackupUiState.Idle].
 *
 * The autobackup section is independent of the export/import state machine —
 * it simply reflects [CommonSettingsRepository] and forwards toggle/folder
 * changes back to it. Side effects (folder picker, "Backup now") are wired
 * via the [runAutobackupNow] and [extractFolderUri] callbacks supplied by
 * the platform-specific module.
 */
class BackupSettingsViewModel(
    private val backupService: BackupService,
    private val settingsRepository: CommonSettingsRepository,
    private val runAutobackupNow: () -> Unit,
    private val extractFolderUri: (PlatformFile) -> String?,
) : ScreenModel {

    private val _state = MutableStateFlow<BackupUiState>(BackupUiState.Idle)
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    private val parseJson = Json { ignoreUnknownKeys = true; classDiscriminator = "type" }

    val autobackupEnabled: StateFlow<Boolean> = settingsRepository.getAutobackupEnabled()
        .stateIn(screenModelScope, SharingStarted.Eagerly, false)
    val autobackupFolderUri: StateFlow<String?> = settingsRepository.getAutobackupFolderUri()
        .stateIn(screenModelScope, SharingStarted.Eagerly, null)
    val autobackupFrequency: StateFlow<AutobackupFrequency> = settingsRepository.getAutobackupFrequency()
        .stateIn(screenModelScope, SharingStarted.Eagerly, AutobackupFrequency.DAILY)
    val autobackupMaxKeep: StateFlow<Int> = settingsRepository.getAutobackupMaxKeep()
        .stateIn(screenModelScope, SharingStarted.Eagerly, 3)
    val autobackupLastSuccessAt: StateFlow<Instant?> = settingsRepository.getAutobackupLastSuccessAt()
        .stateIn(screenModelScope, SharingStarted.Eagerly, null)
    val autobackupLastFailureAt: StateFlow<Instant?> = settingsRepository.getAutobackupLastFailureAt()
        .stateIn(screenModelScope, SharingStarted.Eagerly, null)
    val autobackupLastFailureMessage: StateFlow<String?> = settingsRepository.getAutobackupLastFailureMessage()
        .stateIn(screenModelScope, SharingStarted.Eagerly, null)

    private val _pendingFolderPick = MutableStateFlow<FolderPickIntent?>(null)
    val pendingFolderPick: StateFlow<FolderPickIntent?> = _pendingFolderPick.asStateFlow()

    /** Export button — generates JSON and surfaces it for the save dialog. */
    fun onExportClick() {
        if (_state.value !is BackupUiState.Idle) return
        _state.value = BackupUiState.Exporting
        screenModelScope.launch {
            try {
                val json = backupService.exportToJson()
                _state.value = BackupUiState.ExportReady(json, suggestedFilename())
            } catch (e: Exception) {
                _state.value = BackupUiState.Error("Export failed: ${e.message ?: e::class.simpleName}")
            }
        }
    }

    /** Called by the screen after the save dialog wrote the file. */
    fun onExportFileSaved() {
        _state.value = BackupUiState.Done("Backup saved")
    }

    /** Import button — show the "this will overwrite" confirmation. */
    fun onImportClick() {
        if (_state.value !is BackupUiState.Idle) return
        _state.value = BackupUiState.PreImportConfirm
    }

    /** User confirmed the overwrite warning — open the file picker. */
    fun onImportConfirmed() {
        // No state change here; the screen launches the picker and calls
        // onImportFilePicked once the user selects a file.
    }

    /** File picker callback — parse the bundle, show the section preview. */
    fun onImportFilePicked(content: String) {
        screenModelScope.launch {
            try {
                val bundle = parseJson.decodeFromString(BackupBundle.serializer(), content)
                val summary = describeSections(bundle)
                _state.value = BackupUiState.ImportPreview(bundle, content, summary)
            } catch (e: Exception) {
                _state.value = BackupUiState.Error("Not a valid Kora backup file")
            }
        }
    }

    /** Final confirmation in the preview dialog — actually apply the bundle. */
    fun onImportPreviewConfirmed() {
        val preview = _state.value as? BackupUiState.ImportPreview ?: return
        _state.value = BackupUiState.Importing
        screenModelScope.launch {
            when (val result = backupService.importFromJson(preview.rawJson)) {
                is ImportResult.Success -> _state.value =
                    BackupUiState.Done("Restored: " + result.sectionsRestored.joinToString(", "))
                is ImportResult.Failure -> _state.value = BackupUiState.Error(result.reason)
            }
        }
    }

    /** Cancel any pending confirmation/preview and return to Idle. */
    fun onCancel() {
        when (_state.value) {
            is BackupUiState.PreImportConfirm,
            is BackupUiState.ImportPreview,
            is BackupUiState.ExportReady -> _state.value = BackupUiState.Idle
            else -> Unit
        }
    }

    /** Dismiss the post-action banner (Done/Error). */
    fun onDismissResult() {
        _state.value = BackupUiState.Idle
    }

    /** Called by the screen if writing the chosen export file fails. */
    fun onExportFailed(reason: String) {
        _state.value = BackupUiState.Error("Export failed: $reason")
    }

    /** Called by the screen if reading the chosen import file fails before parsing. */
    fun onImportReadFailed(reason: String) {
        _state.value = BackupUiState.Error("Could not read backup file: $reason")
    }

    fun onAutobackupToggle(enabled: Boolean) {
        screenModelScope.launch {
            if (!enabled) {
                settingsRepository.putAutobackupEnabled(false)
                return@launch
            }
            val currentUri = autobackupFolderUri.value
            if (currentUri.isNullOrBlank()) {
                _pendingFolderPick.value = FolderPickIntent.ENABLE_AFTER
            } else {
                settingsRepository.putAutobackupEnabled(true)
                runAutobackupNow()
            }
        }
    }

    fun onChangeFolderClick() {
        _pendingFolderPick.value = FolderPickIntent.JUST_CHANGE
    }

    fun onFolderPicked(file: PlatformFile?) {
        val intent = _pendingFolderPick.value ?: return
        _pendingFolderPick.value = null
        if (file == null) return
        val uri = extractFolderUri(file)
        if (uri.isNullOrBlank()) {
            _state.value = BackupUiState.Error("Couldn't get a persistable URI for that folder")
            return
        }
        screenModelScope.launch {
            settingsRepository.putAutobackupFolderUri(uri)
            if (intent == FolderPickIntent.ENABLE_AFTER) {
                settingsRepository.putAutobackupEnabled(true)
                runAutobackupNow()
            }
        }
    }

    fun onFrequencyChange(frequency: AutobackupFrequency) {
        screenModelScope.launch { settingsRepository.putAutobackupFrequency(frequency) }
    }

    fun onMaxKeepChange(maxKeep: Int) {
        screenModelScope.launch { settingsRepository.putAutobackupMaxKeep(maxKeep) }
    }

    fun onRunNowClick() {
        if (autobackupFolderUri.value.isNullOrBlank()) return
        runAutobackupNow()
    }

    private fun describeSections(bundle: BackupBundle): List<String> {
        val s = bundle.sections
        val out = mutableListOf<String>()
        if (s.appSettings != null) out += "App settings"
        if (s.imageReaderSettings != null) out += "Image reader settings"
        if (s.epubReaderSettings != null) out += "EPUB reader settings"
        if (s.komfSettings != null) out += "Komf settings"
        if (s.transcriptionSettings != null) out += "Transcription settings"
        s.homeScreenFilters?.let { out += "Home filters (${it.size} entries)" }
        s.librarySeriesFilters?.let { out += "Library filters (${it.size} entries)" }
        s.seriesReaderOverrides?.let { out += "Series reader overrides (${it.size} entries)" }
        return out
    }

    private fun suggestedFilename(): String {
        val now: LocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        fun pad(v: Int) = v.toString().padStart(2, '0')
        val stamp = buildString {
            append(now.year); append(pad(now.monthNumber)); append(pad(now.dayOfMonth))
            append('-')
            append(pad(now.hour)); append(pad(now.minute)); append(pad(now.second))
        }
        return "kora-backup-$stamp"
    }
}

/** Why the user is being shown the folder picker right now. */
enum class FolderPickIntent {
    /** Toggle was flipped on with no folder set — pick + enable + run. */
    ENABLE_AFTER,
    /** "Change folder" button while already enabled — just persist the new URI. */
    JUST_CHANGE,
}

sealed interface BackupUiState {
    data object Idle : BackupUiState
    data object Exporting : BackupUiState
    data class ExportReady(val json: String, val suggestedFilename: String) : BackupUiState
    data object PreImportConfirm : BackupUiState
    data class ImportPreview(
        val bundle: BackupBundle,
        val rawJson: String,
        val summary: List<String>,
    ) : BackupUiState
    data object Importing : BackupUiState
    data class Done(val message: String) : BackupUiState
    data class Error(val message: String) : BackupUiState
}
