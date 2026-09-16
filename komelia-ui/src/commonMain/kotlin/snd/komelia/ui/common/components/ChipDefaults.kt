package snd.komelia.ui.common.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ChipColors
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import snd.komelia.ui.LocalAccentColor

@Composable
fun accentInputChipColors() = run {
    val accentColor = LocalAccentColor.current
    if (accentColor != null) {
        val onAccent = if (accentColor.luminance() > 0.5f) Color.Black else Color.White
        InputChipDefaults.inputChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            selectedContainerColor = accentColor,
            selectedLabelColor = onAccent,
            selectedLeadingIconColor = onAccent,
            selectedTrailingIconColor = onAccent,
        )
    } else {
        KoraChipDefaults.inputChipColors()
    }
}

@Composable
fun accentFilterChipColors() = run {
    val accentColor = LocalAccentColor.current
    if (accentColor != null) {
        val onAccent = if (accentColor.luminance() > 0.5f) Color.Black else Color.White
        FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            selectedContainerColor = accentColor,
            selectedLabelColor = onAccent,
            selectedLeadingIconColor = onAccent,
        )
    } else {
        KoraChipDefaults.filterChipColors()
    }
}

/**
 * Chips are filled, never outlined: an unselected chip sits on
 * `surfaceContainer`, a selected one on the accent. Before 1.8.21 every
 * chip drew a hairline in `outline`, and on a dark screen a row of
 * filters was a row of grey boxes. The screens pass these instead of the
 * Material defaults; [border] is what they pass for `border`.
 */
object KoraChipDefaults {
    val border: BorderStroke? = null

    @Composable
    fun filterChipColors(): SelectableChipColors = FilterChipDefaults.filterChipColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    )

    @Composable
    fun inputChipColors(): SelectableChipColors = InputChipDefaults.inputChipColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    )

    @Composable
    fun suggestionChipColors(): ChipColors = SuggestionChipDefaults.suggestionChipColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    )

    @Composable
    fun assistChipColors(): ChipColors = AssistChipDefaults.assistChipColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    )
}
