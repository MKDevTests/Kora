package snd.komelia.ui.settings.backup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.github.vinceglb.filekit.dialogs.openFileSaver
import io.github.vinceglb.filekit.readString
import io.github.vinceglb.filekit.writeString
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import snd.komelia.settings.model.AutobackupFrequency
import snd.komelia.ui.LocalPlatform
import snd.komelia.ui.LocalViewModelFactory
import snd.komelia.ui.dialogs.ConfirmationDialog
import snd.komelia.ui.dialogs.permissions.StoragePermissionRequestDialog
import snd.komelia.ui.platform.PlatformType
import snd.komelia.ui.settings.SettingsScreenContainer
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Backup & Restore settings screen.
 *
 * Two buttons: Export (writes a JSON file via SAF) and Import (reads a JSON
 * file via SAF and applies it). Import is gated by a double confirmation
 * (overwrite warning -> file pick -> section preview) because it is destructive.
 *
 * Below those, on mobile only, an Autobackup section: toggle, folder picker,
 * frequency picker, max-keep slider, manual "Backup now" button, and a status
 * row showing the last success / last failure.
 */
class BackupSettingsScreen : Screen {

    @Composable
    override fun Content() {
        val viewModelFactory = LocalViewModelFactory.current
        val vm = rememberScreenModel { viewModelFactory.getBackupSettingsViewModel() }
        val state by vm.state.collectAsState()
        val coroutineScope = rememberCoroutineScope()
        val showAutobackup = LocalPlatform.current == PlatformType.MOBILE

        // When the VM transitions to ExportReady, drive the system save dialog
        // and write the JSON to whatever the user picks.
        LaunchedEffect(state) {
            val current = state
            if (current is BackupUiState.ExportReady) {
                val target = runCatching {
                    FileKit.openFileSaver(
                        suggestedName = current.suggestedFilename,
                        extension = "json",
                    )
                }.getOrElse {
                    vm.onExportFailed(it.message ?: "Could not open save dialog")
                    return@LaunchedEffect
                }
                if (target == null) {
                    vm.onCancel()
                } else {
                    runCatching { target.writeString(current.json) }
                        .onSuccess { vm.onExportFileSaved() }
                        .onFailure { vm.onExportFailed(it.message ?: "Could not write file") }
                }
            }
        }

        SettingsScreenContainer("Backup & restore") {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Save a copy of your settings, home filters, library filters " +
                        "and per-series reader overrides to a JSON file - or restore a " +
                        "previous backup.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Server URL, login and reading progress are NOT included. " +
                        "Annotations, bookmarks and color-correction presets are also " +
                        "not part of this backup.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.size(8.dp))

                val busy = state is BackupUiState.Exporting || state is BackupUiState.Importing

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        enabled = !busy,
                        onClick = { vm.onExportClick() },
                    ) {
                        Icon(Icons.Default.Download, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Export…")
                    }
                    Button(
                        enabled = !busy,
                        onClick = { vm.onImportClick() },
                    ) {
                        Icon(Icons.Default.Upload, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Import…")
                    }
                    if (busy) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    }
                }

                // Status banner for Done / Error.
                when (val s = state) {
                    is BackupUiState.Done -> StatusCard(
                        message = s.message,
                        isError = false,
                        onDismiss = vm::onDismissResult
                    )
                    is BackupUiState.Error -> StatusCard(
                        message = s.message,
                        isError = true,
                        onDismiss = vm::onDismissResult
                    )
                    else -> Unit
                }

                if (showAutobackup) {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    AutobackupSection(vm)
                }
            }
        }

        // First dialog: warn about overwrite, then open the picker on confirm.
        if (state is BackupUiState.PreImportConfirm) {
            ConfirmationDialog(
                title = "Import settings",
                body = "This will overwrite your current settings, home filters, " +
                    "library filters and per-series reader overrides with the contents " +
                    "of the backup file. Continue?",
                buttonConfirm = "Choose backup file",
                buttonConfirmColor = MaterialTheme.colorScheme.errorContainer,
                onDialogConfirm = {
                    vm.onImportConfirmed()
                    coroutineScope.launch {
                        val picked = runCatching {
                            FileKit.openFilePicker(type = FileKitType.File(listOf("json")))
                        }.getOrElse {
                            vm.onImportReadFailed(it.message ?: "Could not open file picker")
                            return@launch
                        }
                        if (picked == null) {
                            vm.onCancel()
                            return@launch
                        }
                        runCatching { picked.readString() }
                            .onSuccess { vm.onImportFilePicked(it) }
                            .onFailure { vm.onImportReadFailed(it.message ?: "Could not read file") }
                    }
                },
                onDialogDismiss = vm::onCancel,
            )
        }

        // Second dialog: show what's in the picked bundle and ask for final apply.
        val preview = state as? BackupUiState.ImportPreview
        if (preview != null) {
            ConfirmationDialog(
                title = "Apply backup?",
                body = buildString {
                    appendLine("Exported on ${preview.bundle.exportedAt}")
                    appendLine("By ${preview.bundle.exportedBy}")
                    appendLine()
                    appendLine("This backup contains:")
                    preview.summary.forEach { appendLine(" • $it") }
                },
                buttonConfirm = "Restore",
                buttonConfirmColor = MaterialTheme.colorScheme.errorContainer,
                onDialogConfirm = vm::onImportPreviewConfirmed,
                onDialogDismiss = vm::onCancel,
            )
        }

        // Folder picker. StoragePermissionRequestDialog drives
        // ACTION_OPEN_DOCUMENT_TREE on Android and takes persistable URI
        // permission before returning the wrapped PlatformFile.
        val pendingPick by vm.pendingFolderPick.collectAsState()
        if (pendingPick != null) {
            StoragePermissionRequestDialog(onComplete = { vm.onFolderPicked(it) })
        }
    }
}

