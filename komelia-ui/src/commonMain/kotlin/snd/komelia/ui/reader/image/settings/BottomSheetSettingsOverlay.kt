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
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Tune
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
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import snd.komelia.settings.model.OcrEngine
import snd.komelia.settings.model.OcrLanguage
import snd.komelia.settings.model.OcrSettings
import snd.komelia.settings.model.PanelsFullPageDisplayMode
import snd.komelia.settings.model.RapidOcrModel

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
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomSheetSettingsOverlay(
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
                PAGED -> pagedReaderState.currentSpreadIndex.collectAsState().value
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
                    pages = pages,
                    currentPageIndex = currentPageIndex,
                    onPageNumberChange = {
                        when (readerType) {
                            PAGED -> pagedReaderState.onPageChange(it)
                            CONTINUOUS -> coroutineScope.launch { continuousReaderState.scrollToBookPage(it + 1) }
                            PANELS -> panelsReaderState?.onPageChange(it)
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
                    onSettingsClick = { showSettingsDialog = true },
                    onNotesClick = onNotesClick,
                    onScanTextClick = {
                        val currentImage = when (readerType) {
                            PAGED -> pagedReaderState.currentSpread.value.pages.firstOrNull()?.imageResult?.image
                            CONTINUOUS -> null // TODO
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
                        Text("Display", modifier = Modifier.padding(horizontal = 12.dp))
                    }
                    Tab(
                        selected = pagerState.currentPage == 1,
                        onClick = { coroutineScope.launch { pagerState.animateScrollToPage(1) } },
                        modifier = Modifier.heightIn(min = 40.dp).cursorForHand(),
                    ) {
                        Text("Navigation", modifier = Modifier.padding(horizontal = 12.dp))
                    }
                    Tab(
                        selected = pagerState.currentPage == 2,
                        onClick = { coroutineScope.launch { pagerState.animateScrollToPage(2) } },
                        modifier = Modifier.heightIn(min = 40.dp).cursorForHand(),
                    ) {
                        Text("Image", modifier = Modifier.padding(horizontal = 12.dp))
                    }
                    Tab(
                        selected = pagerState.currentPage == 3,
                        onClick = { coroutineScope.launch { pagerState.animateScrollToPage(3) } },
                        modifier = Modifier.heightIn(min = 40.dp).cursorForHand(),
                    ) {
                        Text("Text", modifier = Modifier.padding(horizontal = 12.dp))
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
                                onOcrSettingsChange = commonReaderState::onOcrSettingsChange
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
) {
    Column {
        Text("Reading mode")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            InputChip(
                selected = readerType == PAGED,
                onClick = { onReaderTypeChange(PAGED) },
                colors = accentInputChipColors(),
                label = { Text("Paged") }
            )
            InputChip(
                selected = readerType == CONTINUOUS,
                onClick = { onReaderTypeChange(CONTINUOUS) },
                colors = accentInputChipColors(),
                label = { Text("Continuous") }
            )
            if (panelsReaderState != null)
                InputChip(
                    selected = readerType == PANELS,
                    onClick = { onReaderTypeChange(PANELS) },
                    colors = accentInputChipColors(),
                    label = { Text("Panels") }
                )
        }

        when (readerType) {
            PAGED -> PagedModeSettings(pageState = pagedReaderState)
            PANELS -> if (panelsReaderState != null) PanelsModeSettings(state = panelsReaderState)
            CONTINUOUS -> ContinuousModeSettings(state = continuousReaderState)
        }
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
            label = { Text("Tap to zoom") },
            contentPadding = PaddingValues(horizontal = 10.dp),
        )

        SwitchWithLabel(
            checked = adaptiveBackground,
            onCheckedChange = pageState::onAdaptiveBackgroundChange,
            label = { Text(strings.adaptiveBackground) },
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
        Text("Show full page")
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
            label = { Text("Tap to zoom") },
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

        Row(verticalAlignment = Alignment.CenterVertically) {
            val sidePadding = state.sidePaddingFraction.collectAsState().value
            val paddingPercentage = remember(sidePadding) { (sidePadding * 200).roundToInt() }
            Column(Modifier.width(100.dp)) {
                Text("Side padding", style = MaterialTheme.typography.labelLarge)
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
                Text("Page spacing", style = MaterialTheme.typography.labelLarge)
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
                contentDescription = "Paged",
            )
            ReaderModeIconButton(
                selected = readerType == CONTINUOUS,
                onClick = { onReaderTypeChange(CONTINUOUS) },
                icon = Icons.Rounded.ViewStream,
                contentDescription = "Continuous",
            )
            if (panelsReaderState != null) {
                ReaderModeIconButton(
                    selected = readerType == PANELS,
                    onClick = { onReaderTypeChange(PANELS) },
                    icon = Icons.Rounded.GridView,
                    contentDescription = "Panels",
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
                    contentDescription = "Upscaling",
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
    pages: List<PageMetadata>,
    currentPageIndex: Int,
    onPageNumberChange: (Int) -> Unit,
    loadThumbnailPreviews: Boolean,
    readerType: ReaderType,
    onReaderTypeChange: (ReaderType) -> Unit,
    panelsReaderState: PanelsReaderState?,
    ncnnSettingsState: NcnnSettingsState,
    ocrSettings: OcrSettings,
    onOcrSettingsChange: (OcrSettings) -> Unit,
    isOcrLoading: Boolean,
    onSettingsClick: () -> Unit,
    onNotesClick: () -> Unit = {},
    onScanTextClick: () -> Unit = {},
    showCarousel: Boolean,
    onToggleCarousel: () -> Unit,
    onPreviousBook: () -> Unit = {},
    onNextBook: () -> Unit = {},
    hasPreviousBook: Boolean = false,
    hasNextBook: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val accentColor = LocalAccentColor.current
    val ncnnSettings by ncnnSettingsState.ncnnUpscalerSettings.collectAsState()
    val showUpscale = isNcnnSupported()

    ReaderControlsCard(modifier = modifier) {
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
                        onPageNumberChange(it)
                        onToggleCarousel()
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Column {
                    Text(
                        text = "Page ${currentPageIndex + 1} of ${pages.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().clickable { onToggleCarousel() },
                    )

                    val accentColorForButtons = LocalAccentColor.current ?: MaterialTheme.colorScheme.primary
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onPreviousBook, enabled = hasPreviousBook) {
                            Icon(
                                Icons.Rounded.SkipPrevious,
                                contentDescription = "Previous volume",
                                tint = if (hasPreviousBook) accentColorForButtons else accentColorForButtons.copy(alpha = 0.3f)
                            )
                        }
                        ProgressSlider(
                            pages = pages,
                            currentPageIndex = currentPageIndex,
                            onPageNumberChange = onPageNumberChange,
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
                                contentDescription = "Next volume",
                                tint = if (hasNextBook) accentColorForButtons else accentColorForButtons.copy(alpha = 0.3f)
                            )
                        }
                    }

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
                            contentDescription = "Paged",
                        )
                        ReaderModeIconButton(
                            selected = readerType == CONTINUOUS,
                            onClick = { onReaderTypeChange(CONTINUOUS) },
                            icon = Icons.Rounded.ViewStream,
                            contentDescription = "Continuous",
                        )
                        if (panelsReaderState != null) {
                            ReaderModeIconButton(
                                selected = readerType == PANELS,
                                onClick = { onReaderTypeChange(PANELS) },
                                icon = Icons.Rounded.GridView,
                                contentDescription = "Panels",
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
                                contentDescription = "Upscaling",
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
                                    contentDescription = "Scan Text",
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
                            contentDescription = "Notes",
                        )

                        IconButton(onClick = onSettingsClick) {
                            Icon(
                                Icons.Rounded.Tune,
                                contentDescription = "Settings",
                                tint = accentColor ?: MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
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
        label = { Text("Linear light downsampling") },
        supportingText = {
            Text("slower but potentially more accurate", style = MaterialTheme.typography.labelMedium)
        },
        contentPadding = PaddingValues(horizontal = 10.dp)
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OcrModeSettings(
    ocrSettings: OcrSettings,
    onOcrSettingsChange: (OcrSettings) -> Unit,
) {
    val platform = LocalPlatform.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SwitchWithLabel(
            checked = ocrSettings.enabled,
            onCheckedChange = { onOcrSettingsChange(ocrSettings.copy(enabled = it)) },
            label = { Text("Enable Text Selection") },
            supportingText = {
                Text("Automatically scan pages for text", style = MaterialTheme.typography.labelMedium)
            },
            contentPadding = PaddingValues(horizontal = 10.dp)
        )

        if (platform == MOBILE) {
            Column {
                Text("OCR Engine")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OcrEngine.entries.forEach { engine ->
                        InputChip(
                            selected = ocrSettings.engine == engine,
                            onClick = { onOcrSettingsChange(ocrSettings.copy(engine = engine)) },
                            colors = accentInputChipColors(),
                            label = { Text(engine.name.replace("_", " ")) }
                        )
                    }
                }
            }
        }

        AnimatedVisibility(ocrSettings.engine == OcrEngine.ML_KIT) {
            Column {
                Text("Text Detection Language")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OcrLanguage.entries.forEach { language ->
                        InputChip(
                            selected = ocrSettings.selectedLanguage == language,
                            onClick = { onOcrSettingsChange(ocrSettings.copy(selectedLanguage = language)) },
                            colors = accentInputChipColors(),
                            label = { Text(language.name) }
                        )
                    }
                }
            }
        }

        AnimatedVisibility(ocrSettings.engine == OcrEngine.RAPID_OCR) {
            Column {
                Text("RapidOCR Model")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    RapidOcrModel.entries.forEach { model ->
                        InputChip(
                            selected = ocrSettings.rapidOcrModel == model,
                            onClick = { onOcrSettingsChange(ocrSettings.copy(rapidOcrModel = model)) },
                            colors = accentInputChipColors(),
                            label = { Text(model.name.replace("_", " ")) }
                        )
                    }
                }
            }
        }

        SwitchWithLabel(
            checked = ocrSettings.mergeBoxes,
            onCheckedChange = { onOcrSettingsChange(ocrSettings.copy(mergeBoxes = it)) },
            label = { Text("Merge text segments") },
            supportingText = {
                Text("Merge adjacent text blocks into a single block", style = MaterialTheme.typography.labelMedium)
            },
            contentPadding = PaddingValues(horizontal = 10.dp)
        )
    }
}

