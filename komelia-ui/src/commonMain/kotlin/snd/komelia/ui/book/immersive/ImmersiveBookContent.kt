package snd.komelia.ui.book.immersive

import snd.komelia.ui.common.ThumbnailConstants.ASPECT_RATIO
import snd.komelia.ui.common.ThumbnailConstants.CARD_SCALE
import snd.komelia.ui.LocalCardHeightScale
import snd.komelia.ui.LocalCardSpacingBelow
import snd.komelia.ui.LocalCardWidthScale
import snd.komelia.ui.LocalCardShadowLevel
import snd.komelia.ui.LocalCardCornerRadius
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import coil3.compose.rememberAsyncImagePainter
import kotlinx.coroutines.flow.first
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.toLocalDateTime
import snd.komelia.DefaultDateTimeFormats.localDateTimeFormat
import snd.komelia.image.coil.BookDefaultThumbnailRequest
import snd.komelia.komga.api.model.KomeliaBook
import snd.komelia.ui.LocalAnimatedVisibilityScope
import snd.komelia.ui.LocalHideParenthesesInNames
import snd.komelia.ui.LocalSharedTransitionScope
import snd.komelia.ui.LocalToggleImmersiveMorphingCover
import snd.komelia.ui.LocalUseFloatingNavigationBar
import snd.komelia.ui.LocalUseImmersiveMorphingCover
import snd.komelia.ui.book.BookInfoColumn
import snd.komelia.ui.common.images.ThumbnailImage
import snd.komelia.ui.common.immersive.PublisherLogo
import snd.komelia.ui.common.immersive.ImmersiveDetailFab
import snd.komelia.ui.common.immersive.ImmersiveDetailScaffold
import snd.komelia.ui.common.immersive.extractDominantColor
import snd.komelia.ui.common.immersive.rememberPublisherLogo
import snd.komelia.ui.common.menus.BookActionsMenu
import snd.komelia.ui.common.menus.BookMenuActions
import snd.komelia.ui.dialogs.ConfirmationDialog
import snd.komelia.ui.dialogs.permissions.DownloadNotificationRequestDialog
import snd.komelia.ui.LocalTransparentNavBarPadding
import snd.komelia.ui.library.SeriesScreenFilter
import snd.komelia.ui.readlist.BookReadListsContent
import snd.komelia.ui.series.view.SeriesDescriptionRow
import snd.komelia.utils.removeParentheses
import snd.komga.client.library.KomgaLibrary
import snd.komga.client.readlist.KomgaReadList
import snd.komga.client.series.KomgaSeriesId
import kotlin.math.roundToInt
import snd.komelia.ui.LocalStrings

private val emphasizedAccelerateEasing = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)

