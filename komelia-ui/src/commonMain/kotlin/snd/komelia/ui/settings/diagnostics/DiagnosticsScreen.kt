package snd.komelia.ui.settings.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import snd.komelia.ui.LocalKomgaState
import snd.komelia.ui.LocalOfflineMode
import snd.komelia.ui.LocalViewModelFactory
import snd.komelia.ui.settings.SettingsScreenContainer
import snd.komelia.updates.AppVersion

/**
 * Settings -> App Settings -> Diagnostics. A read-only, at-a-glance view of the
 * app's state, useful for support.
 *
 * v1 rows (version / mode / server) read from composition Locals. v2 adds
 * cache sizes, offline storage and background-task status from the platform
 * [DiagnosticsViewModel]; on platforms without support those sections are
 * hidden. The only action is a safe "Clear image cache".
 */
class DiagnosticsScreen : Screen {

    @Composable
    override fun Content() {
        val serverUrl = LocalKomgaState.current.serverUrl.value
        val isOffline = LocalOfflineMode.current.collectAsState().value
        val viewModelFactory = LocalViewModelFactory.current
        val vm = rememberScreenModel { viewModelFactory.getDiagnosticsViewModel() }

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

                    DiagnosticSection("Cache")
                    cache?.let { c ->
                        DiagnosticRow("Images (disk)", formatBytes(c.imageDiskBytes))
                        DiagnosticRow("Reader cache", formatBytes(c.readerCacheBytes))
                        DiagnosticRow("Network cache", formatBytes(c.httpCacheBytes))
                        DiagnosticRow("Database", formatBytes(c.databaseBytes))
                        DiagnosticRow("Total", formatBytes(c.totalBytes))
                    } ?: DiagnosticRow("Cache", "Calculating…")
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
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
                }
            }
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
    return if (unit == 0) "${bytes} B"
    else ((value * 10).toLong() / 10.0).toString() + " " + units[unit]
}
