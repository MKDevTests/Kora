package snd.komelia.ui.series.view

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import snd.komelia.image.coil.SeriesDefaultThumbnailRequest
import snd.komelia.readingorder.ReadingOrderKind
import snd.komelia.readingorder.ReadingOrderNode
import snd.komelia.ui.LocalStrings
import snd.komelia.ui.common.images.ThumbnailImage
import snd.komelia.ui.series.ReadingOrderState
import snd.komga.client.series.KomgaSeriesId

/** Entries a branch group shows before it has to be expanded. */
private const val COLLAPSED_BRANCHES = 4

/**
 * "Reading order": where to start a franchise and what follows.
 *
 * A vertical timeline, not a graph widget. The first shape was a horizontal row
 * of text boxes with arrows, and it fell apart on a real franchise: Fairy Tail
 * has about a dozen series, so the spine scrolled sideways out of view and the
 * branches piled up as an undifferentiated list of "└╴" lines. Reading down a
 * numbered rail costs no horizontal scrolling and holds as many entries as the
 * franchise has.
 *
 * Branches are grouped by what they are rather than listed one per line, and
 * each group shows [COLLAPSED_BRANCHES] entries until it is expanded — twelve
 * covers unfolded by default would bury the tabs underneath.
 *
 * The point the layout has to make is unchanged: a prequel is NOT where you
 * start. It sits in its own group under "read after", never to the left of the
 * original.
 *
 * Editions (other languages, colour) never appear as their own entry: they are
 * the same work, and are counted on the entry they belong to.
 */
@Composable
fun ReadingOrderContent(
    state: ReadingOrderState,
    onSeriesIdClick: (KomgaSeriesId) -> Unit,
) {
    LaunchedEffect(Unit) { state.onOpened() }
    val graph = state.graph ?: return
    val strings = LocalStrings.current.ui

    // Forked sequels leave the spine on purpose: chained they would claim an
    // order nobody declared, so they get a group of their own.
    val spine = graph.nodes
        .filter { (it.kind == ReadingOrderKind.ORIGINAL || it.kind == ReadingOrderKind.SEQUEL) && !it.forked }
        .sortedBy { it.depth }

    val forked = graph.nodes.filter { it.forked }
    val byKind = graph.nodes.filterNot { it.forked }.groupBy { it.kind }
    val groups = buildList {
        // "Start here" first: when the graph is drawn from a spin-off, the main
        // series is the answer to the question the panel exists to answer.
        byKind[ReadingOrderKind.MAIN_STORY]?.let { add(strings.readingOrderStartHere to it) }
        if (forked.isNotEmpty()) add(strings.readingOrderNoDeclaredOrder to forked)
        byKind[ReadingOrderKind.PREQUEL]?.let { add(strings.readingOrderReadAfter to it) }
        byKind[ReadingOrderKind.SPIN_OFF]?.let { add(strings.readingOrderSpinOffs to it) }
        byKind[ReadingOrderKind.RELATED]?.let { add(strings.readingOrderSameWorld to it) }
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(strings.readingOrder, style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = { state.refresh() }) {
                Icon(Icons.Default.Refresh, contentDescription = strings.rebuildTheReadingOrder)
            }
        }

        if (spine.size > 1) {
            GroupHeader(strings.readingOrderMainLine, spine.size, expanded = null, onClick = null)
        }
        spine.forEachIndexed { index, node ->
            SpineRow(
                node = node,
                position = index + 1,
                isLast = index == spine.lastIndex,
                onSeriesIdClick = onSeriesIdClick,
            )
        }

        groups.forEach { (label, nodes) -> BranchGroup(label, nodes, onSeriesIdClick) }

        if (graph.hasFork) {
            Note(strings.twoSequelsNoOrderBetween)
        }
        if (graph.truncated) {
            Note(strings.biggerThanThisTheLists)
        }

        FilterChip(
            selected = state.currentIsOriginal,
            onClick = { state.toggleOriginal() },
            label = { Text(strings.originalSeries) },
        )
    }
}