private enum class BookImmersiveTab { TAGS, READ_LISTS }

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun ImmersiveBookContent(
    book: KomeliaBook,
    seriesGenres: List<String> = emptyList(),
    onGenreClick: ((String) -> Unit)? = null,
    siblingBooks: List<KomeliaBook>,
    library: KomgaLibrary?,
    accentColor: Color?,
    onLibraryClick: (KomgaLibrary) -> Unit,
    bookMenuActions: BookMenuActions,
    onBackClick: () -> Unit,
    onReadBook: (KomeliaBook, Boolean) -> Unit,
    onDownload: () -> Unit,
    onFilterClick: (SeriesScreenFilter) -> Unit,
    readLists: Map<KomgaReadList, List<KomeliaBook>>,
    onReadListClick: (KomgaReadList) -> Unit,
    onReadListBookPress: (KomeliaBook, KomgaReadList) -> Unit,
    cardWidth: Dp,
    onSeriesClick: (KomgaSeriesId) -> Unit,
    publisher: String? = null,
    onBookChange: (KomeliaBook) -> Unit = {},
    initiallyExpanded: Boolean,
    onExpandChange: (Boolean) -> Unit,
) {
    // Detect when the shared transition is no longer "entering".
    // animatedVisibilityScope is null when there is no shared transition (fallback: treat as settled).
    val animatedVisibilityScope = LocalAnimatedVisibilityScope.current
    val transitionIsSettled = remember(animatedVisibilityScope) {
        derivedStateOf {
            animatedVisibilityScope == null ||
                animatedVisibilityScope.transition.currentState == EnterExitState.Visible
        }
    }

    // pagerExpanded: controls page count (1 → N). Flipped first so the pager can be scrolled.
    // initialScrollDone: controls the pageBook guard. Flipped AFTER scrollToPage so that no
    // page shows the wrong cover during the brief window between expansion and scroll.
    var pagerExpanded by remember { mutableStateOf(false) }
    var initialScrollDone by remember { mutableStateOf(false) }
    val pagerPageCount = if (pagerExpanded) maxOf(1, siblingBooks.size) else 1
    val pagerState = rememberPagerState(pageCount = { pagerPageCount })

    LaunchedEffect(siblingBooks) {
        if (!initialScrollDone && siblingBooks.isNotEmpty()) {
            // Wait for the transition to settle so new pages don't fire animateEnterExit.
            snapshotFlow { transitionIsSettled.value }.first { it }
            val idx = siblingBooks.indexOfFirst { it.id == book.id }.coerceAtLeast(0)
            // Expand pager (pageBook guard still holds — all pages show book's cover).
            pagerExpanded = true
            // Wait for the pager to recognise the expanded page count, then snap to correct page.
            snapshotFlow { pagerState.pageCount }.first { it > idx }
            pagerState.scrollToPage(idx)
            // Only now unlock pageBook — pager is already on the right page, so siblingBooks[idx]
            // is the same book and coverData key is unchanged → no flash.
            initialScrollDone = true
        }
    }

    // selectedBook drives the FAB and 3-dot menu after each swipe settles.
    // Guard by initialScrollDone: before the pager has landed on the right page, always return
    // the originally-tapped book. Without this guard, settledPage=0 when siblings first load
    // would produce selectedBook=siblingBooks[0], triggering onBookChange with the wrong book —
    // which propagates back as the `book` prop and corrupts the pageBook guard above.
    val selectedBook = remember(pagerState.settledPage, siblingBooks, initialScrollDone) {
        if (initialScrollDone) siblingBooks.getOrNull(pagerState.settledPage) ?: book
        else book
    }

    LaunchedEffect(selectedBook) {
        onBookChange(selectedBook)
    }

    var showDownloadConfirmationDialog by remember { mutableStateOf(false) }

    val publisherLogo = rememberPublisherLogo(publisher)

    var currentTab by remember { mutableStateOf(BookImmersiveTab.TAGS) }

    val sharedTransitionScope = LocalSharedTransitionScope.current

    val fabOverlayModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            with(animatedVisibilityScope) {
                Modifier
                    .renderInSharedTransitionScopeOverlay(zIndexInOverlay = 1f)
                    .animateEnterExit(
                        enter = fadeIn(tween(300, delayMillis = 50)),
                        exit = slideOutVertically(tween(200, easing = emphasizedAccelerateEasing)) { it / 2 }
                               + fadeOut(tween(150))
                    )
            }
        }
    } else Modifier

    val uiOverlayModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            with(animatedVisibilityScope) {
                Modifier
                    .renderInSharedTransitionScopeOverlay(zIndexInOverlay = 0.75f)
                    .animateEnterExit(
                        enter = fadeIn(tween(durationMillis = 500)),
                        exit = fadeOut(tween(durationMillis = 100))
                    )
            }
        }
    } else Modifier

    val useMorphingCover = LocalUseImmersiveMorphingCover.current

    Box(modifier = Modifier.fillMaxSize()) {

        // Outer HorizontalPager — slides the entire scaffold (cover + card) laterally
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            pageSpacing = 8.dp,
        ) { pageIndex ->
            // During the transition (initialScrollDone = false, pager has 1 page) always show the
            // tapped book so the shared-element cover key stays stable throughout the animation.
            val pageBook = if (!initialScrollDone) book else (siblingBooks.getOrNull(pageIndex) ?: book)
            // Memoize to avoid a new Random requestCache on every recomposition, which would
            // cause ThumbnailImage's remember(data,cacheKey) to rebuild the ImageRequest and flash.
            val coverData = remember(pageBook.id) { BookDefaultThumbnailRequest(pageBook.id) }
            val coverPainter = rememberAsyncImagePainter(model = coverData)
            val dominantColor = remember(pageBook.id) { mutableStateOf<Color?>(null) }
            LaunchedEffect(pageBook.id) {
                dominantColor.value = extractDominantColor(coverPainter)
            }

            val writers = remember(pageBook.metadata.authors) {
                pageBook.metadata.authors
                    .filter { it.role.lowercase() == "writer" }
                    .joinToString(", ") { it.name }
            }
            val year = pageBook.metadata.releaseDate?.year
            val authorYearText = buildString {
                if (writers.isNotEmpty()) append(writers)
                if (year != null) {
                    if (writers.isNotEmpty()) append(" ")
                    append("($year)")
                }
            }

            val hideParentheses = LocalHideParenthesesInNames.current
            val seriesTitle = if (hideParentheses) pageBook.seriesTitle.removeParentheses() else pageBook.seriesTitle
            val heroTitle = "$seriesTitle ${pageBook.metadata.number}"

            ImmersiveDetailScaffold(
                coverData = coverData,
                coverKey = pageBook.id.value,
                cardColor = dominantColor.value,
                immersive = true,
                initiallyExpanded = initiallyExpanded,
                onExpandChange = onExpandChange,
                publisherLogo = publisherLogo,
                thumbnailWidth = cardWidth,
                heroTextContent = { expandFraction ->
                    snd.komelia.ui.common.immersive.ImmersiveHeroText(
                        seriesTitle = heroTitle,
                        authorYear = authorYearText,
                        chapterTitle = pageBook.metadata.title,
                        expandFraction = expandFraction,
                        accentColor = accentColor,
                        onSeriesClick = { onSeriesClick(pageBook.seriesId) },
                    )
                },
                topBarContent = {},  // Fixed overlay handles this
                fabContent = {},     // Fixed overlay handles this
                cardContent = { expandFraction, onThumbnailPositioned, onTextPositioned ->
                    val cardWidthScale = LocalCardWidthScale.current
                    val cardHeightScale = LocalCardHeightScale.current
                    val cardSpacingBelow = LocalCardSpacingBelow.current
                    val cornerRadius = LocalCardCornerRadius.current
                    val thumbnailOffset = ((cardWidth + 16.dp) * expandFraction).coerceAtLeast(0.dp)
                    val thumbnailTopGap = if (useMorphingCover) 48.dp else 20.dp
                    val thumbnailHeight = ((cardWidth * cardWidthScale * cardHeightScale) / ASPECT_RATIO) + (cardWidth * cardSpacingBelow)

                    val navBarBottom = with(LocalDensity.current) {
                        WindowInsets.navigationBars.getBottom(this).toDp()
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(1),
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(0.dp),
                        contentPadding = PaddingValues(bottom = navBarBottom + 80.dp),
                    ) {
                        // Header: thumbnail offset + series title · #N, book title, writers (year)
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .let { modifier ->
                                        if (useMorphingCover) {
                                            modifier.layout { measurable, constraints ->
                                                val placeable = measurable.measure(constraints)
                                                val expandedHeight = maxOf(
                                                    (thumbnailTopGap + thumbnailHeight).roundToPx(),
                                                    placeable.height
                                                )
                                                val desiredHeight = (expandedHeight * expandFraction).roundToInt()
                                                layout(constraints.maxWidth, desiredHeight) {
                                                    placeable.place(0, 0)
                                                }
                                            }
                                        } else {
                                            modifier.heightIn(min = (thumbnailTopGap + thumbnailHeight) * expandFraction)
                                        }
                                    }
                                    .padding(
                                        start = 16.dp,
                                        end = 16.dp,
                                        top = lerp(8f, thumbnailTopGap.value, expandFraction).dp,
                                    )
                            ) {
                                if (useMorphingCover) {
                                    // Morphing mode: placeholder reports position; scaffold renders the flying
                                    // overlay. The real thumbnail fades in only after the overlay disappears.
                                    Box(
                                        modifier = Modifier
                                            .size(width = cardWidth * cardWidthScale, height = thumbnailHeight)
                                            .onGloballyPositioned { onThumbnailPositioned(it) }
                                            .graphicsLayer { alpha = if (expandFraction > 0.99f) 1f else 0f }
                                    ) {
                                        ThumbnailImage(
                                            data = coverData,
                                            cacheKey = pageBook.id.value,
                                            crossfade = false,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(width = cardWidth * cardWidthScale, height = thumbnailHeight)
                                                .clip(RoundedCornerShape(cornerRadius.dp))
                                        )
                                    }
                                } else if (expandFraction > 0.01f) {
                                    Box(
                                        modifier = Modifier
                                            .graphicsLayer { alpha = (expandFraction * 2f - 1f).coerceIn(0f, 1f) }
                                    ) {
                                        ThumbnailImage(
                                            data = coverData,
                                            cacheKey = pageBook.id.value,
                                            crossfade = false,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(width = cardWidth * cardWidthScale, height = thumbnailHeight)
                                                .clip(RoundedCornerShape(cornerRadius.dp))
                                        )
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .padding(start = thumbnailOffset)
                                        .onGloballyPositioned { onTextPositioned(it) }
                                        .graphicsLayer {
                                            if (useMorphingCover) alpha = if (expandFraction > 0.99f) 1f else 0f
                                        }
                                ) {
                                    snd.komelia.ui.common.immersive.ImmersiveHeroText(
                                        seriesTitle = heroTitle,
                                        authorYear = authorYearText,
                                        chapterTitle = pageBook.metadata.title,
                                        expandFraction = 1f,
                                        accentColor = accentColor,
                                        onSeriesClick = { onSeriesClick(pageBook.seriesId) },
                                        horizontalPadding = 0.dp,
                                        modifier = Modifier.padding(horizontal = 0.dp)
                                    )
                                }

                                if (publisherLogo != null && expandFraction > 0.01f) {
                                    val logoHeight = thumbnailHeight * 0.25f
                                    Image(
                                        bitmap = publisherLogo.image,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .height(logoHeight)
                                            .widthIn(max = 100.dp)
                                            .align(Alignment.BottomEnd)
                                            .graphicsLayer { alpha = (expandFraction * 2f - 1f).coerceIn(0f, 1f) },
                                        contentScale = ContentScale.Fit
                                    )
                                }
                            }
                        }

                        // Stats line
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            // Unified stats line that is always present but fades in/out based on card expansion logic if needed,
                            // or just always stays there. The user wants it below the header.
                            BookStatsLine(pageBook, Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                        }

                        // Description row (library, status, age rating, etc.)
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            if (library != null) {
                                SeriesDescriptionRow(
                                    library = library,
                                    onLibraryClick = onLibraryClick,
                                    releaseDate = null,
                                    status = null,
                                    ageRating = null, // series-level metadata
                                    language = "",    // series-level metadata
                                    readingDirection = null, // series-level metadata
                                    deleted = pageBook.deleted || library.unavailable,
                                    alternateTitles = emptyList(),
                                    onFilterClick = onFilterClick,
                                    totalPagesCount = pageBook.media.pagesCount,
                                    pagesLeftCount = pageBook.readProgress?.let { if (it.completed) null else pageBook.media.pagesCount - it.page },
                                    accentColor = accentColor,
                                    showReleaseYear = false,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                )
                            }
                        }

                        // Summary
                        if (pageBook.metadata.summary.isNotBlank()) {
                            item {
                                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                    Text(
                                        text = pageBook.metadata.summary,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }
                        }

                        // Tab row
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            BookImmersiveTabRow(
                                currentTab = currentTab,
                                onTabChange = { currentTab = it },
                                showReadListsTab = readLists.isNotEmpty(),
                                accentColor = accentColor,
                            )
                        }

                        when (currentTab) {
                            BookImmersiveTab.TAGS -> {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                        BookInfoColumn(
                                            publisher = null,
                                            // Same on every volume of the series,
                                            // and what people navigate by.
                                            genres = seriesGenres.takeIf { it.isNotEmpty() },
                                            authors = pageBook.metadata.authors,
                                            tags = pageBook.metadata.tags,
                                            links = pageBook.metadata.links,
                                            sizeInMiB = pageBook.size,
                                            mediaType = pageBook.media.mediaType,
                                            isbn = pageBook.metadata.isbn,
                                            fileUrl = pageBook.url,
                                            onGenreClick = onGenreClick,
                                            onFilterClick = onFilterClick,
                                        )
                                    }
                                }
                            }

                            BookImmersiveTab.READ_LISTS -> {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    BookReadListsContent(
                                        readLists = readLists,
                                        onReadListClick = onReadListClick,
                                        onBookClick = onReadListBookPress,
                                        cardWidth = cardWidth,
                                    )
                                }
                            }
                        }
                    }
                }
            )
        }

        // Fixed overlay: back button + 3-dot menu (stays still while pager slides)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(uiOverlayModifier)
                .statusBarsPadding()
                .padding(start = 12.dp, end = 4.dp, top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                    .clickable(onClick = onBackClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.White)
            }

            var expandActions by remember { mutableStateOf(false) }
            Box {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                        .clickable { expandActions = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = null, tint = Color.White)
                }
                BookActionsMenu(
                    book = selectedBook,
                    actions = bookMenuActions,
                    expanded = expandActions,
                    showEditOption = true,
                    showDownloadOption = false,  // download is in FAB
                    onDismissRequest = { expandActions = false },
                    onToggleImmersiveMode = LocalToggleImmersiveMorphingCover.current,
                )
            }
        }

        // Fixed overlay: FAB (stays still while pager slides)
        val extraBottomPadding = LocalTransparentNavBarPadding.current
        val useFloatingNavigationBar = LocalUseFloatingNavigationBar.current
        if (useFloatingNavigationBar) {
            ImmersiveDetailFab(
                onReadClick = { onReadBook(selectedBook, true) },
                onReadIncognitoClick = { onReadBook(selectedBook, false) },
                onDownloadClick = { showDownloadConfirmationDialog = true },
                accentColor = accentColor,
                showReadActions = true,
            )
        } else {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .then(fabOverlayModifier)
                    .then(if (extraBottomPadding == 0.dp) Modifier.windowInsetsPadding(WindowInsets.navigationBars) else Modifier)
                    .padding(bottom = 16.dp + extraBottomPadding)
            ) {
                ImmersiveDetailFab(
                    onReadClick = { onReadBook(selectedBook, true) },
                    onReadIncognitoClick = { onReadBook(selectedBook, false) },
                    onDownloadClick = { showDownloadConfirmationDialog = true },
                    accentColor = accentColor,
                    showReadActions = true,
                )
            }
        }
    }

    // Two-step download confirmation dialog
    if (showDownloadConfirmationDialog) {
        var permissionRequested by remember { mutableStateOf(false) }
        DownloadNotificationRequestDialog { permissionRequested = true }
        if (permissionRequested) {
            ConfirmationDialog(
                body = "Download \"${selectedBook.metadata.title}\"?",
                onDialogConfirm = {
                    onDownload()
                    showDownloadConfirmationDialog = false
                },
                onDialogDismiss = { showDownloadConfirmationDialog = false },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookImmersiveTabRow(
    currentTab: BookImmersiveTab,
    onTabChange: (BookImmersiveTab) -> Unit,
    showReadListsTab: Boolean,
    accentColor: Color?,
) {
    val selectedTabIndex = when (currentTab) {
        BookImmersiveTab.TAGS -> 0
        BookImmersiveTab.READ_LISTS -> if (showReadListsTab) 1 else 0
    }
    PrimaryTabRow(
        selectedTabIndex = selectedTabIndex,
        containerColor = Color.Transparent,
        indicator = {
            TabRowDefaults.PrimaryIndicator(
                modifier = Modifier.tabIndicatorOffset(selectedTabIndex),
                width = 48.dp,
                color = accentColor ?: MaterialTheme.colorScheme.primary
            )
        },
        divider = {}
    ) {
        Tab(
            selected = currentTab == BookImmersiveTab.TAGS,
            onClick = { onTabChange(BookImmersiveTab.TAGS) },
            text = { Text(LocalStrings.current.ui.tags) },
        )
        if (showReadListsTab) {
            Tab(
                selected = currentTab == BookImmersiveTab.READ_LISTS,
                onClick = { onTabChange(BookImmersiveTab.READ_LISTS) },
                text = { Text(LocalStrings.current.ui.readLists) },
            )
        }
    }
}

@Composable
private fun BookStatsLine(book: KomeliaBook, modifier: Modifier = Modifier) {
    val segments = remember(book) {
        buildList {
            book.metadata.releaseDate?.let { add("Publication date: $it") }
            book.readProgress?.let { progress ->
                val accessed = progress.readDate
                    .toLocalDateTime(TimeZone.currentSystemDefault())
                    .format(localDateTimeFormat)
                add("last accessed: $accessed")
            }
        }
    }
    if (segments.isEmpty()) return
    Text(
        text = segments.joinToString(" ! "),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}