@Composable
private fun AutobackupSection(vm: BackupSettingsViewModel) {
    val enabled by vm.autobackupEnabled.collectAsState()
    val folderUri by vm.autobackupFolderUri.collectAsState()
    val frequency by vm.autobackupFrequency.collectAsState()
    val maxKeep by vm.autobackupMaxKeep.collectAsState()
    val lastSuccess by vm.autobackupLastSuccessAt.collectAsState()
    val lastFailure by vm.autobackupLastFailureAt.collectAsState()
    val lastFailureMsg by vm.autobackupLastFailureMessage.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Automatic backups", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "Periodically write the same backup file to a folder of your choice. " +
                "Older copies are pruned automatically.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Enable automatic backups", modifier = Modifier.weight(1f))
            Switch(checked = enabled, onCheckedChange = { vm.onAutobackupToggle(it) })
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Backup folder",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = folderUri?.let(::describeFolderUri) ?: "No folder chosen yet",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = { vm.onChangeFolderClick() }) {
                Text(if (folderUri == null) "Choose…" else "Change…")
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Frequency", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AutobackupFrequency.entries.forEach { option ->
                    FilterChip(
                        selected = option == frequency,
                        onClick = { vm.onFrequencyChange(option) },
                        label = { Text(option.label()) },
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Keep up to $maxKeep ${if (maxKeep == 1) "copy" else "copies"}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Slider(
                value = maxKeep.toFloat(),
                onValueChange = { vm.onMaxKeepChange(it.roundToInt()) },
                valueRange = 1f..10f,
                steps = 8, // 1..10 inclusive = 10 stops, steps = stops - 2
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                enabled = folderUri != null,
                onClick = { vm.onRunNowClick() },
            ) {
                Icon(Icons.Default.PlayArrow, null)
                Spacer(Modifier.width(8.dp))
                Text("Backup now")
            }
        }

        AutobackupStatusRow(
            lastSuccess = lastSuccess,
            lastFailure = lastFailure,
            lastFailureMessage = lastFailureMsg,
        )
    }
}

@Composable
private fun AutobackupStatusRow(
    lastSuccess: Instant?,
    lastFailure: Instant?,
    lastFailureMessage: String?,
) {
    val failureIsNewer = lastFailure != null &&
        (lastSuccess == null || lastFailure > lastSuccess)

    if (failureIsNewer && lastFailure != null) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Last attempt failed — ${describeRelativeTime(lastFailure)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (!lastFailureMessage.isNullOrBlank()) {
                    Text(
                        text = lastFailureMessage,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        return
    }

    if (lastSuccess != null) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
        ) {
            Text(
                text = "Last backup: ${describeRelativeTime(lastSuccess)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun AutobackupFrequency.label(): String = when (this) {
    AutobackupFrequency.DAILY -> "Daily"
    AutobackupFrequency.WEEKLY -> "Weekly"
    AutobackupFrequency.FORTNIGHTLY -> "Every 15 days"
}

private fun describeFolderUri(uri: String): String {
    // SAF tree URIs look like
    // content://com.android.externalstorage.documents/tree/primary%3ABackup
    // Pull the last decoded segment as a friendly label.
    val decoded = uri.substringAfterLast("/").let {
        runCatching {
            java.net.URLDecoder.decode(it, "UTF-8")
        }.getOrDefault(it)
    }
    return decoded.substringAfter(":", decoded).ifBlank { uri }
}

private fun describeRelativeTime(instant: Instant): String {
    val now = Clock.System.now()
    val diff = now - instant
    if (diff < 1.minutes) return "just now"
    if (diff < 60.minutes) return "${diff.inWholeMinutes} min ago"
    if (diff < 24.hours) return "${diff.inWholeHours} h ago"
    if (diff < 7.days) {
        val days = diff.inWholeDays
        return if (days == 1L) "1 day ago" else "$days days ago"
    }
    val dt = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    fun pad(v: Int) = v.toString().padStart(2, '0')
    return "${dt.year}-${pad(dt.monthNumber)}-${pad(dt.dayOfMonth)}"
}

@Composable
private fun StatusCard(message: String, isError: Boolean, onDismiss: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isError) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f).padding(end = 8.dp),
            )
            TextButton(onClick = onDismiss, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text("Dismiss")
            }
        }
    }
}
