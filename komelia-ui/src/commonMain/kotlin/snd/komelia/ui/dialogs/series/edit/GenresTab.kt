package snd.komelia.ui.dialogs.series.edit

import snd.komelia.ui.LocalStrings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import snd.komelia.ui.dialogs.tabs.DialogTab
import snd.komelia.ui.dialogs.tabs.TabItem
import snd.komelia.ui.library.GenreLabels

/** How many genres a series may carry. Above this the tab stops accepting picks. */
private const val MAX_GENRES = 4

/**
 * Admin-only tab that edits a series' Kora genres.
 *
 * Genres are stored as `kora:genre:<slug>` Komga *tags*, not the native `genres`
 * metadata field — that's what the Genre tab, the drill-down and the "Genres :"
 * line all read, so editing anything else would leave them untouched.
 *
 * Only the `kora:genre:*` entries of the tag list are rewritten; every other tag
 * is carried over verbatim, which is what keeps `kora:hidden` (and any manual
 * `kora:tag:*`) from being silently dropped on save. Saving itself is the
 * dialog's existing metadata patch — this tab only mutates the shared state.
 */
internal class GenresTab(
    private val vm: SeriesEditMetadataState,
) : DialogTab {

    @Composable
    override fun options() = TabItem(
        title = LocalStrings.current.ui.genres.uppercase(),
        icon = Icons.Default.Category
    )

    @Composable
    override fun Content() {
        val selected = vm.tags.filter { GenreLabels.isGenreTag(it) }.map { GenreLabels.slugOf(it) }.toSet()

        GenresContent(
            selected = selected,
            onToggle = { slug ->
                val newSelection = when {
                    slug in selected -> selected - slug
                    selected.size >= MAX_GENRES -> selected
                    else -> selected + slug
                }
                if (newSelection != selected) {
                    // Rebuild the tag list: non-genre tags first, untouched, then
                    // the picked genres in taxonomy order.
                    vm.tags = vm.tags.filterNot { GenreLabels.isGenreTag(it) } +
                            GenreLabels.allSlugs.filter { it in newSelection }.map { GenreLabels.tagOf(it) }
                }
            },
        )
    }
}

@Composable
private fun GenresContent(
    selected: Set<String>,
    onToggle: (String) -> Unit,
) {
    Column {
        Text(
            "${selected.size}/$MAX_GENRES genres selectionnes",
            style = MaterialTheme.typography.labelLarge,
            color = if (selected.size >= MAX_GENRES) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp),
        )

        GenreLabels.allSlugs.forEach { slug ->
            val isSelected = slug in selected
            // Unpicked genres go dim once the cap is reached, so the limit is
            // visible before the user taps and nothing happens.
            val atCap = !isSelected && selected.size >= MAX_GENRES
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !atCap) { onToggle(slug) }
                    .padding(vertical = 2.dp),
            ) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggle(slug) },
                    enabled = !atCap,
                )
                Text(
                    GenreLabels.label(slug),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (atCap) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Spacer(Modifier.height(30.dp))
    }
}
