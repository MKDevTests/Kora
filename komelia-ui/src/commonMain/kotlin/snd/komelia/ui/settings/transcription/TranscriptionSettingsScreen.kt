package snd.komelia.ui.settings.transcription

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import snd.komelia.settings.model.TranscriptionEngineType
import snd.komelia.ui.LoadState
import snd.komelia.ui.LocalViewModelFactory
import snd.komelia.ui.common.components.ErrorContent
import snd.komelia.ui.common.components.LoadingMaxSizeIndicator
import snd.komelia.ui.settings.SettingsScreenContainer
import snd.komelia.ui.settings.imagereader.onnxruntime.DownloadDialog
import snd.komelia.ui.LocalStrings

class TranscriptionSettingsScreen : Screen {

    @Composable
    override fun Content() {
        val viewModelFactory = LocalViewModelFactory.current
        val vm = rememberScreenModel { viewModelFactory.getTranscriptionSettingsViewModel() }
        LaunchedEffect(Unit) { vm.initialize() }

        SettingsScreenContainer(title = LocalStrings.current.ui.transcriptionSettings) {
            when (val result = vm.state.collectAsState().value) {
                is LoadState.Error -> ErrorContent(result.exception)
                LoadState.Uninitialized, LoadState.Loading -> LoadingMaxSizeIndicator()
                is LoadState.Success<Unit> -> TranscriptionSettingsContent(vm)
            }
        }
    }
}

@Composable
private fun TranscriptionSettingsContent(vm: TranscriptionSettingsViewModel) {
    val engine by vm.engine.collectAsState()
    val whisperLanguage by vm.whisperLanguage.collectAsState()
    val isWhisperModelDownloaded by vm.isWhisperModelDownloaded.collectAsState()
    var showDownloadDialog by remember { mutableStateOf(false) }

    if (showDownloadDialog) {
        DownloadDialog(
            headerText = "Downloading Whisper base model (~75 MB)",
            onDownloadRequest = vm::onDownloadRequest,
            onDismiss = { showDownloadDialog = false },
        )
    }

    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(LocalStrings.current.ui.speechEngine, style = MaterialTheme.typography.titleMedium)

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = engine == TranscriptionEngineType.ML_KIT,
                onClick = { vm.onEngineChange(TranscriptionEngineType.ML_KIT) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            ) { Text("ML Kit") }
            SegmentedButton(
                selected = engine == TranscriptionEngineType.WHISPER,
                onClick = { if (vm.isWhisperAvailable) vm.onEngineChange(TranscriptionEngineType.WHISPER) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                enabled = vm.isWhisperAvailable,
            ) { Text("Whisper") }
        }

        if (!vm.isWhisperAvailable && engine == TranscriptionEngineType.WHISPER) {
            // Switch back to ML Kit if Whisper was persisted on a non-Android platform
            LaunchedEffect(Unit) { vm.onEngineChange(TranscriptionEngineType.ML_KIT) }
        }

        if (engine == TranscriptionEngineType.ML_KIT) {
            Text(
                LocalStrings.current.ui.usesGoogleSOnDevice,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (engine == TranscriptionEngineType.WHISPER) {
            HorizontalDivider()

            Text(LocalStrings.current.ui.whisperModel, style = MaterialTheme.typography.titleSmall)

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("ggml-base-q5_0 (~75 MB)", modifier = Modifier.weight(1f))
                if (isWhisperModelDownloaded) {
                    Icon(Icons.Default.Check, contentDescription = LocalStrings.current.ui.downloaded, tint = Color.Green)
                    Text(LocalStrings.current.ui.downloaded, style = MaterialTheme.typography.bodySmall)
                } else {
                    Text(
                        LocalStrings.current.ui.notDownloaded,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Button(onClick = { showDownloadDialog = true }) {
                if (isWhisperModelDownloaded) Text(LocalStrings.current.ui.reDownloadModel)
                else Text(LocalStrings.current.ui.downloadModel)
            }

            HorizontalDivider()

            Text(LocalStrings.current.ui.language, style = MaterialTheme.typography.titleSmall)

            val languageOptions = listOf(
                null to "Auto (detect)",
                "en" to "English",
                "he" to "Hebrew",
                "fr" to "French",
                "de" to "German",
                "es" to "Spanish",
            )

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                languageOptions.forEach { (code, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        RadioButton(
                            selected = whisperLanguage == code,
                            onClick = { vm.onLanguageChange(code) }
                        )
                        Text(label)
                    }
                }
            }

            HorizontalDivider()
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(LocalStrings.current.ui.aboutWhisper, style = MaterialTheme.typography.labelLarge)
                    Text(
                        "Whisper provides accurate word-level timestamps but requires a one-time model download (~75 MB). " +
                            "ML Kit uses Google's streaming model that downloads automatically.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
