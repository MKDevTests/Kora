package snd.komelia.ui.reader.image.common

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType.Companion.KeyUp
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.launch
import snd.komelia.settings.model.ReaderTapNavigationMode
import snd.komelia.settings.model.ReaderType
import snd.komelia.settings.model.ReaderType.CONTINUOUS
import snd.komelia.settings.model.ReaderType.PAGED
import snd.komelia.settings.model.ReaderType.PANELS
import snd.komelia.ui.LocalHazeState
import snd.komelia.ui.LocalPlatform
import snd.komelia.ui.LocalTheme
import snd.komelia.ui.LocalUseNewLibraryUI2
import snd.komelia.ui.LocalWindowState
import snd.komelia.ui.common.components.AnimatedDropdownMenu
import snd.komelia.ui.common.components.LoadingMaxSizeIndicator
import snd.komelia.ui.platform.BackPressHandler
import snd.komelia.ui.platform.PlatformType.MOBILE
import snd.komelia.ui.reader.ReaderTopBar
import snd.komelia.ui.reader.common.ImagePageLocation
import snd.komelia.ui.reader.common.NavigationSource
import snd.komelia.ui.reader.common.ReaderNavigationBackButton
import snd.komelia.ui.reader.image.ReaderState
import snd.komelia.ui.reader.image.ScreenScaleState
import snd.komelia.ui.reader.image.continuous.ContinuousReaderContent
import snd.komelia.ui.reader.image.continuous.ContinuousReaderState
import snd.komelia.ui.reader.image.paged.PagedReaderContent
import snd.komelia.ui.reader.image.paged.PagedReaderState
import snd.komelia.ui.reader.image.panels.PanelsReaderContent
import snd.komelia.ui.reader.image.panels.PanelsReaderState
import snd.komelia.ui.reader.image.settings.SettingsOverlay
import snd.komelia.ui.settings.imagereader.ncnn.NcnnSettingsState
import snd.komelia.ui.settings.imagereader.onnxruntime.OnnxRuntimeSettingsState

