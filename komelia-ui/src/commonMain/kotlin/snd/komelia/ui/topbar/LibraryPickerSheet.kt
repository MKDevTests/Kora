package snd.komelia.ui.topbar

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.LocalLibrary
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import snd.komelia.image.coil.SeriesDefaultThumbnailRequest
import snd.komelia.ui.LibraryPreview
import snd.komelia.ui.LocalAccentColor
import snd.komelia.ui.LocalKomgaState
import snd.komelia.ui.LocalOfflineMode
import snd.komelia.ui.LocalStrings
import snd.komelia.ui.MainScreenViewModel
import snd.komelia.ui.common.images.ThumbnailImage
import snd.komelia.ui.common.menus.LibraryActionsMenu
import snd.komelia.ui.dialogs.libraryedit.LibraryEditDialogs
import snd.komga.client.library.KomgaLibrary
import snd.komga.client.library.KomgaLibraryId
import snd.komga.client.series.KomgaSeriesId

/**
 * The library picker: a sheet that rises from the bottom, one tile per
 * library with a fan of its latest covers, "All libraries" across the top
 * and the two personal lists below.
 *
 * It replaced the left drawer, which on a server with a handful of libraries
 * was a 230 dp column of six words and 60 % empty space. Long-press on a
 * tile keeps what the drawer's three-dot button did (scan, edit, empty
 * trash…), with the same admin-or-offline gate.
 *
 * @param currentLibraryId the library on screen, or null when none is.
 * @param allSelected true when the "all libraries" view is on screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryPickerSheet(
    vm: MainScreenViewModel,
    currentLibraryId: KomgaLibraryId?,
    allSelected: Boolean,
    onDismiss: () -> Unit,
    onAllLibraries: () -> Unit,
    onLibrary: (KomgaLibraryId) -> Unit,
    onFavorites: () -> Unit,
    onPlanned: () -> Unit,
) {
    val strings = LocalStrings.current.ui
    val libraries = vm.libraries.collectAsState().value
    val previews = vm.libraryPreviews.collectAsState().value
    val isAdmin = LocalKomgaState.current.authenticatedUser.collectAsState().value?.roleAdmin() ?: true
    val isOffline = LocalOfflineMode.current.collectAsState().value
    val accent = LocalAccentColor.current ?: MaterialTheme.colorScheme.primary
    val tileColor = MaterialTheme.colorScheme.surfaceContainerHigh

    var showAddDialog by remember { mutableStateOf(false) }
    if (showAddDialog) {
        LibraryEditDialogs(library = null, onDismissRequest = { showAddDialog = false })
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns = if (maxWidth >= 900.dp) 3 else 2
            val gap = 10.dp
            val tileWidth = (maxWidth - 32.dp - gap * (columns - 1)) / columns
            // A phone tile is ~160 dp: the fan beside the name leaves the name
            // 35 dp. Under 260 dp the fan goes on top instead.
            val sideBySide = tileWidth >= 260.dp

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                ) {
                    Text(
                        strings.libraries,
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp),
                        modifier = Modifier.weight(1f),
                    )
                    if (isAdmin && !isOffline) {
                        Surface(
                            onClick = { showAddDialog = true },
                            shape = CircleShape,
                            color = tileColor,
                            contentColor = accent,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.height(36.dp).padding(horizontal = 14.dp),
                            ) {
                                Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(strings.addLibrary, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }

                if (libraries.isNotEmpty()) {
                    AllLibrariesTile(
                        libraries = libraries,
                        previews = previews,
                        selected = allSelected,
                        accent = accent,
                        tileColor = tileColor,
                        onClick = onAllLibraries,
                    )
                    Spacer(Modifier.height(gap))
                }

                libraries.chunked(columns).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        row.forEach { library ->
                            LibraryTile(
                                library = library,
                                preview = previews[library.id],
                                selected = library.id == currentLibraryId,
                                sideBySide = sideBySide,
                                canManage = isAdmin || isOffline,
                                accent = accent,
                                tileColor = tileColor,
                                onClick = { onLibrary(library.id) },
                                vm = vm,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                    Spacer(Modifier.height(gap))
                }

                Text(
                    strings.myLists.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 10.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    ListTile(
                        icon = Icons.Rounded.Star,
                        iconColor = Color(0xFFFACC15),
                        title = strings.favoris,
                        subtitle = strings.starredSeries,
                        tileColor = tileColor,
                        onClick = onFavorites,
                        modifier = Modifier.weight(1f),
                    )
                    ListTile(
                        icon = Icons.Rounded.Bookmark,
                        iconColor = accent,
                        title = strings.lire,
                        subtitle = strings.readingList,
                        tileColor = tileColor,
                        onClick = onPlanned,
                        modifier = Modifier.weight(1f),
                    )
                }

                if (isAdmin || isOffline) {
                    Text(
                        strings.libraryLongPressHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AllLibrariesTile(
    libraries: List<KomgaLibrary>,
    previews: Map<KomgaLibraryId, LibraryPreview>,
    selected: Boolean,
    accent: Color,
    tileColor: Color,
    onClick: () -> Unit,
) {
    val strings = LocalStrings.current.ui
    // One cover per library, so the strip reads as "a bit of everything".
    val covers = libraries.mapNotNull { previews[it.id]?.coverSeriesIds?.firstOrNull() }.take(5)
    val loaded = libraries.all { previews.containsKey(it.id) }
    val totalSeries = libraries.sumOf { previews[it.id]?.seriesCount ?: 0 }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) accent.copy(alpha = 0.12f).compositeOver(tileColor) else tileColor,
        modifier = Modifier
            .fillMaxWidth()
            .height(84.dp)
            .then(if (selected) Modifier.border(2.dp, accent, RoundedCornerShape(16.dp)) else Modifier),
    ) {
        Box {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 14.dp)) {
                // Overlapping strip: each cover 28 dp right of the previous one.
                val strip: List<KomgaSeriesId?> = covers.ifEmpty { listOf(null, null, null) }
                Box(Modifier.width(40.dp + 28.dp * (strip.size - 1)).height(56.dp)) {
                    strip.forEachIndexed { i, id ->
                        Cover(id, 40.dp, 56.dp, Modifier.offset(x = 28.dp * i).zIndex(i.toFloat()))
                    }
                }
                Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        strings.allLibraries,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (loaded) "${strings.librariesCount(libraries.size)} · ${strings.seriesCount(totalSeries)}"
                        else strings.librariesCount(libraries.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (selected) SelectedCheck(accent, Modifier.align(Alignment.TopEnd))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryTile(
    library: KomgaLibrary,
    preview: LibraryPreview?,
    selected: Boolean,
    sideBySide: Boolean,
    canManage: Boolean,
    accent: Color,
    tileColor: Color,
    onClick: () -> Unit,
    vm: MainScreenViewModel,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current.ui
    var showMenu by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (selected) accent.copy(alpha = 0.12f).compositeOver(tileColor) else tileColor)
            .then(if (selected) Modifier.border(2.dp, accent, shape) else Modifier)
            .combinedClickable(
                onClick = onClick,
                onLongClick = if (canManage) ({ showMenu = true }) else null,
            ),
    ) {
        val subtitle = when {
            library.unavailable -> strings.unavailable
            preview != null -> strings.seriesCount(preview.seriesCount)
            else -> ""
        }
        if (sideBySide) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(118.dp)) {
                CoverFan(preview, Modifier.width(124.dp).height(118.dp))
                TileText(library.name, subtitle, Modifier.weight(1f).padding(end = 12.dp))
            }
        } else {
            Column(Modifier.padding(bottom = 12.dp)) {
                CoverFan(preview, Modifier.fillMaxWidth().height(108.dp), centered = true)
                TileText(library.name, subtitle, Modifier.padding(horizontal = 12.dp))
            }
        }
        if (selected) SelectedCheck(accent, Modifier.align(Alignment.TopEnd))
        if (canManage) {
            Box(Modifier.align(Alignment.BottomEnd)) {
                LibraryActionsMenu(
                    library = library,
                    actions = vm.getLibraryActions(),
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                )
            }
        }
    }
}

@Composable
private fun TileText(name: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            name,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Three covers fanned out like cards in a hand: -9°, 0°, +9°, middle one on top. */
@Composable
private fun CoverFan(preview: LibraryPreview?, modifier: Modifier, centered: Boolean = false) {
    val ids = preview?.coverSeriesIds.orEmpty()
    Box(modifier, contentAlignment = if (centered) Alignment.Center else Alignment.CenterStart) {
        if (preview != null && ids.isEmpty()) {
            Box(
                Modifier
                    .padding(start = if (centered) 0.dp else 24.dp)
                    .size(76.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.LocalLibrary, null, Modifier.size(34.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Box
        }
        Box(Modifier.width(102.dp).height(86.dp).then(if (centered) Modifier else Modifier.offset(x = 12.dp))) {
            // Left and right first, the middle one last so it sits on top.
            Cover(ids.getOrNull(0), 54.dp, 78.dp, Modifier.offset(x = 0.dp, y = 4.dp).rotate(-9f))
            Cover(ids.getOrNull(2), 54.dp, 78.dp, Modifier.offset(x = 48.dp, y = 4.dp).rotate(9f))
            Cover(ids.getOrNull(1), 54.dp, 78.dp, Modifier.offset(x = 24.dp, y = 0.dp))
        }
    }
}

@Composable
private fun Cover(id: KomgaSeriesId?, width: Dp, height: Dp, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier
            .size(width, height)
            .shadow(6.dp, shape)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        if (id != null) {
            ThumbnailImage(
                data = SeriesDefaultThumbnailRequest(id),
                cacheKey = id.value,
                contentScale = ContentScale.Crop,
                crossfade = false,
            )
        }
    }
}

@Composable
private fun SelectedCheck(accent: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .padding(10.dp)
            .size(24.dp)
            .clip(CircleShape)
            .background(accent),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Check, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.surface)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ListTile(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    subtitle: String,
    tileColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = tileColor,
        modifier = modifier.height(72.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 14.dp)) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(iconColor.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, Modifier.size(22.dp), tint = iconColor)
            }
            Spacer(Modifier.width(14.dp))
            TileText(title, subtitle, Modifier.weight(1f))
        }
    }
}

