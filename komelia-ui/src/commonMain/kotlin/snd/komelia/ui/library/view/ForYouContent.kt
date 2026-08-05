package snd.komelia.ui.library.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import snd.komelia.ui.LocalFloatingToolbarPadding
import snd.komelia.ui.LocalStrings
import snd.komelia.ui.LocalTransparentNavBarPadding
import snd.komelia.ui.common.cards.SeriesImageCard
import snd.komelia.ui.suggestions.ReasonPill
import snd.komelia.ui.suggestions.SuggestionActions
import snd.komelia.ui.library.ForYouSuggestion
import snd.komelia.ui.library.ForYouSectionTitle
import snd.komelia.ui.library.LibraryForYouTabState
import snd.komelia.ui.suggestions.ForYouSourceKind
import snd.komga.client.series.KomgaSeries

/**
 * Library "For you" tab: what to read next in this library, from what the user
 * has already read and rated here.
 *
 * Same grid as the Series tab, so suggestions read as part of the library and
 * not as a widget bolted on. The tab does its work on first open only — it
 * reuses the term index built by the series "Similar" tab.
 */
@Composable
fun ForYouContent(
    state: LibraryForYouTabState,
    onSeriesClick: (KomgaSeries) -> Unit,
    beforeContent: @Composable () -> Unit,
) {
    LaunchedEffect(Unit) { state.onOpened() }
    val cardWidth = state.cardWidth.collectAsState().value
    // Same insets as every other grid on this screen: without the toolbar
    // padding the first row slides under the floating top bar and hides the
    // library name.
    val toolbarPadding = LocalFloatingToolbarPadding.current
    val extraBottomPadding = LocalTransparentNavBarPadding.current

    LazyVerticalGrid(
        columns = GridCells.Adaptive(cardWidth),
        horizontalArrangement = Arrangement.spacedBy(15.dp),
        contentPadding = PaddingValues(
            start = 10.dp,
            end = 10.dp,
            top = toolbarPadding,
            bottom = 15.dp + extraBottomPadding,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { beforeContent() }
        item(span = { GridItemSpan(maxLineSpan) }) { ForYouHeader(state) }

        val top = state.topMatches
        if (top.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(LocalStrings.current.suggestions.closestMatches, style = MaterialTheme.typography.titleSmall)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(top.size) { index ->
                            SuggestionCard(
                                suggestion = top[index],
                                onSeriesClick = onSeriesClick,
                                onDismiss = state::dismiss,
                                width = cardWidth * 1.35f,
                            )
                        }
                    }
                }
            }
        }
        state.sections.forEachIndexed { sectionIndex, section ->
            if (section.title != null) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "header:$sectionIndex") {
                    Text(
                        sectionTitle(section.title),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                    )
                }
            }
            items(
                count = section.items.size,
                key = { index -> "$sectionIndex:${section.items[index].series.id.value}" },
            ) { index ->
                SuggestionCard(section.items[index], onSeriesClick, state::dismiss)
            }
        }
    }
}

/** The heading of a section, in the language the user picked. */
@Composable
private fun sectionTitle(title: ForYouSectionTitle): String {
    val strings = LocalStrings.current.suggestions
    return when (title) {
        ForYouSectionTitle.More -> strings.moreForYou
        is ForYouSectionTitle.Source -> when (title.kind) {
            ForYouSourceKind.LIKED -> strings.becauseYouLiked(title.name)
            ForYouSourceKind.READ -> strings.becauseYouRead(title.name)
            ForYouSourceKind.READING -> strings.becauseYouAreReading(title.name)
        }
    }
}

@Composable
private fun ForYouHeader(state: LibraryForYouTabState) {
    val strings = LocalStrings.current.suggestions
    val progress = state.buildProgress
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when {
            progress != null -> {
                Text(
                    strings.analysingLibrary((progress * 100).toInt()),
                    style = MaterialTheme.typography.bodyMedium,
                )
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            }

            state.isLoading -> Text(strings.buildingProfile, style = MaterialTheme.typography.bodyMedium)

            state.failed -> Text(
                strings.failed,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )

            state.suggestions.isEmpty() -> Text(
                if (state.profileSize == 0) strings.emptyNoProfile else strings.emptyAllRead,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilterChip(
                selected = state.includeRead,
                onClick = { state.toggleIncludeRead() },
                label = { Text(strings.showRead) },
            )
            if (state.dismissedCount > 0) {
                // An irreversible one-tap action needs a way back in sight of
                // where it is taken.
                TextButton(onClick = { state.resetDismissed() }) {
                    Text(strings.resetDismissed(state.dismissedCount))
                }
            }
            if (state.profileSize > 0) {
                Text(
                    // Says what the suggestions are based on, so a surprising
                    // list can be understood instead of just distrusted.
                    strings.profileSize(state.profileSize),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SuggestionCard(
    suggestion: ForYouSuggestion,
    onSeriesClick: (KomgaSeries) -> Unit,
    onDismiss: (snd.komga.client.series.KomgaSeriesId) -> Unit,
    width: Dp? = null,
) {
    Column(modifier = if (width != null) Modifier.width(width) else Modifier) {
        SeriesImageCard(
            series = suggestion.series,
            onSeriesClick = { onSeriesClick(suggestion.series) },
            modifier = if (width != null) Modifier.width(width) else Modifier,
        )
        ReasonPill(suggestion.reasons, modifier = Modifier.padding(top = 4.dp))
        SuggestionActions(suggestion.series.id, onDismiss)
        Spacer(Modifier.height(15.dp))
    }
}
