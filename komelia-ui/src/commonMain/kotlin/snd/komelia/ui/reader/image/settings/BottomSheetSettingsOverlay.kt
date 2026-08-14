package snd.komelia.ui.reader.image.settings

import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.ViewCarousel
import androidx.compose.material.icons.rounded.ViewStream
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import snd.komelia.image.ReduceKernel
import snd.komelia.image.UpscaleStatus
import snd.komelia.image.UpsamplingMode
import snd.komelia.komga.api.model.KomeliaBook
import snd.komelia.settings.model.ContinuousReadingDirection
import snd.komelia.settings.model.LayoutScaleType
import snd.komelia.settings.model.PagedReadingDirection
import snd.komelia.settings.model.ReaderTapNavigationMode
import snd.komelia.settings.model.PageDisplayLayout
import snd.komelia.settings.model.OcrSettings
import snd.komelia.settings.model.PanelsFullPageDisplayMode

import snd.komelia.settings.model.ReaderFlashColor
import snd.komelia.settings.model.ReaderType
import snd.komelia.settings.model.ReaderType.CONTINUOUS
import snd.komelia.settings.model.ReaderType.PAGED
import snd.komelia.settings.model.ReaderType.PANELS
import snd.komelia.ui.LocalAccentColor
import snd.komelia.ui.LocalPlatform
import snd.komelia.ui.platform.PlatformType.MOBILE
import snd.komelia.ui.LocalStrings
import snd.komelia.ui.LocalUseNewLibraryUI2
import snd.komelia.ui.LocalWindowWidth
import snd.komelia.ui.common.components.AppSliderDefaults
import snd.komelia.ui.common.components.SwitchWithLabel
import snd.komelia.ui.common.components.accentInputChipColors
import snd.komelia.ui.platform.WindowSizeClass.COMPACT
import snd.komelia.ui.platform.cursorForHand
import snd.komelia.ui.reader.ReaderExitDestination
import snd.komelia.ui.reader.ReaderNavigationIntent
import snd.komelia.ui.reader.common.ImagePageLocation
import snd.komelia.ui.reader.common.NavigationSource
import snd.komelia.ui.reader.ReaderControlsCard
import snd.komelia.ui.reader.image.PageMetadata
import snd.komelia.ui.reader.image.ReaderState
import snd.komelia.ui.reader.image.common.ProgressSlider
import snd.komelia.ui.reader.image.common.ThumbnailCarousel
import snd.komelia.ui.reader.image.continuous.ContinuousReaderState
import snd.komelia.ui.reader.image.paged.PagedReaderState
import snd.komelia.ui.reader.image.panels.PanelsReaderState
import snd.komelia.ui.settings.imagereader.ncnn.NcnnSettingsState
import snd.komelia.ui.settings.imagereader.ncnn.isNcnnSupported
import kotlin.math.roundToInt
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryBooks
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomSheetSettingsOverlay(
    /**
     * Drives the v1.0.11 minimal-UI-while-reading layout. When false, the
     * rows below the progress slider (mode toggles, OCR/upscale buttons,
     * etc.) animate out and only the [prev book][slider][next book]
     * strip remains. Defaults to true so non-minimal call sites keep the
     * legacy full-controls behavior unchanged.
     */
    expanded: Boolean = true,
    book: KomeliaBook?,
    readerType: ReaderType,
    onReaderTypeChange: (ReaderType) -> Unit,
    isColorCorrectionsActive: Boolean,
    onColorCorrectionClick: () -> Unit,
    availableUpsamplingModes: List<UpsamplingMode>,
    upsamplingMode: UpsamplingMode,
    onUpsamplingModeChange: (UpsamplingMode) -> Unit,
    availableDownsamplingKernels: List<ReduceKernel>,
    downsamplingKernel: ReduceKernel,
    onDownsamplingKernelChange: (ReduceKernel) -> Unit,
    linearLightDownsampling: Boolean,
    onLinearLightDownsamplingChange: (Boolean) -> Unit,
    stretchToFit: Boolean,
    onStretchToFitChange: (Boolean) -> Unit,
    cropBorders: Boolean,
    onCropBordersChange: (Boolean) -> Unit,
    invertSpeechBubbles: Boolean,
    onInvertSpeechBubblesChange: (Boolean) -> Unit,
    webtoonSmartScroll: Boolean,
    onWebtoonSmartScrollChange: (Boolean) -> Unit,
    loadThumbnailPreviews: Boolean,
    onLoadThumbnailPreviewsChange: (Boolean) -> Unit,
    zoom: Float,

    flashEnabled: Boolean,
    onFlashEnabledChange: (Boolean) -> Unit,
    flashEveryNPages: Int,
    onFlashEveryNPagesChange: (Int) -> Unit,
    flashWith: ReaderFlashColor,
    onFlashWithChange: (ReaderFlashColor) -> Unit,
    flashDuration: Long,
    onFlashDurationChange: (Long) -> Unit,

    tapNavigationMode: ReaderTapNavigationMode,
    onTapNavigationModeChange: (ReaderTapNavigationMode) -> Unit,

    pagedReaderState: PagedReaderState,
    continuousReaderState: ContinuousReaderState,
    panelsReaderState: PanelsReaderState?,
    commonReaderState: ReaderState,
    ncnnSettingsState: NcnnSettingsState,
    onBackPress: () -> Unit,
    onNotesClick: () -> Unit = {},
) {

    val windowWidth = LocalWindowWidth.current
    val accentColor = LocalAccentColor.current
    val useNewUI2 = LocalUseNewLibraryUI2.current
    val coroutineScope = rememberCoroutineScope()
    var showSettingsDialog by remember { mutableStateOf(false) }
    val allUpscaleActivities by ncnnSettingsState.globalUpscaleActivities.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        if (!useNewUI2) {
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .fillMaxWidth()
                    .windowInsetsPadding(
                        WindowInsets.statusBars
                            .add(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal))
                    )
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onBackPress,
                        modifier = Modifier.size(46.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }

                    book?.let {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(
                                Modifier.weight(1f)
                                    .padding(horizontal = 10.dp)
                            ) {
                                val titleStyle =
                                    if (windowWidth == COMPACT) MaterialTheme.typography.titleMedium
                                    else MaterialTheme.typography.titleLarge

                                Text(
                                    it.seriesTitle,
                                    maxLines = 1,
                                    style = titleStyle,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    it.metadata.title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                                )
                            }
                        }
                    }
                }
                AnimatedVisibility(visible = allUpscaleActivities.isNotEmpty()) {
                    UpscaleActivityIndicator(allUpscaleActivities)
                }
            }

            FloatingActionButton(
                onClick = { showSettingsDialog = true },
                containerColor = accentColor ?: MaterialTheme.colorScheme.primaryContainer,
                contentColor = if (accentColor != null) {
                    if (accentColor.luminance() > 0.5f) Color.Black else Color.White
                } else MaterialTheme.colorScheme.onPrimaryContainer,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = 80.dp, end = 16.dp)
            ) {
                Icon(Icons.Rounded.Tune, null)
            }

            ReaderFloatingToolbar(
                readerType = readerType,
                onReaderTypeChange = onReaderTypeChange,
                panelsReaderState = panelsReaderState,
                ncnnSettingsState = ncnnSettingsState,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = 80.dp, end = 80.dp),
            )
        } else {
            val bookState by commonReaderState.booksState.collectAsState()
            val pages = bookState?.currentBookPages ?: emptyList()
            val currentPageIndex = when (readerType) {
                // For paged: use the real Komga page number of the first page in
                // the current spread, not the spread index. When auto-skip-blank
                // is on, blank pages are filtered out of the spread map and the
                // spread index drifts away from the real page index — the
                // bottom-bar label and slider thumb would otherwise show the
                // wrong "Page X of Y".
                PAGED -> {
                    val spread = pagedReaderState.currentSpread.collectAsState().value
                    (spread.pages.firstOrNull()?.metadata?.pageNumber ?: 1) - 1
                }
                CONTINUOUS -> continuousReaderState.currentBookPageIndex.collectAsState(0).value
                PANELS -> panelsReaderState?.currentPageIndex?.collectAsState()?.value?.page ?: 0
            }
            val showCarousel by commonReaderState.showCarousel.collectAsState()

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ImageReaderControlsCardNewUI(
                    expanded = expanded,
                    pages = pages,
                    currentPageIndex = currentPageIndex,
                    onSliderPageChange = {
                        commonReaderState.navigationHistory.addEntry(
                            NavigationSource.SLIDER,
                            ImagePageLocation(currentPageIndex)
                        )
                        when (readerType) {
                            ReaderType.PAGED -> pagedReaderState.jumpToPage(it)
                            ReaderType.CONTINUOUS -> coroutineScope.launch { continuousReaderState.scrollToBookPage(it + 1) }
                            ReaderType.PANELS -> panelsReaderState?.jumpToPage(it)
                        }
                    },
                    onCarouselPageChange = {
                        commonReaderState.navigationHistory.addEntry(
                            NavigationSource.CAROUSEL,
                            ImagePageLocation(currentPageIndex)
                        )
                        when (readerType) {
                            ReaderType.PAGED -> pagedReaderState.jumpToPage(it)
                            ReaderType.CONTINUOUS -> coroutineScope.launch { continuousReaderState.scrollToBookPage(it + 1) }
                            ReaderType.PANELS -> panelsReaderState?.jumpToPage(it)
                        }
                    },
                    loadThumbnailPreviews = loadThumbnailPreviews,
                    readerType = readerType,
                    onReaderTypeChange = onReaderTypeChange,
                    panelsReaderState = panelsReaderState,
                    ncnnSettingsState = ncnnSettingsState,
                    ocrSettings = commonReaderState.ocrSettings.collectAsState().value,
                    onOcrSettingsChange = commonReaderState::onOcrSettingsChange,
                    isOcrLoading = commonReaderState.isOcrLoading.collectAsState().value,
                    translationSettings = commonReaderState.translationSettings.collectAsState().value,
                    onTranslationSettingsChange = commonReaderState::onTranslationSettingsChange,
                    isTranslating = commonReaderState.isTranslating.collectAsState().value,
                    onSettingsClick = { showSettingsDialog = true },
                    onNotesClick = onNotesClick,
                    onScanTextClick = {
                        val currentImage = when (readerType) {
                            PAGED -> pagedReaderState.currentSpread.value.pages.firstOrNull()?.imageResult?.image
                            CONTINUOUS -> continuousReaderState.currentPageImage.value
                            PANELS -> panelsReaderState?.currentPage?.value?.imageResult?.image
                        }
                        currentImage?.let { commonReaderState.scanCurrentPageForText(it) }
                    },
                    showCarousel = showCarousel,
                    onToggleCarousel = commonReaderState::onToggleCarousel,
                    onPreviousBook = { coroutineScope.launch { commonReaderState.loadPreviousBook(fromStart = true) } },
                    onNextBook = { coroutineScope.launch { commonReaderState.loadNextBook() } },
                    hasPreviousBook = commonReaderState.booksState.collectAsState().value?.previousBook != null,
                    hasNextBook = commonReaderState.booksState.collectAsState().value?.nextBook != null,
                    onReturnBook = {
                        // Same reason as the two below: the destination re-reads
                        // Komga as soon as we pop, so the progress — and the
                        // "completed" mark when leaving on a last page — has to
                        // land before the navigation, not after.
                        coroutineScope.launch {
                            commonReaderState.flushProgressNow()
                            onBackPress()
                        }
                    },
                    onReturnSeries = {
                        book?.let {
                            // Push current progress synchronously before
                            // navigating: the navigation intent makes the
                            // SeriesScreen fetch its data immediately and
                            // would race the async dispose flush, leaving
                            // the user staring at stale progress.
                            coroutineScope.launch {
                                commonReaderState.flushProgressNow()
                                ReaderNavigationIntent.pending.value =
                                    ReaderExitDestination.Series(it.seriesId)
                                onBackPress()
                            }
                        }
                    },
                    onReturnLibrary = {
                        book?.let {
                            coroutineScope.launch {
                                commonReaderState.flushProgressNow()
                                ReaderNavigationIntent.pending.value =
                                    ReaderExitDestination.Library(it.libraryId)
                                onBackPress()
                            }
                        }
                    },
                    // No book needed, unlike the other three: home has no id to
                    // read off it. The progress flush still comes first — Home's
                    // Keep-reading shelf refreshes the moment we land on it.
                    onReturnHome = {
                        coroutineScope.launch {
                            commonReaderState.flushProgressNow()
                            ReaderNavigationIntent.pending.value = ReaderExitDestination.Home
                            onBackPress()
                        }
                    },
                )
            }
        }
    }

    BoxWithConstraints {

        val maxHeight = this.maxHeight
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val theme = snd.komelia.ui.LocalTheme.current
        val surfaceColor = if (theme.type == snd.komelia.ui.Theme.ThemeType.DARK) Color(43, 43, 43)
        else MaterialTheme.colorScheme.surface

        val ocrSettings by commonReaderState.ocrSettings.collectAsState()
        if (showSettingsDialog) {
            ModalBottomSheet(
                onDismissRequest = { showSettingsDialog = false },
                sheetState = sheetState,
                containerColor = surfaceColor,
            ) {
                val pagerState = rememberPagerState(initialPage = 0, pageCount = { 4 })
                SecondaryScrollableTabRow(
                    selectedTabIndex = pagerState.currentPage,
                    containerColor = Color.Transparent,
                    edgePadding = 16.dp,
                ) {
                    Tab(
                        selected = pagerState.currentPage == 0,
                        onClick = { coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                        modifier = Modifier.heightIn(min = 40.dp).cursorForHand(),
                    ) {
                        Text(LocalStrings.current.ui.display, modifier = Modifier.padding(horizontal = 12.dp))
                    }
                    Tab(
                        selected = pagerState.currentPage == 1,
                        onClick = { coroutineScope.launch { pagerState.animateScrollToPage(1) } },
                        modifier = Modifier.heightIn(min = 40.dp).cursorForHand(),
                    ) {
                        Text(LocalStrings.current.ui.navigation, modifier = Modifier.padding(horizontal = 12.dp))
                    }
                    Tab(
                        selected = pagerState.currentPage == 2,
                        onClick = { coroutineScope.launch { pagerState.animateScrollToPage(2) } },
                        modifier = Modifier.heightIn(min = 40.dp).cursorForHand(),
                    ) {
                        Text(LocalStrings.current.ui.image, modifier = Modifier.padding(horizontal = 12.dp))
                    }
                    Tab(
                        selected = pagerState.currentPage == 3,
                        onClick = { coroutineScope.launch { pagerState.animateScrollToPage(3) } },
                        modifier = Modifier.heightIn(min = 40.dp).cursorForHand(),
                    ) {
                        Text(LocalStrings.current.ui.text, modifier = Modifier.padding(horizontal = 12.dp))
                    }
                }
                val focusManager = LocalFocusManager.current
                val width = LocalWindowWidth.current
                val contentPadding = remember(width) {
                    when (width) {
                        COMPACT -> 10.dp
                        else -> 20.dp
                    }
                }
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .height(maxHeight * (2f / 3f))
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                ) { page ->
                    Column(
                        Modifier
                            .padding(contentPadding)
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .pointerInput(Unit) { detectTapGestures(onTap = { focusManager.clearFocus() }) }
                    ) {

                        when (page) {
                            0 -> {
                                BottomSheetReadingModeSettings(
                                    readerType = readerType,
                                    onReaderTypeChange = onReaderTypeChange,
                                    pagedReaderState = pagedReaderState,
                                    continuousReaderState = continuousReaderState,
                                    panelsReaderState = panelsReaderState,
                                    keepProgressBarVisible = commonReaderState
                                        .keepProgressBarVisibleWhileReading.collectAsState().value,
                                    onKeepProgressBarVisibleChange =
                                        commonReaderState::onKeepProgressBarVisibleWhileReadingChange,
                                )
                            }

                            1 -> NavigationSettings(
                                currentMode = tapNavigationMode,
                                onModeChange = onTapNavigationModeChange
                            )

                            2 -> BottomSheetImageSettings(
                                readerType = readerType,
                                pagedReaderState = pagedReaderState,
                                continuousReaderState = continuousReaderState,
                                panelsReaderState = panelsReaderState,
                                availableUpsamplingModes = availableUpsamplingModes,
                                upsamplingMode = upsamplingMode,
                                onUpsamplingModeChange = onUpsamplingModeChange,
                                availableDownsamplingKernels = availableDownsamplingKernels,
                                downsamplingKernel = downsamplingKernel,
                                onDownsamplingKernelChange = onDownsamplingKernelChange,
                                linearLightDownsampling = linearLightDownsampling,
                                onLinearLightDownsamplingChange = onLinearLightDownsamplingChange,
                                stretchToFit = stretchToFit,
                                onStretchToFitChange = onStretchToFitChange,
                                cropBorders = cropBorders,
                                onCropBordersChange = onCropBordersChange,
                                invertSpeechBubbles = invertSpeechBubbles,
                                onInvertSpeechBubblesChange = onInvertSpeechBubblesChange,
                                webtoonSmartScroll = webtoonSmartScroll,
                                onWebtoonSmartScrollChange = onWebtoonSmartScrollChange,
                                loadThumbnailPreviews = loadThumbnailPreviews,
                                onLoadThumbnailPreviewsChange = onLoadThumbnailPreviewsChange,
                                isColorCorrectionsActive = isColorCorrectionsActive,
                                onColorCorrectionClick = onColorCorrectionClick,
                                zoom = zoom,
                                flashEnabled = flashEnabled,
                                onFlashEnabledChange = onFlashEnabledChange,
                                flashEveryNPages = flashEveryNPages,
                                onFlashEveryNPagesChange = onFlashEveryNPagesChange,
                                flashWith = flashWith,
                                onFlashWithChange = onFlashWithChange,
                                flashDuration = flashDuration,
                                onFlashDurationChange = onFlashDurationChange,
                                ncnnSettingsState = ncnnSettingsState,
                            )

                            3 -> OcrModeSettings(
                                ocrSettings = ocrSettings,
                                onOcrSettingsChange = commonReaderState::onOcrSettingsChange,
                                translationSettings = commonReaderState.translationSettings
                                    .collectAsState().value,
                                onTranslationSettingsChange = commonReaderState::onTranslationSettingsChange,
                                commonReaderState = commonReaderState,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomSheetReadingModeSettings(
    readerType: ReaderType,
    onReaderTypeChange: (ReaderType) -> Unit,
    pagedReaderState: PagedReaderState,
    continuousReaderState: ContinuousReaderState,
    panelsReaderState: PanelsReaderState?,
    keepProgressBarVisible: Boolean,
    onKeepProgressBarVisibleChange: (Boolean) -> Unit,
) {
    Column {
        Text(LocalStrings.current.ui.readingMode)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            InputChip(
                selected = readerType == PAGED,
                onClick = { onReaderTypeChange(PAGED) },
                colors = accentInputChipColors(),
                label = { Text(LocalStrings.current.ui.paged) }
            )
            InputChip(
                selected = readerType == CONTINUOUS,
                onClick = { onReaderTypeChange(CONTINUOUS) },
                colors = accentInputChipColors(),
                label = { Text(LocalStrings.current.ui.continuous) }
            )
            if (panelsReaderState != null)
                InputChip(
                    selected = readerType == PANELS,
                    onClick = { onReaderTypeChange(PANELS) },
                    colors = accentInputChipColors(),
                    label = { Text(LocalStrings.current.ui.panels) }
                )
        }

        when (readerType) {
            PAGED -> PagedModeSettings(pageState = pagedReaderState)
            PANELS -> if (panelsReaderState != null) PanelsModeSettings(state = panelsReaderState)
            CONTINUOUS -> ContinuousModeSettings(state = continuousReaderState)
        }

        Spacer(Modifier.height(12.dp))
        snd.komelia.ui.common.components.SwitchWithLabel(
            checked = keepProgressBarVisible,
            onCheckedChange = onKeepProgressBarVisibleChange,
            label = { Text(LocalStrings.current.ui.alwaysShowProgressBarWhile) },
            supportingText = {
                Text(
                    "Hide the rest of the controls but keep the slim bottom strip. " +
                        "Tap the page to reveal the full UI."
                )
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PagedModeSettings(
    pageState: PagedReaderState,
) {
    val strings = LocalStrings.current.pagedReader
    val scaleType = pageState.scaleType.collectAsState().value
    val tapToZoom = pageState.tapToZoom.collectAsState().value
    val adaptiveBackground = pageState.adaptiveBackground.collectAsState().value
    val splitDoublePages = pageState.splitDoublePages.collectAsState().value
    val autoSkipBlankPages = pageState.autoSkipBlankPages.collectAsState().value
    val autoDetectWebtoon = pageState.autoDetectWebtoon.collectAsState().value
    val autoDirection = pageState.autoDirection.collectAsState().value
    Column {

        Text(strings.scaleType)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            InputChip(
                selected = scaleType == LayoutScaleType.SCREEN,
                onClick = { pageState.onScaleTypeChange(LayoutScaleType.SCREEN) },
                colors = accentInputChipColors(),
                label = { Text(strings.forScaleType(LayoutScaleType.SCREEN)) }
            )
            InputChip(
                selected = scaleType == LayoutScaleType.FIT_WIDTH,
                onClick = { pageState.onScaleTypeChange(LayoutScaleType.FIT_WIDTH) },
                colors = accentInputChipColors(),
                label = { Text(strings.forScaleType(LayoutScaleType.FIT_WIDTH)) }
            )
            InputChip(
                selected = scaleType == LayoutScaleType.FIT_HEIGHT,
                onClick = { pageState.onScaleTypeChange(LayoutScaleType.FIT_HEIGHT) },
                colors = accentInputChipColors(),
                label = { Text(strings.forScaleType(LayoutScaleType.FIT_HEIGHT)) }
            )
            InputChip(
                selected = scaleType == LayoutScaleType.ORIGINAL,
                onClick = { pageState.onScaleTypeChange(LayoutScaleType.ORIGINAL) },
                colors = accentInputChipColors(),
                label = { Text(strings.forScaleType(LayoutScaleType.ORIGINAL)) }
            )
        }

        val readingDirection = pageState.readingDirection.collectAsState().value
        Text(strings.readingDirection)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            InputChip(
                selected = readingDirection == PagedReadingDirection.RIGHT_TO_LEFT,
                onClick = { pageState.onReadingDirectionChange(PagedReadingDirection.RIGHT_TO_LEFT) },
                colors = accentInputChipColors(),
                label = { Text(strings.forReadingDirection(PagedReadingDirection.RIGHT_TO_LEFT)) }
            )
            InputChip(
                selected = readingDirection == PagedReadingDirection.LEFT_TO_RIGHT,
                onClick = { pageState.onReadingDirectionChange(PagedReadingDirection.LEFT_TO_RIGHT) },
                colors = accentInputChipColors(),
                label = { Text(strings.forReadingDirection(PagedReadingDirection.LEFT_TO_RIGHT)) }
            )
        }
        SwitchWithLabel(
            checked = autoDirection,
            onCheckedChange = pageState::onAutoDirectionChange,
            label = { Text(LocalStrings.current.ui.autoDetectDirectionUseSeries) },
            contentPadding = PaddingValues(horizontal = 10.dp),
        )

        val layout = pageState.layout.collectAsState().value
        Text(strings.layout)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            InputChip(
                selected = layout == PageDisplayLayout.SINGLE_PAGE,
                onClick = { pageState.onLayoutChange(PageDisplayLayout.SINGLE_PAGE) },
                colors = accentInputChipColors(),
                label = { Text(strings.forLayout(PageDisplayLayout.SINGLE_PAGE)) }
            )
            InputChip(
                selected = layout == PageDisplayLayout.DOUBLE_PAGES,
                onClick = { pageState.onLayoutChange(PageDisplayLayout.DOUBLE_PAGES) },
                colors = accentInputChipColors(),
                label = { Text(strings.forLayout(PageDisplayLayout.DOUBLE_PAGES)) }
            )
            InputChip(
                selected = layout == PageDisplayLayout.DOUBLE_PAGES_NO_COVER,
                onClick = { pageState.onLayoutChange(PageDisplayLayout.DOUBLE_PAGES_NO_COVER) },
                colors = accentInputChipColors(),
                label = { Text(strings.forLayout(PageDisplayLayout.DOUBLE_PAGES_NO_COVER)) }
            )
        }
        AnimatedVisibility(layout == PageDisplayLayout.DOUBLE_PAGES || layout == PageDisplayLayout.DOUBLE_PAGES_NO_COVER) {
            HorizontalDivider()
            val layoutOffset = pageState.layoutOffset.collectAsState().value
            SwitchWithLabel(
                checked = layoutOffset,
                onCheckedChange = pageState::onLayoutOffsetChange,
                label = { Text(strings.offsetPages) },
                contentPadding = PaddingValues(horizontal = 10.dp),
            )
        }

        SwitchWithLabel(
            checked = tapToZoom,
            onCheckedChange = pageState::onTapToZoomChange,
            label = { Text(LocalStrings.current.ui.tapToZoom) },
            contentPadding = PaddingValues(horizontal = 10.dp),
        )

        SwitchWithLabel(
            checked = adaptiveBackground,
            onCheckedChange = pageState::onAdaptiveBackgroundChange,
            label = { Text(strings.adaptiveBackground) },
            contentPadding = PaddingValues(horizontal = 10.dp),
        )

        AnimatedVisibility(layout == PageDisplayLayout.SINGLE_PAGE) {
            SwitchWithLabel(
                checked = splitDoublePages,
                onCheckedChange = pageState::onSplitDoublePagesChange,
                label = { Text(LocalStrings.current.ui.splitLandscapePages) },
                contentPadding = PaddingValues(horizontal = 10.dp),
            )
        }

        SwitchWithLabel(
            checked = autoSkipBlankPages,
            onCheckedChange = pageState::onAutoSkipBlankPagesChange,
            label = { Text(LocalStrings.current.ui.autoSkipBlankPages) },
            contentPadding = PaddingValues(horizontal = 10.dp),
        )

        SwitchWithLabel(
            checked = autoDetectWebtoon,
            onCheckedChange = pageState::onAutoDetectWebtoonChange,
            label = { Text(LocalStrings.current.ui.autoDetectWebtoonSwitchTo) },
            contentPadding = PaddingValues(horizontal = 10.dp),
        )
    }

}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PanelsModeSettings(
    state: PanelsReaderState,
) {
    val strings = LocalStrings.current.pagedReader
    val tapToZoom = state.tapToZoom.collectAsState().value
    val adaptiveBackground = state.adaptiveBackground.collectAsState().value
    Column {

        val readingDirection = state.readingDirection.collectAsState().value
        Text(strings.readingDirection)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            InputChip(
                selected = readingDirection == PagedReadingDirection.RIGHT_TO_LEFT,
                onClick = { state.onReadingDirectionChange(PagedReadingDirection.RIGHT_TO_LEFT) },
                colors = accentInputChipColors(),
                label = { Text(strings.forReadingDirection(PagedReadingDirection.RIGHT_TO_LEFT)) }
            )
            InputChip(
                selected = readingDirection == PagedReadingDirection.LEFT_TO_RIGHT,
                onClick = { state.onReadingDirectionChange(PagedReadingDirection.LEFT_TO_RIGHT) },
                colors = accentInputChipColors(),
                label = { Text(strings.forReadingDirection(PagedReadingDirection.LEFT_TO_RIGHT)) }
            )
        }

        val displayMode = state.fullPageDisplayMode.collectAsState().value
        Text(LocalStrings.current.ui.showFullPage)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PanelsFullPageDisplayMode.entries.forEach { mode ->
                InputChip(
                    selected = displayMode == mode,
                    onClick = { state.onFullPageDisplayModeChange(mode) },
                    colors = accentInputChipColors(),
                    label = { Text(mode.name) }
                )
            }
        }

        SwitchWithLabel(
            checked = tapToZoom,
            onCheckedChange = state::onTapToZoomChange,
            label = { Text(LocalStrings.current.ui.tapToZoom) },
            contentPadding = PaddingValues(horizontal = 10.dp),
        )

        SwitchWithLabel(
            checked = adaptiveBackground,
            onCheckedChange = state::onAdaptiveBackgroundChange,
            label = { Text(strings.adaptiveBackground) },
            contentPadding = PaddingValues(horizontal = 10.dp),
        )
    }

}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ContinuousModeSettings(
    state: ContinuousReaderState,
) {
    val strings = LocalStrings.current.continuousReader
    val windowWidth = LocalWindowWidth.current
    val accentColor = LocalAccentColor.current
    Column {
        val readingDirection = state.readingDirection.collectAsState().value
        Text(strings.readingDirection)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            InputChip(
                selected = readingDirection == ContinuousReadingDirection.TOP_TO_BOTTOM,
                onClick = { state.onReadingDirectionChange(ContinuousReadingDirection.TOP_TO_BOTTOM) },
                colors = accentInputChipColors(),
                label = { Text(strings.forReadingDirection(ContinuousReadingDirection.TOP_TO_BOTTOM)) }
            )
            InputChip(
                selected = readingDirection == ContinuousReadingDirection.LEFT_TO_RIGHT,
                onClick = { state.onReadingDirectionChange(ContinuousReadingDirection.LEFT_TO_RIGHT) },
                colors = accentInputChipColors(),
                label = { Text(strings.forReadingDirection(ContinuousReadingDirection.LEFT_TO_RIGHT)) }
            )
            InputChip(
                selected = readingDirection == ContinuousReadingDirection.RIGHT_TO_LEFT,
                onClick = { state.onReadingDirectionChange(ContinuousReadingDirection.RIGHT_TO_LEFT) },
                colors = accentInputChipColors(),
                label = { Text(strings.forReadingDirection(ContinuousReadingDirection.RIGHT_TO_LEFT)) }
            )
        }

        // Continuous reading (webtoons included) had no way to turn double-tap
        // zoom off — the toggle existed for the paged reader only.
        SwitchWithLabel(
            checked = state.tapToZoom.collectAsState().value,
            onCheckedChange = state::onTapToZoomChange,
            label = { Text(LocalStrings.current.ui.tapToZoom) },
            contentPadding = PaddingValues(horizontal = 10.dp),
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            val sidePadding = state.sidePaddingFraction.collectAsState().value
            val paddingPercentage = remember(sidePadding) { (sidePadding * 200).roundToInt() }
            Column(Modifier.width(100.dp)) {
                Text(LocalStrings.current.ui.sidePadding, style = MaterialTheme.typography.labelLarge)
                Text("$paddingPercentage%", style = MaterialTheme.typography.labelMedium)
            }
            Slider(
                value = sidePadding,
                onValueChange = state::onSidePaddingChange,
                steps = 15,
                valueRange = 0f..0.4f,
                colors = AppSliderDefaults.colors(accentColor = accentColor)
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            val spacing = state.pageSpacing.collectAsState(Dispatchers.Main.immediate).value
            Column(Modifier.width(100.dp)) {
                Text(LocalStrings.current.ui.pageSpacing, style = MaterialTheme.typography.labelLarge)
                Text("$spacing", style = MaterialTheme.typography.labelMedium)
            }
            when (windowWidth) {
                COMPACT -> Slider(
                    value = spacing.toFloat(),
                    onValueChange = { state.onPageSpacingChange(it.roundToInt()) },
                    steps = 24,
                    valueRange = 0f..250f,
                    colors = AppSliderDefaults.colors(accentColor = accentColor)
                )

                else -> Slider(
                    value = spacing.toFloat(),
                    onValueChange = { state.onPageSpacingChange(it.roundToInt()) },
                    steps = 49,
                    valueRange = 0f..500f,
                    colors = AppSliderDefaults.colors(accentColor = accentColor)
                )
            }

        }
        Spacer(Modifier.heightIn(30.dp))
    }
}

@Composable
private fun BottomSheetImageSettings(
    readerType: ReaderType,
    pagedReaderState: PagedReaderState,
    continuousReaderState: ContinuousReaderState,
    panelsReaderState: PanelsReaderState?,
    availableUpsamplingModes: List<UpsamplingMode>,
    upsamplingMode: UpsamplingMode,
    onUpsamplingModeChange: (UpsamplingMode) -> Unit,

    availableDownsamplingKernels: List<ReduceKernel>,
    downsamplingKernel: ReduceKernel,
    onDownsamplingKernelChange: (ReduceKernel) -> Unit,
    linearLightDownsampling: Boolean,
    onLinearLightDownsamplingChange: (Boolean) -> Unit,
    stretchToFit: Boolean,
    onStretchToFitChange: (Boolean) -> Unit,
    cropBorders: Boolean,
    onCropBordersChange: (Boolean) -> Unit,
    invertSpeechBubbles: Boolean,
    onInvertSpeechBubblesChange: (Boolean) -> Unit,
    webtoonSmartScroll: Boolean,
    onWebtoonSmartScrollChange: (Boolean) -> Unit,
    loadThumbnailPreviews: Boolean,
    onLoadThumbnailPreviewsChange: (Boolean) -> Unit,
    isColorCorrectionsActive: Boolean,
    onColorCorrectionClick: () -> Unit,
    zoom: Float,

    flashEnabled: Boolean,
    onFlashEnabledChange: (Boolean) -> Unit,
    flashEveryNPages: Int,
    onFlashEveryNPagesChange: (Int) -> Unit,
    flashWith: ReaderFlashColor,
    onFlashWithChange: (ReaderFlashColor) -> Unit,
    flashDuration: Long,
    onFlashDurationChange: (Long) -> Unit,
    ncnnSettingsState: NcnnSettingsState,
) {
    Column {
        SamplingModeSettings(
            availableUpsamplingModes = availableUpsamplingModes,
            upsamplingMode = upsamplingMode,
            onUpsamplingModeChange = onUpsamplingModeChange,
            availableDownsamplingKernels = availableDownsamplingKernels,
            downsamplingKernel = downsamplingKernel,
            onDownsamplingKernelChange = onDownsamplingKernelChange,
            linearLightDownsampling = linearLightDownsampling,
            onLinearLightDownsamplingChange = onLinearLightDownsamplingChange,
        )
        CommonImageSettings(
            stretchToFit = stretchToFit,
            onStretchToFitChange = onStretchToFitChange,
            cropBorders = cropBorders,
            onCropBordersChange = onCropBordersChange,
            invertSpeechBubbles = invertSpeechBubbles,
            onInvertSpeechBubblesChange = onInvertSpeechBubblesChange,
            webtoonSmartScroll = webtoonSmartScroll,
            onWebtoonSmartScrollChange = onWebtoonSmartScrollChange,
            loadThumbnailPreviews = loadThumbnailPreviews,
            onLoadThumbnailPreviewsChange = onLoadThumbnailPreviewsChange,
            isColorCorrectionsActive = isColorCorrectionsActive,
            onColorCorrectionClick = onColorCorrectionClick,
            flashEnabled = flashEnabled,
            onFlashEnabledChange = onFlashEnabledChange,
            flashEveryNPages = flashEveryNPages,
            onFlashEveryNPagesChange = onFlashEveryNPagesChange,
            flashWith = flashWith,
            onFlashWithChange = onFlashWithChange,
            flashDuration = flashDuration,
            onFlashDurationChange = onFlashDurationChange,
        )

        if (snd.komelia.ui.settings.imagereader.ncnn.isNcnnSupported()) {
            HorizontalDivider(Modifier.padding(vertical = 10.dp))
            snd.komelia.ui.settings.imagereader.ncnn.NcnnSettingsContent(
                settings = ncnnSettingsState.ncnnUpscalerSettings.collectAsState().value,
                onSettingsChange = ncnnSettingsState::onSettingsChange,
                onDownloadRequest = ncnnSettingsState::onNcnnDownloadRequest
            )
        }

        HorizontalDivider(Modifier.padding(vertical = 5.dp))

        val strings = LocalStrings.current.reader
        val zoomPercentage = remember(zoom) { (zoom * 100).roundToInt() }
        Text("${strings.zoom}: $zoomPercentage%")
        when (readerType) {
            PAGED ->
                PagedReaderPagesInfo(
                    pages = pagedReaderState.currentSpread.collectAsState().value.pages,
                    modifier = Modifier.animateContentSize()
                )

            PANELS -> {
                if (panelsReaderState != null) {
                    val panelsPage = panelsReaderState.currentPage.collectAsState().value
                    val pages = remember(panelsPage) {
                        panelsPage?.let { listOf(PagedReaderState.Page(it.metadata, it.imageResult)) } ?: emptyList()
                    }
                    PagedReaderPagesInfo(
                        pages = pages,
                        modifier = Modifier.animateContentSize()
                    )
                }
            }

            CONTINUOUS -> ContinuousReaderPagesInfo(
                lazyListState = continuousReaderState.lazyListState,
                waitForImage = continuousReaderState::waitForImage,
                modifier = Modifier.animateContentSize()
            )
        }
    }

}

@Composable
internal fun UpscaleActivityIndicator(activities: Map<Int, UpscaleStatus>) {
    if (activities.isEmpty()) return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(end = 8.dp)
    ) {
        activities.entries.sortedBy { it.key }.forEach { (page, status) ->
            when (status) {
                UpscaleStatus.Upscaling -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 1.5.dp)
                    Spacer(Modifier.width(2.dp))
                    Text("p$page", style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.width(6.dp))
                }
                UpscaleStatus.Upscaled -> {
                    Text("p$page ✓", style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.width(6.dp))
                }
                UpscaleStatus.Idle -> {}
            }
        }
    }
}

@Composable
private fun ReaderFloatingToolbar(
    readerType: ReaderType,
    onReaderTypeChange: (ReaderType) -> Unit,
    panelsReaderState: PanelsReaderState?,
    ncnnSettingsState: NcnnSettingsState,
    modifier: Modifier = Modifier,
) {
    val ncnnSettings by ncnnSettingsState.ncnnUpscalerSettings.collectAsState()
    val showUpscale = isNcnnSupported()

    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 6.dp,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ReaderModeIconButton(
                selected = readerType == PAGED,
                onClick = { onReaderTypeChange(PAGED) },
                icon = Icons.AutoMirrored.Rounded.MenuBook,
                contentDescription = LocalStrings.current.ui.paged,
            )
            ReaderModeIconButton(
                selected = readerType == CONTINUOUS,
                onClick = { onReaderTypeChange(CONTINUOUS) },
                icon = Icons.Rounded.ViewStream,
                contentDescription = LocalStrings.current.ui.continuous,
            )
            if (panelsReaderState != null) {
                ReaderModeIconButton(
                    selected = readerType == PANELS,
                    onClick = { onReaderTypeChange(PANELS) },
                    icon = Icons.Rounded.GridView,
                    contentDescription = LocalStrings.current.ui.panels,
                )
            }

            if (showUpscale) {
                VerticalDivider(
                    modifier = Modifier
                        .height(24.dp)
                        .padding(horizontal = 4.dp)
                )
                ReaderModeIconButton(
                    selected = ncnnSettings.enabled,
                    onClick = { ncnnSettingsState.onSettingsChange(ncnnSettings.copy(enabled = !ncnnSettings.enabled)) },
                    icon = Icons.Rounded.AutoAwesome,
                    contentDescription = LocalStrings.current.ui.upscaling,
                )
            }
        }
    }
}

