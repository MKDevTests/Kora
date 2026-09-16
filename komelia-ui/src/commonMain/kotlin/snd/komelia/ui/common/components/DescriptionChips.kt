package snd.komelia.ui.common.components

import snd.komelia.ui.KoraShapes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape
import snd.komelia.ui.LocalAccentColor
import snd.komelia.ui.LocalUseNewLibraryUI
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun <T> DescriptionChips(
    label: String,
    chipValue: LabeledEntry<T>,
    onClick: (T) -> Unit = {},
    modifier: Modifier = Modifier
) {
    DescriptionChips(
        label = label,
        chipValues = listOf(chipValue),
        onChipClick = onClick,
        modifier = modifier
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> DescriptionChips(
    label: String,
    chipValues: List<LabeledEntry<T>>,
    secondaryValues: List<LabeledEntry<T>>? = null,
    onChipClick: (T) -> Unit = {},
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
) {
    if (chipValues.isEmpty() && secondaryValues.isNullOrEmpty()) return
    Row(modifier = modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.width(120.dp)
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            chipValues.forEach { entry ->
                NoPaddingChip(onClick = { onChipClick(entry.value) }) {
                    Text(entry.label, style = MaterialTheme.typography.labelMedium)
                    icon?.let { Icon(it, null, modifier = Modifier.size(18.dp)) }
                }
            }
            secondaryValues?.filter { it !in chipValues }?.forEach { entry ->
                NoPaddingChip(
                    borderColor = MaterialTheme.colorScheme.primary,
                    onClick = { onChipClick(entry.value) }) {
                    Text(entry.label, style = MaterialTheme.typography.labelMedium)
                    icon?.let { Icon(it, null, modifier = Modifier.size(18.dp)) }
                }
            }

        }

    }
}


@Composable
fun NoPaddingChip(
    borderColor: Color = MaterialTheme.colorScheme.outline,
    color: Color = Color.Unspecified,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .border(Dp.Hairline, borderColor, KoraShapes.small)
            .clip(KoraShapes.small)
            .background(color)
            .clickable { onClick() }
            .padding(10.dp, 5.dp)
            .pointerHoverIcon(PointerIcon.Hand),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            content()
        }
    }
}


object AppFilterChipDefaults {

    @Composable
    fun shape(): Shape {
        return FilterChipDefaults.shape
    }

    @Composable
    fun filterChipColors(): androidx.compose.material3.SelectableChipColors {
        val accent = LocalAccentColor.current ?: MaterialTheme.colorScheme.primary
        val onAccent = if (0.299 * accent.red + 0.587 * accent.green + 0.114 * accent.blue > 0.5f)
            Color.Black else Color.White
        return FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            labelColor = MaterialTheme.colorScheme.onSurface,
            selectedContainerColor = accent,
            selectedLabelColor = onAccent,
        )
    }

    @Composable
    fun filterChipBorder(selected: Boolean): BorderStroke? {
        // Filled chips, see KoraChipDefaults.
        return null
    }
}

object AppSuggestionChipDefaults {
    @Composable
    fun shape(): Shape {
        return androidx.compose.material3.SuggestionChipDefaults.shape
    }
}