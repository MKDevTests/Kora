package snd.komelia.ui.library.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import snd.komelia.ui.common.cards.SeriesImageCard
import snd.komelia.ui.library.ForYouSuggestion
import snd.komelia.ui.library.LibraryForYouTabState
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

    LazyVerticalGrid(
        columns = GridCells.Adaptive(cardWidth),
        horizontalArrangement = Arrangement.spacedBy(15.dp),
        contentPadding = PaddingValues(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { beforeContent() }
        item(span = { GridItemSpan(maxLineSpan) }) { ForYouHeader(state) }

        items(state.suggestions.size) { index ->
            SuggestionCard(state.suggestions[index], onSeriesClick)
        }
    }
}

@Composable
private fun ForYouHeader(state: LibraryForYouTabState) {
    val progress = state.buildProgress
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when {
            progress != null -> {
                Text(
                    "Analysing the library… ${(progress * 100).toInt()} %",
                    style = MaterialTheme.typography.bodyMedium,
                )
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            }

            state.isLoading -> Text("Building your profile…", style = MaterialTheme.typography.bodyMedium)

            state.failed -> Text(
                "Could not compute suggestions.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )

            state.suggestions.isEmpty() -> Text(
                if (state.profileSize == 0) {
                    "Nothing to go on yet. Read or rate a few series in this library and " +
                        "suggestions will appear here."
                } else {
                    "No suggestion left. Try showing the series you have already read."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilterChip(
                selected = state.includeRead,
                onClick = { state.toggleIncludeRead() },
                label = { Text("Show read") },
            )
            if (state.profileSize > 0) {
                Text(
                    // Says what the suggestions are based on, so a surprising
                    // list can be understood instead of just distrusted.
                    "Based on ${state.profileSize} series you have read or rated",
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
) {
    Column {
        SeriesImageCard(
            series = suggestion.series,
            onSeriesClick = { onSeriesClick(suggestion.series) },
        )
        Text(
            text = suggestion.reasons.take(3).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
        Spacer(Modifier.height(15.dp))
    }
}
