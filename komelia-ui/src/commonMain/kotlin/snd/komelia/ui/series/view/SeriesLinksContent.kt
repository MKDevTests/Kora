package snd.komelia.ui.series.view

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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

/** Display order + labels for related-series sections (from this series' view). */
private val relationDisplayOrder = listOf(
    SeriesRelationType.PREQUEL,
    SeriesRelationType.SEQUEL,
    SeriesRelationType.SPIN_OFF,
    SeriesRelationType.MAIN_STORY,
    SeriesRelationType.RELATED,
)

private fun SeriesRelationType.label(): String = when (this) {
    SeriesRelationType.PREQUEL -> "Prequel"
    SeriesRelationType.SEQUEL -> "Sequel"
    SeriesRelationType.SPIN_OFF -> "Spin-offs"
    SeriesRelationType.MAIN_STORY -> "Main series"
    SeriesRelationType.RELATED -> "Related"
}

@Composable
fun SeriesLinksContent(
    state: SeriesLinksState,
    onSeriesClick: (KomgaSeries) -> Unit,
) {
    val cardWidth = state.cardWidth.collectAsState().value
    var showAdd by remember { mutableStateOf(false) }
    val isAdmin = LocalKomgaState.current.authenticatedUser.collectAsState().value?.roleAdmin() ?: false

    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Button(onClick = { showAdd = true }) {
            Icon(Icons.Default.Add, null)
            Spacer(Modifier.width(8.dp))
            Text("Add link")
        }

        // Online AniList suggestions — only when the user opted in (Settings →
        // Navigation). Resolves the current series on AniList and proposes its
        // related series that are already in the library; confirm-only.
        val aniListEnabled by state.aniListEnabled.collectAsState()
        if (aniListEnabled) {
            OutlinedButton(onClick = { state.analyze() }) {
                Text("Analyze with AniList")
            }
        }

        val versions = state.versions
        val relations = state.relations
        if (versions.isEmpty() && relations.isEmpty()) {
            Text(
                text = "No links yet. Use “Add link” to mark other versions (another " +
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
                            contentDescription = "Shared on server",
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
            title = "Unlink series",
            body = "Remove the link to “${pending.metadata.title}”? This unlinks it on both series.",
            onDialogConfirm = {
                onUnlink(pending)
                pendingUnlink = null
            },
            onDialogDismiss = { pendingUnlink = null },
        )
    }
}

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
        title = { Text("Add link") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it; selected = null },
                    label = { Text("Search series") },
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
                ) { Text("Suggest (same author / similar title)") }

                val sel = selected
                when {
                    loading -> CircularProgressIndicator(Modifier.padding(8.dp))
                    sel == null -> LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                        items(results, key = { it.id.value }) { s ->
                            Text(
                                text = s.metadata.title,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selected = s }
                                    .padding(vertical = 10.dp),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }

                    else -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Link “${sel.metadata.title}” as:",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        KindButton("Other version") { state.linkVersion(sel.id); onDismiss() }
                        KindButton("Sequel") { state.linkRelation(sel.id, SeriesRelationType.SEQUEL); onDismiss() }
                        KindButton("Prequel") { state.linkRelation(sel.id, SeriesRelationType.PREQUEL); onDismiss() }
                        KindButton("Spin-off") { state.linkRelation(sel.id, SeriesRelationType.SPIN_OFF); onDismiss() }
                        KindButton("Related") { state.linkRelation(sel.id, SeriesRelationType.RELATED); onDismiss() }
                        TextButton(onClick = { selected = null }) { Text("Back to results") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun KindButton(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(label) }
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
        title = { Text("AniList suggestions") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (analysis.loading) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Analyzing…")
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

                    if (analysis.ignoredCount > 0) {
                        Text(
                            "${analysis.ignoredCount} related not shown (not in your library, " +
                                "anime, or a very different localized title).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            val count = analysis.rows.count { it.checked }
            TextButton(onClick = { state.confirmAnalysis() }, enabled = count > 0) {
                Text(if (count > 0) "Link $count" else "Link")
            }
        },
        dismissButton = {
            TextButton(onClick = { state.dismissAnalysis() }) { Text("Cancel") }
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Checkbox(checked = row.checked, onCheckedChange = { onCheckedChange() })
        Column(modifier = Modifier.weight(1f)) {
            Text(
                row.series.metadata.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(
                onClick = onCorrect,
                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
            ) { Text("Correct", style = MaterialTheme.typography.bodySmall) }
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
            label = { Text("Wrong series? Search AniList") },
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
        title = { Text("Pick the correct series") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search series") },
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
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
