package snd.komelia.ui.reader.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import snd.komelia.annotations.BookAnnotation
import snd.komelia.ui.LocalTheme
import snd.komelia.ui.Theme
import snd.komelia.ui.LocalStrings

/**
 * Shared annotation dialog used for create and edit in both EPUB3 and comic readers.
 *
 * @param referenceText For EPUB: the selected text. For comic: "Page N · (x%, y%)".
 * @param existingAnnotation Non-null when editing; null when creating new.
 * @param initialColor The pre-selected highlight color (last used).
 * @param onSave Called with the note text and chosen color when user taps Save.
 * @param onDelete Called when user taps Delete (only shown when existingAnnotation != null).
 * @param onDismiss Called when user taps Cancel or dismisses the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnotationDialog(
    referenceText: String,
    existingAnnotation: BookAnnotation?,
    initialColor: Int,
    onSave: (note: String?, color: Int) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    onPrevious: (() -> Unit)? = null,
    onNext: (() -> Unit)? = null,
    onShowList: (() -> Unit)? = null,
    hasPrevious: Boolean = false,
    hasNext: Boolean = false,
    initialNote: String? = null,
) {
    val sheetState = rememberModalBottomSheetState()
    val coroutineScope = rememberCoroutineScope()

    var note by remember(existingAnnotation) { mutableStateOf(existingAnnotation?.note ?: initialNote ?: "") }
    var selectedColor by remember(existingAnnotation) {
        mutableIntStateOf(existingAnnotation?.highlightColor ?: initialColor)
    }

    val theme = LocalTheme.current
    val surfaceColor = if (theme.type == Theme.ThemeType.DARK) Color(43, 43, 43)
    else MaterialTheme.colorScheme.background

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = surfaceColor,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
        ) {
            // Reference text (quoted excerpt or page location)
            Text(
                text = referenceText,
                style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
            )

            // Color swatches and delete button
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AnnotationColorSwatches(
                    selectedColor = selectedColor,
                    onColorSelected = { selectedColor = it },
                )

                if (existingAnnotation != null) {
                    IconButton(onClick = {
                        coroutineScope.launch {
                            sheetState.hide()
                            onDelete()
                        }
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = LocalStrings.current.ui.delete)
                    }
                }
            }

            // Note text field
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(LocalStrings.current.ui.addANote) },
                minLines = 3,
                maxLines = 6,
            )

            Spacer(Modifier.height(12.dp))

            // Action row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (existingAnnotation != null) {
                    IconButton(
                        onClick = { onPrevious?.invoke() },
                        enabled = hasPrevious,
                    ) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = LocalStrings.current.ui.previous)
                    }

                    IconButton(onClick = {
                        coroutineScope.launch {
                            sheetState.hide()
                            onShowList?.invoke()
                        }
                    }) {
                        Icon(Icons.Default.List, contentDescription = LocalStrings.current.ui.list)
                    }

                    IconButton(
                        onClick = { onNext?.invoke() },
                        enabled = hasNext,
                    ) {
                        Icon(Icons.Default.ChevronRight, contentDescription = LocalStrings.current.ui.next)
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = {
                    coroutineScope.launch {
                        sheetState.hide()
                        onDismiss()
                    }
                }) { Text(LocalStrings.current.ui.cancel) }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = {
                    coroutineScope.launch {
                        sheetState.hide()
                        onSave(note.ifBlank { null }, selectedColor)
                    }
                }) { Text(LocalStrings.current.ui.save) }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}
