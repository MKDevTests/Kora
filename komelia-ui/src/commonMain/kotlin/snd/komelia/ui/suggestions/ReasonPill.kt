package snd.komelia.ui.suggestions

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * The headline reason for one suggestion, as a pill.
 *
 * Three reasons stacked under every cover turned a page of suggestions into a
 * wall of small grey text — the signal that makes a suggestion trustworthy was
 * drowning the covers it was meant to support. One short reason carries the
 * same trust; the count says there is more behind it.
 */
@Composable
fun ReasonPill(
    reasons: List<String>,
    modifier: Modifier = Modifier,
) {
    val headline = reasons.firstOrNull() ?: return
    val extra = reasons.size - 1
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            text = if (extra > 0) "$headline  +$extra" else headline,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
