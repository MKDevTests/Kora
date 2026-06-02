package snd.komelia.ui.series.view

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import snd.komelia.links.SeriesRelationType
import snd.komelia.ui.common.cards.SeriesImageCard
import snd.komelia.ui.dialogs.ConfirmationDialog
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

    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Button(onClick = { showAdd = true }) {
            Icon(Icons.Default.Add, null)
            Spacer(Modifier.width(8.dp))
            Text("Add link")
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
            LinkSection(type.label(), list, cardWidth, onSeriesClick) { state.unlinkRelation(it.id) }
        }
    }

    if (showAdd) AddLinkDialog(state, onDismiss = { showAdd = false })
}

@Composable
private fun LinkSection(
    title: String,
    series: List<KomgaSeries>,
    cardWidth: Dp,
    onSeriesClick: (KomgaSeries) -> Unit,
    onUnlink: (KomgaSeries) -> Unit,
) {
    var pendingUnlink by remember { mutableStateOf<KomgaSeries?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(series, key = { it.id.value }) { s ->
                // Tap = open; long-press = ask to unlink (onSeriesSelect is the
                // long-press hook when no menu actions are supplied).
                SeriesImageCard(
                    series = s,
                    onSeriesClick = { onSeriesClick(s) },
                    onSeriesSelect = { pendingUnlink = s },
                    modifier = Modifier.width(cardWidth),
                )
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
