package snd.komelia.ui.common.lists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import snd.komelia.ui.common.components.CheckboxWithLabel
import snd.komga.client.library.KomgaLibrary

/**
 * Library scope selector for the personal lists (Favorites / Planned): "All"
 * plus one chip per library actually holding an entry.
 *
 * Which libraries count towards "All" is edited through the trailing button,
 * NOT a long-press on a chip: FilterChip handles its own clicks and swallows a
 * combinedClickable placed on it, and long-press doesn't exist on desktop
 * anyway. An excluded library is struck through so the state stays visible.
 *
 * The row hides itself when there is nothing to choose between (0 or 1 library).
 */
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

    var showScopeDialog by remember { mutableStateOf(false) }

    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        LazyRow(
            modifier = Modifier.weight(1f),
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
                FilterChip(
                    selected = selectedLibraryId == id,
                    onClick = { onSelect(id) },
                    label = {
                        Text(
                            library.name,
                            textDecoration = if (id in excludedLibraryIds) TextDecoration.LineThrough else null,
                        )
                    },
                )
            }
        }
        IconButton(onClick = { showScopeDialog = true }) {
            Icon(Icons.Default.FilterList, contentDescription = "Bibliothèques incluses dans « Toutes »")
        }
    }

    if (showScopeDialog) {
        AlertDialog(
            onDismissRequest = { showScopeDialog = false },
            title = { Text("Bibliothèques dans « Toutes »") },
            text = {
                Column {
                    Text(
                        "Une bibliothèque décochée n'apparaît plus dans « Toutes », mais reste " +
                            "consultable en sélectionnant sa puce. Le réglage vaut pour les favoris " +
                            "et pour « À lire ».",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                    libraries.forEach { library ->
                        CheckboxWithLabel(
                            checked = library.id.value !in excludedLibraryIds,
                            onCheckedChange = { onToggleExcluded(library.id.value) },
                            label = { Text(library.name) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showScopeDialog = false }) { Text("Fermer") }
            },
        )
    }
}
