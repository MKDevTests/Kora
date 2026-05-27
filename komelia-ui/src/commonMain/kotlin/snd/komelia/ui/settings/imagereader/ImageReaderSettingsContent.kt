package snd.komelia.ui.settings.imagereader

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import snd.komelia.ui.LocalAccentColor
import snd.komelia.ui.LocalPlatform
import snd.komelia.ui.common.components.SwitchWithLabel
import snd.komelia.ui.platform.PlatformType
import snd.komelia.ui.settings.imagereader.ncnn.*
import snd.komelia.ui.settings.imagereader.onnxruntime.OnnxRuntimeSettingsContent
import snd.komelia.ui.settings.imagereader.onnxruntime.OnnxRuntimeSettingsState
import snd.komelia.ui.settings.imagereader.onnxruntime.isOnnxRuntimeSupported
import snd.komelia.ui.settings.imagereader.rapidocr.RapidOcrSettingsContent
import snd.komelia.ui.settings.imagereader.rapidocr.RapidOcrSettingsState
import snd.komelia.ui.settings.imagereader.rapidocr.isRapidOcrSupported

@Composable
fun ImageReaderSettingsContent(
    loadThumbnailPreviews: Boolean,
    onLoadThumbnailPreviewsChange: (Boolean) -> Unit,

    volumeKeysNavigation: Boolean,
    onVolumeKeysNavigationChange: (Boolean) -> Unit,

    keepReaderScreenOn: Boolean,
    onKeepReaderScreenOnChange: (Boolean) -> Unit,

    imageCacheSizeLimitMb: Long,
    onImageCacheSizeLimitMbChange: (Long) -> Unit,

    pagedReaderAutoDirection: Boolean,
    onPagedReaderAutoDirectionChange: (Boolean) -> Unit,

    pagedAutoSkipBlankPages: Boolean,
    onPagedAutoSkipBlankPagesChange: (Boolean) -> Unit,

    pagedAutoDetectWebtoon: Boolean,
    onPagedAutoDetectWebtoonChange: (Boolean) -> Unit,

    continuousReaderStopAtEnd: Boolean,
    onContinuousReaderStopAtEndChange: (Boolean) -> Unit,

    keepProgressBarVisibleWhileReading: Boolean,
    onKeepProgressBarVisibleWhileReadingChange: (Boolean) -> Unit,

    onCacheClear: () -> Unit,
    onnxRuntimeSettingsState: OnnxRuntimeSettingsState,
    ncnnSettingsState: NcnnSettingsState,
    rapidOcrSettingsState: RapidOcrSettingsState,
) {
    var showLogs by remember { mutableStateOf(false) }
    var showCrashLogs by remember { mutableStateOf(false) }
    val accentColor = LocalAccentColor.current

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val platform = LocalPlatform.current
        SwitchWithLabel(
            checked = loadThumbnailPreviews,
            onCheckedChange = onLoadThumbnailPreviewsChange,
            label = { Text("Load small previews when dragging navigation slider") },
            supportingText = { Text("can be slow for high resolution images") },
        )

        if (platform == PlatformType.MOBILE) {
            SwitchWithLabel(
                checked = volumeKeysNavigation,
                onCheckedChange = onVolumeKeysNavigationChange,
                label = { Text("Volume keys navigation") },
            )
            SwitchWithLabel(
                checked = keepReaderScreenOn,
                onCheckedChange = onKeepReaderScreenOnChange,
                label = { Text("Keep screen on while reading") },
            )
            SwitchWithLabel(
                checked = keepProgressBarVisibleWhileReading,
                onCheckedChange = onKeepProgressBarVisibleWhileReadingChange,
                label = { Text("Always show progress bar while reading") },
                supportingText = {
                    Text("Hide the other controls but keep the slim bottom strip. Tap to reveal the full UI.")
                },
            )
        }

        SwitchWithLabel(
            checked = pagedReaderAutoDirection,
            onCheckedChange = onPagedReaderAutoDirectionChange,
            label = { Text("Auto-detect reading direction") },
            supportingText = { Text("Use series metadata. Manual flips are stored locally per-series.") },
        )

        SwitchWithLabel(
            checked = pagedAutoSkipBlankPages,
            onCheckedChange = onPagedAutoSkipBlankPagesChange,
            label = { Text("Auto-skip blank pages") },
            supportingText = { Text("When Crop borders is on, blank pages are removed from the reader instead of crashing.") },
        )

        SwitchWithLabel(
            checked = pagedAutoDetectWebtoon,
            onCheckedChange = onPagedAutoDetectWebtoonChange,
            label = { Text("Auto-detect webtoon") },
            supportingText = { Text("If the first 3 pages are very tall (height ≥ 4× width), switch to panel-by-panel reader (auto-zoom on each panel). Your manual override sticks for the rest of the reader session.") },
        )

        SwitchWithLabel(
            checked = continuousReaderStopAtEnd,
            onCheckedChange = onContinuousReaderStopAtEndChange,
            label = { Text("Stop at end of book (continuous reader)") },
            supportingText = { Text("Pause at the last page instead of rolling silently into the next book. Swipe again to continue. Also ensures the current book gets marked as read.") },
        )

        FilledTonalButton(
            onClick = onCacheClear,
            colors = accentColor?.let {
                val contentColor = if (it.luminance() > 0.5f) Color.Black else Color.White
                ButtonDefaults.filledTonalButtonColors(containerColor = it, contentColor = contentColor)
            } ?: ButtonDefaults.filledTonalButtonColors()
        ) { Text("Clear image cache") }

        Column {
            Text(
                "Max Image Cache Size: ${"%.1f".format(imageCacheSizeLimitMb.toDouble() / 1024)} GB",
                style = MaterialTheme.typography.labelLarge
            )
            Slider(
                value = imageCacheSizeLimitMb.toFloat(),
                onValueChange = { onImageCacheSizeLimitMbChange(it.toLong()) },
                valueRange = 500f..5000f,
                steps = 44, // (5000 - 500) / 100 - 1 = 44 steps for 100MB intervals
                colors = accentColor?.let {
                    SliderDefaults.colors(
                        thumbColor = it,
                        activeTrackColor = it,
                    )
                } ?: SliderDefaults.colors()
            )
            Text(
                "Requires app restart to take effect",
                style = MaterialTheme.typography.labelSmall
            )
        }

        if (isOnnxRuntimeSupported()) {
            HorizontalDivider(Modifier.padding(vertical = 10.dp))
            OnnxRuntimeSettingsContent(
                executionProvider = onnxRuntimeSettingsState.currentExecutionProvider,
                availableDevices = onnxRuntimeSettingsState.availableDevices,
                deviceId = onnxRuntimeSettingsState.deviceId.collectAsState().value,
                onDeviceIdChange = onnxRuntimeSettingsState::onDeviceIdChange,
                upscaleMode = onnxRuntimeSettingsState.upscaleMode.collectAsState().value,
                onUpscaleModeChange = onnxRuntimeSettingsState::onUpscaleModeChange,
                upscalerTileSize = onnxRuntimeSettingsState.upscalerTileSize.collectAsState().value,
                onUpscalerTileSizeChange = onnxRuntimeSettingsState::onTileSizeChange,
                upscaleModelPath = onnxRuntimeSettingsState.upscaleModelPath.collectAsState().value,
                onUpscaleModelPathChange = onnxRuntimeSettingsState::onUpscaleModelPathChange,
                onOrtInstall = onnxRuntimeSettingsState::onInstallRequest,
                mangaJaNaiIsInstalled = onnxRuntimeSettingsState.mangaJaNaiIsInstalled.collectAsState().value,
                onMangaJaNaiDownload = onnxRuntimeSettingsState::onMangaJaNaiDownloadRequest,
                panelModelIsDownloaded = onnxRuntimeSettingsState.panelModelIsDownloaded.collectAsState().value,
                panelDetectionUrl = onnxRuntimeSettingsState.panelDetectionUrl.collectAsState().value,
                onPanelDetectionUrlChange = onnxRuntimeSettingsState::onPanelDetectionUrlChange,
                onPanelDetectionModelDownloadRequest = onnxRuntimeSettingsState::onPanelDetectionModelDownloadRequest
            )
        }

        if (isNcnnSupported()) {
            HorizontalDivider(Modifier.padding(vertical = 10.dp))
            NcnnSettingsContent(
                settings = ncnnSettingsState.ncnnUpscalerSettings.collectAsState().value,
                onSettingsChange = ncnnSettingsState::onSettingsChange,
                onDownloadRequest = ncnnSettingsState::onNcnnDownloadRequest
            )
        }

        if (isRapidOcrSupported()) {
            HorizontalDivider(Modifier.padding(vertical = 10.dp))
            RapidOcrSettingsContent(
                isDownloaded = rapidOcrSettingsState.isDownloaded.collectAsState().value,
                rapidOcrModelsUrl = rapidOcrSettingsState.rapidOcrModelsUrl.collectAsState().value,
                onRapidOcrModelsUrlChange = rapidOcrSettingsState::onRapidOcrModelsUrlChange,
                downloadFlow = rapidOcrSettingsState::downloadFlow
            )
        }

        if (isNcnnSupported()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = { showLogs = true },
                    colors = accentColor?.let { ButtonDefaults.textButtonColors(contentColor = it) }
                        ?: ButtonDefaults.textButtonColors()
                ) {
                    Text("View Logs")
                }
                TextButton(
                    onClick = { showCrashLogs = true },
                    colors = accentColor?.let { ButtonDefaults.textButtonColors(contentColor = it) }
                        ?: ButtonDefaults.textButtonColors()
                ) {
                    Text("Crash Logs")
                }
            }
        }

        if (showLogs) {
            NcnnLogViewerDialog(onDismiss = { showLogs = false })
        }
        if (showCrashLogs) {
            NcnnCrashLogViewerDialog(onDismiss = { showCrashLogs = false })
        }
    }
}
