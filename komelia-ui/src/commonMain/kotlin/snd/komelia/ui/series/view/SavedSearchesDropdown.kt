package snd.komelia.ui.series.view

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import snd.komelia.ui.LocalStrings
import snd.komelia.ui.series.SeriesFilterState

/**
 * Picks one of the searches the user named and kept.
 *
 * The panel could already express a precise search — reading status, language,
 * completion, genres — and then had no memory of it: coming back meant walking
 * eight dropdowns again from scratch. That cost is what kept those filters to
 * one-off looks.
 *
 * Renders nothing when the library holds no saved search. An empty dropdown
 * teaches nothing; the save button in the panel's action bar is what introduces
 * the feature, and this appears the moment it has something to offer.
 */
@Composable
fun SavedSearchesDropdown(
    filterState: SeriesFilterState,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current.ui
    val scope = rememberCoroutineScope()

    LaunchedEffect(filterState) { filterState.loadSavedFilters() }

    val saved = filterState.savedFilters
    if (saved.isEmpty()) return

    var expanded by remember { mutableStateOf(false) }

    Box(modifier) {
        OutlinedButton(onClick = { expanded = true }) {
            Icon(
                Icons.Rounded.BookmarkBorder,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(strings.savedSearches, modifier = Modifier.padding(horizontal = 8.dp))
            Icon(Icons.Rounded.ArrowDropDown, contentDescription = null)
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            saved.forEach { entry ->
                DropdownMenuItem(
                    text = { Text(entry.name) },
                    onClick = {
                        filterState.applySavedFilter(entry)
                        expanded = false
                    },
                    // Delete sits in the row rather than behind a long press: a
                    // long press on a menu item is invisible, and a management
                    // screen would be a lot of app for a list of five entries.
                    trailingIcon = {
                        IconButton(onClick = { scope.launch { filterState.deleteSavedFilter(entry) } }) {
                            Icon(
                                Icons.Rounded.DeleteOutline,
                                contentDescription = strings.deleteSavedSearch,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    },
                )
            }
        }
    }
}

/**
 * Asks for the name to keep the current filter under.
 *
 * Saving over an existing name replaces it — that is how a search you got
 * slightly wrong gets corrected — so the dialog says so as soon as the name
 * typed matches one, instead of letting the old entry disappear unannounced.
 */
@Composable
fun SaveSearchDialog(
    filterState: SeriesFilterState,
    onDismissRequest: () -> Unit,
) {
    val strings = LocalStrings.current.ui
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    val replaces = remember(name, filterState.savedFilters) {
        val trimmed = name.trim()
        trimmed.isNotEmpty() && filterState.savedFilters.any { it.name.equals(trimmed, ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(strings.saveSearch) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text(strings.saveSearchName) },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (replaces) {
                    Text(
                        strings.savedSearchReplaced,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    scope.launch { filterState.saveCurrentFilterAs(name) }
                    onDismissRequest()
                },
            ) { Text(strings.saveSearch) }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) { Text(strings.cancel) }
        },
    )
}
