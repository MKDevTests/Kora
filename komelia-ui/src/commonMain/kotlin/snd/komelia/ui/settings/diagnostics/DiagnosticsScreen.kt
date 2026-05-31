package snd.komelia.ui.settings.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import snd.komelia.ui.LocalKomgaState
import snd.komelia.ui.LocalOfflineMode
import snd.komelia.ui.settings.SettingsScreenContainer
import snd.komelia.updates.AppVersion

/**
 * Settings -> App Settings -> Diagnostics. A read-only, at-a-glance view of the
 * app's current state, useful for support. v1 shows version / online state /
 * active server (read from existing composition Locals, no extra wiring).
 *
 * Planned for a later pass: cache sizes, offline storage usage, WorkManager
 * tasks, the offline log journal, and a redacted log export — those need
 * platform file access / DI and are deliberately out of this first version.
 */
class DiagnosticsScreen : Screen {

    @Composable
    override fun Content() {
        val serverUrl = LocalKomgaState.current.serverUrl.value
        val isOffline = LocalOfflineMode.current.collectAsState().value

        SettingsScreenContainer("Diagnostics") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DiagnosticRow("App version", AppVersion.current.toString())
                DiagnosticRow("Mode", if (isOffline) "Offline" else "Online")
                DiagnosticRow("Active server", serverUrl.ifBlank { "—" })
            }
        }
    }
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
