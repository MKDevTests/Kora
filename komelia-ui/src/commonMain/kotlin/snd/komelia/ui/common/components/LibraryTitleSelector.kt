package snd.komelia.ui.common.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import snd.komga.client.library.KomgaLibrary
import snd.komga.client.library.KomgaLibraryId
import snd.komelia.ui.LocalMainScreenViewModel
import snd.komelia.ui.LocalStrings

/**
 * Renders a big page title that doubles as the library switcher.
 *
 * The label (e.g. "Home" or "Mangas") is shown in the caller-provided
 * [titleStyle], with a small chevron after it. Tapping anywhere on the row
 * opens the library picker sheet (UI 2026), which replaced the dropdown this
 * used to open: the sheet shows covers and counts, and is the same one the
 * floating bar's long-press opens.
 *
 * [currentLibraryId], [onPickHome] and [onPickLibrary] are kept so the two
 * callers did not have to change; the sheet navigates on its own.
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
    // If there are no libraries (still loading, or empty server), the
    // picker wouldn't add anything useful — fall back to a plain Text.
    if (libraries.isEmpty()) {
        Text(label, style = titleStyle, modifier = modifier)
        return
    }

    val mainScreenVm = LocalMainScreenViewModel.current
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12))
                .clickable { mainScreenVm.openLibraryPicker() },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                style = titleStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = LocalStrings.current.ui.switchLibrary,
            )
        }
    }
}
