package snd.komelia.ui.series.view

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import snd.komelia.readingorder.ReadingOrderKind
import snd.komelia.readingorder.ReadingOrderNode
import snd.komelia.ui.series.ReadingOrderState
import snd.komga.client.series.KomgaSeriesId

/**
 * "Reading order": where to start a franchise and what follows.
 *
 * Deliberately not a graph widget — one row for the spine (the original and its
 * sequels), then one line per branch. The point it has to make is that a
 * prequel is NOT where you start: it hangs off the original like a spin-off,
 * under "read after", instead of sitting to its left.
 *
 * Editions (other languages, colour) never appear: they are the same work, and
 * four boxes for one series would drown the order in noise.
 */
@Composable
fun ReadingOrderContent(
    state: ReadingOrderState,
    onSeriesIdClick: (KomgaSeriesId) -> Unit,
) {
    LaunchedEffect(Unit) { state.onOpened() }
    val graph = state.graph ?: return

    val spine = graph.nodes.filter { it.kind == ReadingOrderKind.ORIGINAL || it.kind == ReadingOrderKind.SEQUEL }
        .sortedBy { it.depth }
    val branches = graph.nodes.filterNot { it.kind == ReadingOrderKind.ORIGINAL || it.kind == ReadingOrderKind.SEQUEL }

    Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Reading order", style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = { state.refresh() }) {
                Icon(Icons.Default.Refresh, contentDescription = "Rebuild the reading order")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            spine.forEachIndexed { index, node ->
                if (index > 0) Text("→", style = MaterialTheme.typography.bodyMedium)
                NodeCard(node, highlighted = node.kind == ReadingOrderKind.ORIGINAL, onSeriesIdClick)
            }
        }

        branches.forEach { node ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("└╴", style = MaterialTheme.typography.bodyMedium)
                NodeCard(node, highlighted = false, onSeriesIdClick)
                Text(
                    node.kind.hint(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (graph.truncated) {
            Text(
                "Bigger than this — the lists below have everything.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        FilterChip(
            selected = state.currentIsOriginal,
            onClick = { state.toggleOriginal() },
            label = { Text("Original series") },
        )
    }
}

@Composable
private fun NodeCard(
    node: ReadingOrderNode,
    highlighted: Boolean,
    onSeriesIdClick: (KomgaSeriesId) -> Unit,
) {
    ElevatedCard(
        onClick = { onSeriesIdClick(KomgaSeriesId(node.seriesId)) },
        colors = if (highlighted) {
            CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else CardDefaults.elevatedCardColors(),
    ) {
        Column(modifier = Modifier.widthIn(min = 96.dp, max = 160.dp).padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text(
                node.title,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (node.editionCount > 0) {
                // Says the editions were folded in, so a missing box doesn't
                // read as a missing link.
                Text(
                    "+${node.editionCount} edition${if (node.editionCount > 1) "s" else ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun ReadingOrderKind.hint(): String = when (this) {
    ReadingOrderKind.PREQUEL -> "prequel · read after"
    ReadingOrderKind.SPIN_OFF -> "spin-off · anytime"
    ReadingOrderKind.RELATED -> "same world · anytime"
    ReadingOrderKind.SEQUEL, ReadingOrderKind.ORIGINAL -> ""
}
