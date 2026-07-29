package snd.komelia.ui.series.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import snd.komelia.ui.common.cards.SeriesImageCard
import snd.komelia.ui.series.SimilarSeriesState
import snd.komelia.ui.series.SimilarSuggestion
import snd.komga.client.series.KomgaSeries

/**
 * "Similar" tab: series of the same library scored against this one.
 *
 * Emitted straight into the screen's grid, like the Books tab, rather than as
 * one full-width island: the suggestions then line up on the same adaptive
 * columns as the volumes instead of forming a ragged block of their own.
 *
 * Opening the tab is what triggers the work — the library index is built here,
 * with a progress bar, the first time. Already-read series stay in the list
 * (the cards carry the read badge); hidden and ignored ones are filtered out.
 */
fun LazyGridScope.SimilarSeriesContent(
    state: SimilarSeriesState,
    onSeriesClick: (KomgaSeries) -> Unit,
) {
    item(span = { GridItemSpan(maxLineSpan) }) {
        LaunchedEffect(Unit) { state.onOpened() }
        SimilarHeader(state)
    }

    items(state.suggestions, key = { it.series.id.value }) { suggestion ->
        SuggestionCard(suggestion, onSeriesClick)
    }

    if (state.buildProgress == null && !state.isLoading && !state.failed) {
        item(span = { GridItemSpan(maxLineSpan) }) { RebuildRow(state) }
    }
}

@Composable
private fun SimilarHeader(state: SimilarSeriesState) {
    val progress = state.buildProgress
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when {
            progress != null -> {
                // First open on a large library is about one request per 100
                // series, so show what it is doing rather than a bare spinner.
                Text(
                    "Analysing the library… ${(progress * 100).toInt()} %",
                    style = MaterialTheme.typography.bodyMedium,
                )
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                Text(
                    "Once per library. Suggestions are then computed on the device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            state.isLoading -> Text("Computing suggestions…", style = MaterialTheme.typography.bodyMedium)

            state.failed -> Text(
                "Could not compute suggestions.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )

            state.suggestions.isEmpty() -> Text(
                "No close series in this library. Genres and authors on this series " +
                    "would give better suggestions.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SuggestionCard(
    suggestion: SimilarSuggestion,
    onSeriesClick: (KomgaSeries) -> Unit,
) {
    // No width modifier: the grid cell sizes the card, the same way the Books
    // tab does. Forcing cardWidth here is what made the tab look off-grid.
    Column {
        SeriesImageCard(
            series = suggestion.series,
            onSeriesClick = { onSeriesClick(suggestion.series) },
        )
        // The reason is what makes a suggestion trustworthy instead of magic —
        // and what lets a bad one be diagnosed rather than just endured.
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

@Composable
private fun RebuildRow(state: SimilarSeriesState) {
    Column(
        modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        OutlinedButton(onClick = { state.rebuild() }) {
            Icon(Icons.Default.Refresh, null)
            Spacer(Modifier.width(8.dp))
            Text("Re-analyse library")
        }
        Text(
            "${state.indexedCount} series analysed. Re-run it after a large import or a tagging pass.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