@Composable
internal fun ReaderModeIconButton(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String?,
) {
    val accentColor = LocalAccentColor.current
    val indicatorColor = accentColor ?: MaterialTheme.colorScheme.secondaryContainer
    val selectedIconTint = if (accentColor != null) {
        if (accentColor.luminance() > 0.5f) Color.Black else Color.White
    } else MaterialTheme.colorScheme.onSecondaryContainer

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (selected) indicatorColor else Color.Transparent)
    ) {
        IconButton(onClick = onClick) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (selected) selectedIconTint else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun ImageReaderControlsCardNewUI(
    /**
     * False when in v1.0.11 minimal-UI-while-reading mode — the rows
     * below the progress slider animate out and only the
     * [prev book][slider][next book] strip stays visible. Defaults true
     * so existing call sites keep the legacy full-controls behavior.
     */
    expanded: Boolean = true,
    pages: List<PageMetadata>,
    currentPageIndex: Int,
    onSliderPageChange: (Int) -> Unit,
    onCarouselPageChange: (Int) -> Unit,
    loadThumbnailPreviews: Boolean,
    readerType: ReaderType,
    onReaderTypeChange: (ReaderType) -> Unit,
    panelsReaderState: PanelsReaderState?,
    ncnnSettingsState: NcnnSettingsState,
    ocrSettings: OcrSettings,
    onOcrSettingsChange: (OcrSettings) -> Unit,
    isOcrLoading: Boolean,
    translationSettings: snd.komelia.settings.model.TranslationSettings,
    onTranslationSettingsChange: (snd.komelia.settings.model.TranslationSettings) -> Unit,
    isTranslating: Boolean,
    onSettingsClick: () -> Unit,
    onNotesClick: () -> Unit = {},
    onScanTextClick: () -> Unit = {},
    showCarousel: Boolean,
    onToggleCarousel: () -> Unit,
    onPreviousBook: () -> Unit = {},
    onNextBook: () -> Unit = {},
    hasPreviousBook: Boolean = false,
    hasNextBook: Boolean = false,
    onReturnBook: () -> Unit = {},
    onReturnSeries: () -> Unit = {},
    onReturnLibrary: () -> Unit = {},
    onReturnHome: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val accentColor = LocalAccentColor.current
    val ncnnSettings by ncnnSettingsState.ncnnUpscalerSettings.collectAsState()
    val showUpscale = isNcnnSupported()

    ReaderControlsCard(
        modifier = modifier,
        isFullWidth = showCarousel
    ) {
        AnimatedContent(
            targetState = showCarousel,
            transitionSpec = {
                (slideInVertically(initialOffsetY = { it }) + fadeIn())
                    .togetherWith(slideOutVertically(targetOffsetY = { it }) + fadeOut())
            },
            label = "CarouselTransition"
        ) { targetShowCarousel ->
            if (targetShowCarousel) {
                ThumbnailCarousel(
                    pages = pages,
                    currentPageIndex = currentPageIndex,
                    onPageChange = {
                        onCarouselPageChange(it)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                } else {
                Column {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Row(
                            modifier = Modifier.clickable { onToggleCarousel() },
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ViewCarousel,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                modifier = Modifier.size(20.dp).padding(end = 4.dp).offset(y = (-2).dp)
                            )
                            Text(
                                text = "Page ${currentPageIndex + 1} of ${pages.size}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.offset(y = (-2).dp)
                            )
                        }
                    }

                    val accentColorForButtons = LocalAccentColor.current ?: MaterialTheme.colorScheme.primary
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onPreviousBook, enabled = hasPreviousBook) {
                            Icon(
                                Icons.Rounded.SkipPrevious,
                                contentDescription = LocalStrings.current.ui.previousVolume,
                                tint = if (hasPreviousBook) accentColorForButtons else accentColorForButtons.copy(alpha = 0.3f)
                            )
                        }
                        ProgressSlider(
                            pages = pages,
                            currentPageIndex = currentPageIndex,
                            onPageNumberChange = onSliderPageChange,
                            loadThumbnailPreviews = loadThumbnailPreviews,
                            show = true,
                            layoutDirection = androidx.compose.ui.unit.LayoutDirection.Ltr, // TODO: handle RTL
                            isBare = true,
                            modifier = Modifier.weight(1f),
                            onLabelClick = onToggleCarousel
                        )
                        IconButton(onClick = onNextBook, enabled = hasNextBook) {
                            Icon(
                                Icons.Rounded.SkipNext,
                                contentDescription = LocalStrings.current.ui.nextVolume,
                                tint = if (hasNextBook) accentColorForButtons else accentColorForButtons.copy(alpha = 0.3f)
                            )
                        }
                    }

                    // v1.0.11 minimal-UI: hide everything below the progress
                    // slider when [expanded] is false (keep-progress-bar mode
                    // with the user not tapping). The hidden content slides
                    // out toward the bottom and fades; tapping the reader
                    // flips expanded back to true and the rows slide up.
                    AnimatedVisibility(
                        visible = expanded,
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                    ) {
                    Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        ReaderModeIconButton(
                            selected = readerType == PAGED,
                            onClick = { onReaderTypeChange(PAGED) },
                            icon = Icons.AutoMirrored.Rounded.MenuBook,
                            contentDescription = LocalStrings.current.ui.paged,
                        )
                        ReaderModeIconButton(
                            selected = readerType == CONTINUOUS,
                            onClick = { onReaderTypeChange(CONTINUOUS) },
                            icon = Icons.Rounded.ViewStream,
                            contentDescription = LocalStrings.current.ui.continuous,
                        )
                        if (panelsReaderState != null) {
                            ReaderModeIconButton(
                                selected = readerType == PANELS,
                                onClick = { onReaderTypeChange(PANELS) },
                                icon = Icons.Rounded.GridView,
                                contentDescription = LocalStrings.current.ui.panels,
                            )
                        }

                        VerticalDivider(
                            modifier = Modifier
                                .height(24.dp)
                        )

                        if (showUpscale) {
                            ReaderModeIconButton(
                                selected = ncnnSettings.enabled,
                                onClick = {
                                    ncnnSettingsState.onSettingsChange(
                                        ncnnSettings.copy(
                                            enabled = !ncnnSettings.enabled
                                        )
                                    )
                                },
                                icon = Icons.Rounded.AutoAwesome,
                                contentDescription = LocalStrings.current.ui.upscaling,
                            )
                        }

                        if (LocalPlatform.current == MOBILE) {
                            if (isOcrLoading) {
                                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(Modifier.size(24.dp))
                                }
                            } else {
                                ReaderModeIconButton(
                                    selected = ocrSettings.enabled,
                                    onClick = { onOcrSettingsChange(ocrSettings.copy(enabled = !ocrSettings.enabled)) },
                                    icon = Icons.Rounded.TextFields,
                                    contentDescription = LocalStrings.current.ui.scanText,
                                )
                            }

                            // Page translation. Stays on across page turns: this
                            // is a reading mode, not a one-shot action.
                            if (isTranslating) {
                                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(Modifier.size(24.dp))
                                }
                            } else {
                                ReaderModeIconButton(
                                    selected = translationSettings.enabled,
                                    onClick = {
                                        onTranslationSettingsChange(
                                            translationSettings.copy(enabled = !translationSettings.enabled)
                                        )
                                    },
                                    icon = Icons.Default.Translate,
                                    contentDescription = LocalStrings.current.ui.translate,
                                )
                            }
                        }

                        VerticalDivider(
                            modifier = Modifier
                                .height(24.dp)
                        )

                        ReaderModeIconButton(
                            selected = false,
                            onClick = onNotesClick,
                            icon = Icons.Rounded.EditNote,
                            contentDescription = LocalStrings.current.ui.notes,
                        )

                        IconButton(onClick = onSettingsClick) {
                            Icon(
                                Icons.Rounded.Tune,
                                contentDescription = LocalStrings.current.ui.settings,
                                tint = accentColor ?: MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        ReturnNavButton(
                            icon = Icons.Rounded.Book,
                            label = LocalStrings.current.ui.returnBook,
                            onClick = onReturnBook,
                        )
                        ReturnNavButton(
                            icon = Icons.Rounded.Collections,
                            label = LocalStrings.current.ui.returnSerie,
                            onClick = onReturnSeries,
                        )
                        ReturnNavButton(
                            icon = Icons.Rounded.LibraryBooks,
                            label = LocalStrings.current.ui.returnLibrary,
                            onClick = onReturnLibrary,
                        )
                        ReturnNavButton(
                            icon = Icons.Rounded.Home,
                            label = LocalStrings.current.ui.returnHome,
                            onClick = onReturnHome,
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
private fun ReturnNavButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 11.sp,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SamplingModeSettings(
    availableUpsamplingModes: List<UpsamplingMode>,
    upsamplingMode: UpsamplingMode,
    onUpsamplingModeChange: (UpsamplingMode) -> Unit,
    availableDownsamplingKernels: List<ReduceKernel>,
    downsamplingKernel: ReduceKernel,
    onDownsamplingKernelChange: (ReduceKernel) -> Unit,
    linearLightDownsampling: Boolean,
    onLinearLightDownsamplingChange: (Boolean) -> Unit,
) {
    val strings = LocalStrings.current.imageSettings

    if (availableUpsamplingModes.size > 1) {
        Column {
            Text(strings.upsamplingMode)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                availableUpsamplingModes.forEach { mode ->
                    InputChip(
                        selected = upsamplingMode == mode,
                        onClick = { onUpsamplingModeChange(mode) },
                        colors = accentInputChipColors(),
                        label = { Text(strings.forUpsamplingMode(mode)) }
                    )

                }
            }
        }
    }

    if (availableDownsamplingKernels.size > 1) {
        Column {
            Text(strings.downsamplingKernel)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                availableDownsamplingKernels.forEach { kernel ->
                    InputChip(
                        selected = downsamplingKernel == kernel,
                        onClick = { onDownsamplingKernelChange(kernel) },
                        colors = accentInputChipColors(),
                        label = { Text(strings.forDownsamplingKernel(kernel)) }
                    )

                }
            }
        }
    }


    SwitchWithLabel(
        checked = linearLightDownsampling,
        onCheckedChange = onLinearLightDownsamplingChange,
        label = { Text(LocalStrings.current.ui.linearLightDownsampling) },
        supportingText = {
            Text(LocalStrings.current.ui.slowerButPotentiallyMoreAccurate, style = MaterialTheme.typography.labelMedium)
        },
        contentPadding = PaddingValues(horizontal = 10.dp)
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OcrModeSettings(
    ocrSettings: OcrSettings,
    onOcrSettingsChange: (OcrSettings) -> Unit,
    translationSettings: snd.komelia.settings.model.TranslationSettings,
    onTranslationSettingsChange: (snd.komelia.settings.model.TranslationSettings) -> Unit,
    commonReaderState: ReaderState,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SwitchWithLabel(
            checked = ocrSettings.enabled,
            onCheckedChange = { onOcrSettingsChange(ocrSettings.copy(enabled = it)) },
            label = { Text(LocalStrings.current.ui.enableTextSelection) },
            supportingText = {
                Text(LocalStrings.current.ui.automaticallyScanPagesForText, style = MaterialTheme.typography.labelMedium)
            },
            contentPadding = PaddingValues(horizontal = 10.dp)
        )

        // No engine, model or detection-language picker any more. There is one
        // engine worth using (RapidOCR with PP-OCRv6 small, which reads pages
        // ML Kit could not and covers every script the old v4 models split
        // between them), and the detection language now follows the translation
        // source rather than being a separate thing to keep in step.

        // Which detector runs. Only the detector: recognition stays PP-OCRv6
        // small in both modes. Fast is worth offering because detection runs on
        // the whole page — 1.7-1.9 s of a 3.3-4.4 s scan, measured — but the
        // tiny detector gives up 5.2 points of recall on artistic text, which
        // on a comic page is exactly the sound effects and the lettering over
        // artwork. Hence a manual choice rather than a default.
        Column {
            Text("OCR detection")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                snd.komelia.settings.model.OcrSpeedMode.entries.forEach { mode ->
                    InputChip(
                        selected = ocrSettings.speedMode == mode,
                        onClick = { onOcrSettingsChange(ocrSettings.copy(speedMode = mode)) },
                        colors = accentInputChipColors(),
                        label = {
                            Text(
                                when (mode) {
                                    snd.komelia.settings.model.OcrSpeedMode.NORMAL -> "Normal (small)"
                                    snd.komelia.settings.model.OcrSpeedMode.FAST -> "Fast (tiny)"
                                }
                            )
                        }
                    )
                }
            }
        }

        // Which language the page is written in, for translation. The OCR
        // language follows it (see ReaderState.onTranslationSettingsChange):
        // reading Latin on a Japanese page finds nothing and looks like a
        // broken translation rather than a mismatched setting.
        Column {
            Text("Translate from")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(
                    snd.komelia.settings.model.TranslationLanguage.ENGLISH,
                    snd.komelia.settings.model.TranslationLanguage.JAPANESE,
                ).forEach { language ->
                    InputChip(
                        selected = translationSettings.source == language,
                        onClick = {
                            onTranslationSettingsChange(translationSettings.copy(source = language))
                        },
                        colors = accentInputChipColors(),
                        label = { Text(language.name) }
                    )
                }
            }
        }

        SwitchWithLabel(
            checked = ocrSettings.mergeBoxes,
            onCheckedChange = { onOcrSettingsChange(ocrSettings.copy(mergeBoxes = it)) },
            label = { Text(LocalStrings.current.ui.mergeTextSegments) },
            supportingText = {
                Text(LocalStrings.current.ui.mergeAdjacentTextBlocksInto, style = MaterialTheme.typography.labelMedium)
            },
            contentPadding = PaddingValues(horizontal = 10.dp)
        )

        HorizontalDivider()
        TranslationModelSettings(commonReaderState)

        HorizontalDivider()
        SeriesGlossarySettings(commonReaderState)
    }
}

/**
 * Offers the better translation engine's model for the pair being read.
 *
 * Bergamot reads a French ML Kit does not — "Nous vous demandons de cesser
 * toute poursuite" against "Nous demandons que vous avez cessé toute la
 * poursuite" — but it costs a 36MB download, and it is 106ms a bubble against
 * ML Kit's 60ms on this hardware. On a page whose OCR takes 3.9s that is 10%,
 * which is why it is offered rather than argued about.
 *
 * Nothing is shown when there is no model for the pair: the engine then cannot
 * be used at all, and a disabled button would only raise a question with no
 * answer.
 */
@Composable
private fun TranslationModelSettings(
    commonReaderState: ReaderState,
) {
    val state = commonReaderState.translationModelState.collectAsState().value
    LaunchedEffect(Unit) { commonReaderState.refreshTranslationModel() }
    if (state == null) return

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Better translation")
        when (state) {
            is snd.komelia.ui.reader.image.TranslationModelState.Missing -> {
                Text(
                    "A 36 MB model that reads far better French than the built-in " +
                            "translator, at about ten percent more time per page.",
                    style = MaterialTheme.typography.labelMedium,
                )
                TextButton(onClick = { commonReaderState.downloadTranslationModel() }) {
                    Text("Download model")
                }
            }

            is snd.komelia.ui.reader.image.TranslationModelState.Downloading -> {
                Text(
                    "${state.what} — ${state.percent}%",
                    style = MaterialTheme.typography.labelMedium,
                )
            }

            is snd.komelia.ui.reader.image.TranslationModelState.Ready -> {
                Text(
                    "In use. Pages already read keep the translation they were " +
                            "given; turn the page to see the difference.",
                    style = MaterialTheme.typography.labelMedium,
                )
                TextButton(onClick = { commonReaderState.deleteTranslationModel() }) {
                    Text("Remove model")
                }
            }
        }
    }
}

/**
 * Per-series terms the translator must not decide for itself.
 *
 * Lives in the reader rather than in a settings screen because this is where a
 * wrong term is seen: the bubble that says "Meryl discorde" is on screen while
 * the sheet is open.
 */
@Composable
private fun SeriesGlossarySettings(
    commonReaderState: ReaderState,
) {
    val terms = commonReaderState.glossaryTerms.collectAsState().value
    // Loaded when the tab is first shown rather than with the book: most
    // reading sessions never open this.
    LaunchedEffect(Unit) { commonReaderState.refreshGlossaryTerms() }

    var source by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Glossary for this series")
        Text(
            "A term the translator keeps getting wrong. Leave the translation " +
                    "empty to keep the word as it is, which is what a name needs.",
            style = MaterialTheme.typography.labelMedium,
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = source,
                onValueChange = { source = it },
                label = { Text("Term") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = target,
                onValueChange = { target = it },
                label = { Text("Translation") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        TextButton(
            enabled = source.isNotBlank(),
            onClick = {
                commonReaderState.addGlossaryTerm(source, target)
                source = ""
                target = ""
            },
        ) { Text("Add term") }

        terms.forEach { term ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    // A name-only entry has nothing to show on the right, and an
                    // arrow to itself reads as a mistake.
                    if (term.isNameOnly) term.source else "${term.source}  →  ${term.target}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { commonReaderState.removeGlossaryTerm(term.source) }) {
                    Icon(Icons.Rounded.Close, contentDescription = "Remove ${term.source}")
                }
            }
        }
    }
}

