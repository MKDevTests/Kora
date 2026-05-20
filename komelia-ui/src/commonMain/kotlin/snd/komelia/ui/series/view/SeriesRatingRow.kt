package snd.komelia.ui.series.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import snd.komelia.ui.LocalSeriesRatingsRepository
import snd.komelia.ui.common.components.RatingStars
import snd.komga.client.series.KomgaSeriesId

/**
 * Compact row shown on the series detail screen: a label + the
 * tappable 5-star widget. Reads/writes through the repository
 * exposed by [LocalSeriesRatingsRepository] — same backing store as
 * the long-press menu and the "Just finished?" modal.
 *
 * Tapping a star saves immediately. Tapping the current top star
 * clears the rating (delegated to RatingStars' built-in toggle).
 */
@Composable
fun SeriesRatingRow(seriesId: KomgaSeriesId) {
    val repo = LocalSeriesRatingsRepository.current
    val scope = rememberCoroutineScope()
    val ratingFlow = remember(seriesId) { repo.observe(seriesId) }
    val rating = ratingFlow.collectAsState(initial = null).value
    val stars = rating?.stars ?: 0

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Your rating",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        RatingStars(
            rating = stars,
            onRatingChange = { newStars ->
                scope.launch {
                    if (newStars == 0) repo.delete(seriesId)
                    else repo.put(seriesId, newStars)
                }
            },
        )
    }
}

