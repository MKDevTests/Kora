package snd.komelia.ui.series.view

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Checkbox
import androidx.compose.ui.text.style.TextOverflow
import snd.komelia.anilist.AniListMedia
import snd.komelia.links.SeriesRelationType
import snd.komelia.ui.LocalKomgaState
import snd.komelia.ui.common.cards.SeriesImageCard
import snd.komelia.ui.common.components.DropdownChoiceMenu
import snd.komelia.ui.common.components.LabeledEntry
import snd.komelia.ui.dialogs.ConfirmationDialog
import snd.komelia.ui.series.AniListAnalysis
import snd.komelia.ui.series.AniListSuggestionRow
import snd.komelia.ui.series.SeriesLinksState
import snd.komga.client.series.KomgaSeries
import snd.komelia.ui.LocalStrings
import androidx.compose.foundation.layout.height
import androidx.compose.ui.layout.ContentScale
import snd.komelia.image.coil.SeriesDefaultThumbnailRequest
import snd.komelia.ui.common.images.ThumbnailImage

/** Display order + labels for related-series sections (from this series' view). */
private val relationDisplayOrder = listOf(
    SeriesRelationType.PREQUEL,
    SeriesRelationType.SEQUEL,
    SeriesRelationType.SPIN_OFF,
    SeriesRelationType.MAIN_STORY,
    SeriesRelationType.LANGUAGE,
    SeriesRelationType.COLORED,
    SeriesRelationType.CHAPTERS,
    SeriesRelationType.VOLUMES,
    SeriesRelationType.RELATED,
)

private fun SeriesRelationType.label(): String = when (this) {
    SeriesRelationType.PREQUEL -> "Prequel"
    SeriesRelationType.SEQUEL -> "Sequel"
    SeriesRelationType.SPIN_OFF -> "Spin-offs"
    SeriesRelationType.MAIN_STORY -> "Main series"
    SeriesRelationType.LANGUAGE -> "Other languages"
    SeriesRelationType.COLORED -> "Colour editions"
    SeriesRelationType.CHAPTERS -> "Chapters"
    SeriesRelationType.VOLUMES -> "Volumes"
    SeriesRelationType.RELATED -> "Related"
}

@Composable
fun SeriesLinksContent(
    state: SeriesLinksState,
    readingOrderState: snd.komelia.ui.series.ReadingOrderState,
    onSeriesClick: (KomgaSeries) -> Unit,
    onSeriesIdClick: (snd.komga.client.series.KomgaSeriesId) -> Unit,
) {
    val cardWidth = state.cardWidth.collectAsState().value
    var showAdd by remember { mutableStateOf(false) }
    val isAdmin = LocalKomgaState.current.authenticatedUser.collectAsState().value?.roleAdmin() ?: false

    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // The picture goes first: "where do I start" is the question the lists
        // below cannot answer at a glance.
        ReadingOrderContent(readingOrderState, onSeriesIdClick)

        Button(onClick = { showAdd = true }) {
            Icon(Icons.Default.Add, null)
            Spacer(Modifier.width(8.dp))
            Text(LocalStrings.current.ui.addLink)
        }

        // Online AniList suggestions — only when the user opted in (Settings →
        // Navigation). Resolves the current series on AniList and proposes its
        // related series that are already in the library; confirm-only.
        val aniListEnabled by state.aniListEnabled.collectAsState()
        if (aniListEnabled) {
            OutlinedButton(onClick = { state.analyze() }) {
                Text(LocalStrings.current.ui.analyzeWithAnilist)
            }
        }

        val versions = state.versions
        val relations = state.relations
        if (versions.isEmpty() && relations.isEmpty()) {
            Text(
                text = LocalStrings.current.ui.noLinksYetUseAdd +
                    "language or edition) or related series (sequel, prequel, spin-off). " +
                    "Long-press a linked series to unlink it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (versions.isNotEmpty()) {
            LinkSection("Other versions", versions, cardWidth, onSeriesClick) { state.unlinkVersion(it.id) }
        }
        relationDisplayOrder.forEach { type ->
            val list = relations[type] ?: return@forEach
            LinkSection(
                title = type.label(),
                series = list,
                cardWidth = cardWidth,
                onSeriesClick = onSeriesClick,
                sharedIds = state.sharedRelationIds,
                canUnlinkShared = isAdmin,
                onUnlink = { state.unlinkRelation(it.id) },
            )
        }
    }

    if (showAdd) AddLinkDialog(state, onDismiss = { showAdd = false })

    state.analysis?.let { AniListAnalysisDialog(it, state) }
}

