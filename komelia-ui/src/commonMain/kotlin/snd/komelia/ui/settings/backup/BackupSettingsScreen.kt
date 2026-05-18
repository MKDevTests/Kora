package snd.komelia.ui.settings.backup

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import snd.komelia.ui.LocalViewModelFactory
import snd.komelia.ui.dialogs.ConfirmationDialog
import snd.komelia.ui.settings.SettingsScreenContainer

/**
 * Backup & Restore settings screen.
 *
 * Two buttons: Export (writes a JSON file via SAF) and Import (reads a JSON
 * file via SAF and applies it). Import is gated by a double confirmation
 * (overwrite warning → file pick → section preview) because it is destructive.
 *
 * Uses FileKit's suspend dialog API rather than the launcher pattern so we
 * don't have to thread DialogSettings through and so the flow reads top to
 * bottom in a single LaunchedEffect / coroutine.
 */
class BackupSettingsScreen : Screen {

    @Composable
    override fun Content() {
        val viewModelFactory = LocalViewModelFactory.current
        val vm = rememberScreenModel { viewModelFactory.getBackupSettingsViewModel() }
        val state by vm.state.collectAsState()
        val coroutineScope = rememberCoroutineScope()

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
                        "and per-series reader overrides to a JSON file — or restore a " +
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
    }
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
