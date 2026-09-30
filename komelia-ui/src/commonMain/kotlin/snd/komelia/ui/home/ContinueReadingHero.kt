package snd.komelia.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import snd.komelia.image.coil.BookDefaultThumbnailRequest
import snd.komelia.komga.api.model.KomeliaBook
import snd.komelia.ui.LocalStrings
import snd.komelia.ui.common.images.ThumbnailImage

/**
 * The book in progress, at the top of Home, tinted by its own cover.
 *
 * Same book as the "Continue reading" button in the bottom row (one lookup,
 * shared by the caller): the card is what you see on arrival, the button is
 * what is left once you have scrolled down to the shelves.
 *
 * Always light text on a darkened blur, whatever the theme: the background
 * is the cover, so the theme's own text colour would have no fixed contrast.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContinueReadingHero(
    book: KomeliaBook,
    onContinue: () -> Unit,
    onDetails: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    val shape = RoundedCornerShape(20.dp)
    val coverShape = RoundedCornerShape(12.dp)
    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(shape)
            .background(Color(0xFF1A1A1A)),
    ) {
        ThumbnailImage(
            data = BookDefaultThumbnailRequest(book.id),
            cacheKey = book.id.value,
            contentScale = ContentScale.Crop,
            crossfade = false,
            modifier = Modifier
                .matchParentSize()
                .scale(1.3f)
                .blur(28.dp, BlurredEdgeTreatment.Rectangle),
        )
        // Below Android 12 blur() is a no-op: the overlay alone must carry
        // the contrast, hence the heavy right-hand side where the text is.
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.Black.copy(alpha = 0.40f), Color.Black.copy(alpha = 0.72f))
                    )
                )
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.padding(18.dp),
        ) {
            Box(
                Modifier
                    .width(112.dp)
                    .aspectRatio(0.703f)
                    .shadow(10.dp, coverShape)
                    .clip(coverShape)
                    .clickable(onClick = onDetails),
            ) {
                ThumbnailImage(
                    data = BookDefaultThumbnailRequest(book.id),
                    cacheKey = book.id.value,
                    contentScale = ContentScale.Crop,
                    crossfade = false,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    strings.shelves.keepReading.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 1.2.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                )
                Text(
                    book.seriesTitle,
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp, lineHeight = 28.sp),
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    volumeLine(book, strings.ui::volumeNumber),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = Color.White.copy(alpha = 0.88f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val page = book.readProgress?.page
                val total = book.media.pagesCount
                if (page != null && total > 0) {
                    Text(
                        strings.ui.pagesProgress(page, total),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.65f),
                        maxLines = 1,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(top = 6.dp),
                ) {
                    Surface(
                        onClick = onContinue,
                        shape = CircleShape,
                        color = Color.White,
                        contentColor = Color(0xFF0E0E0E),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.height(44.dp).padding(horizontal = 20.dp),
                        ) {
                            Icon(Icons.Rounded.PlayArrow, null, Modifier.size(20.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                strings.ui.continueCta,
                                style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                                maxLines = 1,
                            )
                        }
                    }
                    Surface(
                        onClick = onDetails,
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.16f),
                        contentColor = Color.White,
                        modifier = Modifier.size(44.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.Info, null)
                        }
                    }
                }
            }
        }
    }
}

/**
 * "Vol. 33 — The Bad Joke". A title that already carries the number
 * ("Bleach 33", "Tome 33") is shown alone rather than as "Tome 33 — Tome 33".
 */
private fun volumeLine(book: KomeliaBook, volumeNumber: (String) -> String): String {
    val number = book.metadata.number.trim()
    val title = book.metadata.title.trim()
    return when {
        title.isEmpty() -> volumeNumber(number)
        number.isEmpty() || title.contains(number) -> title
        else -> "${volumeNumber(number)} — $title"
    }
}
