package snd.komelia.ui.settings.imagereader.onnxruntime

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow
import snd.komelia.db.ImageReaderSettings
import snd.komelia.ui.common.components.DropdownChoiceMenu
import snd.komelia.ui.common.components.LabeledEntry
import snd.komelia.ui.platform.cursorForHand
import snd.komelia.updates.UpdateProgress
import snd.komelia.ui.LocalStrings

@Composable
fun PanelDetectionSettings(
    isDownloaded: Boolean,
    currentUrl: String,
    onUrlChange: (String) -> Unit,
    onDownloadRequest: () -> Flow<UpdateProgress>
) {
    var showDownloadDialog by remember { mutableStateOf(false) }
    if (showDownloadDialog) {
        DownloadDialog(
            headerText = "Downloading panel detection model",
            onDownloadRequest = onDownloadRequest,
            onDismiss = { showDownloadDialog = false },
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            """
                If model is available, a new "Panels" reader mode will be added.
                In this mode reader will zoom and scroll from panel to panel
            """.trimIndent(),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 5.dp)
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalButton(
                onClick = { showDownloadDialog = true },
                modifier = Modifier.cursorForHand()
            ) {
                Text(if (isDownloaded) "Re-download Model" else "Download Model")
            }
            if (isDownloaded) {
                Text(LocalStrings.current.ui.installed)
                Icon(Icons.Default.Check, null, tint = Color.Green)
            }
        }

        val options = remember {
            listOf(
                LabeledEntry(ImageReaderSettings.PANEL_DETECTION_DEFAULT_GITHUB_URL, "Default (GitHub)"),
                LabeledEntry(ImageReaderSettings.PANEL_DETECTION_DEFAULT_ORIGINAL_URL, "Default (Original)"),
                LabeledEntry("Custom", "Custom"),
            )
        }
        var isCustomUrl by remember { mutableStateOf(!options.any { it.value == currentUrl }) }
        val selectedOption = remember(currentUrl, isCustomUrl) {
            if (isCustomUrl) options.last()
            else options.find { it.value == currentUrl } ?: options.last()
        }

        DropdownChoiceMenu(
            selectedOption = selectedOption,
            options = options,
            onOptionChange = {
                if (it.value != "Custom") {
                    isCustomUrl = false
                    onUrlChange(it.value)
                } else {
                    isCustomUrl = true
                }
            },
            label = { Text(LocalStrings.current.ui.modelUrlSource) },
            inputFieldModifier = Modifier.fillMaxWidth()
        )

        if (isCustomUrl) {
            OutlinedTextField(
                value = currentUrl,
                onValueChange = onUrlChange,
                label = { Text(LocalStrings.current.ui.customUrl) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
