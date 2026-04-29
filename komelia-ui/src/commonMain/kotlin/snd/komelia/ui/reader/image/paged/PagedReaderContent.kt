package snd.komelia.ui.reader.image.paged

import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType.Companion.KeyUp
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import snd.komelia.ui.LocalKeyEvents
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import snd.komelia.image.ReaderImageResult
import snd.komelia.settings.model.PageDisplayLayout.DOUBLE_PAGES
import snd.komelia.settings.model.PageDisplayLayout.DOUBLE_PAGES_NO_COVER
import snd.komelia.settings.model.PageDisplayLayout.SINGLE_PAGE
import snd.komelia.settings.model.PagedReadingDirection
import snd.komelia.settings.model.ReaderTapNavigationMode
import snd.komelia.settings.model.PagedReadingDirection.LEFT_TO_RIGHT
import snd.komelia.settings.model.PagedReadingDirection.RIGHT_TO_LEFT
import snd.komelia.ui.reader.image.ScreenScaleState
import snd.komelia.ui.reader.image.common.PagedReaderHelpDialog
import snd.komelia.ui.reader.image.common.ReaderControlsOverlay
import snd.komelia.ui.reader.image.common.ReaderImageContent
import snd.komelia.ui.reader.image.common.ScalableContainer
import androidx.compose.ui.platform.LocalDensity
import snd.komelia.ui.reader.image.common.ReaderAnimation
import snd.komelia.ui.reader.image.paged.PagedReaderState.Page
import snd.komelia.ui.reader.image.paged.PagedReaderState.TransitionPage
import snd.komelia.ui.reader.image.paged.PagedReaderState.TransitionPage.BookEnd
import snd.komelia.ui.reader.image.paged.PagedReaderState.TransitionPage.BookStart
import snd.komelia.ui.reader.image.common.AdaptiveBackground
import kotlin.math.abs

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.toSize