/**
 * One step of the spine: a numbered dot on a rail, then the entry.
 *
 * The rail line is a weighted box inside a full-height column, and the row is
 * measured at its minimum intrinsic height — that is what makes the line stop
 * exactly at the next dot whatever the title wraps to.
 */
@Composable
private fun SpineRow(
    node: ReadingOrderNode,
    position: Int,
    isLast: Boolean,
    onSeriesIdClick: (KomgaSeriesId) -> Unit,
) {
    val isOriginal = node.kind == ReadingOrderKind.ORIGINAL
    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Column(
            modifier = Modifier.width(24.dp).fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(
                        if (isOriginal) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    position.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isOriginal) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!isLast) {
                // weight, not fillMaxHeight: the column is already full height,
                // so filling would ask for the whole of it again below the dot.
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .weight(1f)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        NodeRow(
            node = node,
            highlighted = isOriginal,
            badge = if (isOriginal) LocalStrings.current.ui.readingOrderStartHere else null,
            onSeriesIdClick = onSeriesIdClick,
        )
    }
}

@Composable
private fun BranchGroup(
    label: String,
    nodes: List<ReadingOrderNode>,
    onSeriesIdClick: (KomgaSeriesId) -> Unit,
) {
    var expanded by remember(label) { mutableStateOf(false) }
    val collapsible = nodes.size > COLLAPSED_BRANCHES
    GroupHeader(
        label = label,
        count = nodes.size,
        expanded = if (collapsible) expanded else null,
        onClick = if (collapsible) ({ expanded = !expanded }) else null,
    )
    val shown = if (collapsible && !expanded) nodes.take(COLLAPSED_BRANCHES) else nodes
    Column(
        modifier = Modifier.padding(start = 32.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        shown.forEach { node ->
            NodeRow(node = node, highlighted = false, badge = null, onSeriesIdClick = onSeriesIdClick)
        }
        if (collapsible) {
            Text(
                text = if (expanded) LocalStrings.current.ui.readingOrderShowLess
                else "${LocalStrings.current.ui.readingOrderShowAll} (${nodes.size})",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable { expanded = !expanded }
                    .padding(vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun GroupHeader(
    label: String,
    count: Int,
    expanded: Boolean?,
    onClick: (() -> Unit)?,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(top = 10.dp, bottom = 2.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
        )
        if (expanded != null) {
            Spacer(Modifier.width(4.dp))
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

/** Cover, title, and what the entry is — the same row everywhere in the panel. */
@Composable
private fun NodeRow(
    node: ReadingOrderNode,
    highlighted: Boolean,
    badge: String?,
    onSeriesIdClick: (KomgaSeriesId) -> Unit,
) {
    val seriesId = remember(node.seriesId) { KomgaSeriesId(node.seriesId) }
    // SeriesThumbnail is not used here: it registers a shared-element key per
    // series, and the series being viewed is also in its own graph — two
    // composables would claim the same key at once. This panel wants the image,
    // not the transition.
    val request = remember(node.seriesId) { SeriesDefaultThumbnailRequest(seriesId) }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (highlighted) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onSeriesIdClick(seriesId) },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(6.dp),
        ) {
            ThumbnailImage(
                data = request,
                cacheKey = node.seriesId,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(width = 38.dp, height = 54.dp).clip(RoundedCornerShape(4.dp)),
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    node.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val editions = node.editionCount
                if (editions > 0) {
                    // Says the editions were folded in, so a missing entry does
                    // not read as a missing link.
                    val word = if (editions > 1) LocalStrings.current.ui.readingOrderEditions
                    else LocalStrings.current.ui.readingOrderEdition
                    Text(
                        "+$editions $word",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (badge != null) {
                Text(
                    badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp),
    )
}
