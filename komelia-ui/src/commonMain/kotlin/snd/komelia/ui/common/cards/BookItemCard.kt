package snd.komelia.ui.common.cards

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OfflinePin
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.filter
import snd.komelia.komga.api.model.KomeliaBook
import snd.komelia.offline.sync.model.DownloadEvent
import snd.komelia.ui.LocalBookDownloadEvents
import snd.komelia.ui.LocalHideParenthesesInNames
import snd.komelia.ui.LocalLibraries
import snd.komelia.ui.LocalWindowWidth
import snd.komelia.ui.common.BookReadButton
import snd.komelia.ui.common.components.NoPaddingChip
import snd.komelia.ui.common.images.BookThumbnail
import snd.komelia.ui.common.menus.BookActionsMenu
import snd.komelia.ui.common.menus.BookMenuActions
import snd.komelia.ui.common.readIsSupported
import snd.komelia.ui.platform.WindowSizeClass.COMPACT
import snd.komelia.ui.platform.WindowSizeClass.MEDIUM
import snd.komelia.ui.platform.cursorForHand
import snd.komelia.utils.removeParentheses

@Composable
fun BookImageCard(
    book: KomeliaBook,
    bookMenuActions: BookMenuActions? = null,
    onBookClick: (() -> Unit)? = null,
    onBookReadClick: ((markProgress: Boolean) -> Unit)? = null,
    isSelected: Boolean = false,
    onSelect: (() -> Unit)? = null,
    showSeriesTitle: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val libraries = LocalLibraries.current
    val libraryIsDeleted = remember {
        libraries.value.firstOrNull { it.id == book.libraryId }?.unavailable ?: false
    }
    val hideParentheses = LocalHideParenthesesInNames.current
    val bookTitle = if (hideParentheses && book.oneshot) book.metadata.title.removeParentheses() else book.metadata.title
    val seriesTitle = if (hideParentheses) book.seriesTitle.removeParentheses() else book.seriesTitle

    // Hoisted so long-press anywhere on the card AND the hover-only MoreVert
    // button open the same actions menu (parity with SeriesItemCard). Long-press
    // opens the menu when bookMenuActions is provided; otherwise it selects.
    var isActionsMenuExpanded by remember { mutableStateOf(false) }
    val longClick: (() -> Unit)? = when {
        bookMenuActions != null -> { -> isActionsMenuExpanded = true }
        else -> onSelect
    }

    LibraryItemCard(
        modifier = modifier,
        title = bookTitle,
        secondaryText = if (showSeriesTitle && !book.oneshot) seriesTitle else null,
        secondaryTextTop = true,
        isUnavailable = book.deleted || libraryIsDeleted,
        onClick = onBookClick,
        onLongClick = longClick,
        image = {
            BookThumbnail(
                book.id,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        },
        badges = {
            BookHoverOverlay(
                book = book,
                libraryIsDeleted = libraryIsDeleted,
                bookMenuActions = bookMenuActions,
                onBookReadClick = onBookReadClick,
                onSelect = onSelect,
                isSelected = isSelected,
                isActionsMenuExpanded = isActionsMenuExpanded,
                onActionsMenuExpand = { isActionsMenuExpanded = it },
            ) {
                BookImageBadges(
                    book = book,
                    libraryIsDeleted = libraryIsDeleted,
                )
            }
        },
        progress = {
            val readProgress = book.readProgress
            if (readProgress != null && !readProgress.completed) {
                LinearProgressIndicator(
                    progress = { getReadProgressPercentage(book) },
                    color = MaterialTheme.colorScheme.tertiary,
                    trackColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f),
                    modifier = Modifier.height(4.dp).fillMaxWidth().align(Alignment.BottomStart),
                    drawStopIndicator = {}
                )
            }
        }
    )
}