@Composable
fun BoxScope.PagedReaderContent(
    showHelpDialog: Boolean,
    onShowHelpDialogChange: (Boolean) -> Unit,
    showSettingsMenu: Boolean,
    onShowSettingsMenuChange: (Boolean) -> Unit,
    screenScaleState: ScreenScaleState,
    pagedReaderState: PagedReaderState,
    volumeKeysNavigation: Boolean,
    tapNavigationMode: ReaderTapNavigationMode,
    onLongPress: (Offset) -> Unit = {},
    annotations: List<snd.komelia.annotations.BookAnnotation> = emptyList(),
    onAnnotationTap: (snd.komelia.annotations.BookAnnotation) -> Unit = {},
    onAddNote: (text: String, page: Int, x: Float, y: Float) -> Unit = { _, _, _, _ -> },
) {
    if (showHelpDialog) {
        PagedReaderHelpDialog(onDismissRequest = { onShowHelpDialogChange(false) })
    }

    val density = LocalDensity.current.density
    LaunchedEffect(density) { screenScaleState.setDensity(density) }

    val readingDirection = pagedReaderState.readingDirection.collectAsState().value
    val layoutDirection = when (readingDirection) {
        LEFT_TO_RIGHT -> LayoutDirection.Ltr
        RIGHT_TO_LEFT -> LayoutDirection.Rtl
    }
    val spreads = pagedReaderState.pageSpreads.collectAsState().value
    val currentSpreadIndex = pagedReaderState.currentSpreadIndex.collectAsState().value
    val layout = pagedReaderState.layout.collectAsState().value
    val layoutOffset = pagedReaderState.layoutOffset.collectAsState().value
    val tapToZoom = pagedReaderState.tapToZoom.collectAsState().value
    val adaptiveBackground = pagedReaderState.adaptiveBackground.collectAsState().value

    val ocrResults by pagedReaderState.readerState.ocrResults.collectAsState()
    val ocrPageId by pagedReaderState.readerState.ocrPageId.collectAsState()

    val keyEvents = LocalKeyEvents.current
    LaunchedEffect(volumeKeysNavigation, readingDirection) {
        keyEvents.collect { event ->
            if (!volumeKeysNavigation) return@collect
            if (event.type != KeyUp) return@collect
            when (event.key) {
                Key.VolumeUp -> {
                    if (readingDirection == LEFT_TO_RIGHT) pagedReaderState.previousPage()
                    else pagedReaderState.nextPage()
                }
                Key.VolumeDown -> {
                    if (readingDirection == LEFT_TO_RIGHT) pagedReaderState.nextPage()
                    else pagedReaderState.previousPage()
                }
                else -> {}
            }
        }
    }
    val currentContainerSize = screenScaleState.areaSize.collectAsState().value

    val pagerState = rememberPagerState(
        initialPage = currentSpreadIndex,
        pageCount = { spreads.size }
    )

    LaunchedEffect(pagerState, readingDirection) {
        screenScaleState.setScrollState(pagerState)
        screenScaleState.setScrollOrientation(Orientation.Horizontal, readingDirection == RIGHT_TO_LEFT)
    }

    val coroutineScope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        pagedReaderState.pageNavigationEvents.collect { event ->
            if (pagerState.currentPage != event.pageIndex) {
                when (event) {
                    is PagedReaderState.PageNavigationEvent.Animated -> {
                        pagerState.animateScrollToPage(
                            page = event.pageIndex,
                            animationSpec = ReaderAnimation.navSpringSpec(density)
                        )
                    }

                    is PagedReaderState.PageNavigationEvent.Immediate -> {
                        pagerState.scrollToPage(event.pageIndex)
                    }
                }
            }
        }
    }

    LaunchedEffect(pagerState.currentPage, pagerState.isScrollInProgress) {
        if (!pagerState.isScrollInProgress) {
            if (pagerState.currentPage < spreads.size) {
                pagedReaderState.onPageChange(pagerState.currentPage)
            }
        }
    }

    // Snapping effect
    val isGestureInProgress by screenScaleState.isGestureInProgress.collectAsState()
    val isFlinging by screenScaleState.isFlinging.collectAsState()
    LaunchedEffect(isGestureInProgress, isFlinging) {
        if (!isGestureInProgress && !isFlinging) {
            val pageOffset = pagerState.currentPageOffsetFraction
            if (abs(pageOffset) > 0.001f) {
                pagerState.animateScrollToPage(
                    page = pagerState.currentPage,
                    animationSpec = ReaderAnimation.navSpringSpec(density)
                )
            }
        }
    }

    ReaderControlsOverlay(
        readingDirection = layoutDirection,
        onNexPageClick = { coroutineScope.launch { pagedReaderState.nextPage() } },
        onPrevPageClick = { coroutineScope.launch { pagedReaderState.previousPage() } },
        contentAreaSize = currentContainerSize,
        scaleState = screenScaleState,
        tapToZoom = tapToZoom,
        tapNavigationMode = tapNavigationMode,
        isSettingsMenuOpen = showSettingsMenu,
        onSettingsMenuToggle = { onShowSettingsMenuChange(!showSettingsMenu) },
        onLongPress = onLongPress,
        modifier = Modifier.onKeyEvent { event ->
            pagedReaderOnKeyEvents(
                event = event,
                readingDirection = readingDirection,
                layoutOffset = layoutOffset,
                onReadingDirectionChange = pagedReaderState::onReadingDirectionChange,
                onScaleTypeCycle = pagedReaderState::onScaleTypeCycle,
                onLayoutCycle = pagedReaderState::onLayoutCycle,
                onChangeLayoutOffset = pagedReaderState::onLayoutOffsetChange,
                onPageChange = pagedReaderState::onPageChange,
                onMoveToLastPage = pagedReaderState::moveToLastPage,
                onMoveToNextPage = { coroutineScope.launch { pagedReaderState.nextPage() } },
                onMoveToPrevPage = { coroutineScope.launch { pagedReaderState.previousPage() } },
                volumeKeysNavigation = volumeKeysNavigation
            )
        }
    ) {
        ScalableContainer(
            scaleState = screenScaleState,
            flingSpec = exponentialDecay(frictionMultiplier = 0.4f),
        ) {
            val transitionPage = pagedReaderState.transitionPage.collectAsState().value
            if (transitionPage != null) {
                TransitionPage(transitionPage)
            } else {
                if (spreads.isNotEmpty()) {
                        HorizontalPager(
                            state = pagerState,
                            userScrollEnabled = false,
                            reverseLayout = readingDirection == RIGHT_TO_LEFT,
                            modifier = Modifier.fillMaxSize(),
                            key = { if (it < spreads.size) spreads[it].first().pageNumber else it }
                        ) { pageIdx ->
                            if (pageIdx >= spreads.size) return@HorizontalPager
                            val spreadMetadata = spreads[pageIdx]
                            val spreadPages = remember(spreadMetadata) {
                                spreadMetadata.map { meta ->
                                    val pageState = mutableStateOf<Page?>(null)
                                    meta to pageState
                                }
                            }

                            spreadPages.forEach { (meta, pageState) ->
                                LaunchedEffect(meta) {
                                    pageState.value = pagedReaderState.getPage(meta)
                                }
                            }

                            val pages = spreadPages.map { (meta, pageState) ->
                                pageState.value ?: Page(meta, null)
                            }

                            val edgeSampling = if (adaptiveBackground && pages.size == 1) pages.first().edgeSampling else null
                            val imageSize = if (pages.size == 1) pages.first().imageSize else null
                            val imageBounds = remember(imageSize, currentContainerSize) {
                                if (imageSize == null) null
                                else {
                                    val left = (currentContainerSize.width - imageSize.width) / 2f
                                    val top = (currentContainerSize.height - imageSize.height) / 2f
                                    Rect(Offset(left, top), imageSize.toSize())
                                }
                            }
                            LaunchedEffect(imageBounds) {
                                pagedReaderState.lastImageBounds.value = imageBounds
                            }

                            AdaptiveBackground(
                                edgeSampling = edgeSampling,
                                imageBounds = imageBounds,
                            ) {
                                when (layout) {
                                    SINGLE_PAGE -> pages.firstOrNull()?.let {
                                        val ocr = if (ocrPageId == it.metadata.toPageId()) ocrResults else emptyList()
                                        SinglePageLayout(
                                            page = it,
                                            ocrResults = ocr,
                                            onSelectionChanged = { results -> pagedReaderState.readerState.ocrResults.value = results },
                                            onAddNote = { text, x, y -> onAddNote(text, it.metadata.pageNumber - 1, x, y) }
                                        )
                                    }

                                    DOUBLE_PAGES, DOUBLE_PAGES_NO_COVER -> DoublePageLayout(
                                        pages = pages,
                                        readingDirection = readingDirection,
                                        ocrResults = ocrResults,
                                        ocrPageId = ocrPageId,
                                        onSelectionChanged = { results -> pagedReaderState.readerState.ocrResults.value = results },
                                        onAddNote = { text, page, x, y -> onAddNote(text, page, x, y) }
                                    )
                                }
                                // Annotation pins overlay
                                val pageAnnotations = annotationsForPage(annotations, pageIdx)
                                snd.komelia.ui.reader.image.ComicAnnotationOverlay(
                                    annotations = pageAnnotations,
                                    imageBounds = imageBounds,
                                    onAnnotationTap = onAnnotationTap,
                                )
                            }
                        }
                }
            }
        }
    }
}


