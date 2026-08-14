package snd.komelia.ui.settings.imagereader.rapidocr

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow
import snd.komelia.db.ImageReaderSettings
import snd.komelia.ui.LocalAccentColor
import snd.komelia.ui.common.components.DropdownChoiceMenu
import snd.komelia.ui.common.components.LabeledEntry
import snd.komelia.ui.settings.imagereader.onnxruntime.DownloadDialog
import snd.komelia.updates.UpdateProgress
import snd.komelia.ui.LocalStrings

@Composable
fun RapidOcrSettingsContent(
    isDownloaded: Boolean,
    rapidOcrModelsUrl: String,
    onRapidOcrModelsUrlChange: (String) -> Unit,
    downloadFlow: () -> Flow<UpdateProgress>,
) {
    var showDownloadDialog by remember { mutableStateOf(false) }
    val accentColor = LocalAccentColor.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(LocalStrings.current.ui.rapidocrModels, style = MaterialTheme.typography.titleMedium)

        val options = remember {
            listOf(
                LabeledEntry(ImageReaderSettings.RAPID_OCR_MODELS_DEFAULT_URL, "Default (GitHub)"),
                LabeledEntry("Custom", "Custom"),
            )
        }
        var isCustomUrl by remember { mutableStateOf(!options.any { it.value == rapidOcrModelsUrl }) }
        val selectedOption = remember(rapidOcrModelsUrl, isCustomUrl) {
            if (isCustomUrl) options.last()
            else options.find { it.value == rapidOcrModelsUrl } ?: options.last()
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DropdownChoiceMenu(
                selectedOption = selectedOption,
                options = options,
                onOptionChange = {
                    if (it.value != "Custom") {
                        isCustomUrl = false
                        onRapidOcrModelsUrlChange(it.value)
                    } else {
                        isCustomUrl = true
                    }
                },
                label = { Text(LocalStrings.current.ui.modelUrlSource) },
                modifier = Modifier.weight(0.7f)
            )

            Button(
                onClick = { showDownloadDialog = true },
                modifier = Modifier.weight(0.3f),
                colors = accentColor?.let { ButtonDefaults.buttonColors(containerColor = it) }
                    ?: ButtonDefaults.buttonColors()
            ) {
                if (isDownloaded) Text(LocalStrings.current.ui.reDownloadModels)
                else Text(LocalStrings.current.ui.downloadModels)
            }
        }

        if (isCustomUrl) {
            OutlinedTextField(
                value = rapidOcrModelsUrl,
                onValueChange = onRapidOcrModelsUrlChange,
                label = { Text(LocalStrings.current.ui.customUrl) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    if (showDownloadDialog) {
        DownloadDialog(
            onDownloadRequest = downloadFlow,
            onDismiss = { showDownloadDialog = false },
            headerText = "Downloading RapidOCR models (~27 MB)",
        )
    }
}
