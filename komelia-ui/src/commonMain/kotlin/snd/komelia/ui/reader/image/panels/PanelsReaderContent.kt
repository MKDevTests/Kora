package snd.komelia.ui.reader.image.panels

import androidx.compose.animation.core.tween
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType.Companion.KeyUp
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import snd.komelia.image.ReaderImageResult
import snd.komelia.settings.model.PagedReadingDirection
import snd.komelia.settings.model.PagedReadingDirection.LEFT_TO_RIGHT
import snd.komelia.settings.model.PagedReadingDirection.RIGHT_TO_LEFT
import snd.komelia.settings.model.ReaderTapNavigationMode
import snd.komelia.ui.reader.image.ScreenScaleState
import snd.komelia.ui.reader.image.common.PagedReaderHelpDialog
import snd.komelia.ui.reader.image.common.ReaderAnimation
import snd.komelia.ui.reader.image.common.ReaderControlsOverlay
import snd.komelia.ui.reader.image.common.ReaderImageContent
import snd.komelia.ui.reader.image.common.ScalableContainer
import snd.komelia.ui.reader.image.paged.PagedReaderState.PageNavigationEvent
import snd.komelia.ui.reader.image.paged.PagedReaderState.TransitionPage
import snd.komelia.ui.reader.image.paged.PagedReaderState.TransitionPage.BookEnd
import snd.komelia.ui.reader.image.paged.PagedReaderState.TransitionPage.BookStart
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toSize
import snd.komelia.ui.reader.image.common.AdaptiveBackground

