package snd.komelia.ui.reader.epub.audio

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.ClosedCaptionOff
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import snd.komelia.transcription.TranscriptEngineState
import snd.komelia.transcription.TranscriptSegment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.readium.r2.shared.publication.Locator
import snd.komelia.audiobook.AudioBookmark
import snd.komelia.audiobook.AudioFolderTrack
import snd.komelia.image.coil.BookDefaultThumbnailRequest
import snd.komelia.ui.LocalAccentColor
import snd.komelia.ui.LocalImmersiveColorEnabled
import snd.komelia.ui.common.components.AppSlider
import snd.komelia.ui.common.components.AppSliderDefaults
import snd.komelia.ui.common.components.accentFilterChipColors
import snd.komelia.ui.common.images.ThumbnailImage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import snd.komelia.ui.reader.epub.Epub3BookmarkToggleButton
import snd.komelia.ui.reader.epub.Epub3LocationLabel
import snd.komelia.ui.reader.epub.locatorToPositionIndex
import snd.komga.client.book.KomgaBookId
import kotlin.math.roundToInt
import snd.komelia.ui.LocalStrings

private val emphasizedEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
private val emphasizedAccelerateEasing = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)

@Composable
private fun TranscriptPanel(
    segments: List<TranscriptSegment>,
    state: TranscriptEngineState?,
    playbackMs: Long,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        val stateLabel = when (state) {
            null -> "state: null (not started)"
            TranscriptEngineState.Idle -> "state: Idle"
            is TranscriptEngineState.Active -> "state: Active — ${state.diagnostics.ifEmpty { "starting…" }}"
            TranscriptEngineState.UnsupportedDevice -> "state: UnsupportedDevice"
            is TranscriptEngineState.Downloading -> "state: Downloading (${state.progress?.let { "${(it * 100).toInt()}%" } ?: "…"})"
            is TranscriptEngineState.Error -> "state: Error"
        }

        when {
            state is TranscriptEngineState.UnsupportedDevice ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        LocalStrings.current.ui.speechModelNotAvailableOn,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(stateLabel, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.4f))
                }

            state is TranscriptEngineState.Downloading ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val progress = state.progress
                    if (progress != null) {
                        CircularProgressIndicator(progress = { progress })
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Downloading model… ${(progress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                    } else {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(LocalStrings.current.ui.downloadingTranscriptionModel, style = MaterialTheme.typography.bodySmall)
                    }
                }

            state is TranscriptEngineState.Error ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(stateLabel, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.4f))
                }

            segments.isEmpty() ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        LocalStrings.current.ui.listening,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(stateLabel, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.4f))
                }

            else -> {
                val sorted = segments.sortedByDescending { it.startMs }
                val listState = rememberLazyListState()

                LaunchedEffect(sorted.size) {
                    listState.scrollToItem(0)
                }

                LazyColumn(
                    state = listState,
                    reverseLayout = true,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(sorted, key = { _, seg -> seg.id }) { index, seg ->
                        val nextSeg = sorted.getOrNull(index + 1)
                        if (seg.chunkId != null && nextSeg?.chunkId != null && seg.chunkId != nextSeg.chunkId) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 6.dp),
                                color = Color.White.copy(alpha = 0.2f),
                            )
                        }
                        val alpha = when {
                            !seg.isFinal -> 0.55f
                            seg.startMs <= playbackMs -> 1f
                            else -> {
                                val aheadMs = (seg.startMs - playbackMs).coerceAtMost(7_000L)
                                1f - (aheadMs / 7_000f) * 0.75f
                            }
                        }
                        Text(
                            text = seg.text,
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = alpha),
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun formatHMS(seconds: Double): String {
    val total = seconds.toLong().coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

private fun formatTimeLeft(seconds: Double): String = "−" + formatHMS(seconds)

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AudioFullScreenPlayer(
    controller: EpubAudioController,
    bookId: KomgaBookId,
    bookTitle: String,
    chapterTitle: String,
    backgroundColor: Color,
    positions: List<Locator>,
    currentLocator: Locator?,
    onNavigateToPosition: (Int) -> Unit,
    onDismiss: () -> Unit,
    onDrag: (fraction: Float) -> Unit,
    onDragEnd: (fraction: Float) -> Unit,
    onChapterClick: () -> Unit,
    isBookmarked: Boolean,
    onBookmarkToggle: () -> Unit,
    audioTracks: List<AudioFolderTrack> = emptyList(),
    audioBookmarks: List<AudioBookmark> = emptyList(),
    isAudioBookmarked: Boolean = false,
    onAudioBookmarkToggle: () -> Unit = {},
    currentAudioTrackIndex: Int = 0,
    onSeekToTrackPosition: ((trackIndex: Int, positionSeconds: Double) -> Unit)? = null,
    playbackSpeed: Double,
    onSpeedChange: (Double) -> Unit,
    transcriptState: TranscriptEngineState? = null,
    transcriptSegments: List<TranscriptSegment> = emptyList(),
    onTranscriptToggle: () -> Unit = {},
    isTranscribing: Boolean = false,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    modifier: Modifier = Modifier,
) {
    val isPlaying by controller.isPlaying.collectAsState()
    val volume by controller.volume.collectAsState()
    val elapsedSeconds by controller.elapsedSeconds.collectAsState()
    val totalDurationSeconds by controller.totalDurationSeconds.collectAsState()
    val accentColor = LocalAccentColor.current
    val immersiveColorEnabled = LocalImmersiveColorEnabled.current

    val coverRequest = remember(bookId) { BookDefaultThumbnailRequest(bookId) }

    var playerHeightPx by remember { mutableIntStateOf(1) }

    val containerShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    var showMetadataDialog by remember { mutableStateOf(false) }

    if (showMetadataDialog) {
        AudioMetadataDialog(
            controller = controller,
            onDismiss = { showMetadataDialog = false }
        )
    }

    // The modifier fills the available space from the parent (Alignment.BottomCenter in the Box).
    // The Surface itself wraps its content height — it's a bottom sheet, not a true full-screen
    // composable. The sharedBounds animates from pill bounds to sheet bounds.
    Box(
        modifier = modifier,
        contentAlignment = Alignment.BottomCenter,
    ) {
        with(sharedTransitionScope) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .onGloballyPositioned { playerHeightPx = it.size.height }
                    .sharedBounds(
                        rememberSharedContentState(key = "audio-player-surface-${bookId.value}"),
                        animatedVisibilityScope = animatedVisibilityScope,
                        enter = fadeIn(tween(400, easing = emphasizedEasing)),
                        exit = fadeOut(tween(300, easing = emphasizedAccelerateEasing)),
                        boundsTransform = { _, _ -> tween(500, easing = emphasizedEasing) },
                        resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                        clipInOverlayDuringTransition = OverlayClip(containerShape),
                    )
                    .pointerInput(Unit) {
                        var dragOffsetY = 0f
                        detectVerticalDragGestures(
                            onDragEnd = {
                                val fraction = (dragOffsetY / playerHeightPx).coerceIn(0f, 1f)
                                onDragEnd(fraction)
                                dragOffsetY = 0f
                            },
                            onDragCancel = {
                                onDragEnd(0f)
                                dragOffsetY = 0f
                            },
                            onVerticalDrag = { _, delta ->
                                dragOffsetY = (dragOffsetY + delta).coerceAtLeast(0f)
                                onDrag((dragOffsetY / playerHeightPx).coerceIn(0f, 1f))
                            },
                        )
                    },
                shape = containerShape,
                color = Color.Transparent,
            ) {
                Box {
                    if (immersiveColorEnabled) {
                        ThumbnailImage(
                            data = coverRequest,
                            cacheKey = bookId.value,
                            contentScale = ContentScale.Crop,
                            placeholder = null,
                            modifier = Modifier
                                .matchParentSize()
                                .blur(radius = 40.dp, edgeTreatment = BlurredEdgeTreatment.Rectangle),
                        )
                    }
                    if (immersiveColorEnabled) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(backgroundColor.copy(alpha = 0.72f)),
                        )
                    } else {
                        NonImmersiveAudioBackground(Modifier.matchParentSize())
                    }
                Column(
                    modifier = Modifier
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    with(animatedVisibilityScope) {
                        val fadeModifier = Modifier.animateEnterExit(
                            enter = fadeIn(tween(300, delayMillis = 150)),
                            exit = fadeOut(tween(150)),
                        )

                        // Drag handle
                        Box(
                            modifier = fadeModifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            BottomSheetDefaults.DragHandle()
                        }

                        // Cover / Transcript panel — AnimatedContent switches between the two
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 48.dp)
                                .aspectRatio(1f),
                        ) {
                            AnimatedContent(
                                targetState = isTranscribing,
                                transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
                                modifier = Modifier.fillMaxSize(),
                                label = "cover-transcript",
                            ) { showTranscript ->
                                if (showTranscript) {
                                    TranscriptPanel(
                                        segments = transcriptSegments,
                                        state = transcriptState,
                                        playbackMs = (elapsedSeconds * 1000).toLong(),
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                } else {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        shadowElevation = 8.dp,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .sharedBounds(
                                                rememberSharedContentState(key = "audio-cover-${bookId.value}"),
                                                animatedVisibilityScope = animatedVisibilityScope,
                                                enter = fadeIn(tween(400, easing = emphasizedEasing)),
                                                exit = fadeOut(tween(300, easing = emphasizedAccelerateEasing)),
                                                boundsTransform = { _, _ -> tween(500, easing = emphasizedEasing) },
                                            ),
                                    ) {
                                        ThumbnailImage(
                                            data = coverRequest,
                                            cacheKey = bookId.value,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    }
                                }
                            }
                            if (!isTranscribing) {
                                IconButton(
                                    onClick = { showMetadataDialog = true },
                                    modifier = Modifier.align(Alignment.TopEnd),
                                ) {
                                    Icon(Icons.Default.Info, contentDescription = LocalStrings.current.ui.metadata)
                                }
                            }
                        }

                        // Book title
                        Text(
                            text = bookTitle,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = fadeModifier
                                .fillMaxWidth()
                                .padding(horizontal = 48.dp)
                                .padding(top = 16.dp, bottom = 4.dp),
                        )

                        // Chapter title — tap to open TOC; transcribe on left, bookmark on right
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = fadeModifier
                                .fillMaxWidth()
                                .padding(horizontal = 48.dp),
                        ) {
                            if (audioTracks.isNotEmpty()) {
                                IconButton(onClick = onTranscriptToggle) {
                                    Icon(
                                        imageVector = if (isTranscribing) Icons.Default.ClosedCaption
                                                      else Icons.Default.ClosedCaptionOff,
                                        contentDescription = LocalStrings.current.ui.toggleTranscript,
                                        tint = if (isTranscribing) accentColor ?: LocalContentColor.current
                                               else LocalContentColor.current,
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            SuggestionChip(
                                onClick = onChapterClick,
                                label = {
                                    Text(
                                        text = chapterTitle,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                },
                                modifier = Modifier.weight(1f),
                            )
                            val effectiveIsBookmarked = if (audioTracks.isNotEmpty()) isAudioBookmarked else isBookmarked
                            val effectiveOnBookmarkToggle = if (audioTracks.isNotEmpty()) onAudioBookmarkToggle else onBookmarkToggle
                            Spacer(modifier = Modifier.width(8.dp))
                            Epub3BookmarkToggleButton(
                                isBookmarked = effectiveIsBookmarked,
                                onClick = effectiveOnBookmarkToggle,
                                accentColor = accentColor,
                            )
                        }

                        // SMIL current page index — needed by slider (Task 6) AND inner controls
                        val smilCurrentIndex = remember(currentLocator, positions) {
                            if (positions.size > 1) locatorToPositionIndex(positions, currentLocator) else 0
                        }

                        // Hoisted drag state — used by both the slider and the time display below
                        var sliderDraft by remember { mutableStateOf(elapsedSeconds.toFloat()) }
                        var isInteracting by remember { mutableStateOf(false) }

                        if (audioTracks.isNotEmpty() && onSeekToTrackPosition != null) {
                            // Folder-mode: full-book seek slider
                            AppSlider(
                                value = if (isInteracting) sliderDraft else elapsedSeconds.toFloat(),
                                onValueChange = { isInteracting = true; sliderDraft = it },
                                onValueChangeFinished = {
                                    isInteracting = false
                                    val target = sliderDraft.toDouble()
                                        .coerceIn(0.0, totalDurationSeconds)
                                    var cumulative = 0.0
                                    for ((idx, track) in audioTracks.withIndex()) {
                                        val trackEnd = cumulative + track.durationSeconds
                                        if (trackEnd >= target || idx == audioTracks.lastIndex) {
                                            onSeekToTrackPosition(idx, target - cumulative)
                                            break
                                        }
                                        cumulative = trackEnd
                                    }
                                },
                                valueRange = 0f..totalDurationSeconds.toFloat().coerceAtLeast(1f),
                                accentColor = accentColor,
                                colors = AppSliderDefaults.colors(accentColor = accentColor),
                                modifier = fadeModifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 32.dp)
                                    .padding(top = 16.dp),
                            )
                        } else if (positions.size > 1) {
                            // SMIL mode slider — plain, no +/− buttons (they move to controls row)
                            // smilCurrentIndex is declared above this block (Task 8 Step 2)
                            val smilScope = rememberCoroutineScope()
                            var smilEndJob by remember { mutableStateOf<Job?>(null) }
                            var smilInteracting by remember { mutableStateOf(false) }
                            var smilDraft by remember { mutableStateOf(smilCurrentIndex.toFloat()) }

                            LaunchedEffect(smilCurrentIndex) {
                                if (!smilInteracting) smilDraft = smilCurrentIndex.toFloat()
                            }

                            fun navigateSmil(newIndex: Int) {
                                smilDraft = newIndex.toFloat()
                                onNavigateToPosition(newIndex)
                                smilInteracting = true
                                smilEndJob?.cancel()
                                smilEndJob = smilScope.launch {
                                    delay(700)
                                    smilInteracting = false
                                }
                            }

                            Column(
                                modifier = fadeModifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 32.dp)
                                    .padding(top = 16.dp),
                            ) {
                                Epub3LocationLabel(
                                    positions = positions,
                                    currentLocator = currentLocator,
                                    overrideIndex = smilDraft.roundToInt(),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                AppSlider(
                                    value = smilDraft,
                                    onValueChange = { smilInteracting = true; smilDraft = it },
                                    onValueChangeFinished = { navigateSmil(smilDraft.roundToInt()) },
                                    valueRange = 0f..(positions.size - 1).toFloat(),
                                    steps = 0,
                                    accentColor = accentColor,
                                    colors = AppSliderDefaults.colors(accentColor = accentColor),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }

                        // Time display — live during folder-mode drag; static otherwise
                        if (totalDurationSeconds > 0) {
                            val displayedElapsed = if (isInteracting && audioTracks.isNotEmpty()) {
                                sliderDraft.toDouble()
                            } else {
                                elapsedSeconds
                            }
                            val remaining = (totalDurationSeconds - displayedElapsed).coerceAtLeast(0.0)
                            Row(
                                modifier = fadeModifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 48.dp)
                                    .padding(top = if (positions.size > 1) 0.dp else 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = formatHMS(displayedElapsed),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = formatTimeLeft(remaining),
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = formatHMS(totalDurationSeconds),
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }

                        // Mode detection
                        val isSMILMode = audioTracks.isEmpty() && positions.size > 1

                        // Chapter start indices (used for SMIL outer buttons)
                        val chapterStartIndices = remember(positions) {
                            positions.mapIndexedNotNull { idx, loc ->
                                if (idx == 0 || loc.href != positions[idx - 1].href) idx else null
                            }
                        }

                        val onPrevChapter: () -> Unit = if (isSMILMode) {
                            {
                                val currentChapterStart =
                                    chapterStartIndices.lastOrNull { it <= smilCurrentIndex } ?: 0
                                val target = if (smilCurrentIndex > currentChapterStart) {
                                    currentChapterStart
                                } else {
                                    chapterStartIndices.lastOrNull { it < currentChapterStart } ?: 0
                                }
                                onNavigateToPosition(target)
                            }
                        } else controller::seekToPrev

                        val onNextChapter: () -> Unit = if (isSMILMode) {
                            {
                                val nextIdx = chapterStartIndices.firstOrNull { it > smilCurrentIndex }
                                if (nextIdx != null) onNavigateToPosition(nextIdx)
                            }
                        } else controller::seekToNext

                        // Controls
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = fadeModifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                        ) {
                            // Outer left: prev chapter (folder = prev track; SMIL = prev epub chapter)
                            IconButton(onClick = onPrevChapter) {
                                Icon(Icons.Filled.SkipPrevious, contentDescription = LocalStrings.current.ui.previousChapter)
                            }
                            // Inner left: folder = −20 s; SMIL = prev epub position
                            if (isSMILMode) {
                                IconButton(
                                    onClick = {
                                        onNavigateToPosition((smilCurrentIndex - 1).coerceAtLeast(0))
                                    },
                                    enabled = smilCurrentIndex > 0,
                                ) {
                                    Icon(Icons.Filled.Remove, contentDescription = LocalStrings.current.ui.previousPage)
                                }
                            } else {
                                IconButton(onClick = { controller.seekRelative(-10.0) }) {
                                    Icon(Icons.Filled.Replay10, contentDescription = LocalStrings.current.ui.rewind10Seconds)
                                }
                            }
                            // Play / Pause
                            FilledIconButton(
                                onClick = controller::togglePlayPause,
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = accentColor ?: MaterialTheme.colorScheme.primaryContainer,
                                ),
                                modifier = Modifier.size(72.dp),
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    modifier = Modifier.size(36.dp),
                                )
                            }
                            // Inner right: folder = +30 s; SMIL = next epub position
                            if (isSMILMode) {
                                IconButton(
                                    onClick = {
                                        onNavigateToPosition(
                                            (smilCurrentIndex + 1).coerceAtMost(positions.size - 1)
                                        )
                                    },
                                    enabled = smilCurrentIndex < positions.size - 1,
                                ) {
                                    Icon(Icons.Filled.Add, contentDescription = LocalStrings.current.ui.nextPage)
                                }
                            } else {
                                IconButton(onClick = { controller.seekRelative(30.0) }) {
                                    Icon(Icons.Filled.Forward30, contentDescription = LocalStrings.current.ui.forward30Seconds)
                                }
                            }
                            // Outer right: next chapter (folder = next track; SMIL = next epub chapter)
                            IconButton(onClick = onNextChapter) {
                                Icon(Icons.Filled.SkipNext, contentDescription = LocalStrings.current.ui.nextChapter)
                            }
                        }

                        // Volume
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = fadeModifier
                                .fillMaxWidth()
                                .padding(horizontal = 32.dp)
                                .padding(top = 8.dp),
                        ) {
                            Icon(
                                Icons.Filled.VolumeDown,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                            AppSlider(
                                value = volume,
                                onValueChange = controller::setVolume,
                                valueRange = 0f..1f,
                                accentColor = accentColor,
                                colors = AppSliderDefaults.colors(accentColor = accentColor),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 8.dp),
                            )
                            Icon(
                                Icons.Filled.VolumeUp,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                        }

                        // Speed chips — 2 rows of 3, no label
                        val speeds = listOf(0.5, 1.0, 1.25, 1.5, 1.75, 2.0)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = fadeModifier
                                .fillMaxWidth()
                                .padding(horizontal = 48.dp)
                                .padding(top = 8.dp),
                        ) {
                            speeds.chunked(3).forEach { rowSpeeds ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    rowSpeeds.forEach { speed ->
                                        val selected = kotlin.math.abs(playbackSpeed - speed) < 0.01
                                        FilterChip(
                                            selected = selected,
                                            onClick = { onSpeedChange(speed) },
                                            label = { Text("${speed}×") },
                                            colors = accentFilterChipColors(),
                                        )
                                    }
                                }
                            }
                        }

                    }
                }
                } // Box
            }
        }
    }
}
