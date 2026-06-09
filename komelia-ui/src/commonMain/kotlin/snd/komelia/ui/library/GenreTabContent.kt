package snd.komelia.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import snd.komelia.image.coil.SeriesDefaultThumbnailRequest
import snd.komelia.ui.LocalFloatingToolbarPadding
import snd.komelia.ui.LocalTransparentNavBarPadding
import snd.komelia.ui.LocalUseNewLibraryUI
import snd.komelia.ui.common.cards.LibraryItemCard
import snd.komelia.ui.common.images.ThumbnailImage
import snd.komga.client.series.KomgaSeries
import snd.komga.client.series.KomgaSeriesId

@Composable
fun GenreGridContent(
    genres: List<GenreTile>,
    minSize: Dp,
    overriddenSlugs: Set<String>,
    onGenreClick: (GenreTile) -> Unit,
    onChooseCover: (GenreTile) -> Unit,
    onRename: (GenreTile) -> Unit,
    onResetOverride: (GenreTile) -> Unit,
    modifier: Modifier = Modifier,
    beforeContent: @Composable () -> Unit = {},
) {
    val useNewLibraryUI = LocalUseNewLibraryUI.current
    val cardSpacing = if (useNewLibraryUI) 7.dp else 15.dp
    val horizontalPadding = if (useNewLibraryUI) 10.dp else 20.dp
    val toolbarPadding = LocalFloatingToolbarPadding.current
    val bottomPadding = LocalTransparentNavBarPadding.current

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize),
        horizontalArrangement = Arrangement.spacedBy(cardSpacing),
        verticalArrangement = Arrangement.spacedBy(cardSpacing),
        contentPadding = PaddingValues(
            start = horizontalPadding,
            end = horizontalPadding,
            top = toolbarPadding,
            bottom = bottomPadding + 10.dp,
        ),
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
            GenreCard(
                tile = tile,
                hasOverride = tile.slug in overriddenSlugs,
                onClick = { onGenreClick(tile) },
                onChooseCover = { onChooseCover(tile) },
                onRename = { onRename(tile) },
                onResetOverride = { onResetOverride(tile) },
            )
        }
    }
}

@Composable
private fun GenreCard(
    tile: GenreTile,
    hasOverride: Boolean,
    onClick: () -> Unit,
    onChooseCover: () -> Unit,
    onRename: () -> Unit,
    onResetOverride: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Box(modifier) {
        LibraryItemCard(
            title = tile.label,
            secondaryText = "${tile.count} ${if (tile.count > 1) "séries" else "série"}",
            titleBold = true,
            onClick = onClick,
            onLongClick = { menuExpanded = true },
            image = {
                val coverId = tile.coverSeriesId
                if (coverId != null) {
                    ThumbnailImage(
                        data = SeriesDefaultThumbnailRequest(coverId),
                        cacheKey = coverId.value,
                        contentScale = ContentScale.Crop,
                        crossfade = false,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
                }
            },
        )
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            DropdownMenuItem(
                text = { Text("Choisir la couverture") },
                onClick = {
                    menuExpanded = false
                    onChooseCover()
                },
            )
            DropdownMenuItem(
                text = { Text("Renommer") },
                onClick = {
                    menuExpanded = false
                    onRename()
                },
            )
            if (hasOverride) {
                DropdownMenuItem(
                    text = { Text("Réinitialiser") },
                    onClick = {
                        menuExpanded = false
                        onResetOverride()
                    },
                )
            }
        }
    }
}

/** Pick a cover for a genre from the series already in that genre. */
@Composable
fun GenreCoverPickerDialog(
    genreLabel: String,
    loadSeries: suspend () -> List<KomgaSeries>,
    onPick: (KomgaSeriesId) -> Unit,
    onDismiss: () -> Unit,
) {
    var series by remember { mutableStateOf<List<KomgaSeries>?>(null) }
    LaunchedEffect(Unit) { series = loadSeries() }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text(
                    "Cover for « $genreLabel »",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(12.dp))
                val list = series
                if (list == null) {
                    Box(
                        Modifier.fillMaxWidth().height(200.dp),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(90.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.heightIn(max = 520.dp),
                    ) {
                        items(list, key = { it.id.value }) { s ->
                            Box(
                                Modifier
                                    .aspectRatio(0.7f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { onPick(s.id) },
                            ) {
                                ThumbnailImage(
                                    data = SeriesDefaultThumbnailRequest(s.id),
                                    cacheKey = s.id.value,
                                    contentScale = ContentScale.Crop,
                                    crossfade = false,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Rename a genre's displayed label. An empty value resets to the default. */
@Composable
fun GenreRenameDialog(
    current: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename genre") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text("Displayed name (empty = default)") },
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
