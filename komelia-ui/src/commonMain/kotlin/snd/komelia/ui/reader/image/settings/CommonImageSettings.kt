package snd.komelia.ui.reader.image.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import snd.komelia.settings.model.NightModeSettings
import snd.komelia.settings.model.ReaderFlashColor
import snd.komelia.ui.LocalAccentColor
import snd.komelia.ui.LocalPlatform
import snd.komelia.ui.LocalStrings
import snd.komelia.ui.common.components.AppSliderDefaults
import snd.komelia.ui.common.components.SwitchWithLabel
import snd.komelia.ui.common.components.accentInputChipColors
import snd.komelia.ui.platform.PlatformType
import kotlin.math.roundToInt
import kotlin.math.roundToLong

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CommonImageSettings(
    stretchToFit: Boolean,
    onStretchToFitChange: (Boolean) -> Unit,
    cropBorders: Boolean,
    onCropBordersChange: (Boolean) -> Unit,
    invertSpeechBubbles: Boolean,
    onInvertSpeechBubblesChange: (Boolean) -> Unit,
    webtoonSmartScroll: Boolean,
    onWebtoonSmartScrollChange: (Boolean) -> Unit,
    loadThumbnailPreviews: Boolean,
    onLoadThumbnailPreviewsChange: (Boolean) -> Unit,

    isColorCorrectionsActive: Boolean,
    onColorCorrectionClick: () -> Unit,

    nightMode: NightModeSettings,
    onNightModeChange: (NightModeSettings) -> Unit,

    flashEnabled: Boolean,
    onFlashEnabledChange: (Boolean) -> Unit,
    flashEveryNPages: Int,
    onFlashEveryNPagesChange: (Int) -> Unit,
    flashWith: ReaderFlashColor,
    onFlashWithChange: (ReaderFlashColor) -> Unit,
    flashDuration: Long,
    onFlashDurationChange: (Long) -> Unit,

    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    val readerStrings = strings.reader
    val platform = LocalPlatform.current
    val accentColor = LocalAccentColor.current
    Column(modifier = modifier) {
        SwitchWithLabel(
            checked = stretchToFit,
            onCheckedChange = onStretchToFitChange,
            label = { Text(readerStrings.stretchToFit) },
            contentPadding = PaddingValues(horizontal = 10.dp)
        )

        if (LocalPlatform.current != PlatformType.WEB_KOMF) {
            SwitchWithLabel(
                checked = cropBorders,
                onCheckedChange = onCropBordersChange,
                label = { Text(LocalStrings.current.ui.cropBorders) },
                contentPadding = PaddingValues(horizontal = 10.dp)
            )

            SwitchWithLabel(
                checked = invertSpeechBubbles,
                onCheckedChange = onInvertSpeechBubblesChange,
                label = { Text(LocalStrings.current.ui.invertSpeechBubbles) },
                supportingText = { Text(LocalStrings.current.ui.blackBubbleWhiteTextArtwork) },
                contentPadding = PaddingValues(horizontal = 10.dp)
            )

            SwitchWithLabel(
                checked = webtoonSmartScroll,
                onCheckedChange = onWebtoonSmartScrollChange,
                label = { Text(LocalStrings.current.ui.webtoonSmartScroll) },
                supportingText = { Text(LocalStrings.current.ui.tappingInAVerticalStrip) },
                contentPadding = PaddingValues(horizontal = 10.dp)
            )

            SwitchWithLabel(
                checked = loadThumbnailPreviews,
                onCheckedChange = onLoadThumbnailPreviewsChange,
                label = { Text(LocalStrings.current.ui.loadSmallPreviews) },
                supportingText = { Text(LocalStrings.current.ui.showsAThumbnailWhileDragging) },
                contentPadding = PaddingValues(horizontal = 10.dp)
            )
        }

        Row(
            modifier = Modifier
                .clickable { onColorCorrectionClick() }
                .pointerHoverIcon(PointerIcon.Hand)
                .padding(horizontal = 10.dp, vertical = 15.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(LocalStrings.current.ui.colorCorrection)
            Spacer(Modifier.width(10.dp))
            Icon(
                imageVector = Icons.Default.BarChart,
                contentDescription = null,
                tint = if (isColorCorrectionsActive) MaterialTheme.colorScheme.secondary
                else LocalContentColor.current
            )
            if (isColorCorrectionsActive) {
                Text(
                    LocalStrings.current.ui.active,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }

        SwitchWithLabel(
            checked = nightMode.enabled,
            onCheckedChange = { onNightModeChange(nightMode.copy(enabled = it)) },
            label = { Text(LocalStrings.current.ui.nightMode) },
            supportingText = { Text(LocalStrings.current.ui.nightModeWarmTintOnPages) },
            contentPadding = PaddingValues(horizontal = 10.dp)
        )
        AnimatedVisibility(nightMode.enabled) {
            Column(
                verticalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.padding(start = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.width(100.dp)) {
                        Text(
                            LocalStrings.current.ui.nightModeIntensity,
                            style = MaterialTheme.typography.labelLarge
                        )
                        Text(
                            "${(nightMode.intensity * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    Slider(
                        value = nightMode.intensity,
                        onValueChange = { onNightModeChange(nightMode.copy(intensity = it)) },
                        steps = 9,
                        valueRange = 0f..1f,
                        colors = AppSliderDefaults.colors(accentColor = accentColor)
                    )
                }

                SwitchWithLabel(
                    checked = nightMode.scheduleEnabled,
                    onCheckedChange = { onNightModeChange(nightMode.copy(scheduleEnabled = it)) },
                    label = { Text(LocalStrings.current.ui.nightModeSchedule) },
                    contentPadding = PaddingValues(horizontal = 0.dp)
                )
                AnimatedVisibility(nightMode.scheduleEnabled) {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TimeOfDayField(
                                label = LocalStrings.current.ui.nightModeFrom,
                                minuteOfDay = nightMode.startMinute,
                                onMinuteOfDayChange = {
                                    onNightModeChange(nightMode.copy(startMinute = it))
                                },
                            )
                            TimeOfDayField(
                                label = LocalStrings.current.ui.nightModeTo,
                                minuteOfDay = nightMode.endMinute,
                                onMinuteOfDayChange = {
                                    onNightModeChange(nightMode.copy(endMinute = it))
                                },
                            )
                        }
                    }
                }
            }
        }

        if (platform != PlatformType.DESKTOP) {
            SwitchWithLabel(
                checked = flashEnabled,
                onCheckedChange = onFlashEnabledChange,
                label = { Text(LocalStrings.current.ui.flashOnPageChange) },
                supportingText = { Text(LocalStrings.current.ui.preventsGhostingOnEInk) },
                contentPadding = PaddingValues(horizontal = 10.dp)
            )
            AnimatedVisibility(flashEnabled) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.padding(start = 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.width(100.dp)) {
                            Text(LocalStrings.current.ui.flashDuration, style = MaterialTheme.typography.labelLarge)
                            Text("$flashDuration ms", style = MaterialTheme.typography.labelMedium)
                        }
                        Slider(
                            value = flashDuration.toFloat(),
                            onValueChange = { onFlashDurationChange(it.roundToLong()) },
                            steps = 13,
                            valueRange = 100f..1500f,
                            colors = AppSliderDefaults.colors(accentColor = accentColor)
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.width(100.dp)) {
                            Text(LocalStrings.current.ui.flashEvery, style = MaterialTheme.typography.labelLarge)
                            val pagesText = remember(flashEveryNPages) {
                                if (flashEveryNPages == 1) "$flashEveryNPages page"
                                else "$flashEveryNPages pages"
                            }
                            Text(pagesText, style = MaterialTheme.typography.labelMedium)
                        }
                        Slider(
                            value = flashEveryNPages.toFloat(),
                            onValueChange = { onFlashEveryNPagesChange(it.roundToInt()) },
                            steps = 10,
                            valueRange = 1f..10f,
                            colors = AppSliderDefaults.colors(accentColor = accentColor)
                        )
                    }

                    Column {
                        Text(LocalStrings.current.ui.flashWith)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            InputChip(
                                selected = flashWith == ReaderFlashColor.BLACK,
                                onClick = { onFlashWithChange(ReaderFlashColor.BLACK) },
                                colors = accentInputChipColors(),
                                label = { Text(LocalStrings.current.ui.black) }
                            )
                            InputChip(
                                selected = flashWith == ReaderFlashColor.WHITE,
                                onClick = { onFlashWithChange(ReaderFlashColor.WHITE) },
                                colors = accentInputChipColors(),
                                label = { Text(LocalStrings.current.ui.white) }
                            )
                            InputChip(
                                selected = flashWith == ReaderFlashColor.WHITE_AND_BLACK,
                                onClick = { onFlashWithChange(ReaderFlashColor.WHITE_AND_BLACK) },
                                colors = accentInputChipColors(),
                                label = { Text(LocalStrings.current.ui.whiteAndBlack) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * One end of the night-mode schedule: the time as a tappable field that opens
 * the platform time picker.
 *
 * This replaced a pair of sliders that moved in quarter hours. A slider is 96
 * indistinguishable stops under a thumb; the picker lets you aim at an hour,
 * or type it. Snapping to quarter hours went with the sliders — it only ever
 * existed to make them usable, and a dial that accepts 22:07 while the field
 * reads 22:00 is worse than no constraint at all.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeOfDayField(
    label: String,
    minuteOfDay: Int,
    onMinuteOfDayChange: (Int) -> Unit,
) {
    var editing by remember { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 8.dp),
        )
        OutlinedButton(onClick = { editing = true }) {
            Text(formatMinuteOfDay(minuteOfDay), style = MaterialTheme.typography.titleMedium)
        }
    }

    if (editing) {
        val state = rememberTimePickerState(
            initialHour = minuteOfDay / 60,
            initialMinute = minuteOfDay % 60,
            is24Hour = true,
        )
        // Dial by default, keyboard for anyone who already knows the time they
        // want. Both write the same state, so the toggle keeps what was typed.
        var keyboard by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text(label) },
            text = {
                Column {
                    if (keyboard) TimeInput(state) else TimePicker(state)
                }
            },
            dismissButton = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { keyboard = !keyboard }) {
                        Icon(
                            imageVector = if (keyboard) Icons.Default.Schedule
                            else Icons.Default.Keyboard,
                            contentDescription = null,
                        )
                    }
                    TextButton(onClick = { editing = false }) {
                        Text(LocalStrings.current.ui.cancel)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onMinuteOfDayChange(state.hour * 60 + state.minute)
                    editing = false
                }) { Text(LocalStrings.current.ui.ok) }
            },
        )
    }
}

private fun formatMinuteOfDay(minuteOfDay: Int): String {
    val hours = minuteOfDay / 60
    val minutes = minuteOfDay % 60
    return "${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}"
}
