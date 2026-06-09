package snd.komelia.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import snd.komelia.ui.common.images.SeriesThumbnail

@Composable
fun GenreGridContent(
    genres: List<GenreTile>,
    minSize: Dp,
    onGenreClick: (GenreTile) -> Unit,
    modifier: Modifier = Modifier,
    beforeContent: @Composable () -> Unit = {},
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { beforeContent() }

        if (genres.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    Modifier.fillMaxWidth().padding(40.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "No genres found in this library. Add kora:genre:* tags to your series in Komga.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        items(genres, key = { it.tag }) { tile ->
            GenreCard(tile = tile, onClick = { onGenreClick(tile) })
        }
    }
}

@Composable
private fun GenreCard(
    tile: GenreTile,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(0.703f)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
    ) {
        if (tile.coverSeriesId != null) {
            SeriesThumbnail(
                seriesId = tile.coverSeriesId,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
        }

        // Bottom scrim so the label stays readable over any cover.
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0.45f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.78f),
                )
            )
        )

        Column(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(8.dp),
        ) {
            Text(
                tile.label,
                color = Color.White,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${tile.count} ${if (tile.count > 1) "séries" else "série"}",
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