@Composable
private fun LinkSection(
    title: String,
    series: List<KomgaSeries>,
    cardWidth: Dp,
    onSeriesClick: (KomgaSeries) -> Unit,
    sharedIds: Set<String> = emptySet(),
    canUnlinkShared: Boolean = true,
    onUnlink: (KomgaSeries) -> Unit,
) {
    var pendingUnlink by remember { mutableStateOf<KomgaSeries?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(series, key = { it.id.value }) { s ->
                val shared = s.id.value in sharedIds
                // Tap = open; long-press = ask to unlink (onSeriesSelect is the
                // long-press hook). A shared link can only be unlinked by an admin.
                Box {
                    SeriesImageCard(
                        series = s,
                        onSeriesClick = { onSeriesClick(s) },
                        onSeriesSelect = { if (!shared || canUnlinkShared) pendingUnlink = s },
                        modifier = Modifier.width(cardWidth),
                    )
                    if (shared) {
                        Icon(
                            Icons.Default.Public,
                            contentDescription = LocalStrings.current.ui.sharedOnServer,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(4.dp)
                                .size(16.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }

    val pending = pendingUnlink
    if (pending != null) {
        ConfirmationDialog(
            title = LocalStrings.current.ui.unlinkSeries,
            body = "Remove the link to “${pending.metadata.title}”? This unlinks it on both series.",
            onDialogConfirm = {
                onUnlink(pending)
                pendingUnlink = null
            },
            onDialogDismiss = { pendingUnlink = null },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddLinkDialog(
    state: SeriesLinksState,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<KomgaSeries>>(emptyList()) }
    var selected by remember { mutableStateOf<KomgaSeries?>(null) }
    var loading by remember { mutableStateOf(false) }

    LaunchedEffectQuery(query) {
        if (query.isBlank()) {
            results = emptyList()
        } else {
            loading = true
            results = state.search(query)
            loading = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(LocalStrings.current.ui.addLink) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it; selected = null },
                    label = { Text(LocalStrings.current.ui.searchSeries) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(
                    onClick = {
                        selected = null
                        scope.launch {
                            loading = true
                            results = state.suggestions()
                            loading = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(LocalStrings.current.ui.suggestSameAuthorSimilarTitle) }

                val sel = selected
                when {
                    loading -> CircularProgressIndicator(Modifier.padding(8.dp))
                    sel == null -> LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                        items(results, key = { it.id.value }) { s -> SearchResultRow(s) { selected = s } }
                    }

                    // Chips wrapping across a few rows rather than nine
                    // full-width buttons stacked: stacked, the list overflowed
                    // the dialog's 480 dp and everything past "Colour edition"
                    // was clipped away with nothing on screen to suggest it was
                    // there. Every kind has to be visible at once — a kind you
                    // have to go looking for is a kind nobody picks.
                    else -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Link “${sel.metadata.title}” as:",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            KindChip("Other version") { state.linkVersion(sel.id); onDismiss() }
                            KindChip("Sequel") { state.linkRelation(sel.id, SeriesRelationType.SEQUEL); onDismiss() }
                            KindChip("Prequel") { state.linkRelation(sel.id, SeriesRelationType.PREQUEL); onDismiss() }
                            KindChip("Spin-off") { state.linkRelation(sel.id, SeriesRelationType.SPIN_OFF); onDismiss() }
                            KindChip("Other language") { state.linkRelation(sel.id, SeriesRelationType.LANGUAGE); onDismiss() }
                            KindChip("Colour edition") { state.linkRelation(sel.id, SeriesRelationType.COLORED); onDismiss() }
                            KindChip("Chapters") { state.linkRelation(sel.id, SeriesRelationType.CHAPTERS); onDismiss() }
                            KindChip("Volumes") { state.linkRelation(sel.id, SeriesRelationType.VOLUMES); onDismiss() }
                            KindChip("Related") { state.linkRelation(sel.id, SeriesRelationType.RELATED); onDismiss() }
                        }
                        TextButton(onClick = { selected = null }) { Text(LocalStrings.current.ui.backToResults) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(LocalStrings.current.ui.close) } },
    )
}

/**
 * One candidate series, with enough to tell it apart.
 *
 * A title alone is useless here: the whole point of linking is that several
 * series share it — the French edition, the English one, the colour one. The
 * cover, the language, the publisher and the volume count are what actually
 * distinguish them, and all four are already in the search result.
 */
@Composable
private fun SearchResultRow(series: KomgaSeries, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Not SeriesThumbnail: it registers a shared element keyed on the
        // series id, and this row lives in an AlertDialog — its own window,
        // its own layout hierarchy. When a result happened to be a series
        // whose cover was also on screen behind the dialog, the two claimants
        // sat in different windows and Compose crashed measuring the distance
        // between them ("layouts are not part of the same hierarchy").
        // A dialog has no business in a shared-element transition anyway.
        ThumbnailImage(
            data = remember(series.id) { SeriesDefaultThumbnailRequest(series.id) },
            cacheKey = series.id.value,
            contentScale = ContentScale.Crop,
            modifier = Modifier.width(40.dp).height(60.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = series.metadata.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val details = listOfNotNull(
                series.metadata.language.takeIf { it.isNotBlank() }?.uppercase(),
                series.metadata.publisher.takeIf { it.isNotBlank() },
                LocalStrings.current.counts.booksCount(series.booksCount),
            ).joinToString(" · ")
            if (details.isNotBlank()) {
                Text(
                    text = details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun KindChip(label: String, onClick: () -> Unit) {
    AssistChip(onClick = onClick, label = { Text(label) })
}

/** Debounced reaction to a changing query string. */
@Composable
private fun LaunchedEffectQuery(query: String, block: suspend () -> Unit) {
    androidx.compose.runtime.LaunchedEffect(query) {
        delay(300)
        block()
    }
}

// -- AniList analysis popup --------------------------------------------------

private val relationTypeOptions = listOf(
    LabeledEntry(SeriesRelationType.SEQUEL, "Sequel"),
    LabeledEntry(SeriesRelationType.PREQUEL, "Prequel"),
    LabeledEntry(SeriesRelationType.SPIN_OFF, "Spin-off"),
    LabeledEntry(SeriesRelationType.MAIN_STORY, "Main series"),
    LabeledEntry(SeriesRelationType.LANGUAGE, "Other language"),
    LabeledEntry(SeriesRelationType.COLORED, "Colour edition"),
    LabeledEntry(SeriesRelationType.CHAPTERS, "Chapters"),
    LabeledEntry(SeriesRelationType.VOLUMES, "Volumes"),
    LabeledEntry(SeriesRelationType.RELATED, "Related"),
)

@Composable
private fun AniListAnalysisDialog(
    analysis: AniListAnalysis,
    state: SeriesLinksState,
) {
    var correctingIndex by remember { mutableStateOf<Int?>(null) }

    AlertDialog(
        onDismissRequest = { state.dismissAnalysis() },
        title = { Text(LocalStrings.current.ui.anilistSuggestions) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (analysis.loading) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(LocalStrings.current.ui.analyzing)
                    }
                } else {
                    SourceSection(analysis, state)

                    if (analysis.error != null) {
                        Text(analysis.error, color = MaterialTheme.colorScheme.error)
                    } else if (analysis.rows.isEmpty()) {
                        Text(
                            "No related series from AniList are in your library yet. Pick a " +
                                "different source above, or add links manually.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            itemsIndexed(analysis.rows) { index, row ->
                                AniListRowItem(
                                    row = row,
                                    onCheckedChange = { state.toggleRow(index) },
                                    onTypeChange = { state.setRowType(index, it) },
                                    onCorrect = { correctingIndex = index },
                                )
                            }
                        }
                    }

                }
            }
        },
        confirmButton = {
            val count = analysis.rows.count { it.checked && it.series != null }
            TextButton(onClick = { state.confirmAnalysis() }, enabled = count > 0) {
                Text(if (count > 0) "Link $count" else "Link")
            }
        },
        dismissButton = {
            TextButton(onClick = { state.dismissAnalysis() }) { Text(LocalStrings.current.ui.cancel) }
        },
    )

    correctingIndex?.let { index ->
        CorrectSeriesDialog(
            state = state,
            onPicked = {
                state.correctRow(index, it)
                correctingIndex = null
            },
            onDismiss = { correctingIndex = null },
        )
    }
}

@Composable
private fun AniListRowItem(
    row: AniListSuggestionRow,
    onCheckedChange: () -> Unit,
    onTypeChange: (SeriesRelationType) -> Unit,
    onCorrect: () -> Unit,
) {
    val matched = row.series != null
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (matched) Checkbox(checked = row.checked, onCheckedChange = { onCheckedChange() })
        else Spacer(Modifier.width(40.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.series?.metadata?.title ?: row.anilistTitle,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                color = if (matched) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(
                onClick = onCorrect,
                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
            ) {
                Text(
                    if (matched) "Change series" else "Pick your series",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        DropdownChoiceMenu(
            selectedOption = relationTypeOptions.first { it.value == row.type },
            options = relationTypeOptions,
            onOptionChange = { onTypeChange(it.value) },
            inputFieldModifier = Modifier.width(132.dp),
        )
    }
}

@Composable
private fun SourceSection(
    analysis: AniListAnalysis,
    state: SeriesLinksState,
) {
    var manualQuery by remember { mutableStateOf("") }
    LaunchedEffectQuery(manualQuery) { state.searchSource(manualQuery) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        analysis.sourceMedia?.let { source ->
            Text(
                "Recognized: ${source.displayTitle ?: "?"}",
                style = MaterialTheme.typography.titleSmall,
            )
        }
        OutlinedTextField(
            value = manualQuery,
            onValueChange = { manualQuery = it },
            label = { Text(LocalStrings.current.ui.wrongSeriesSearchAnilist) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (manualQuery.isNotBlank() && analysis.sourceCandidates.isNotEmpty()) {
            Column {
                analysis.sourceCandidates.take(6).forEach { candidate ->
                    Text(
                        text = candidate.displayTitle ?: "?",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { state.repickSource(candidate) }
                            .padding(vertical = 6.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun CorrectSeriesDialog(
    state: SeriesLinksState,
    onPicked: (KomgaSeries) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<KomgaSeries>>(emptyList()) }

    LaunchedEffectQuery(query) {
        results = if (query.isBlank()) emptyList() else state.search(query)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(LocalStrings.current.ui.pickTheCorrectSeries) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(LocalStrings.current.ui.searchSeries) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                    items(results, key = { it.id.value }) { s ->
                        Text(
                            text = s.metadata.title,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPicked(s) }
                                .padding(vertical = 10.dp),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(LocalStrings.current.ui.cancel) } },
    )
}
