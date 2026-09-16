package snd.komelia.ui.common.cards

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import snd.komelia.ui.KoraShapes
import snd.komelia.ui.LocalHiddenAuthorRoles
import snd.komelia.ui.LocalHideParenthesesInNames
import snd.komelia.ui.LocalStrings
import snd.komelia.ui.common.authorRolesOrder
import snd.komelia.ui.common.images.SeriesThumbnail
import snd.komelia.ui.common.menus.SeriesActionsMenu
import snd.komelia.ui.common.menus.SeriesMenuActions
import snd.komelia.utils.removeParentheses
import snd.komga.client.series.KomgaSeries

/**
 * One series as a list line: cover, title, writers, book counts and a thin
 * read-progress bar, with the same long-press / ⋮ actions menu as the grid
 * card. Used by the library series tab when the header toggle says list.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SeriesListRow(
    series: KomgaSeries,
    onSeriesClick: (() -> Unit)? = null,
    isSelected: Boolean = false,
    onSeriesSelect: (() -> Unit)? = null,
    seriesMenuActions: SeriesMenuActions? = null,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    val hideParentheses = LocalHideParenthesesInNames.current
    val title = if (hideParentheses) series.metadata.title.removeParentheses() else series.metadata.title

    val hiddenAuthorRoles = LocalHiddenAuthorRoles.current
    val writers = remember(series.booksMetadata.authors, hiddenAuthorRoles) {
        val roles = if (hiddenAuthorRoles == null) listOf("writer")
        else authorRolesOrder.filterNot { it in hiddenAuthorRoles }
        series.booksMetadata.authors
            .filter { it.role.lowercase() in roles }
            .distinctBy { it.name }
            .joinToString(", ") { it.name }
    }

    var isMenuExpanded by remember { mutableStateOf(false) }
    val longClick: (() -> Unit)? = when {
        seriesMenuActions != null -> { -> isMenuExpanded = true }
        else -> onSeriesSelect
    }
    val selectedBackground = if (isSelected) Modifier.background(MaterialTheme.colorScheme.secondary.copy(alpha = .3f))
    else Modifier

    val total = series.booksCount
    val unread = series.booksUnreadCount
    val counts = buildString {
        append(strings.counts.booksCount(total))
        append(" · ")
        append(
            when {
                unread <= 0 -> strings.ui.allRead
                unread == 1 -> strings.ui.unreadCountOne
                else -> strings.ui.unreadCountMany(unread)
            }
        )
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(KoraShapes.medium)
            .then(selectedBackground)
            .combinedClickable(onClick = onSeriesClick ?: {}, onLongClick = longClick)
            .padding(horizontal = 6.dp, vertical = 5.dp)
            .alpha(if (series.deleted) 0.5f else 1f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SeriesThumbnail(
            series.id,
            modifier = Modifier.size(width = 64.dp, height = 90.dp).clip(KoraShapes.small),
            contentScale = ContentScale.Crop,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (writers.isNotEmpty()) {
                Text(
                    writers,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                counts,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            if (total > 0 && series.booksReadCount in 1 until total) {
                LinearProgressIndicator(
                    progress = { series.booksReadCount.toFloat() / total },
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth().height(3.dp).padding(top = 1.dp),
                    drawStopIndicator = {},
                )
            }
        }
        if (seriesMenuActions != null) {
            Box {
                IconButton(onClick = { isMenuExpanded = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                SeriesActionsMenu(
                    series = series,
                    actions = seriesMenuActions,
                    expanded = isMenuExpanded,
                    showEditOption = true,
                    showDownloadOption = true,
                    onDismissRequest = { isMenuExpanded = false },
                    onSelect = onSeriesSelect,
                )
            }
        }
    }
}
