package snd.komelia.ui.settings.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.openFileSaver
import io.github.vinceglb.filekit.writeString
import kotlinx.coroutines.launch
import snd.komelia.ui.LocalKomgaState
import snd.komelia.ui.LocalOfflineMode
import snd.komelia.ui.LocalViewModelFactory
import snd.komelia.ui.settings.SettingsScreenContainer
import snd.komelia.updates.AppVersion
import kotlin.time.Clock

/**
 * Settings -> App Settings -> Diagnostics. A read-only, at-a-glance view of the
 * app's state, useful for support.
 *
 * v1 rows (version / mode / server) read from composition Locals. v2 adds
 * cache sizes, offline storage, background-task status and a logs section
 * (size, configurable cap, in-app viewer, redacted export) from the platform
 * [DiagnosticsViewModel]; on platforms without support those sections hide.
 */
class DiagnosticsScreen : Screen {

    @Composable
    override fun Content() {
        val serverUrl = LocalKomgaState.current.serverUrl.value
        val isOffline = LocalOfflineMode.current.collectAsState().value
        val viewModelFactory = LocalViewModelFactory.current
        val vm = rememberScreenModel { viewModelFactory.getDiagnosticsViewModel() }
        val scope = rememberCoroutineScope()

        var showLogs by remember { mutableStateOf(false) }
        var logText by remember { mutableStateOf("") }

        SettingsScreenContainer("Diagnostics") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DiagnosticRow("App version", AppVersion.current.toString())
                DiagnosticRow("Mode", if (isOffline) "Offline" else "Online")
                DiagnosticRow("Active server", serverUrl.ifBlank { "—" })

                if (vm.isSupported) {
                    val cache by vm.cache.collectAsState()
                    val offline by vm.offline.collectAsState()
                    val tasks by vm.tasks.collectAsState()
                    val clearing by vm.clearing.collectAsState()
                    val logInfo by vm.logInfo.collectAsState()

                    DiagnosticSection("Cache")
                    cache?.let { c ->
                        DiagnosticRow("Images (disk)", formatBytes(c.imageDiskBytes))
                        DiagnosticRow("Reader cache", formatBytes(c.readerCacheBytes))
                        DiagnosticRow("Network cache", formatBytes(c.httpCacheBytes))
                        DiagnosticRow("Database", formatBytes(c.databaseBytes))
                        DiagnosticRow("Total", formatBytes(c.totalBytes))
                    } ?: DiagnosticRow("Cache", "Calculating…")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(enabled = !clearing, onClick = { vm.clearImageCache() }) {
                            Text("Clear image cache")
                        }
                        if (clearing) {
                            Spacer(Modifier.width(12.dp))
                            CircularProgressIndicator(Modifier.size(18.dp))
                        }
                    }

                    DiagnosticSection("Offline storage")
                    offline?.let { o ->
                        DiagnosticRow("Downloaded", formatBytes(o.totalBytes))
                        DiagnosticRow("Location", o.location)
                    } ?: DiagnosticRow("Offline", "Calculating…")

                    DiagnosticSection("Background tasks")
                    if (tasks.isEmpty()) {
                        DiagnosticRow("Tasks", "—")
                    } else {
                        tasks.forEach { t ->
                            DiagnosticRow(t.label, t.detail?.let { "${t.state} · $it" } ?: t.state)
                        }
                    }

                    DiagnosticSection("Logs")
                    DiagnosticRow("Log size", formatBytes(logInfo?.totalBytes ?: 0))
                    Text("Maximum log size", style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LogSizeCap.entries.forEach { cap ->
                            FilterChip(
                                selected = logInfo?.cap == cap,
                                onClick = { vm.setLogCap(cap) },
                                label = { Text("${cap.totalMb} MB") },
                            )
                        }
                    }
                    Text(
                        text = "Applied on the next app start.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = {
                            scope.launch {
                                logText = vm.readRecentLogs()
                                showLogs = true
                            }
                        }) { Text("View logs") }
                        OutlinedButton(onClick = {
                            scope.launch {
                                val text = vm.buildLogExport()
                                val stamp = Clock.System.now().toEpochMilliseconds()
                                val target = runCatching {
                                    FileKit.openFileSaver(suggestedName = "kora-logs-$stamp", extension = "txt")
                                }.getOrNull() ?: return@launch
                                runCatching { target.writeString(text) }
                            }
                        }) { Text("Export logs…") }
                    }
                }
            }
        }

        if (showLogs) {
            AlertDialog(
                onDismissRequest = { showLogs = false },
                title = { Text("Logs") },
                text = {
                    Column(modifier = Modifier.fillMaxHeight(0.8f)) {
                        HorizontalDivider()
                        SelectionContainer {
                            Column(
                                modifier = Modifier
                                    .verticalScroll(rememberScrollState())
                                    .fillMaxWidth()
                            ) {
                                Text(
                                    text = logText.ifBlank { "No logs found." },
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    lineHeight = 12.sp,
                                )
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showLogs = false }) { Text("Close") } },
            )
        }
    }
}

@Composable
private fun DiagnosticSection(title: String) {
    HorizontalDivider(Modifier.fillMaxWidth())
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
    )
}

@Composable
private fun DiagnosticRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.width(140.dp),
        )
        SelectionContainer {
            Text(text = value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Compact human-readable byte size (binary units). */
private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return if (unit == 0) "$bytes B"
    else ((value * 10).toLong() / 10.0).toString() + " " + units[unit]
}