@Composable
fun ReaderContent(
    commonReaderState: ReaderState,
    pagedReaderState: PagedReaderState,
    continuousReaderState: ContinuousReaderState,
    panelsReaderState: PanelsReaderState?,
    onnxRuntimeSettingsState: OnnxRuntimeSettingsState?,
    ncnnSettingsState: NcnnSettingsState,
    screenScaleState: ScreenScaleState,

    isColorCorrectionActive: Boolean,
    onColorCorrectionClick: () -> Unit,
    onExit: () -> Unit,
) {
    var showHelpDialog by remember { mutableStateOf(false) }
    var showSettingsMenu by remember { mutableStateOf(false) }
    LaunchedEffect(showSettingsMenu) {
        if (!showSettingsMenu) {
            commonReaderState.showCarousel.value = false
        }
    }
    var showImageContextMenu by remember { mutableStateOf(false) }
    var showComicContentDialog by remember { mutableStateOf(false) }
    var contextMenuAnchorOffset by remember { mutableStateOf(Offset.Zero) }
    val onLongPress: (Offset) -> Unit = { offset ->
        contextMenuAnchorOffset = offset
        showImageContextMenu = true
    }
    if (LocalPlatform.current == MOBILE) {
        val windowState = LocalWindowState.current
        DisposableEffect(showSettingsMenu) {
            if (showSettingsMenu) {
                windowState.setFullscreen(false)
            } else {
                windowState.setFullscreen(true)
            }
            onDispose {
                windowState.setFullscreen(false)
            }
        }
    }
    val coroutineScope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        screenScaleState.composeScope = coroutineScope
    }
    val density = LocalDensity.current
    LaunchedEffect(density) {
        commonReaderState.pixelDensity.value = density
    }

    BackPressHandler {
        when {
            showImageContextMenu -> showImageContextMenu = false
            showSettingsMenu -> showSettingsMenu = false
            showHelpDialog -> showHelpDialog = false
            else -> onExit()
        }
    }

    val useNewUI2 = LocalUseNewLibraryUI2.current

    val theme = LocalTheme.current
    val readerHazeState = if (theme.transparentBars) rememberHazeState() else null

    val topLevelFocus = remember { FocusRequester() }
    val volumeKeysNavigation = commonReaderState.volumeKeysNavigation.collectAsState().value
    val tapNavigationMode = commonReaderState.tapNavigationMode.collectAsState().value
    var hasFocus by remember { mutableStateOf(false) }
    val ocrSettings by commonReaderState.ocrSettings.collectAsState()
    val readerType by commonReaderState.readerType.collectAsState()

    val backButtonBottomPadding by animateDpAsState(
        targetValue = if (showSettingsMenu && useNewUI2) 200.dp else 32.dp,
        animationSpec = spring(),
        label = "BackButtonBottomPadding"
    )
    val isGestureInProgress by screenScaleState.isGestureInProgress.collectAsState()
    LaunchedEffect(isGestureInProgress) {
        if (isGestureInProgress) {
            commonReaderState.navigationHistory.dismissBackButton()
        }
    }

    CompositionLocalProvider(LocalHazeState provides readerHazeState) {
        Box(
            Modifier
                .fillMaxSize()
                .onSizeChanged {
                    screenScaleState.setAreaSize(it)
                }
                .focusable()
                .focusRequester(topLevelFocus)
                .onFocusChanged { hasFocus = it.hasFocus }
                .onKeyEvent { event ->
                    if (event.type != KeyUp) return@onKeyEvent false

                    var consumed = true
                    when (event.key) {
                        Key.M -> showSettingsMenu = !showSettingsMenu
                        Key.Escape -> {
                            when {
                                showImageContextMenu -> showImageContextMenu = false
                                showSettingsMenu -> showSettingsMenu = false
                                showHelpDialog -> showHelpDialog = false
                                else -> consumed = false
                            }
                        }

                        Key.H -> showHelpDialog = true
                        Key.DirectionLeft -> if (event.isAltPressed) onExit() else consumed = false
                        Key.Back -> {
                            when {
                                showImageContextMenu -> showImageContextMenu = false
                                showSettingsMenu -> showSettingsMenu = false
                                showHelpDialog -> showHelpDialog = false
                                else -> onExit()
                            }
                        }

                        Key.U -> commonReaderState.onStretchToFitCycle()
                        Key.C -> if (event.isAltPressed) commonReaderState.onColorCorrectionDisable() else consumed = false
                        else -> consumed = false
                    }
                    consumed
                }
        ) {
            val areaSize = screenScaleState.areaSize.collectAsState()
            if (areaSize.value == IntSize.Zero) {
                LoadingMaxSizeIndicator()
            } else {
                Box(
                    Modifier.fillMaxSize().then(
                        if (readerHazeState != null) Modifier.hazeSource(readerHazeState) else Modifier
                    )
                ) {
                    when (commonReaderState.readerType.collectAsState().value) {
                        PAGED -> {
                            PagedReaderContent(
                                showHelpDialog = showHelpDialog,
                                onShowHelpDialogChange = { showHelpDialog = it },
                                showSettingsMenu = showSettingsMenu,
                                onShowSettingsMenuChange = { showSettingsMenu = it },
                                screenScaleState = screenScaleState,
                                pagedReaderState = pagedReaderState,
                                volumeKeysNavigation = volumeKeysNavigation,
                                tapNavigationMode = tapNavigationMode,
                                onLongPress = onLongPress,
                                annotations = commonReaderState.annotations.collectAsState().value,
                                onAnnotationTap = { annotation ->
                                    commonReaderState.editingComicAnnotation.value = annotation
                                    commonReaderState.showAnnotationDialog.value = true
                                },
                                onAddNote = { text, page, x, y ->
                                    commonReaderState.pendingAnnotationPage.value = page
                                    commonReaderState.pendingAnnotationX.value = x
                                    commonReaderState.pendingAnnotationY.value = y
                                    commonReaderState.pendingAnnotationNote.value = text
                                    commonReaderState.showAnnotationDialog.value = true
                                }
                            )
                        }

                        CONTINUOUS -> {
                            ContinuousReaderContent(
                                showHelpDialog = showHelpDialog,
                                onShowHelpDialogChange = { showHelpDialog = it },
                                showSettingsMenu = showSettingsMenu,
                                onShowSettingsMenuChange = { showSettingsMenu = it },
                                screenScaleState = screenScaleState,
                                continuousReaderState = continuousReaderState,
                                volumeKeysNavigation = volumeKeysNavigation,
                                tapNavigationMode = tapNavigationMode,
                                onLongPress = onLongPress,
                                onAddNote = { text, page, x, y ->
                                    commonReaderState.pendingAnnotationPage.value = page
                                    commonReaderState.pendingAnnotationX.value = x
                                    commonReaderState.pendingAnnotationY.value = y
                                    commonReaderState.pendingAnnotationNote.value = text
                                    commonReaderState.showAnnotationDialog.value = true
                                }
                            )
                        }

                        PANELS -> {
                            check(panelsReaderState != null)
                            PanelsReaderContent(
                                showHelpDialog = showHelpDialog,
                                onShowHelpDialogChange = { showHelpDialog = it },
                                showSettingsMenu = showSettingsMenu,
                                onShowSettingsMenuChange = { showSettingsMenu = it },
                                screenScaleState = screenScaleState,
                                panelsReaderState = panelsReaderState,
                                volumeKeysNavigation = volumeKeysNavigation,
                                tapNavigationMode = tapNavigationMode,
                                onLongPress = onLongPress,
                                onAddNote = { text, page, x, y ->
                                    commonReaderState.pendingAnnotationPage.value = page
                                    commonReaderState.pendingAnnotationX.value = x
                                    commonReaderState.pendingAnnotationY.value = y
                                    commonReaderState.pendingAnnotationNote.value = text
                                    commonReaderState.showAnnotationDialog.value = true
                                }
                            )
                        }
                    }
                }

                Box(
                    Modifier.offset {
                        IntOffset(contextMenuAnchorOffset.x.toInt(), contextMenuAnchorOffset.y.toInt())
                    }
                ) {
                    AnimatedDropdownMenu(
                        expanded = showImageContextMenu,
                        onDismissRequest = { showImageContextMenu = false },
                        transformOrigin = TransformOrigin(0f, 0f)
                    ) {
                        if (!ocrSettings.enabled) {
                            DropdownMenuItem(
                                text = { Text("Select Text") },
                                leadingIcon = { Icon(Icons.Default.TextFields, contentDescription = null) },
                                onClick = {
                                    showImageContextMenu = false
                                    val currentImage = when (readerType) {
                                        PAGED -> pagedReaderState.currentSpread.value.pages.firstOrNull()?.imageResult?.image
                                        CONTINUOUS -> null // TODO
                                        PANELS -> panelsReaderState?.currentPage?.value?.imageResult?.image
                                    }
                                    currentImage?.let { commonReaderState.scanCurrentPageForText(it) }
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Save image") },
                            leadingIcon = { Icon(Icons.Rounded.Download, contentDescription = null) },
                            onClick = {
                                showImageContextMenu = false
                                commonReaderState.saveCurrentPageToDownloads()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Note") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = {
                                showImageContextMenu = false
                                val bounds = pagedReaderState.lastImageBounds.value
                                if (bounds != null && bounds.width > 0 && bounds.height > 0) {
                                    val x = ((contextMenuAnchorOffset.x - bounds.left) / bounds.width).coerceIn(0f, 1f)
                                    val y = ((contextMenuAnchorOffset.y - bounds.top) / bounds.height).coerceIn(0f, 1f)
                                    val page = pagedReaderState.currentSpreadIndex.value
                                    commonReaderState.pendingAnnotationPage.value = page
                                    commonReaderState.pendingAnnotationX.value = x
                                    commonReaderState.pendingAnnotationY.value = y
                                    commonReaderState.showAnnotationDialog.value = true
                                }
                            }
                        )
                    }
                }

                SettingsOverlay(
                    show = showSettingsMenu,
                    commonReaderState = commonReaderState,
                    pagedReaderState = pagedReaderState,
                    continuousReaderState = continuousReaderState,
                    panelsReaderState = panelsReaderState,
                    onnxRuntimeSettingsState = onnxRuntimeSettingsState,
                    ncnnSettingsState = ncnnSettingsState,
                    screenScaleState = screenScaleState,
                    isColorCorrectionsActive = isColorCorrectionActive,
                    onColorCorrectionClick = onColorCorrectionClick,
                    onBackPress = onExit,
                    ohShowHelpDialogChange = { showHelpDialog = it },
                    onNotesClick = { showComicContentDialog = true },
                )

                if (showSettingsMenu && useNewUI2) {
                    val book = commonReaderState.booksState.collectAsState().value?.currentBook
                    val series = commonReaderState.series.collectAsState().value
                    val totalBooks = series?.metadata?.totalBookCount ?: series?.booksCount
                    val allUpscaleActivities by ncnnSettingsState.globalUpscaleActivities.collectAsState()
                    ReaderTopBar(
                        seriesTitle = book?.seriesTitle ?: "",
                        bookNumber = book?.number ?: 0,
                        seriesBookCount = totalBooks,
                        bookTitle = book?.metadata?.title.orEmpty(),
                        onBack = onExit,
                        upscaleActivities = allUpscaleActivities,
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                }

                ReaderNavigationBackButton(
                    isVisible = commonReaderState.navigationHistory.buttonVisible.collectAsState().value,
                    onClick = {
                        val currentIdx = when (readerType) {
                            PAGED -> pagedReaderState.currentSpreadIndex.value
                            CONTINUOUS -> commonReaderState.readProgressPage.value - 1
                            PANELS -> panelsReaderState?.currentPageIndex?.value?.page ?: 0
                        }
                        val entry = commonReaderState.navigationHistory.popEntry()
                        if (entry != null && entry.location is ImagePageLocation) {
                            val targetPage = entry.location.page
                            commonReaderState.navigationHistory.addEntry(
                                NavigationSource.BACK_BUTTON,
                                ImagePageLocation(currentIdx)
                            )
                            when (readerType) {
                                PAGED -> pagedReaderState.jumpToPage(targetPage)
                                CONTINUOUS -> coroutineScope.launch {
                                    continuousReaderState.scrollToBookPage(targetPage + 1)
                                }

                                PANELS -> panelsReaderState?.jumpToPage(targetPage)
                            }
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(
                            bottom = backButtonBottomPadding,
                            end = 32.dp
                        )
                )

                EInkFlashOverlay(
                    enabled = commonReaderState.flashOnPageChange.collectAsState().value,
                    pageChangeFlow = commonReaderState.pageChangeFlow,
                    flashEveryNPages = commonReaderState.flashEveryNPages.collectAsState().value,
                    flashWith = commonReaderState.flashWith.collectAsState().value,
                    flashDuration = commonReaderState.flashDuration.collectAsState().value
                )

                // Comic annotations list dialog
                if (showComicContentDialog) {
                    snd.komelia.ui.reader.image.ComicContentDialog(
                        annotations = commonReaderState.annotations.collectAsState().value,
                        onAnnotationTap = { annotation ->
                            showComicContentDialog = false
                            val loc = annotation.location as? snd.komelia.annotations.AnnotationLocation.ComicLocation
                            if (loc != null) {
                                val currentIdx = when (readerType) {
                                    PAGED -> pagedReaderState.currentSpreadIndex.value
                                    CONTINUOUS -> commonReaderState.readProgressPage.value - 1
                                    PANELS -> panelsReaderState?.currentPageIndex?.value?.page ?: 0
                                }
                                commonReaderState.navigationHistory.addEntry(
                                    NavigationSource.NOTES,
                                    ImagePageLocation(currentIdx)
                                )

                                when (readerType) {
                                    PAGED -> pagedReaderState.jumpToPage(loc.page)
                                    CONTINUOUS -> coroutineScope.launch {
                                        continuousReaderState.scrollToBookPage(loc.page + 1)
                                    }

                                    PANELS -> panelsReaderState?.jumpToPage(loc.page)
                                }
                            }
                            commonReaderState.editingComicAnnotation.value = annotation
                            commonReaderState.showAnnotationDialog.value = true
                        },
                        onDeleteAnnotation = { commonReaderState.deleteComicAnnotation(it) },
                        onDismiss = { showComicContentDialog = false },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }

                // Comic annotation create/edit dialog
                val showAnnotationDialogComic by commonReaderState.showAnnotationDialog.collectAsState()
                val editingComicAnnotation by commonReaderState.editingComicAnnotation.collectAsState()
                val lastColorComic by commonReaderState.lastHighlightColor.collectAsState()
                val pendingAnnotationNote by commonReaderState.pendingAnnotationNote.collectAsState()
                val annotationsComic by commonReaderState.annotations.collectAsState()
                val sortedAnnotationsComic = remember(annotationsComic) {
                    annotationsComic.sortedWith(
                        compareBy(
                            { (it.location as? snd.komelia.annotations.AnnotationLocation.ComicLocation)?.page ?: 0 },
                            { (it.location as? snd.komelia.annotations.AnnotationLocation.ComicLocation)?.y ?: 0f },
                            { (it.location as? snd.komelia.annotations.AnnotationLocation.ComicLocation)?.x ?: 0f },
                        )
                    )
                }
                val currentIndexComic = sortedAnnotationsComic.indexOf(editingComicAnnotation)

                if (showAnnotationDialogComic) {
                    val isEditing = editingComicAnnotation != null
                    val loc = editingComicAnnotation?.location as? snd.komelia.annotations.AnnotationLocation.ComicLocation
                    val pendingPage = commonReaderState.pendingAnnotationPage.value
                    val pendingX = commonReaderState.pendingAnnotationX.value
                    val pendingY = commonReaderState.pendingAnnotationY.value
                    val referenceText = if (isEditing && loc != null) {
                        "Page ${loc.page + 1} · (${(loc.x * 100).toInt()}%, ${(loc.y * 100).toInt()}%)"
                    } else {
                        "Page ${pendingPage + 1} · (${(pendingX * 100).toInt()}%, ${(pendingY * 100).toInt()}%)"
                    }
                    snd.komelia.ui.reader.common.AnnotationDialog(
                        referenceText = referenceText,
                        existingAnnotation = editingComicAnnotation,
                        initialColor = lastColorComic,
                        initialNote = pendingAnnotationNote,
                        onSave = { note, color ->
                            if (isEditing) {
                                editingComicAnnotation?.let { commonReaderState.updateComicAnnotation(it, note, color) }
                            } else {
                                commonReaderState.saveComicAnnotation(pendingPage, pendingX, pendingY, color, note)
                            }
                            commonReaderState.showAnnotationDialog.value = false
                            commonReaderState.editingComicAnnotation.value = null
                            commonReaderState.pendingAnnotationNote.value = null
                        },
                        onDelete = {
                            editingComicAnnotation?.let { commonReaderState.deleteComicAnnotation(it) }
                            commonReaderState.showAnnotationDialog.value = false
                            commonReaderState.editingComicAnnotation.value = null
                            commonReaderState.pendingAnnotationNote.value = null
                        },
                        onDismiss = {
                            commonReaderState.showAnnotationDialog.value = false
                            commonReaderState.editingComicAnnotation.value = null
                            commonReaderState.pendingAnnotationNote.value = null
                        },
                        onPrevious = {
                            if (currentIndexComic > 0) {
                                val prev = sortedAnnotationsComic[currentIndexComic - 1]
                                commonReaderState.editingComicAnnotation.value = prev
                                (prev.location as? snd.komelia.annotations.AnnotationLocation.ComicLocation)?.let { loc ->
                                    val currentIdx = when (readerType) {
                                        PAGED -> pagedReaderState.currentSpreadIndex.value
                                        CONTINUOUS -> commonReaderState.readProgressPage.value - 1
                                        PANELS -> panelsReaderState?.currentPageIndex?.value?.page ?: 0
                                    }
                                    commonReaderState.navigationHistory.addEntry(
                                        NavigationSource.NOTES,
                                        ImagePageLocation(currentIdx)
                                    )

                                    commonReaderState.onProgressChange(loc.page + 1)
                                    coroutineScope.launch {
                                        when (readerType) {
                                            PAGED -> {
                                                pagedReaderState.pageSpreads.value.indexOfFirst { spread ->
                                                    spread.any { it.pageNumber == loc.page + 1 }
                                                }.takeIf { it != -1 }?.let { pagedReaderState.jumpToPage(it) }
                                            }

                                            CONTINUOUS -> continuousReaderState.scrollToBookPage(loc.page + 1)
                                            PANELS -> panelsReaderState?.jumpToPage(loc.page)
                                        }
                                    }
                                }
                            }
                        },
                        onNext = {
                            if (currentIndexComic < sortedAnnotationsComic.size - 1) {
                                val next = sortedAnnotationsComic[currentIndexComic + 1]
                                commonReaderState.editingComicAnnotation.value = next
                                (next.location as? snd.komelia.annotations.AnnotationLocation.ComicLocation)?.let { loc ->
                                    val currentIdx = when (readerType) {
                                        PAGED -> pagedReaderState.currentSpreadIndex.value
                                        CONTINUOUS -> commonReaderState.readProgressPage.value - 1
                                        PANELS -> panelsReaderState?.currentPageIndex?.value?.page ?: 0
                                    }
                                    commonReaderState.navigationHistory.addEntry(
                                        NavigationSource.NOTES,
                                        ImagePageLocation(currentIdx)
                                    )

                                    commonReaderState.onProgressChange(loc.page + 1)
                                    coroutineScope.launch {
                                        when (readerType) {
                                            PAGED -> {
                                                pagedReaderState.pageSpreads.value.indexOfFirst { spread ->
                                                    spread.any { it.pageNumber == loc.page + 1 }
                                                }.takeIf { it != -1 }?.let { pagedReaderState.jumpToPage(it) }
                                            }

                                            CONTINUOUS -> continuousReaderState.scrollToBookPage(loc.page + 1)
                                            PANELS -> panelsReaderState?.jumpToPage(loc.page)
                                        }
                                    }
                                }
                            }
                        },
                        onShowList = {
                            showComicContentDialog = true
                        },
                        hasPrevious = currentIndexComic > 0,
                        hasNext = currentIndexComic != -1 && currentIndexComic < sortedAnnotationsComic.size - 1,
                    )
                }
            }
        }
    }
    LaunchedEffect(hasFocus) {
        if (!hasFocus) topLevelFocus.requestFocus()
    }
}

@Composable
fun ReaderControlsOverlay(
    readingDirection: LayoutDirection,
    onNexPageClick: suspend () -> Unit,
    onPrevPageClick: suspend () -> Unit,
    isSettingsMenuOpen: Boolean,
    onSettingsMenuToggle: () -> Unit,
    tapNavigationMode: ReaderTapNavigationMode,
    contentAreaSize: IntSize,
    scaleState: ScreenScaleState,
    tapToZoom: Boolean,
    modifier: Modifier,
    onLongPress: ((Offset) -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()

    val nextAction = { coroutineScope.launch { onNexPageClick() } }
    val prevAction = { coroutineScope.launch { onPrevPageClick() } }

    val areaCenter = remember(contentAreaSize) { Offset(contentAreaSize.width / 2f, contentAreaSize.height / 2f) }
    Box(
        modifier = modifier
            .fillMaxSize()
            .focusable()
            .pointerInput(
                contentAreaSize,
                readingDirection,
                onSettingsMenuToggle,
                isSettingsMenuOpen,
                tapNavigationMode,
                tapToZoom,
                onLongPress
            ) {
                detectTapGestures(
                    onLongPress = onLongPress,
                    onTap = { offset ->
                        val width = contentAreaSize.width.toFloat()
                        val height = contentAreaSize.height.toFloat()
                        val actionWidth = width / 3

                        if (isSettingsMenuOpen) {
                            onSettingsMenuToggle()
                            return@detectTapGestures
                        }

                        if (offset.x in actionWidth..actionWidth * 2) {
                            onSettingsMenuToggle()
                            return@detectTapGestures
                        }

                        val isLeft = offset.x < actionWidth
                        when (tapNavigationMode) {
                            ReaderTapNavigationMode.LEFT_RIGHT -> {
                                if (readingDirection == LayoutDirection.Ltr) {
                                    if (isLeft) prevAction() else nextAction()
                                } else {
                                    if (isLeft) nextAction() else prevAction()
                                }
                            }

                            ReaderTapNavigationMode.RIGHT_LEFT -> {
                                if (readingDirection == LayoutDirection.Ltr) {
                                    if (isLeft) nextAction() else prevAction()
                                } else {
                                    if (isLeft) prevAction() else nextAction()
                                }
                            }

                            ReaderTapNavigationMode.HORIZONTAL_SPLIT -> {
                                if (offset.y < height / 2) prevAction() else nextAction()
                            }

                            ReaderTapNavigationMode.REVERSED_HORIZONTAL_SPLIT -> {
                                if (offset.y < height / 2) nextAction() else prevAction()
                            }
                        }
                    },
                    onDoubleTap = if (tapToZoom) { offset ->
                        scaleState.toggleZoom(offset - areaCenter)
                    } else null
                )
            },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
