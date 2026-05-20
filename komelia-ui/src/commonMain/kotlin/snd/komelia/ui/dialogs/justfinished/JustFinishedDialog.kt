package snd.komelia.ui.dialogs.justfinished

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import snd.komelia.ui.LocalSeriesRatingsRepository
import snd.komelia.ui.common.components.RatingStars
import snd.komelia.ui.dialogs.AppDialog
import snd.komelia.ui.platform.cursorForHand
import snd.komga.client.series.KomgaSeriesId

/**
 * Modal fired after a book reaches 100% during a reader session.
 * Lets the user assign (or update) a star rating for the parent
 * series in one tap, then dismisses.
 *
 * Intentionally minimal: one action — rate the series. "Next book"
 * and "Mark series done" were considered for v1 but skipped to keep
 * the modal a true "you finished, want to commit a thought?" moment
 * instead of an action hub. Those fit better in the long-press cover
 * menu (planned for the next release).
 *
 * The parent screen is expected to resolve the bookId → seriesId
 * lookup (via [JustFinishedBookData]) before showing the dialog,
 * so the modal stays decoupled from the bookApi.
 */
@Composable
fun JustFinishedDialog(
    data: JustFinishedBookData,
    onDismiss: () -> Unit,
) {
    val ratingsRepo = LocalSeriesRatingsRepository.current
    val scope = rememberCoroutineScope()
    val existing = remember(data.seriesId) { ratingsRepo.observe(data.seriesId) }
        .collectAsState(initial = null).value
    val currentStars = existing?.stars ?: 0

    AppDialog(
        modifier = Modifier.widthIn(max = 480.dp),
        header = {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "You just finished",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = data.bookTitle,
                    style = MaterialTheme.typography.headlineSmall,
                )
                if (data.seriesTitle.isNotBlank() && data.seriesTitle != data.bookTitle) {
                    Text(
                        text = "from ${data.seriesTitle}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HorizontalDivider()
            }
        },
        content = {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = if (currentStars == 0) "Rate this series?" else "Your rating",
                    style = MaterialTheme.typography.titleSmall,
                )
                RatingStars(
                    rating = currentStars,
                    onRatingChange = { newStars ->
                        scope.launch {
                            if (newStars == 0) ratingsRepo.delete(data.seriesId)
                            else ratingsRepo.put(data.seriesId, newStars)
                        }
                    },
                )
                Text(
                    text = "Tap a star to save. Tap the highest one again to clear.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        controlButtons = {
            Row(modifier = Modifier.padding(12.dp)) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.cursorForHand(),
                ) { Text("Skip") }
                FilledTonalButton(
                    onClick = onDismiss,
                    modifier = Modifier.cursorForHand(),
                ) { Text("Done") }
            }
        },
        onDismissRequest = onDismiss,
    )
}

/** Pre-resolved book/series info that the parent screen passes to the dialog. */
data class JustFinishedBookData(
    val seriesId: KomgaSeriesId,
    val seriesTitle: String,
    val bookTitle: String,
)
