package snd.komelia.ui.library

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import snd.komelia.ui.LocalStrings

/**
 * Komga-style A-Z filter bar. Tap a letter to narrow the displayed series to
 * titles whose titleSort starts with that letter. "ALL" clears the filter;
 * "#" selects series whose titleSort starts with a digit (0-9).
 *
 * Layout: a [FlowRow] wraps to multiple rows on narrow screens so the bar
 * always fits the screen width with all chips visible. Chips are kept
 * compact (small font, tight padding) so on a typical tablet landscape the
 * 28 chips fit on a single line.
 *
 * Filtering is server-side via SeriesConditionBuilder.titleSort { beginsWith },
 * which uses Komga's indexed titleSort column — fast and accurate.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LetterFilterBar(
    selected: String?,
    onLetterClick: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    // One line of plain letters, like a phone book's index: twenty-eight
    // pills wrapped onto two rows and weighed more than the grid they filter.
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LetterChip(label = LocalStrings.current.ui.all2, isSelected = selected == null, onClick = { onLetterClick(null) })
        LetterChip(label = "#", isSelected = selected == "#", onClick = { onLetterClick("#") })
        ('A'..'Z').forEach { c ->
            val s = c.toString()
            LetterChip(label = s, isSelected = selected == s, onClick = { onLetterClick(s) })
        }
    }
}

@Composable
private fun LetterChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val onAccent = MaterialTheme.colorScheme.onPrimary
    val wide = label.length > 1
    Text(
        text = label,
        fontSize = 12.sp,
        color = when {
            isSelected && wide -> onAccent
            isSelected -> accent
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (isSelected && wide) accent else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = if (wide) 10.dp else 4.dp, vertical = 8.dp),
    )
}
