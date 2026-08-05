package snd.komelia.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.github.vinceglb.filekit.dialogs.FileKitMode
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.readBytes
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import snd.komelia.image.coil.SeriesDefaultThumbnailRequest
import snd.komelia.ui.LocalFloatingToolbarPadding
import snd.komelia.ui.platform.rememberGenreCoverFolderPicker
import snd.komelia.ui.LocalTransparentNavBarPadding
import androidx.compose.runtime.CompositionLocalProvider
import snd.komelia.ui.LocalCardLayoutBelow
import snd.komelia.ui.LocalUseNewLibraryUI
import snd.komelia.ui.common.cards.LibraryItemCard
import snd.komelia.ui.common.images.ThumbnailImage
import snd.komga.client.series.KomgaSeries
import snd.komga.client.series.KomgaSeriesId
import snd.komelia.ui.LocalStrings

@Composable
fun GenreGridContent(
    genres: List<GenreTile>,
    minSize: Dp,
    overriddenSlugs: Set<String>,
    onGenreClick: (GenreTile) -> Unit,
    onChooseCover: (GenreTile) -> Unit,
    onRename: (GenreTile) -> Unit,
    onResetOverride: (GenreTile) -> Unit,
    textBelow: Boolean? = null,
    showCount: Boolean = true,
    modifier: Modifier = Modifier,
    beforeContent: @Composable () -> Unit = {},
    /** Non-null shows the bulk cover import button. Receives (file name, bytes). */
    onImportCovers: ((List<Pair<String, ByteArray>>) -> Unit)? = null,
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

        if (onImportCovers != null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                GenreCoverImportButton(onImportCovers)
            }
        }

        if (genres.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    Modifier.fillMaxWidth().padding(40.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        LocalStrings.current.ui.noGenresFoundInThis,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        items(genres, key = { it.tag }) { tile ->
            val card = @Composable {
                GenreCard(
                    tile = tile,
                    hasOverride = tile.slug in overriddenSlugs,
                    showCount = showCount,
                    onClick = { onGenreClick(tile) },
                    onChooseCover = { onChooseCover(tile) },
                    onRename = { onRename(tile) },
                    onResetOverride = { onResetOverride(tile) },
                )
            }
            if (textBelow != null) {
                CompositionLocalProvider(LocalCardLayoutBelow provides textBelow) { card() }
            } else {
                card()
            }
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
    showCount: Boolean = true,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Box(modifier) {
        LibraryItemCard(
            title = tile.label,
            secondaryText = if (showCount && tile.count >= 0) "${tile.count} ${if (tile.count > 1) "séries" else "série"}" else null,
            titleBold = true,
            onClick = onClick,
            onLongClick = { menuExpanded = true },
            image = {
                val coverId = tile.coverSeriesId
                val localPath = tile.coverLocalPath
                when {
                    localPath != null -> ThumbnailImage(
                        data = "file://$localPath",
                        cacheKey = localPath,
                        contentScale = ContentScale.Crop,
                        crossfade = false,
                        modifier = Modifier.fillMaxSize(),
                    )

                    coverId != null -> ThumbnailImage(
                        data = SeriesDefaultThumbnailRequest(coverId),
                        cacheKey = coverId.value,
                        contentScale = ContentScale.Crop,
                        crossfade = false,
                        modifier = Modifier.fillMaxSize(),
                    )

                    else -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
                }
            },
        )
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            DropdownMenuItem(
                text = { Text(LocalStrings.current.ui.choisirLaCouverture) },
                onClick = {
                    menuExpanded = false
                    onChooseCover()
                },
            )
            DropdownMenuItem(
                text = { Text(LocalStrings.current.ui.renommer) },
                onClick = {
                    menuExpanded = false
                    onRename()
                },
            )
            if (hasOverride) {
                DropdownMenuItem(
                    text = { Text(LocalStrings.current.ui.rInitialiser) },
                    onClick = {
                        menuExpanded = false
                        onResetOverride()
                    },
                )
            }
        }
    }
}

/**
 * Pick a cover for a genre. Shows the genre's own series by default, and a live
 * full-text search of the whole library by name (the user names their files).
 */
/**
 * Bulk cover import: pick the FOLDER holding the covers and let the file NAMES
 * decide which genre each one belongs to (see GenreLabels.slugForFileName).
 * Setting 50+ covers one dialog at a time is unusable, and these images are not
 * in the JSON backup — they live in private app storage — so this is also how a
 * lost set is restored from the source files.
 */
@Composable
private fun GenreCoverImportButton(onImportCovers: (List<Pair<String, ByteArray>>) -> Unit) {
    val pickFolder = rememberGenreCoverFolderPicker(onImportCovers)

    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(onClick = pickFolder) {
            Text(LocalStrings.current.ui.importerDesCouverturesDossier)
        }
    }
}

@Composable
fun GenreCoverPickerDialog(
    genreLabel: String,
    loadGenreSeries: suspend () -> List<KomgaSeries>,
    searchSeries: suspend (String) -> List<KomgaSeries>,
    onPick: (KomgaSeriesId) -> Unit,
    onPickLocal: (ByteArray) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    // File(), NOT Image: Image opens the system PHOTO picker, which only lists
    // the media gallery — a cover sitting in Download or any other folder is
    // invisible there. File() opens the document picker, which browses anywhere.
    // No extension filter: filtering maps extensions to MIME types and silently
    // hid images that were right there in the folder.
    val imagePicker = rememberFilePickerLauncher(
        type = FileKitType.File(),
        mode = FileKitMode.Single,
    ) { file ->
        file?.let { picked -> scope.launch { onPickLocal(picked.readBytes()) } }
    }
    var query by remember { mutableStateOf("") }
    var series by remember { mutableStateOf<List<KomgaSeries>?>(null) }

    LaunchedEffect(query) {
        if (query.isBlank()) {
            series = loadGenreSeries()
        } else {
            delay(350)
            series = searchSeries(query.trim())
        }
    }

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
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(LocalStrings.current.ui.searchASeriesByName) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = { imagePicker.launch() }) {
                    Text(LocalStrings.current.ui.importAnImage)
                }
                Spacer(Modifier.height(12.dp))
                val list = series
                when {
                    list == null -> Box(
                        Modifier.fillMaxWidth().height(200.dp),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }

                    list.isEmpty() -> Box(
                        Modifier.fillMaxWidth().height(200.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (query.isBlank()) "No series in this genre." else "No match.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    else -> LazyVerticalGrid(
                        columns = GridCells.Adaptive(90.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.heightIn(max = 480.dp),
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
        title = { Text(LocalStrings.current.ui.renameGenre) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text(LocalStrings.current.ui.displayedNameEmptyDefault) },
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text(LocalStrings.current.ui.ok) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(LocalStrings.current.ui.cancel) } },
    )
}
