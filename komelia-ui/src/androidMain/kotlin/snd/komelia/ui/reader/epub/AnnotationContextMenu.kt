package snd.komelia.ui.reader.epub

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import snd.komelia.ui.LocalTheme
import snd.komelia.ui.Theme
import snd.komelia.ui.reader.common.AnnotationColorSwatches
import snd.komelia.ui.LocalStrings

/**
 * Popup context menu shown when the user selects text in the EPUB reader.
 *
 * Uses [DropdownMenu] so it appears above/below [position] automatically, matching
 * standard system menu behaviour.  [position] should be in **pixels** relative to
 * the window (convert dp values from the WebView with LocalDensity before passing).
 *
 * @param selectedText The currently selected text (copied on "Copy").
 * @param position Window-relative pixel coordinates of the selection anchor point.
 * @param selectedColor The currently active highlight colour (shown in the swatches row).
 * @param onColorSelected Called when the user picks a different highlight colour.
 * @param onCopy Copy [selectedText] to clipboard and dismiss.
 * @param onHighlight Save a highlight with the current colour immediately (no note).
 * @param onNote Open the annotation dialog for adding a note.
 * @param onDismiss Dismiss the menu without any action.
 */
@Composable
fun AnnotationContextMenu(
    selectedText: String?,
    position: IntOffset,
    selectedColor: Int,
    onColorSelected: (Int) -> Unit,
    onCopy: () -> Unit,
    onHighlight: () -> Unit,
    onNote: () -> Unit,
    onDismiss: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val theme = LocalTheme.current
    val surfaceColor = if (theme.type == Theme.ThemeType.DARK) Color(43, 43, 43)
    else MaterialTheme.colorScheme.surface

    // Zero-size anchor box placed at the selection coordinates.
    // DropdownMenu positions itself above or below this anchor automatically.
    Box(modifier = Modifier.offset { position }) {
        DropdownMenu(
            expanded = true,
            onDismissRequest = onDismiss,
            containerColor = surfaceColor,
        ) {
            // Colour-swatch row at the top
            Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                AnnotationColorSwatches(
                    selectedColor = selectedColor,
                    onColorSelected = onColorSelected,
                )
            }

            HorizontalDivider()

            DropdownMenuItem(
                text = { Text(LocalStrings.current.ui.copy) },
                leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                onClick = {
                    if (!selectedText.isNullOrBlank()) {
                        clipboard.setText(AnnotatedString(selectedText))
                    }
                    onCopy()
                },
            )
            DropdownMenuItem(
                text = { Text(LocalStrings.current.ui.translate) },
                leadingIcon = { Icon(Icons.Default.Translate, contentDescription = null) },
                onClick = {
                    if (!selectedText.isNullOrBlank()) {
                        context.openGoogleTranslate(selectedText)
                    }
                    onDismiss()
                },
            )
            DropdownMenuItem(
                text = { Text(LocalStrings.current.ui.highlight) },
                leadingIcon = { Icon(Icons.Default.BorderColor, contentDescription = null) },
                onClick = onHighlight,
            )
            DropdownMenuItem(
                text = { Text(LocalStrings.current.ui.note) },
                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                onClick = onNote,
            )
        }
    }
}

private fun Context.findGoogleTranslateActivity(): ActivityInfo? {
    val intent = Intent(Intent.ACTION_PROCESS_TEXT).apply {
        type = "text/plain"
    }
    return packageManager
        .queryIntentActivities(intent, 0)
        .firstOrNull { it.activityInfo.packageName == "com.google.android.apps.translate" }
        ?.activityInfo
}

private fun Context.openGoogleTranslate(text: String) {
    val activity = findGoogleTranslateActivity()
    val intent = Intent(Intent.ACTION_PROCESS_TEXT).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_PROCESS_TEXT, text)
        putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
        if (activity != null) {
            setClassName(activity.packageName, activity.name)
        }
    }
    startActivity(intent)
}
