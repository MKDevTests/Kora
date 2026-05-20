package snd.komelia.ui.common.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Reusable 1..5 star rating widget. When [onRatingChange] is null the
 * stars are display-only (no taps registered, no IconButton wrapping).
 *
 * Tapping a star sets the rating to that index. Tapping the *currently
 * selected* star clears the rating (passes 0) — the common "tap-to-untoggle"
 * idiom that lets the user remove a rating without a separate "clear"
 * button.
 *
 * [size] tunes star icon size for compact embedding (e.g. inside menu
 * items vs. the dedicated rating row on the series detail screen).
 */
@Composable
fun RatingStars(
    rating: Int,
    onRatingChange: ((Int) -> Unit)? = null,
    size: Dp = 28.dp,
    modifier: Modifier = Modifier,
) {
    val active = MaterialTheme.colorScheme.primary
    val inactive = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        for (i in 1..5) {
            val filled = i <= rating
            val icon = if (filled) Icons.Filled.Star else Icons.Outlined.StarOutline
            val tint = if (filled) active else inactive
            if (onRatingChange != null) {
                IconButton(
                    onClick = {
                        // Tap the current top star → clear (= 0).
                        val newValue = if (filled && i == rating) 0 else i
                        onRatingChange(newValue)
                    },
                    modifier = Modifier.size(size + 16.dp), // expand tap target
                ) {
                    Icon(icon, contentDescription = "Rate $i stars", tint = tint, modifier = Modifier.size(size))
                }
            } else {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size))
            }
        }
    }
}
