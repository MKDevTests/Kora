package snd.komelia.ui.series.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import snd.komelia.ui.LoadState
import snd.komelia.ui.common.cards.SeriesImageCard
import snd.komelia.ui.series.SimilarSeriesState
import snd.komelia.ui.series.SimilarSuggestion
import snd.komga.client.series.KomgaSeries

/**
 * "Similaire" tab: series of the same library scored against this one.
 *
 * Opening the tab is what triggers the work — the index is built here, with a
 * progress bar, if the library has never been indexed. Already-read series stay
 * in the list (the cards carry the read badge); hidden and ignored ones are
 * filtered out upstream.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SimilarSeriesContent(
    state: SimilarSeriesState,
    onSeriesClick: (KomgaSeries) -> Unit,
) {
    LaunchedEffect(Unit) { state.onOpened() }

    val cardWidth = state.cardWidth.collectAsState().value
    val loadState by state.state.collectAsState()
    val progress = state.buildProgress

    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (progress != null) {
            // First open on a large library is ~one request per 100 series, so
            // show what it is doing rather than an opaque spinner.
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Analyse de la bibliothèque… ${(progress * 100).toInt()} %",
                    style = MaterialTheme.typography.bodyMedium,
                )
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                Text(
                    "Une seule fois par bibliothèque. Les suggestions sont ensuite calculées sur l'appareil.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Column
        }

        when (loadState) {
            is LoadState.Error -> Text(
                "Impossible de calculer les suggestions.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )

            is LoadState.Success -> {
                if (state.suggestions.isEmpty()) {
                    Text(
                        "Aucune série proche dans cette bibliothèque. Des genres et des auteurs " +
                            "sur cette série donneraient de meilleures suggestions.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        state.suggestions.forEach { suggestion ->
                            SuggestionCard(suggestion, cardWidth, onSeriesClick)
                        }
                    }
                }
                RebuildRow(state)
            }

            else -> Text("Calcul des suggestions…", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SuggestionCard(
    suggestion: SimilarSuggestion,
    cardWidth: Dp,
    onSeriesClick: (KomgaSeries) -> Unit,
) {
    Column(modifier = Modifier.width(cardWidth)) {
        SeriesImageCard(
            series = suggestion.series,
            onSeriesClick = { onSeriesClick(suggestion.series) },
            modifier = Modifier.width(cardWidth),
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
    }
}

@Composable
private fun RebuildRow(state: SimilarSeriesState) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedButton(onClick = { state.rebuild() }) {
            Icon(Icons.Default.Refresh, null)
            Spacer(Modifier.width(8.dp))
            Text("Réanalyser la bibliothèque")
        }
        Text(
            "${state.indexedCount} séries analysées. À relancer après un gros ajout de séries " +
                "ou une passe de tags.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