private fun annotationsForPage(
    annotations: List<snd.komelia.annotations.BookAnnotation>,
    pageNumber: Int,
): List<snd.komelia.annotations.BookAnnotation> {
    return annotations.filter { annotation ->
        (annotation.location as? snd.komelia.annotations.AnnotationLocation.ComicLocation)?.page == pageNumber
    }
}

@Composable
private fun TransitionPage(page: TransitionPage) {
    // ... rest of file same
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (page) {
            is BookEnd -> {
                Column {
                    Text("Finished:", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        page.currentBook.metadata.title,
                        style = MaterialTheme.typography.titleLarge
                    )
                }
                Spacer(Modifier.size(50.dp))

                if (page.nextBook != null) {
                    Column {
                        Text("Next:", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            page.nextBook.metadata.title,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                } else {
                    Text("There's no next book")
                }

            }

            is BookStart -> {
                if (page.previousBook != null) {
                    Column {
                        Text("Previous:", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            page.previousBook.metadata.title,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                } else {
                    Text("There's no previous book")

                }
                Spacer(Modifier.size(50.dp))
                Column {
                    Text("Current:", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        page.currentBook.metadata.title,
                        style = MaterialTheme.typography.titleLarge
                    )
                }

            }
        }
    }
}

@Composable
private fun SinglePageLayout(
    page: Page,
    ocrResults: List<snd.komelia.image.OcrElementBox>,
    onSelectionChanged: (List<snd.komelia.image.OcrElementBox>) -> Unit,
    onAddNote: (text: String, x: Float, y: Float) -> Unit,
) {
    Layout(content = {
        ReaderImageContent(
            imageResult = page.imageResult,
            ocrResults = ocrResults,
            onSelectionChanged = onSelectionChanged,
            onAddNote = onAddNote,
        )
    }) { measurable, constraints ->
        val placeable = measurable.first().measure(constraints)
        val startPadding = (constraints.maxWidth - placeable.width) / 2
        val topPadding = ((constraints.maxHeight - placeable.height) / 2).coerceAtLeast(0)
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeable.placeRelative(startPadding, topPadding)
        }
    }
}

@Composable
private fun DoublePageLayout(
    pages: List<Page>,
    readingDirection: PagedReadingDirection,
    ocrResults: List<snd.komelia.image.OcrElementBox>,
    ocrPageId: snd.komelia.image.ReaderImage.PageId?,
    onSelectionChanged: (List<snd.komelia.image.OcrElementBox>) -> Unit,
    onAddNote: (text: String, page: Int, x: Float, y: Float) -> Unit,
) {
    Layout(content = {
        when (pages.size) {
            0 -> {}
            1 -> {
                val page = pages.first()
                val ocr = if (ocrPageId == page.metadata.toPageId()) ocrResults else emptyList()
                ReaderImageContent(
                    imageResult = page.imageResult,
                    ocrResults = ocr,
                    onSelectionChanged = onSelectionChanged,
                    onAddNote = { text, x, y -> onAddNote(text, page.metadata.pageNumber - 1, x, y) }
                )
            }

            2 -> {
                val page1 = pages[0]
                val ocr1 = if (ocrPageId == page1.metadata.toPageId()) ocrResults else emptyList()
                ReaderImageContent(
                    imageResult = page1.imageResult,
                    ocrResults = ocr1,
                    onSelectionChanged = onSelectionChanged,
                    onAddNote = { text, x, y -> onAddNote(text, page1.metadata.pageNumber - 1, x, y) }
                )

                val page2 = pages[1]
                val ocr2 = if (ocrPageId == page2.metadata.toPageId()) ocrResults else emptyList()
                ReaderImageContent(
                    imageResult = page2.imageResult,
                    ocrResults = ocr2,
                    onSelectionChanged = onSelectionChanged,
                    onAddNote = { text, x, y -> onAddNote(text, page2.metadata.pageNumber - 1, x, y) }
                )
            }

            else -> error("can't display more than 2 images")
        }
    }) { measurables, constraints ->
        val measured = measurables
            .map { it.measure(constraints.copy(maxWidth = constraints.maxWidth / measurables.size)) }
            .let {
                when (readingDirection) {
                    LEFT_TO_RIGHT -> it
                    RIGHT_TO_LEFT -> it.reversed()
                }
            }
        val startPadding: Int
        if (measured.size == 1 && !pages.first().metadata.isLandscape()) {
            startPadding = when (readingDirection) {
                LEFT_TO_RIGHT -> (constraints.maxWidth - (measured.first().width * 2)) / 2
                RIGHT_TO_LEFT -> ((constraints.maxWidth - (measured.first().width * 2)) / 2) + measured.first().width
            }
        } else {
            val totalWidth = measured.fold(0) { acc, placeable -> acc + placeable.width }
            startPadding = (constraints.maxWidth - totalWidth) / 2
        }

        var widthTaken = startPadding
        layout(constraints.maxWidth, constraints.maxHeight) {
            measured.forEach {
                val topPadding = ((constraints.maxHeight - it.height) / 2).coerceAtLeast(0)
                it.placeRelative(widthTaken, topPadding)
                widthTaken += it.width
            }
        }
    }
}

private fun pagedReaderOnKeyEvents(
    event: KeyEvent,
    readingDirection: PagedReadingDirection,
    layoutOffset: Boolean,
    onReadingDirectionChange: (PagedReadingDirection) -> Unit,
    onScaleTypeCycle: () -> Unit,
    onLayoutCycle: () -> Unit,
    onChangeLayoutOffset: (Boolean) -> Unit,
    onPageChange: (Int) -> Unit,
    onMoveToLastPage: () -> Unit,
    onMoveToNextPage: () -> Unit,
    onMoveToPrevPage: () -> Unit,
    volumeKeysNavigation: Boolean,
): Boolean {
    if (event.type != KeyUp) {
        return volumeKeysNavigation && (event.key == Key.VolumeUp || event.key == Key.VolumeDown)
    }

    val previousPage = {
        if (readingDirection == LEFT_TO_RIGHT) onMoveToPrevPage()
        else onMoveToNextPage()
    }
    val nextPage = {
        if (readingDirection == LEFT_TO_RIGHT) onMoveToNextPage()
        else onMoveToPrevPage()
    }

    var consumed = true
    when (event.key) {
        Key.DirectionLeft -> {
            previousPage()
            if (event.isAltPressed) consumed = false
        }

        Key.DirectionRight -> nextPage()
        Key.MoveHome -> onPageChange(0)
        Key.MoveEnd -> onMoveToLastPage()
        Key.L -> onReadingDirectionChange(LEFT_TO_RIGHT)
        Key.R -> onReadingDirectionChange(RIGHT_TO_LEFT)
        Key.C -> if (event.isAltPressed) consumed = false else onScaleTypeCycle()
        Key.D -> onLayoutCycle()
        Key.O -> onChangeLayoutOffset(!layoutOffset)
        Key.VolumeUp -> if (volumeKeysNavigation) previousPage() else consumed = false
        Key.VolumeDown -> if (volumeKeysNavigation) nextPage() else consumed = false
        else -> consumed = false
    }
    return consumed
}
