package snd.komelia.ui.common.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import snd.komga.client.library.KomgaLibrary
import snd.komga.client.library.KomgaLibraryId

/**
 * Renders a big page title that doubles as a library-switcher dropdown.
 *
 * The label (e.g. "Home" or "Mangas") is shown in the caller-provided
 * [titleStyle], with a small chevron after it. Tapping anywhere on the
 * row opens a dropdown listing Home + every library, with the active one
 * checkmarked. Picking an entry calls back to the parent to navigate.
 *
 * Designed to replace the bare `Text(...)` inside HomeHeaderSection /
 * LibraryHeaderSection so the title slot itself is the affordance,
 * without adding a second visual row (which conflicted with the existing
 * filter chips below it).
 */
@Composable
fun LibraryTitleSelector(
    label: String,
    titleStyle: TextStyle,
    libraries: List<KomgaLibrary>,
    currentLibraryId: KomgaLibraryId?,
    onPickHome: () -> Unit,
    onPickLibrary: (KomgaLibraryId) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    // If there are no libraries (still loading, or empty server), the
    // dropdown wouldn't add anything useful — fall back to a plain Text.
    if (libraries.isEmpty()) {
        Text(label, style = titleStyle, modifier = modifier)
        return
    }

    Box(modifier = modifier) {
        Row(
            modifier = Modifier.clickable { expanded = true },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = titleStyle)
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = "Switch library",
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            // "Home" entry: always first, checkmarked when no library is
            // active (the caller passes currentLibraryId = null on Home).
            DropdownMenuItem(
                text = { Text("Home") },
                onClick = {
                    expanded = false
                    onPickHome()
                },
                leadingIcon = {
                    if (currentLibraryId == null) {
                        Icon(Icons.Filled.Check, contentDescription = null)
                    } else {
                        Spacer(Modifier.width(24.dp))
                    }
                },
            )
            HorizontalDivider()
            libraries.forEach { lib ->
                DropdownMenuItem(
                    text = { Text(lib.name) },
                    onClick = {
                        expanded = false
                        onPickLibrary(lib.id)
                    },
                    leadingIcon = {
                        if (currentLibraryId == lib.id) {
                            Icon(Icons.Filled.Check, contentDescription = null)
                        } else {
                            Spacer(Modifier.width(24.dp))
                        }
                    },
                )
            }
        }
    }
}