@Composable
fun BookSimpleImageCard(
    book: KomeliaBook,
    onBookClick: (() -> Unit)? = null,
    fillMaxWidth: Boolean = true,
    modifier: Modifier = Modifier
) {
    val hideParentheses = LocalHideParenthesesInNames.current
    val bookTitle = if (hideParentheses && book.oneshot) book.metadata.title.removeParentheses() else book.metadata.title

    LibraryItemCard(
        modifier = modifier,
        title = bookTitle,
        showText = false,
        onClick = onBookClick,
        fillMaxWidth = fillMaxWidth,
        image = {
            BookThumbnail(
                book.id,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        },
        badges = {
            BookImageBadges(
                book = book,
                libraryIsDeleted = false,
            )
        }
    )
}

@Composable
private fun BookImageBadges(
    book: KomeliaBook,
    libraryIsDeleted: Boolean,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered = interactionSource.collectIsHoveredAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        Row {
            if (book.downloaded) {
                val isOutOfSync = book.isLocalFileOutdated || book.remoteFileUnavailable
                val neonRed = Color(0xFFFF3131)
                val color = if (isOutOfSync) neonRed else MaterialTheme.colorScheme.tertiary

                IndicatorBadge {
                    Icon(
                        imageVector = Icons.Filled.Download,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))
    }
    BookDownloadCardOverlay(book)

    // Fully-read marker: a filled dark-green corner (top-right) with a white
    // check, drawn entirely in a Canvas sized as a FRACTION of the card width
    // so it scales with the card-size setting (a fixed dp looked tiny on large
    // cards). The badges layer is already clipped to the card's rounded corner
    // by ItemCard, so the triangle follows the rounding automatically.
    if (book.readProgress?.completed == true) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopEnd
        ) {
            Canvas(modifier = Modifier.fillMaxWidth(0.31f).aspectRatio(1f)) {
                val s = size.width
                drawPath(
                    path = Path().apply {
                        moveTo(0f, 0f)
                        lineTo(s, 0f)
                        lineTo(s, s)
                        close()
                    },
                    color = Color(0xFF2E7D32)
                )
                // White check, positioned/scaled relative to s (near the
                // triangle's centroid toward the top-right corner).
                val cx = s * 0.64f
                val cy = s * 0.34f
                val stroke = s * 0.08f
                drawLine(
                    color = Color.White,
                    start = Offset(cx - 0.13f * s, cy + 0.01f * s),
                    end = Offset(cx - 0.03f * s, cy + 0.11f * s),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = Color.White,
                    start = Offset(cx - 0.03f * s, cy + 0.11f * s),
                    end = Offset(cx + 0.16f * s, cy - 0.11f * s),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

@Composable
private fun BookDownloadCardOverlay(book: KomeliaBook) {
    val downloadEvents = LocalBookDownloadEvents.current
    var downloadEvent: DownloadEvent? by remember { mutableStateOf(null) }
    LaunchedEffect(downloadEvents, book) {
        downloadEvents?.filter { it.bookId == book.id }?.collect { downloadEvent = it }
    }

    when (val event = downloadEvent) {
        is DownloadEvent.BookDownloadCompleted -> {}
        is DownloadEvent.BookDownloadError -> {}
        is DownloadEvent.BookDownloadProgress -> {

            Box(
                modifier = Modifier.fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .8f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Download, null, tint = MaterialTheme.colorScheme.tertiary)
                CircularProgressIndicator(
                    progress = { event.completed / event.total.toFloat() },
                    color = MaterialTheme.colorScheme.tertiary,
                    trackColor = MaterialTheme.colorScheme.primary,
                )
            }
        }

        null -> {}
    }

}

@Composable
private fun BookHoverOverlay(
    book: KomeliaBook,
    libraryIsDeleted: Boolean,
    bookMenuActions: BookMenuActions?,
    onBookReadClick: ((Boolean) -> Unit)?,
    isSelected: Boolean,
    onSelect: (() -> Unit)?,
    isActionsMenuExpanded: Boolean,
    onActionsMenuExpand: (Boolean) -> Unit,
    content: @Composable () -> Unit
) {
    var isReadButtonExpanded by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered = interactionSource.collectIsHoveredAsState()
    val showOverlay = derivedStateOf { isHovered.value || isActionsMenuExpanded || isReadButtonExpanded || isSelected }

    val border =
        if (showOverlay.value) overlayBorderModifier() else Modifier

    Box(
        modifier = Modifier
            .fillMaxSize()
            .hoverable(interactionSource)
            .then(border),
        contentAlignment = Alignment.Center
    ) {
        content()
        if (showOverlay.value) {
            val backgroundColor =
                if (isSelected)
                    Modifier.background(MaterialTheme.colorScheme.secondary.copy(alpha = .5f))
                else Modifier
            Column(backgroundColor.fillMaxSize()) {
                if (onSelect != null) {
                    SelectionRadioButton(
                        isSelected,
                        onSelect
                    )
                    Spacer(Modifier.weight(1f))
                }

                Row(
                    modifier = Modifier.padding(vertical = 5.dp).fillMaxSize(),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    if (onBookReadClick != null && !book.deleted && readIsSupported(book) && !libraryIsDeleted) {
                        BookReadButton(
                            modifier = Modifier.padding(start = 5.dp, bottom = 5.dp),
                            onRead = { onBookReadClick(true) },
                            onIncognitoRead = { onBookReadClick(false) },
                            onDropdownOpenChange = { isReadButtonExpanded = it }
                        )
                    }

                    Spacer(Modifier.weight(1f))
                    if (bookMenuActions != null)
                        BookMenuActionsDropdown(
                            book = book,
                            bookMenuActions = bookMenuActions,
                            isActionsMenuExpanded = isActionsMenuExpanded,
                            onActionsMenuExpand = onActionsMenuExpand,
                            onSelect = onSelect,
                        )
                }
            }
        }
    }
}

private fun getReadProgressPercentage(book: KomeliaBook): Float {
    val progress = book.readProgress ?: return 0f
    if (progress.completed) return 100f

    return progress.page / book.media.pagesCount.toFloat()
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookDetailedListCard(
    book: KomeliaBook,
    onClick: (() -> Unit)? = null,
    bookMenuActions: BookMenuActions? = null,
    onBookReadClick: ((Boolean) -> Unit)? = null,
    isSelected: Boolean = false,
    onSelect: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered = interactionSource.collectIsHoveredAsState()
    val hideParentheses = LocalHideParenthesesInNames.current
    val bookTitle = if (hideParentheses && book.oneshot) book.metadata.title.removeParentheses() else book.metadata.title

    Card(
        modifier
            .cursorForHand()
            .combinedClickable(onClick = onClick ?: {}, onLongClick = onSelect)
            .hoverable(interactionSource)
            .fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .height(160.dp)
                .fillMaxWidth()
                .then(
                    if (isSelected) Modifier.background(
                        MaterialTheme.colorScheme.secondary.copy(
                            alpha = .3f
                        )
                    )
                    else Modifier
                )
                .padding(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(Modifier.width(100.dp)) {
                BookSimpleImageCard(book = book, fillMaxWidth = true)
                if (onSelect != null && (isSelected || isHovered.value)) {
                    SelectionRadioButton(
                        isSelected,
                        onSelect
                    )
                }
            }
            BookDetailedListDetails(
                bookTitle = bookTitle,
                book = book,
                bookMenuActions = bookMenuActions,
                onBookReadClick = onBookReadClick,
            )
        }
    }

}

@Composable
private fun BookDetailedListDetails(
    bookTitle: String,
    book: KomeliaBook,
    bookMenuActions: BookMenuActions?,
    onBookReadClick: ((Boolean) -> Unit)? = null,
) {
    Column(Modifier.padding(start = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                bookTitle,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        LazyRow(
            modifier = Modifier.padding(vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            item {
                Text(
                    "${book.media.pagesCount} pages",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            items(book.metadata.tags) {
                NoPaddingChip(
                    borderColor = MaterialTheme.colorScheme.surface,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Text(
            book.metadata.summary,
            maxLines = 2,
            style = MaterialTheme.typography.bodyMedium,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 1500.dp)
        )

        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.Start) {
            if (onBookReadClick != null && !book.deleted && readIsSupported(book)) {
                BookReadButton(
                    modifier = Modifier.padding(start = 5.dp, bottom = 5.dp),
                    onRead = { onBookReadClick(true) },
                    onIncognitoRead = { onBookReadClick(false) }
                )
            }
            if (bookMenuActions != null) {
                Box {
                    var isMenuExpanded by remember { mutableStateOf(false) }
                    IconButton(
                        onClick = { isMenuExpanded = true },
                        colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Icon(Icons.Rounded.MoreVert, null)
                    }
                    BookActionsMenu(
                        book = book,
                        actions = bookMenuActions,
                        expanded = isMenuExpanded,
                        showEditOption = true,
                        showDownloadOption = true,
                        onDismissRequest = { isMenuExpanded = false },
                    )
                }
            }
        }
    }
}

@Composable
private fun BookMenuActionsDropdown(
    book: KomeliaBook,
    bookMenuActions: BookMenuActions,
    isActionsMenuExpanded: Boolean,
    onActionsMenuExpand: (Boolean) -> Unit,
    onSelect: (() -> Unit)? = null,
) {
    Box {
        IconButton(
            onClick = { onActionsMenuExpand(true) },
            colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Icon(Icons.Rounded.MoreVert, null)
        }

        BookActionsMenu(
            book = book,
            actions = bookMenuActions,
            expanded = isActionsMenuExpanded,
            showEditOption = true,
            showDownloadOption = true,
            onDismissRequest = { onActionsMenuExpand(false) },
            onSelect = onSelect,
        )
    }
}
