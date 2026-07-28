package snd.komelia.ui.common.lists

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import snd.komga.client.library.KomgaLibrary

/**
 * Library scope selector for the personal lists (Favorites / Planned): "All"
 * plus one chip per library actually holding an entry.
 *
 * Long-pressing a library chip toggles whether that library is part of "All" —
 * the point being to keep, say, a "Divers" library out of the global view while
 * still being able to browse it by selecting its chip. An excluded library is
 * struck through so the state is visible without opening anything.
 *
 * The row hides itself when there is nothing to choose between (0 or 1 library).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryScopeFilterRow(
    libraries: List<KomgaLibrary>,
    selectedLibraryId: String?,
    excludedLibraryIds: Set<String>,
    onSelect: (String?) -> Unit,
    onToggleExcluded: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (libraries.size < 2) return

    var pendingExclusion by remember { mutableStateOf<KomgaLibrary?>(null) }

    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 4.dp),
    ) {
        item {
            FilterChip(
                selected = selectedLibraryId == null,
                onClick = { onSelect(null) },
                label = { Text("Toutes") },
            )
        }
        items(libraries, key = { it.id.value }) { library ->
            val id = library.id.value
            val excluded = id in excludedLibraryIds
            FilterChip(
                selected = selectedLibraryId == id,
                onClick = { onSelect(id) },
                label = {
                    Text(
                        library.name,
                        textDecoration = if (excluded) TextDecoration.LineThrough else null,
                    )
                },
                modifier = Modifier.combinedClickable(
                    onClick = { onSelect(id) },
                    onLongClick = { pendingExclusion = library },
                ),
            )
        }
    }

    pendingExclusion?.let { library ->
        val excluded = library.id.value in excludedLibraryIds
        AlertDialog(
            onDismissRequest = { pendingExclusion = null },
            title = { Text(library.name) },
            text = {
                Text(
                    if (excluded) "Réintégrer cette bibliothèque dans « Toutes » ?"
                    else "Masquer cette bibliothèque de « Toutes » ? Elle restera consultable " +
                        "en sélectionnant son onglet. Le réglage vaut aussi pour « À lire »."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onToggleExcluded(library.id.value)
                    pendingExclusion = null
                }) { Text(if (excluded) "Réintégrer" else "Masquer") }
            },
            dismissButton = {
                TextButton(onClick = { pendingExclusion = null }) { Text("Annuler") }
            },
        )
    }
}
