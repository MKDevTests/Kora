package snd.komelia.ui.settings.imagereader.ncnn

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow
import snd.komelia.settings.model.NcnnEngine
import snd.komelia.settings.model.NcnnUpscalerSettings
import snd.komelia.ui.LocalStrings
import snd.komelia.ui.common.components.DropdownChoiceMenu
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.graphics.Color
import snd.komelia.ui.common.components.LabeledEntry
import snd.komelia.ui.common.components.NumberField
import snd.komelia.ui.common.components.SwitchWithLabel
import snd.komelia.ui.settings.imagereader.onnxruntime.DownloadDialog
import snd.komelia.updates.UpdateProgress

@Composable
expect fun NcnnLogViewerDialog(onDismiss: () -> Unit)

@Composable
expect fun NcnnCrashLogViewerDialog(onDismiss: () -> Unit)

@Composable
fun NcnnSettingsContent(
    settings: NcnnUpscalerSettings,
    onSettingsChange: (NcnnUpscalerSettings) -> Unit,
    onDownloadRequest: () -> Flow<UpdateProgress>,
) {
    val strings = LocalStrings.current.imageSettings
    var showDownloadDialog by remember { mutableStateOf(false) }
    if (showDownloadDialog) {
        DownloadDialog(
            headerText = "Downloading NCNN models",
            onDownloadRequest = onDownloadRequest,
            onDismiss = { showDownloadDialog = false },
        )
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SwitchWithLabel(
            checked = settings.enabled,
            onCheckedChange = { onSettingsChange(settings.copy(enabled = it)) },
            label = { Text(LocalStrings.current.ui.enableNcnnUpscalerMobileOnly) },
            modifier = Modifier.fillMaxWidth(),
            enabled = settings.isDownloaded
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = { showDownloadDialog = true }) {
                if (settings.isDownloaded) Text(LocalStrings.current.ui.reDownloadModels)
                else Text(LocalStrings.current.ui.downloadModels)
            }

            if (settings.isDownloaded) {
                Text(LocalStrings.current.ui.installed)
                Icon(Icons.Default.Check, null, tint = Color.Green)
            }
        }

        val urlOptions = remember {
            listOf(
                LabeledEntry("https://github.com/eserero/Sipurra/releases/download/model/NcnnUpscalerModels.zip", "Default (GitHub)"),
                LabeledEntry("Custom", "Custom"),
            )
        }
        var isCustomUrl by remember { mutableStateOf(!urlOptions.any { it.value == settings.ncnnUpscalerUrl }) }
        val selectedUrlOption = remember(settings.ncnnUpscalerUrl, isCustomUrl) {
            if (isCustomUrl) urlOptions.last()
            else urlOptions.find { it.value == settings.ncnnUpscalerUrl } ?: urlOptions.last()
        }

        DropdownChoiceMenu(
            selectedOption = selectedUrlOption,
            options = urlOptions,
            onOptionChange = {
                if (it.value != "Custom") {
                    isCustomUrl = false
                    onSettingsChange(settings.copy(ncnnUpscalerUrl = it.value))
                } else {
                    isCustomUrl = true
                }
            },
            label = { Text(LocalStrings.current.ui.modelUrlSource) },
            inputFieldModifier = Modifier.fillMaxWidth()
        )

        if (isCustomUrl) {
            OutlinedTextField(
                value = settings.ncnnUpscalerUrl,
                onValueChange = { onSettingsChange(settings.copy(ncnnUpscalerUrl = it)) },
                label = { Text(LocalStrings.current.ui.customUrl) },
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (settings.enabled && settings.isDownloaded) {
            DropdownChoiceMenu(
                selectedOption = when (settings.engine) {
                    NcnnEngine.WAIFU2X -> LabeledEntry(NcnnEngine.WAIFU2X, strings.ncnnUpscaleModeWaifu2x)
                    NcnnEngine.REALCUGAN -> LabeledEntry(NcnnEngine.REALCUGAN, strings.ncnnUpscaleModeRealCugan)
                    NcnnEngine.REALSR -> LabeledEntry(NcnnEngine.REALSR, strings.ncnnUpscaleModeRealSr)
                    NcnnEngine.REAL_ESRGAN -> LabeledEntry(NcnnEngine.REAL_ESRGAN, strings.ncnnUpscaleModeRealEsrgan)
                },
                options = remember {
                    listOf(
                        LabeledEntry(NcnnEngine.WAIFU2X, strings.ncnnUpscaleModeWaifu2x),
                        LabeledEntry(NcnnEngine.REALCUGAN, strings.ncnnUpscaleModeRealCugan),
                        LabeledEntry(NcnnEngine.REALSR, strings.ncnnUpscaleModeRealSr),
                        LabeledEntry(NcnnEngine.REAL_ESRGAN, strings.ncnnUpscaleModeRealEsrgan),
                    )
                },
                onOptionChange = { onSettingsChange(settings.copy(engine = it.value, model = getDefaultModelForEngine(it.value))) },
                label = { Text(LocalStrings.current.ui.engine) },
                inputFieldModifier = Modifier.fillMaxSize()
            )

            val models = when (settings.engine) {
                NcnnEngine.WAIFU2X -> ncnnWaifu2xModels
                NcnnEngine.REALCUGAN -> ncnnRealCuganModels
                NcnnEngine.REALSR -> ncnnRealSrModels
                NcnnEngine.REAL_ESRGAN -> ncnnRealEsrganModels
            }

            DropdownChoiceMenu(
                selectedOption = LabeledEntry(settings.model, settings.model),
                options = remember(settings.engine) {
                    models.map { LabeledEntry(it, it) }
                },
                onOptionChange = { onSettingsChange(settings.copy(model = it.value)) },
                label = { Text(LocalStrings.current.ui.model) },
                inputFieldModifier = Modifier.fillMaxSize()
            )

            SwitchWithLabel(
                checked = settings.upscaleOnLoad,
                onCheckedChange = { onSettingsChange(settings.copy(upscaleOnLoad = it)) },
                label = { Text(strings.ncnnUpscaleOnLoad) },
                supportingText = { Text(strings.ncnnUpscaleOnLoadTooltip) }
            )

            if (settings.upscaleOnLoad) {
                NumberField(
                    value = settings.upscaleThreshold,
                    onValueChange = { onSettingsChange(settings.copy(upscaleThreshold = it ?: 1200)) },
                    label = { Text(strings.ncnnUpscaleOnLoadThreshold) },
                )
            }

            // Advanced settings
            SwitchWithLabel(
                checked = settings.ttaMode,
                onCheckedChange = { onSettingsChange(settings.copy(ttaMode = it)) },
                label = { Text(LocalStrings.current.ui.ttaMode) },
                supportingText = { Text(LocalStrings.current.ui.testTimeAugmentationSlowerBut) }
            )
        }
    }
}

val ncnnWaifu2xModels = listOf(
    "models-cunet/noise0_scale2.0x_model",
    "models-cunet/noise1_scale2.0x_model",
    "models-cunet/noise2_scale2.0x_model",
    "models-cunet/noise3_scale2.0x_model",
    "models-cunet/scale2.0x_model",
    "models-upconv_7_anime_style_art_rgb/noise0_scale2.0x_model",
    "models-upconv_7_anime_style_art_rgb/noise1_scale2.0x_model",
    "models-upconv_7_anime_style_art_rgb/noise2_scale2.0x_model",
    "models-upconv_7_anime_style_art_rgb/noise3_scale2.0x_model",
    "models-upconv_7_anime_style_art_rgb/scale2.0x_model",
)
val ncnnRealCuganModels = listOf(
    "models-realcugan/up2x-conservative"
)
val ncnnRealSrModels = listOf(
    "models-realsr"
)
val ncnnRealEsrganModels = listOf(
    "models-realesrgan"
)

private fun getDefaultModelForEngine(engine: NcnnEngine): String {
    return when (engine) {
        NcnnEngine.WAIFU2X -> ncnnWaifu2xModels.first()
        NcnnEngine.REALCUGAN -> ncnnRealCuganModels.first()
        NcnnEngine.REALSR -> ncnnRealSrModels.first()
        NcnnEngine.REAL_ESRGAN -> ncnnRealEsrganModels.first()
    }
}
