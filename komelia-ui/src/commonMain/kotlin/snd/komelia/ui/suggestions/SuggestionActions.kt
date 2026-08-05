package snd.komelia.ui.suggestions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import snd.komelia.ui.LocalPlanned
import snd.komelia.ui.LocalStrings
import snd.komga.client.series.KomgaSeriesId

/**
 * The two answers a suggestion deserves: keep it, or never show it again.
 *
 * Without them a good suggestion cost three taps (open, menu, mark) and a bad
 * one came back forever. "Not interested" also feeds the taste profile, so the
 * user does not have to repeat themselves on every near-identical series.
 */
@Composable
fun SuggestionActions(
    seriesId: KomgaSeriesId,
    onDismiss: (KomgaSeriesId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val planned = LocalPlanned.current
    val strings = LocalStrings.current.suggestions
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        if (planned != null) {
            val isPlanned = seriesId.value in planned.plannedIds.collectAsState().value
            IconButton(onClick = { planned.toggle(seriesId) }, modifier = Modifier.size(28.dp)) {
                Icon(
                    imageVector = if (isPlanned) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                    contentDescription = if (isPlanned) strings.unmarkPlanned else strings.markPlanned,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = { onDismiss(seriesId) }, modifier = Modifier.size(28.dp)) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = strings.notInterested,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
