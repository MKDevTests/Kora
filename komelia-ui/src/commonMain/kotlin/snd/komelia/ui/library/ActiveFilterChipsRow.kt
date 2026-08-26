package snd.komelia.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import snd.komelia.ui.LocalStrings
import snd.komelia.ui.common.components.AppFilterChipDefaults
import snd.komelia.ui.series.ActiveFilter
import snd.komga.client.book.KomgaReadStatus
import snd.komga.client.series.KomgaSeriesStatus

/**
 * Names, one chip at a time, every criterion currently narrowing the grid — and
 * lets each one be dropped on its own.
 *
 * The screen this sits on used to say nothing at all. Tapping an author on a
 * series opens the library with that author applied; the destination is titled
 * with the library's name, reports "1 SERIES", and mentions the author nowhere.
 * A narrow filter is then indistinguishable from an empty library, and the only
 * way out is to open the filter panel and guess what to clear. The one existing
 * signal — a tinted filter icon — said that something was filtered, never what.
 *
 * Renders nothing when the filter is untouched, so an ordinary library keeps
 * the vertical space.
 */
@Composable
fun ActiveFilterChipsRow(
    filters: List<ActiveFilter>,
    onRemove: (ActiveFilter) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    if (filters.isEmpty()) return
    val strings = LocalStrings.current

    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = contentPadding,
    ) {
        items(
            items = filters,
            // Kind AND value: several chips can share a kind (three genres), and
            // one value can appear under two kinds (a word that is both a tag
            // and a genre). Either half alone collides.
            key = { "${it.kind}:${it.value}" },
        ) { filter ->
            AssistChip(
                // The whole chip removes, not just the cross: the cross is a
                // 18dp target on a screen held in two hands.
                onClick = { onRemove(filter) },
                label = { Text(activeFilterLabel(filter)) },
                shape = AppFilterChipDefaults.shape(),
                trailingIcon = {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = strings.ui.removeFilter,
                        modifier = Modifier.size(18.dp),
                    )
                },
            )
        }

        // Only past two criteria: with a single chip its own cross already
        // clears everything, and two controls doing the same thing side by side
        // is a question the user should not have to answer.
        if (filters.size >= 2) {
            item(key = "clear-all") {
                AssistChip(
                    onClick = onClearAll,
                    label = { Text(strings.ui.clearAll) },
                    shape = AppFilterChipDefaults.shape(),
                )
            }
        }
    }
}

/**
 * The prefix is what makes the value readable: "Callixte" on its own could be a
 * series, a publisher or a tag.
 *
 * Read statuses and publication statuses reuse the filter panel's own
 * translations rather than showing an enum name.
 */
@Composable
private fun activeFilterLabel(filter: ActiveFilter): String {
    val strings = LocalStrings.current
    val panel = strings.seriesFilter
    return when (filter.kind) {
        ActiveFilter.Kind.SEARCH -> "${strings.ui.search} : \"${filter.value}\""
        ActiveFilter.Kind.LETTER -> "${strings.ui.filterLetter} : ${filter.value}"
        ActiveFilter.Kind.AUTHOR -> "${strings.ui.author} : ${filter.value}"
        ActiveFilter.Kind.GENRE -> "${strings.ui.genre} : ${filter.value}"
        ActiveFilter.Kind.GENRE_EXCLUDED ->
            "${strings.ui.genre} ${strings.ui.filterExcluded} : ${filter.value}"

        ActiveFilter.Kind.TAG -> "${strings.ui.filterTag} : ${filter.value}"
        ActiveFilter.Kind.TAG_EXCLUDED ->
            "${strings.ui.filterTag} ${strings.ui.filterExcluded} : ${filter.value}"

        ActiveFilter.Kind.PUBLISHER -> "${panel.publisher} : ${filter.value}"
        ActiveFilter.Kind.LANGUAGE -> "${panel.language} : ${filter.value}"
        ActiveFilter.Kind.AGE_RATING -> "${panel.ageRating} : ${filter.value}"
        ActiveFilter.Kind.RELEASE_DATE -> "${panel.releaseDate} : ${filter.value}"
        // An unknown enum name means the stored filter predates this build.
        // Showing the raw value beats dropping the chip, which would leave a
        // criterion active with nothing on screen admitting to it.
        ActiveFilter.Kind.READ_STATUS ->
            runCatching { panel.forSeriesReadStatus(KomgaReadStatus.valueOf(filter.value)) }
                .getOrDefault(filter.value)

        ActiveFilter.Kind.PUBLICATION_STATUS ->
            runCatching { panel.forPublicationStatus(KomgaSeriesStatus.valueOf(filter.value)) }
                .getOrDefault(filter.value)

        ActiveFilter.Kind.COMPLETION -> panel.complete
        ActiveFilter.Kind.FORMAT -> panel.oneshot
        ActiveFilter.Kind.MIN_RATING -> "${strings.ui.filterRating} ≥ ${filter.value}"
    }
}
