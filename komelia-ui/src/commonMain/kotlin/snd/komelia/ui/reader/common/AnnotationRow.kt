package snd.komelia.ui.reader.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import snd.komelia.annotations.AnnotationLocation
import snd.komelia.annotations.BookAnnotation
import snd.komelia.ui.LocalStrings

/**
 * A row in the Annotations management tab. Works for both EPUB and comic annotations.
 *
 * @param annotation The annotation to display.
 * @param locationLabel For EPUB: "Chapter · Location X of N". For comic: "Page N".
 * @param onTap Navigate to annotation location and open edit dialog.
 * @param onDelete Remove annotation.
 */
@Composable
fun AnnotationRow(
    annotation: BookAnnotation,
    locationLabel: String,
    onTap: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onTap)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Color chip
        val chipColor = annotation.highlightColor
        if (chipColor != null) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(Color(chipColor))
                    .padding(end = 10.dp)
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = if (chipColor != null) 10.dp else 0.dp),
        ) {
            // Location label
            Text(
                text = locationLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // For EPUB: show the highlighted text snippet
            val epubLocation = annotation.location as? AnnotationLocation.EpubLocation
            if (epubLocation != null) {
                val snippet = epubLocation.selectedText
                if (!snippet.isNullOrBlank()) {
                    Text(
                        text = snippet,
                        style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            // Note preview (up to 2 lines)
            val noteText = annotation.note
            if (!noteText.isNullOrBlank()) {
                Text(
                    text = noteText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = LocalStrings.current.ui.deleteAnnotation)
        }
    }
}
