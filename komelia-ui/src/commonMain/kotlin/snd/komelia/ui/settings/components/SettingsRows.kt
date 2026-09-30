package snd.komelia.ui.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import snd.komelia.ui.LocalAccentColor
import snd.komelia.ui.LocalTheme
import snd.komelia.ui.Theme
import snd.komelia.ui.hslColor
import snd.komelia.ui.toHsl
import snd.komelia.ui.common.components.AppSlider
import snd.komelia.ui.common.components.AppSliderDefaults
import snd.komelia.ui.platform.cursorForHand

/*
 * The settings vocabulary of "Refonte 2": groups are rounded panels with a
 * small uppercase heading, rows carry their own padding, and a slider shows
 * its value on the right of its label instead of in a sentence above it.
 * Separators come from the rows (a hairline between two rows of the same
 * card), so a page no longer alternates text and full-width dividers.
 */

/** A titled panel of rows. [title] null: the panel alone. */
@Composable
fun SettingsCard(
    title: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        if (title != null) {
            Text(
                title.uppercase(),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                letterSpacing = 0.8.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 6.dp, top = 10.dp, bottom = 8.dp),
            )
        }
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(content = content)
        }
    }
}

/** Hairline between two rows of the same card. */
@Composable
fun SettingsRowDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

@Composable
fun SettingsSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    description: String? = null,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    iconTint: Color? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .cursorForHand()
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        if (icon != null) SettingsIcon(icon, iconTint ?: MaterialTheme.colorScheme.primary)
        SettingsTexts(label, description, Modifier.weight(1f), enabled)
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

/** Label on the left, current value on the right, slider underneath. */
@Composable
fun SettingsSliderRow(
    label: String,
    valueText: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    description: String? = null,
) {
    val accent = LocalAccentColor.current ?: MaterialTheme.colorScheme.primary
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SettingsTexts(label, description, Modifier.weight(1f))
            Text(
                valueText,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = accent,
            )
        }
        AppSlider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            colors = AppSliderDefaults.colors(accentColor = LocalAccentColor.current),
            modifier = Modifier.cursorForHand(),
        )
    }
}

/**
 * One choice among a few, as a segmented row of pills. Replaces the
 * dropdowns of short lists (unread badge style, title font…): all options
 * visible, one tap instead of two.
 */
@Composable
fun <T> SettingsChoiceRow(
    label: String?,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    description: String? = null,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        if (label != null) {
            SettingsTexts(label, description, Modifier.padding(bottom = 8.dp))
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .padding(3.dp),
        ) {
            options.forEach { (value, text) ->
                val isSelected = value == selected
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (isSelected) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent)
                        .clickable { onSelect(value) }
                        .cursorForHand()
                        .padding(vertical = 9.dp),
                ) {
                    Text(
                        text,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                        ),
                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** A row that opens a page: tinted icon, title, one-line summary, chevron. */
@Composable
fun SettingsNavRow(
    label: String,
    onClick: () -> Unit,
    summary: String? = null,
    icon: ImageVector? = null,
    iconTint: Color? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .cursorForHand()
            .heightIn(min = 60.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        if (icon != null) SettingsIcon(icon, iconTint ?: MaterialTheme.colorScheme.primary)
        SettingsTexts(label, summary, Modifier.weight(1f))
        trailing?.invoke()
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
    }
}

/** The icon of a settings row: tinted glyph in a disc of the same tint. */
@Composable
fun SettingsIcon(icon: ImageVector, tint: Color) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(36.dp).clip(CircleShape).background(tint.copy(alpha = 0.16f)),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun SettingsTexts(label: String, description: String?, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val alpha = if (enabled) 1f else 0.4f
    Column(modifier) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
        )
        if (!description.isNullOrBlank()) {
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
            )
        }
    }
}

/**
 * The pastel tints of the settings icons are made for a dark page; on a
 * light one they are pulled darker so the glyph keeps its contrast.
 */
@Composable
fun settingsTint(color: Color): Color {
    if (LocalTheme.current.type == Theme.ThemeType.DARK) return color
    val (h, s, _) = color.toHsl()
    return hslColor(h, s, 0.42f)
}