@Composable
fun BoxScope.PanelsReaderContent(
    showHelpDialog: Boolean,
    onShowHelpDialogChange: (Boolean) -> Unit,
    showSettingsMenu: Boolean,
    onShowSettingsMenuChange: (Boolean) -> Unit,
    screenScaleState: ScreenScaleState,
    panelsReaderState: PanelsReaderState,
    volumeKeysNavigation: Boolean,
    tapNavigationMode: ReaderTapNavigationMode,
    onLongPress: (Offset) -> Unit = {},
    onAddNote: (text: String, page: Int, x: Float, y: Float) -> Unit = { _, _, _, _ -> },
) {
    if (showHelpDialog) {
        PagedReaderHelpDialog(onDismissRequest = { onShowHelpDialogChange(false) })
    }

    val density = LocalDensity.current.density
    LaunchedEffect(density) { screenScaleState.setDensity(density) }

    val readingDirection = panelsReaderState.readingDirection.collectAsState().value
    val layoutDirection = when (readingDirection) {
        LEFT_TO_RIGHT -> LayoutDirection.Ltr
        RIGHT_TO_LEFT -> LayoutDirection.Rtl
    }
    val metadata = panelsReaderState.pageMetadata.collectAsState().value
    val currentPageIndex = panelsReaderState.currentPageIndex.collectAsState().value
    val currentContainerSize = screenScaleState.areaSize.collectAsState().value
    val tapToZoom = panelsReaderState.tapToZoom.collectAsState().value
    val adaptiveBackground = panelsReaderState.adaptiveBackground.collectAsState().value

    val ocrResults by panelsReaderState.readerState.ocrResults.collectAsState()
    val ocrPageId by panelsReaderState.readerState.ocrPageId.collectAsState()

    val pagerState = rememberPagerState(
        initialPage = currentPageIndex.page,
        pageCount = { metadata.size }
    )

    LaunchedEffect(Unit) {
        panelsReaderState.pageNavigationEvents.collect { event ->
            if (pagerState.currentPage != event.pageIndex) {
                when (event) {
                    is PageNavigationEvent.Animated -> {
                        pagerState.animateScrollToPage(
                            page = event.pageIndex,
                            animationSpec = ReaderAnimation.navSpringSpec(density)
                        )
                    }

                    is PageNavigationEvent.Immediate -> {
                        pagerState.scrollToPage(event.pageIndex)
                    }
                }
            }
        }
    }

    LaunchedEffect(pagerState.currentPage, pagerState.isScrollInProgress) {
        if (!pagerState.isScrollInProgress) {
            if (pagerState.currentPage < metadata.size) {
                panelsReaderState.onPageChange(pagerState.currentPage)
            }
        }
    }

    val coroutineScope = rememberCoroutineScope()
    val currentPage = panelsReaderState.currentPage.collectAsState().value
    val edgeSampling = if (adaptiveBackground) currentPage?.edgeSampling else null
    val transforms = screenScaleState.transformation.collectAsState().value
    val targetSize = screenScaleState.targetSize.collectAsState().value
    val imageBounds = remember(transforms, targetSize, currentContainerSize) {
        if (targetSize == Size.Zero || currentContainerSize == IntSize.Zero) null
        else {
            val width = targetSize.width * transforms.scale
            val height = targetSize.height * transforms.scale
            val left = (currentContainerSize.width / 2f) - (width / 2f) + transforms.offset.x
            val top = (currentContainerSize.height / 2f) - (height / 2f) + transforms.offset.y
            Rect(left, top, left + width, top + height)
        }
    }

    AdaptiveBackground(
        edgeSampling = edgeSampling,
        imageBounds = imageBounds,
    ) {
        ReaderControlsOverlay(
            readingDirection = layoutDirection,
            onNexPageClick = panelsReaderState::nextPanel,
            onPrevPageClick = panelsReaderState::previousPanel,
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
                    onReadingDirectionChange = panelsReaderState::onReadingDirectionChange,
                    onMoveToNextPage = { coroutineScope.launch { panelsReaderState.nextPanel() } },
                    onMoveToPrevPage = { coroutineScope.launch { panelsReaderState.previousPanel() } },
                    volumeKeysNavigation = volumeKeysNavigation
                )
            }
        ) {
            ScalableContainer(scaleState = screenScaleState) {
                val transitionPage = panelsReaderState.transitionPage.collectAsState().value
                if (transitionPage != null) {
                    TransitionPage(transitionPage)
                } else {
                    if (metadata.isNotEmpty()) {
                        HorizontalPager(
                            state = pagerState,
                            userScrollEnabled = false,
                            reverseLayout = readingDirection == RIGHT_TO_LEFT,
                            modifier = Modifier.fillMaxSize(),
                            key = { if (it < metadata.size) metadata[it].pageNumber else it }
                        ) { pageIdx ->
                            if (pageIdx >= metadata.size) return@HorizontalPager
                            val pageMeta = metadata[pageIdx]

                            val pageState = remember(pageMeta) { mutableStateOf<PanelsReaderState.PanelsPage?>(null) }
                            LaunchedEffect(pageMeta) {
                                pageState.value = panelsReaderState.getPage(pageMeta)
                            }

                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                val panelsPage = pageState.value
                                val ocr = if (panelsPage != null && ocrPageId == panelsPage.metadata.toPageId()) ocrResults else emptyList()
                                ReaderImageContent(
                                    imageResult = panelsPage?.imageResult,
                                    ocrResults = ocr,
                                    onSelectionChanged = { results: List<snd.komelia.image.OcrElementBox> ->
                                        panelsReaderState.readerState.ocrResults.value = results
                                    },
                                    onAddNote = { text, x, y ->
                                        if (panelsPage != null) {
                                            onAddNote(text, panelsPage.metadata.pageNumber - 1, x, y)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun TransitionPage(page: TransitionPage) {
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
private fun SinglePageLayout(page: PanelsReaderState.PanelsPage) {
    Layout(content = { ReaderImageContent(page.imageResult) }) { measurable, constraints ->
        val placeable = measurable.first().measure(constraints)
        val startPadding = (constraints.maxWidth - placeable.width) / 2
        val topPadding = ((constraints.maxHeight - placeable.height) / 2).coerceAtLeast(0)
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeable.placeRelative(startPadding, topPadding)
        }
    }
}

private fun pagedReaderOnKeyEvents(
    event: KeyEvent,
    readingDirection: PagedReadingDirection,
    onReadingDirectionChange: (PagedReadingDirection) -> Unit,
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
        Key.L -> onReadingDirectionChange(LEFT_TO_RIGHT)
        Key.R -> onReadingDirectionChange(RIGHT_TO_LEFT)
        Key.VolumeUp -> if (volumeKeysNavigation) previousPage() else consumed = false
        Key.VolumeDown -> if (volumeKeysNavigation) nextPage() else consumed = false
        else -> consumed = false
    }
    return consumed
}
