package snd.komelia.ui.reader.epub

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.core.content.ContextCompat
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.flowOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import coil3.compose.rememberAsyncImagePainter
import com.storyteller.reader.EpubView
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import snd.komelia.image.coil.BookDefaultThumbnailRequest
import snd.komelia.settings.model.Epub3NativeSettings
import snd.komelia.ui.LocalHazeState
import snd.komelia.ui.LocalImmersiveColorAlpha
import snd.komelia.ui.LocalImmersiveColorEnabled
import snd.komelia.ui.LocalPlatform
import snd.komelia.ui.LocalTheme
import snd.komelia.ui.LocalUseNewLibraryUI2
import snd.komelia.ui.LocalWindowState
import snd.komelia.ui.common.immersive.extractDominantColor
import snd.komelia.ui.platform.BackPressHandler
import snd.komelia.ui.platform.PlatformType.MOBILE
import snd.komelia.ui.reader.ReaderTopBar
import snd.komelia.audiobook.AudioBookmark
import snd.komelia.audiobook.AudioChapterEntry
import snd.komelia.audiobook.AudioFolderTrack
import snd.komelia.ui.reader.epub.audio.AudiobookFolderController
import snd.komelia.ui.reader.epub.audio.AudioFullScreenPlayer
import snd.komelia.ui.reader.epub.audio.AudioMiniPlayer
import snd.komelia.ui.reader.epub.audio.AudioTrackListDialog

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
actual fun Epub3ReaderContent(state: EpubReaderState) {
    val activity = LocalContext.current as FragmentActivity
    val epub3State = state as? Epub3ReaderState
    val useNewUI2 = LocalUseNewLibraryUI2.current

    val settingsFlow = remember(epub3State) {
        epub3State?.settings ?: MutableStateFlow(Epub3NativeSettings())
    }
    val settings by settingsFlow.collectAsState()
    val themeBgColor = Color(settings.theme.background)

    val coroutineScope = rememberCoroutineScope()
    val playerTransitionState = remember { SeekableTransitionState(false) }
    val playerTransition = rememberTransition(playerTransitionState, label = "audio-player")

    val theme = LocalTheme.current
    val readerHazeState = if (theme.transparentBars) rememberHazeState() else null

    // Hoist showControls so we can gate hazeSource on it (see comment below).
    val showControls by remember(epub3State) {
        epub3State?.showControls ?: MutableStateFlow(true)
    }.collectAsState()

    // Provide null hazeState when fullscreen (controls hidden) so overlays use solid surface
    // instead of an unsourced blur. hazeSource is also gated on showControls for the same reason.
    CompositionLocalProvider(LocalHazeState provides if (showControls) readerHazeState else null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(themeBgColor)
        ) {
            // hazeSource is only active when controls are visible (non-fullscreen).
            // hazeEffect + WebView in fullscreen causes continuous WebView invalidation/flicker.
            // When controls are hidden the mini player's hazeEffect blurs the solid background
            // instead of live page content — visually indistinguishable since they match.
            Box(
                Modifier.fillMaxSize().then(
                    if (readerHazeState != null && showControls) Modifier.hazeSource(readerHazeState) else Modifier
                )
            ) {
                Spacer(Modifier.fillMaxSize().background(themeBgColor))
                AndroidView(
                    factory = { ctx ->
                        EpubView(context = ctx, activity = activity, shouldApplyInsetsPadding = false).also { view ->
                            epub3State?.onEpubViewCreated(view)
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = settings.topMargin.dp, bottom = settings.bottomMargin.dp)
                )
            }

            if (epub3State != null) {
                if (LocalPlatform.current == MOBILE) {
                    val windowState = LocalWindowState.current
                    DisposableEffect(showControls) {
                        if (showControls) windowState.setFullscreen(false)
                        else windowState.setFullscreen(true)
                        onDispose { windowState.setFullscreen(false) }
                    }
                }

                val showSettings by epub3State.showSettings.collectAsState()
                val showContentDialog by epub3State.showContentDialog.collectAsState()
                val bookmarks by epub3State.bookmarks.collectAsState()
                val toc by epub3State.tableOfContents.collectAsState()
                val positions by epub3State.positions.collectAsState()
                val controller by epub3State.mediaOverlayController.collectAsState()
                val currentLocator by epub3State.currentLocator.collectAsState()

                val folderController = controller as? AudiobookFolderController
                val audioTracks by (folderController?.tracks ?: MutableStateFlow(emptyList<AudioFolderTrack>())).collectAsState()
                val audioBookmarks by (folderController?.audioBookmarks ?: MutableStateFlow(emptyList<AudioBookmark>())).collectAsState()
                val currentAudioTrackIndex by (folderController?.currentTrackIndex ?: MutableStateFlow(0)).collectAsState()
                val isAudioBookmarked by (folderController?.isCurrentPositionBookmarked ?: MutableStateFlow(false)).collectAsState()
                val audioChapters by (folderController?.chapters ?: MutableStateFlow(emptyList<AudioChapterEntry>())).collectAsState()
                val currentChapterIndex by (folderController?.currentChapterIndex ?: MutableStateFlow(0)).collectAsState()
                var showAudioTrackDialog by remember { mutableStateOf(false) }

                val transcriptSegments by (folderController?.liveTranscriptSegments ?: flowOf(emptyList()))
                    .collectAsState(initial = emptyList())
                val transcriptState by (folderController?.transcriptState ?: flowOf(null))
                    .collectAsState(initial = null)
                var isTranscribing by remember { mutableStateOf(false) }
                val transcriptSnackbarState = remember { SnackbarHostState() }

                val recordAudioLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { granted ->
                    if (granted) {
                        isTranscribing = true
                        controller?.startTranscription()
                    } else {
                        coroutineScope.launch {
                            transcriptSnackbarState.showSnackbar("Microphone permission is required for live transcription")
                        }
                    }
                }

                val onTranscriptToggle: () -> Unit = {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                        coroutineScope.launch {
                            transcriptSnackbarState.showSnackbar("Live transcription requires Android 12 or later")
                        }
                    } else if (isTranscribing) {
                        isTranscribing = false
                        controller?.stopTranscription()
                    } else {
                        val hasPermission = ContextCompat.checkSelfPermission(
                            activity, Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasPermission) {
                            isTranscribing = true
                            controller?.startTranscription()
                        } else {
                            recordAudioLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                }

                val dateTimeText by produceState("") {
                    while (true) {
                        val now = java.time.LocalDateTime.now()
                        val formatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm · EEE, MMM d")
                        value = now.format(formatter)
                        val secondsUntilNextMinute = 60L - now.second
                        kotlinx.coroutines.delay(secondsUntilNextMinute * 1000L)
                    }
                }
                val overlayColor = Color(settings.theme.foreground).copy(alpha = 0.45f)

                val chapterTitle = remember(currentLocator, toc, currentAudioTrackIndex, audioTracks, audioChapters, currentChapterIndex) {
                    if (audioTracks.isNotEmpty()) {
                        audioChapters.getOrNull(currentChapterIndex)?.title
                            ?: audioTracks.getOrNull(currentAudioTrackIndex)?.title
                            ?: ""
                    } else {
                        currentLocator?.let { loc ->
                            loc.title
                                ?: findTocLink(toc, loc.href)?.title
                                ?: loc.href.toString()
                                    .substringAfterLast('/').substringBeforeLast('.')
                                    .replace('-', ' ').replace('_', ' ')
                        } ?: ""
                    }
                }

                val density = LocalDensity.current
                var cardHeightPx by remember { mutableStateOf(0) }
                val audioPlayerBottomPadding by animateDpAsState(
                    targetValue = if (showControls && positions.isNotEmpty()) {
                        with(density) { cardHeightPx.toDp() } + 10.dp
                    } else {
                        10.dp
                    },
                    label = "AudioPlayerBottomPadding"
                )


                if (showControls) {
                    if (useNewUI2) {
                        val book by epub3State.book.collectAsState()
                        ReaderTopBar(
                            seriesTitle = book?.seriesTitle ?: "",
                            bookNumber = book?.number ?: 0,
                            bookTitle = book?.metadata?.title.orEmpty(),
                            onBack = { epub3State.closeWebview() },
                            modifier = Modifier.align(Alignment.TopCenter)
                        )
                    } else {
                        // Top bar
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopStart)
                                .background(MaterialTheme.colorScheme.surface)
                                .statusBarsPadding()
                        ) {
                            IconButton(onClick = { epub3State.closeWebview() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Leave")
                            }
                            val book by epub3State.book.collectAsState()
                            Text(
                                text = book?.metadata?.title ?: "",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                // Bottom navigation card
                if (positions.isNotEmpty()) {
                    AnimatedVisibility(
                        visible = showControls,
                        enter = slideInVertically(initialOffsetY = { it }),
                        exit = slideOutVertically(targetOffsetY = { it }),
                        modifier = Modifier.align(Alignment.BottomCenter)
                    ) {
                        if (useNewUI2) {
                            Epub3ControlsCardNewUI(
                                state = epub3State,
                                onSettingsClick = { epub3State.toggleSettings() },
                                onChapterClick = { epub3State.openContentDialog(0) },
                                onBookmarkToggle = { epub3State.toggleBookmark(it) },
                                onCardHeightChanged = { cardHeightPx = it },
                            )
                        } else {
                            Epub3ControlsCard(
                                state = epub3State,
                                onDismiss = { epub3State.toggleControls() },
                                onCardHeightChanged = { cardHeightPx = it },
                                onSettingsClick = { epub3State.toggleSettings() },
                                onChapterClick = { epub3State.openContentDialog(0) },
                                onBookmarkToggle = { epub3State.toggleBookmark(it) },
                            )
                        }
                    }
                }

                // SharedTransitionLayout fills the full screen so shared elements have the full
                // coordinate space to fly between the mini-player pill and the full-screen sheet.
                controller?.let { ctrl ->
                    val book by epub3State.book.collectAsState()

                    val bookId by epub3State.bookId.collectAsState()
                    val coverRequest = remember(bookId) { BookDefaultThumbnailRequest(bookId) }
                    val coverPainter = rememberAsyncImagePainter(model = coverRequest)
                    var dominantColor by remember(bookId) { mutableStateOf<Color?>(null) }
                    LaunchedEffect(bookId) { dominantColor = extractDominantColor(coverPainter) }

                    val immersiveEnabled = LocalImmersiveColorEnabled.current
                    val immersiveAlpha = LocalImmersiveColorAlpha.current
                    val surface = MaterialTheme.colorScheme.surface
                    val playerBackgroundColor = remember(dominantColor, immersiveEnabled, immersiveAlpha) {
                        if (immersiveEnabled && dominantColor != null)
                            dominantColor!!.copy(alpha = immersiveAlpha).compositeOver(surface)
                        else surface
                    }

                    val isBookmarked = remember(currentLocator, bookmarks) { epub3State.isBookmarked(currentLocator) }

                    SharedTransitionLayout(modifier = Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            // Mini player at bottom — fades out as shared elements morph upward
                            playerTransition.AnimatedVisibility(
                                visible = { !it },
                                enter = fadeIn(tween(300)),
                                exit = fadeOut(tween(200)),
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .then(if (useNewUI2) Modifier.fillMaxWidth().padding(horizontal = 16.dp) else Modifier.padding(horizontal = 2.dp))
                                    .padding(bottom = audioPlayerBottomPadding),
                            ) {
                                AudioMiniPlayer(
                                    controller = ctrl,
                                    bookId = epub3State.bookId.value,
                                    bookTitle = book?.metadata?.title ?: "",
                                    chapterTitle = chapterTitle,
                                    backgroundColor = playerBackgroundColor,
                                    onCoverClick = { coroutineScope.launch { playerTransitionState.animateTo(true) } },
                                    sharedTransitionScope = this@SharedTransitionLayout,
                                    animatedVisibilityScope = this,
                                    useNewUI2 = useNewUI2,
                                )
                            }

                            // Full-screen player — sharedBounds on its Surface drives the animation
                            playerTransition.AnimatedVisibility(
                                visible = { it },
                                enter = EnterTransition.None,
                                exit = fadeOut(tween(500)),
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                AudioFullScreenPlayer(
                                    controller = ctrl,
                                    bookId = epub3State.bookId.value,
                                    bookTitle = book?.metadata?.title ?: "",
                                    chapterTitle = chapterTitle,
                                    backgroundColor = playerBackgroundColor,
                                    positions = positions,
                                    currentLocator = currentLocator,
                                    onNavigateToPosition = epub3State::navigateToPosition,
                                    onDismiss = { coroutineScope.launch { playerTransitionState.animateTo(false) } },
                                    onDrag = { fraction ->
                                        coroutineScope.launch { playerTransitionState.seekTo(fraction, targetState = false) }
                                    },
                                    onDragEnd = { fraction ->
                                        coroutineScope.launch {
                                            if (fraction > 0.15f) playerTransitionState.animateTo(false)
                                            else playerTransitionState.animateTo(true)
                                        }
                                    },
                                    onChapterClick = {
                                        if (audioTracks.isNotEmpty()) showAudioTrackDialog = true
                                        else epub3State.openContentDialog(0)
                                    },
                                    isBookmarked = isBookmarked,
                                    onBookmarkToggle = { currentLocator?.let { epub3State.toggleBookmark(it) } },
                                    audioTracks = audioTracks,
                                    audioBookmarks = audioBookmarks,
                                    isAudioBookmarked = isAudioBookmarked,
                                    onAudioBookmarkToggle = {
                                        folderController?.toggleAudioBookmark(
                                            audioTracks.getOrNull(currentAudioTrackIndex)?.title ?: ""
                                        )
                                    },
                                    currentAudioTrackIndex = currentAudioTrackIndex,
                                    onSeekToTrackPosition = { trackIndex, positionSeconds ->
                                        folderController?.seekToTrackPosition(trackIndex, positionSeconds)
                                    },
                                    playbackSpeed = settings.playbackSpeed,
                                    onSpeedChange = { epub3State.updateSettings(settings.copy(playbackSpeed = it)) },
                                    transcriptState = transcriptState,
                                    transcriptSegments = transcriptSegments,
                                    onTranscriptToggle = onTranscriptToggle,
                                    isTranscribing = isTranscribing,
                                    sharedTransitionScope = this@SharedTransitionLayout,
                                    animatedVisibilityScope = this,
                                    modifier = Modifier.fillMaxSize().align(Alignment.BottomCenter),
                                )
                            }
                        }
                    }
                }

                // Settings card
                AnimatedVisibility(
                    visible = showSettings,
                    enter = slideInVertically(initialOffsetY = { it }),
                    exit = slideOutVertically(targetOffsetY = { it }),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    val userFonts by remember(epub3State) {
                        epub3State?.userFonts ?: MutableStateFlow(emptyList())
                    }.collectAsState()
                    Epub3SettingsCard(
                        settings = settings,
                        onSettingsChange = epub3State::updateSettings,
                        onDismiss = { epub3State.toggleSettings() },
                        userFonts = userFonts,
                        onLoadFont = { epub3State.loadFont(it) },
                        onDeleteFont = { epub3State.deleteFont(it) },
                    )
                }

                // Date/time overlay — top left, shown when controls are hidden.
                // Delayed enter so the overlay only appears after the fullscreen/inset
                // transition has settled (avoids a jump from status-bar height to zero).
                AnimatedVisibility(
                    visible = settings.showDateTimeOverlay && !showControls,
                    enter = fadeIn(animationSpec = tween(durationMillis = 600, delayMillis = 400)),
                    exit = ExitTransition.None,
                    modifier = Modifier.align(Alignment.TopStart),
                ) {
                    Text(
                        text = dateTimeText,
                        style = MaterialTheme.typography.bodySmall,
                        color = overlayColor,
                        modifier = Modifier
                            .statusBarsPadding()
                            .padding(start = 8.dp, top = 4.dp),
                    )
                }

                // Location overlay — top right, shown when controls are hidden.
                AnimatedVisibility(
                    visible = settings.showLocationOverlay && !showControls && positions.isNotEmpty(),
                    enter = fadeIn(animationSpec = tween(durationMillis = 600, delayMillis = 400)),
                    exit = ExitTransition.None,
                    modifier = Modifier.align(Alignment.TopEnd),
                ) {
                    val locationIndex = remember(currentLocator, positions) {
                        locatorToPositionIndex(positions, currentLocator)
                    }
                    Text(
                        text = "Loc. ${locationIndex + 1} of ${positions.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = overlayColor,
                        modifier = Modifier
                            .statusBarsPadding()
                            .padding(end = 8.dp, top = 4.dp),
                    )
                }

                // Content dialog
                AnimatedVisibility(
                    visible = showContentDialog,
                    enter = slideInVertically(initialOffsetY = { it }),
                    exit = slideOutVertically(targetOffsetY = { it }),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    val searchQuery by epub3State.searchQuery.collectAsState()
                    val searchResults by epub3State.searchResults.collectAsState()
                    val isSearching by epub3State.isSearching.collectAsState()

                    Epub3ContentDialog(
                        toc = toc,
                        bookmarks = bookmarks,
                        positions = positions,
                        searchQuery = searchQuery,
                        searchResults = searchResults,
                        isSearching = isSearching,
                        currentHref = currentLocator?.href,
                        currentLocator = currentLocator,
                        onNavigateLink = { link ->
                            epub3State.navigateToLink(link)
                            epub3State.showContentDialog.value = false
                        },
                        onNavigateLocator = {
                            epub3State.navigateToLocator(it)
                            epub3State.showContentDialog.value = false
                        },
                        onDeleteBookmark = { epub3State.deleteBookmark(it) },
                        annotations = epub3State.annotations.collectAsState().value,
                        onAnnotationTap = { annotation ->
                            epub3State.editingAnnotation.value = annotation
                            epub3State.showContentDialog.value = false
                            epub3State.showAnnotationDialog.value = true
                            val loc = annotation.location as? snd.komelia.annotations.AnnotationLocation.EpubLocation
                            loc?.let {
                                runCatching {
                                    org.readium.r2.shared.publication.Locator.fromJSON(org.json.JSONObject(it.locatorJson))
                                }.getOrNull()?.let { locator -> epub3State.navigateToLocator(locator) }
                            }
                        },
                        onDeleteAnnotation = { epub3State.deleteAnnotation(it) },
                        onSearch = { epub3State.performSearch(it) },
                        onDismiss = { epub3State.showContentDialog.value = false },
                        initialTab = epub3State.initialContentTab
                    )
                }

                // Annotation context menu (shown after text selection)
                val showContextMenu by epub3State.showAnnotationContextMenu.collectAsState()
                val pendingLocator by epub3State.pendingSelectionLocator.collectAsState()
                val lastColor by epub3State.lastHighlightColor.collectAsState()
                val selX by epub3State.pendingSelectionX.collectAsState()
                val selY by epub3State.pendingSelectionY.collectAsState()

                if (showContextMenu) {
                    // selX/selY are in dp (from WebView selection rect ÷ density).
                    // Convert to pixels for the IntOffset anchor used by DropdownMenu.
                    val density = LocalDensity.current
                    val selXPx = with(density) { selX.dp.roundToPx() }
                    val selYPx = with(density) { selY.dp.roundToPx() }

                    snd.komelia.ui.reader.epub.AnnotationContextMenu(
                        selectedText = pendingLocator?.text?.highlight,
                        position = IntOffset(selXPx, selYPx),
                        selectedColor = lastColor,
                        onColorSelected = { epub3State.lastHighlightColor.value = it },
                        onCopy = { epub3State.showAnnotationContextMenu.value = false },
                        onHighlight = {
                            pendingLocator?.let { locator ->
                                epub3State.saveAnnotation(
                                    locator = locator,
                                    selectedText = locator.text?.highlight,
                                    color = epub3State.lastHighlightColor.value,
                                    note = null,
                                )
                            }
                            epub3State.showAnnotationContextMenu.value = false
                        },
                        onNote = {
                            epub3State.showAnnotationContextMenu.value = false
                            epub3State.showAnnotationDialog.value = true
                        },
                        onDismiss = { epub3State.showAnnotationContextMenu.value = false },
                    )
                }

                val showAnnotationDialog by epub3State.showAnnotationDialog.collectAsState()
                val editingAnnotation by epub3State.editingAnnotation.collectAsState()
                val annotations by epub3State.annotations.collectAsState()
                val sortedAnnotations = remember(annotations) {
                    annotations.sortedBy { annotation ->
                        (annotation.location as? snd.komelia.annotations.AnnotationLocation.EpubLocation)
                            ?.let { loc ->
                                runCatching {
                                    org.readium.r2.shared.publication.Locator.fromJSON(org.json.JSONObject(loc.locatorJson))
                                }.getOrNull()?.locations?.totalProgression
                            } ?: 0.0
                    }
                }
                val currentIndex = sortedAnnotations.indexOf(editingAnnotation)

                if (showAnnotationDialog) {
                    val isEditing = editingAnnotation != null
                    val referenceText = if (isEditing) {
                        (editingAnnotation!!.location as? snd.komelia.annotations.AnnotationLocation.EpubLocation)
                            ?.selectedText ?: ""
                    } else {
                        pendingLocator?.text?.highlight ?: ""
                    }
                    snd.komelia.ui.reader.common.AnnotationDialog(
                        referenceText = referenceText,
                        existingAnnotation = editingAnnotation,
                        initialColor = lastColor,
                        onSave = { note, color ->
                            if (isEditing) {
                                epub3State.updateAnnotation(editingAnnotation!!, note, color)
                            } else {
                                pendingLocator?.let { locator ->
                                    epub3State.saveAnnotation(locator, locator.text?.highlight, color, note)
                                }
                            }
                            epub3State.showAnnotationDialog.value = false
                            epub3State.editingAnnotation.value = null
                            epub3State.pendingSelectionLocator.value = null
                        },
                        onDelete = {
                            editingAnnotation?.let { epub3State.deleteAnnotation(it) }
                            epub3State.showAnnotationDialog.value = false
                            epub3State.editingAnnotation.value = null
                        },
                        onDismiss = {
                            epub3State.showAnnotationDialog.value = false
                            epub3State.editingAnnotation.value = null
                        },
                        onPrevious = {
                            if (currentIndex > 0) {
                                val prev = sortedAnnotations[currentIndex - 1]
                                epub3State.editingAnnotation.value = prev
                                val loc = prev.location as? snd.komelia.annotations.AnnotationLocation.EpubLocation
                                loc?.let {
                                    runCatching {
                                        org.readium.r2.shared.publication.Locator.fromJSON(org.json.JSONObject(it.locatorJson))
                                    }.getOrNull()?.let { locator -> epub3State.navigateToLocator(locator) }
                                }
                            }
                        },
                        onNext = {
                            if (currentIndex < sortedAnnotations.size - 1) {
                                val next = sortedAnnotations[currentIndex + 1]
                                epub3State.editingAnnotation.value = next
                                val loc = next.location as? snd.komelia.annotations.AnnotationLocation.EpubLocation
                                loc?.let {
                                    runCatching {
                                        org.readium.r2.shared.publication.Locator.fromJSON(org.json.JSONObject(it.locatorJson))
                                    }.getOrNull()?.let { locator -> epub3State.navigateToLocator(locator) }
                                }
                            }
                        },
                        onShowList = {
                            epub3State.initialContentTab = 2 // Annotations tab
                            epub3State.showContentDialog.value = true
                        },
                        hasPrevious = currentIndex > 0,
                        hasNext = currentIndex != -1 && currentIndex < sortedAnnotations.size - 1,
                    )
                }

                // Audio track list dialog (folder audiobook mode)
                if (showAudioTrackDialog && folderController != null) {
                    AudioTrackListDialog(
                        chapters = audioChapters,
                        bookmarks = audioBookmarks,
                        currentChapterIndex = currentChapterIndex,
                        onChapterClick = { index -> folderController.seekToChapter(index) },
                        onBookmarkClick = { bookmark ->
                            folderController.seekToTrackPosition(bookmark.trackIndex, bookmark.positionSeconds)
                        },
                        onDeleteBookmark = { bookmark ->
                            folderController.deleteAudioBookmark(bookmark.id)
                        },
                        onDismiss = { showAudioTrackDialog = false },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        }
    }

    BackPressHandler {
        if (playerTransitionState.currentState || playerTransitionState.targetState) {
            coroutineScope.launch { playerTransitionState.animateTo(false) }
        } else state.onBackButtonPress()
    }
}
