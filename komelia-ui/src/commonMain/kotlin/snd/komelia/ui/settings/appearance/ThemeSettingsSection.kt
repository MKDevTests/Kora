package snd.komelia.ui.settings.appearance

import kotlin.math.roundToInt
import snd.komelia.ui.common.components.AppSlider
import snd.komelia.settings.model.TitleFont
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import snd.komelia.settings.model.AppTheme
import snd.komelia.ui.KoraShapes
import snd.komelia.ui.LocalStrings
import snd.komelia.ui.LocalTheme
import snd.komelia.ui.Theme
import snd.komelia.ui.common.components.DropdownChoiceMenu
import snd.komelia.ui.common.components.KoraChipDefaults
import snd.komelia.ui.common.components.LabeledEntry
import snd.komelia.ui.common.components.SwitchWithLabel
import snd.komelia.ui.hslColor
import snd.komelia.ui.toHsl

internal fun hourLabel(minutes: Int): String {
    val h = (minutes / 60) % 24
    val m = minutes % 60
    return "${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}"
}

/** A tiny hue nudge that keeps the colour recognisable but no longer equal to a preset. */
internal fun Color.withHueShift(degrees: Float): Color {
    val (h, s, l) = toHsl()
    return hslColor(h + degrees, s, l)
}

@Composable
internal fun PaletteSwatch(
    label: String,
    color: Color?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier.clip(KoraShapes.small).clickable(onClick = onClick).padding(vertical = 4.dp),
    ) {
        val ring = if (selected) Modifier.border(2.dp, color ?: MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier
        Box(
            modifier = Modifier
                .size(44.dp)
                .then(ring)
                .padding(if (selected) 4.dp else 0.dp)
                .clip(CircleShape)
                .then(
                    if (color != null) Modifier.background(color)
                    else Modifier.border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
                ),
            contentAlignment = Alignment.Center,
        ) {
            when {
                color == null -> Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                selected -> Icon(
                    Icons.Rounded.Check, null,
                    tint = if (color.luminance() > 0.5f) Color.Black else Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/**
 * Hue and saturation on gradient tracks, or a hex code. Lightness is not a
 * knob: the ramp in Theme sets it per role, so a seed only needs a hue and
 * how strong it is.
 */
@Composable
internal fun CustomSeedEditor(seed: Color, onSeedChange: (Color) -> Unit) {
    val strings = LocalStrings.current.ui
    val (h0, s0, _) = seed.toHsl()
    var hue by remember(seed.toArgb()) { mutableStateOf(h0) }
    var sat by remember(seed.toArgb()) { mutableStateOf(s0) }
    var hex by remember(seed.toArgb()) { mutableStateOf(seed.toHex()) }
    fun commit(h: Float, s: Float) = onSeedChange(hslColor(h, s, 0.68f))

    Column(
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(strings.customColor, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            OutlinedTextField(
                value = hex,
                onValueChange = { raw ->
                    val clean = raw.trimStart('#').take(6).uppercase()
                    hex = "#$clean"
                    if (clean.length == 6) {
                        clean.toLongOrNull(16)?.let { rgb ->
                            val c = Color((0xFF000000L or rgb).toInt())
                            val (h, s, _) = c.toHsl()
                            hue = h; sat = s
                            commit(h, s)
                        }
                    }
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
                leadingIcon = { Box(Modifier.size(16.dp).clip(KoraShapes.small).background(seed)) },
                modifier = Modifier.width(150.dp),
            )
        }
        Text(strings.hue, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        GradientSlider(
            value = hue,
            range = 0f..360f,
            brush = Brush.horizontalGradient(
                (0..6).map { hslColor(it * 60f, 0.85f, 0.65f) },
            ),
            thumbColor = hslColor(hue, sat.coerceAtLeast(0.3f), 0.68f),
            onValueChange = { hue = it },
            onValueChangeFinished = { commit(hue, sat) },
        )
        Text(strings.saturation, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        GradientSlider(
            value = sat,
            range = 0f..1f,
            brush = Brush.horizontalGradient(listOf(hslColor(hue, 0f, 0.68f), hslColor(hue, 1f, 0.68f))),
            thumbColor = hslColor(hue, sat, 0.68f),
            onValueChange = { sat = it },
            onValueChangeFinished = { commit(hue, sat) },
        )
    }
}

@Composable
private fun GradientSlider(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    brush: Brush,
    thumbColor: Color,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        valueRange = range,
        track = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .clip(CircleShape)
                    .background(brush)
            )
        },
        thumb = {
            Box(
                Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .padding(3.dp)
                    .clip(CircleShape)
                    .background(thumbColor)
            )
        },
        colors = SliderDefaults.colors(),
    )
}

private fun Color.toHex(): String {
    val argb = toArgb()
    return "#" + (argb and 0xFFFFFF).toString(16).uppercase().padStart(6, '0')
}

/** What the palette does to the controls people actually touch. */
@Composable
internal fun ThemePreview(modifier: Modifier = Modifier) {
    val strings = LocalStrings.current
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(KoraShapes.medium)
            .background(scheme.surfaceContainer)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(KoraShapes.medium)
                    .background(scheme.primary)
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.AutoMirrored.Rounded.MenuBook, null, tint = scheme.onPrimary, modifier = Modifier.size(18.dp))
                Text(strings.ui.resume, color = scheme.onPrimary, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
                Text(strings.counts.pageOf(64, 232), color = scheme.onPrimary.copy(alpha = 0.75f), style = MaterialTheme.typography.labelMedium)
            }
            Box(Modifier.size(44.dp).clip(KoraShapes.medium).background(scheme.surfaceContainerHighest))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(selected = true, onClick = {}, label = { Text(strings.ui.all) }, colors = KoraChipDefaults.filterChipColors(), border = KoraChipDefaults.border)
            FilterChip(selected = false, onClick = {}, label = { Text(strings.seriesFilter.readStatusInProgress) }, colors = KoraChipDefaults.filterChipColors(), border = KoraChipDefaults.border)
            Spacer(Modifier.weight(1f))
            Switch(checked = true, onCheckedChange = {})
        }
        Slider(value = 0.62f, onValueChange = {}, enabled = true)
    }
}
