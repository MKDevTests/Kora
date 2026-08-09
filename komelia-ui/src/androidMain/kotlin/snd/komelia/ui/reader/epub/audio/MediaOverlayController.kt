package snd.komelia.ui.reader.epub.audio

import android.content.Context
import androidx.core.net.toUri
import com.storyteller.reader.AudiobookPlayer
import com.storyteller.reader.BookService
import com.storyteller.reader.EpubView
import com.storyteller.reader.Listener
import com.storyteller.reader.OverlayPar
import com.storyteller.reader.Track
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import snd.komelia.settings.model.Epub3NativeSettings
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.readium.r2.shared.InternalReadiumApi
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.fromEpubHref
import java.io.File

private val logger = KotlinLogging.logger {}

class MediaOverlayController(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
    private val bookUuid: String,
    private val extractedDir: File,
) : EpubAudioController {
    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _volume = MutableStateFlow(1f)
    override val volume: StateFlow<Float> = _volume

    private val _elapsedSeconds = MutableStateFlow(0.0)
    override val elapsedSeconds: StateFlow<Double> = _elapsedSeconds

    private val _totalDurationSeconds = MutableStateFlow(0.0)
    override val totalDurationSeconds: StateFlow<Double> = _totalDurationSeconds

    private var loadedTracks: List<Track> = emptyList()
    private var trackStartOffsetsSeconds: List<Double> = emptyList()

    override fun setVolume(v: Float) {
        val clamped = v.coerceIn(0f, 1f)
        _volume.value = clamped
        player.setVolume(clamped)
    }

    private var epubView: EpubView? = null

    // Timestamp of last audio-driven navigation — used to suppress feedback loops in F1.
    private var audioNavigatingAt = 0L
    private var lastHighlightedClip: OverlayPar? = null
    private var pendingUserLocator: Locator? = null   // page the user navigated to while paused
    private var pageTurnJob: Job? = null              // scheduled mid-clip page flip
    private var lastScheduledClip: OverlayPar? = null // clip of currently running pageTurnJob

    private val player: AudiobookPlayer = AudiobookPlayer(
        context = context,
        coroutineScope = coroutineScope,
        listener = object : Listener {
            override fun onClipChanged(overlayPar: OverlayPar) {
                logger.info { "[EPUB-DIAG] [AUDIO-MOVE] Audio playing clip: ${overlayPar.audioResource} at ${overlayPar.start}s -> Target Text: ${overlayPar.locator.href}" }
                logger.info {
                    "[komelia-epub] AUDIO-CLIP: clip=${overlayPar.locator.href} " +
                    "fragment=${overlayPar.locator.locations.fragments.firstOrNull()} " +
                    "audioResource=${overlayPar.audioResource}"
                }
                pageTurnJob?.cancel()
                lastScheduledClip = null
                lastHighlightedClip = overlayPar
                if (!_isPlaying.value) return
                val view = epubView ?: return
                audioNavigatingAt = System.currentTimeMillis()
                view.pendingProps.isPlaying = true
                view.pendingProps.locator = overlayPar.locator
                view.finalizeProps()
                schedulePageTurnIfNeeded(overlayPar)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
                if (isPlaying) {
                    // onClipChanged doesn't fire for the clip already current when play() is called.
                    // Apply lastHighlightedClip here so the first segment is highlighted immediately.
                    val clip = lastHighlightedClip
                    val view = epubView
                    if (clip != null && view != null) {
                        audioNavigatingAt = System.currentTimeMillis()
                        view.pendingProps.isPlaying = true
                        view.pendingProps.locator = clip.locator
                        view.finalizeProps()
                        schedulePageTurnIfNeeded(clip)
                    }
                } else {
                    updateElapsedPosition(player.getCurrentTrackIndex(), player.getPosition())
                    pageTurnJob?.cancel()
                    lastScheduledClip = null
                    epubView?.clearHighlightFragment()
                }
            }

            override fun onPositionChanged(position: Double) {
                updateElapsedPosition(player.getCurrentTrackIndex(), position)
                if (!_isPlaying.value) return
                val currentClip = player.getCurrentClip() ?: return
                if (currentClip == lastHighlightedClip) return
                audioNavigatingAt = System.currentTimeMillis()
                lastHighlightedClip = currentClip
                val view = epubView ?: return
                view.pendingProps.isPlaying = true
                view.pendingProps.locator = currentClip.locator
                view.finalizeProps()
            }

            override fun onTrackChanged(track: Track, position: Double, index: Int) {
                updateElapsedPosition(index, position)
            }
        }
    )

    private fun updateElapsedPosition(index: Int, positionSeconds: Double) {
        if (index !in loadedTracks.indices) return
        _elapsedSeconds.value = trackStartOffsetsSeconds.getOrElse(index) { 0.0 } + positionSeconds
    }

    private fun buildTrackStartOffsets(tracks: List<Track>): List<Double> {
        val offsets = ArrayList<Double>(tracks.size)
        var elapsedSeconds = 0.0
        for (track in tracks) {
            offsets += elapsedSeconds
            elapsedSeconds += track.duration
        }
        return offsets
    }

    @OptIn(InternalReadiumApi::class)
    suspend fun initialize(clips: List<OverlayPar>, initialLocator: Locator?) {
        if (clips.isEmpty()) return

        logger.debug { "[epub3-audio] initialize: start, clips=${clips.size}" }
        val publication = BookService.getPublication(bookUuid) ?: return

        val durationByResource = mutableMapOf<String, Double>()
        for (clip in clips) {
            durationByResource[clip.audioResource] =
                (durationByResource[clip.audioResource] ?: 0.0) + (clip.end - clip.start)
        }
        logger.debug { "[epub3-audio] initialize: durationByResource built (${durationByResource.size} resources)" }

        val toc = publication.tableOfContents
        val clipsByResource = clips.groupBy { it.audioResource }
        val bookTitle = publication.metadata.title ?: ""

        val coverUri = publication.resources
            .firstOrNull { "cover" in it.rels }
            ?.let { link ->
                val path = link.url()?.removeFragment()?.path?.trimStart('/') ?: return@let null
                File(extractedDir, path).takeIf { it.exists() }?.toUri()
            }

        val tracks = durationByResource.keys.mapNotNull { resource ->
            val url = Url.fromEpubHref(resource) ?: return@mapNotNull null
            val link = publication.linkWithHref(url)
            Track(
                uri = File(extractedDir, resource).toUri(),
                bookUuid = bookUuid,
                title = clipsByResource[resource]
                    ?.firstOrNull()?.locator?.href
                    ?.let { findChapterTitleFromToc(toc, it) }
                    ?: bookTitle.ifEmpty { resource },
                duration = durationByResource[resource] ?: 0.0,
                bookTitle = bookTitle,
                author = bookTitle.ifEmpty { null },
                coverUri = coverUri,
                relativeUri = resource,
                narrator = null,
                mimeType = link?.mediaType?.toString() ?: "audio/mpeg"
            )
        }
        logger.debug { "[epub3-audio] initialize: tracks built (${tracks.size} tracks)" }

        if (tracks.isNotEmpty()) {
            loadedTracks = tracks
            trackStartOffsetsSeconds = buildTrackStartOffsets(tracks)
            _totalDurationSeconds.value = tracks.sumOf { it.duration }

            val initialClip = initialLocator?.let { findClipForLocator(it) }
            val initialIndex = initialClip?.let { clip ->
                tracks.indexOfFirst { it.relativeUri == clip.audioResource }.coerceAtLeast(0)
            } ?: 0
            val initialPosition = initialClip?.start ?: 0.0

            logger.debug { "[epub3-audio] initialize: calling loadTracks with initialIndex=$initialIndex initialPosition=$initialPosition" }
            player.loadTracks(tracks, initialIndex, initialPosition)
            updateElapsedPosition(initialIndex, initialPosition)
            logger.info { "[EPUB-DIAG] [AUDIO-READY] ClipsLoaded: ${clips.size} | TracksCreated: ${loadedTracks.size}" }
            logger.debug { "[epub3-audio] initialize: loadTracks returned" }
        }
    }

    override fun applyAudioSettings(settings: Epub3NativeSettings) {
        player.setRate(settings.playbackSpeed)
        player.setAutomaticRewind(
            enabled = settings.rewindEnabled,
            afterInterruption = settings.rewindAfterInterruption,
            afterBreak = settings.rewindAfterBreak,
        )
    }

    fun attachView(view: EpubView) {
        epubView = view
        if (_isPlaying.value) {
            // Use getCurrentClip() for the actual playback position — more accurate than
            // lastHighlightedClip which may lag if a track boundary was just crossed and
            // the first clip message of the new track hasn't fired yet.
            val clip = player.getCurrentClip() ?: lastHighlightedClip ?: return
            audioNavigatingAt = System.currentTimeMillis()
            view.pendingProps.isPlaying = true
            view.pendingProps.locator = clip.locator
            view.finalizeProps()
            schedulePageTurnIfNeeded(clip)
        }
    }

    override fun seekToNext() {
        val allClips = BookService.getOverlayClips(bookUuid)
        val current = player.getCurrentClip() ?: return
        val idx = allClips.indexOf(current)
        val next = allClips.getOrNull(idx + 1) ?: return
        player.seekTo(next.audioResource, next.start, skipEmit = false)
    }

    override fun seekToPrev() {
        val allClips = BookService.getOverlayClips(bookUuid)
        val current = player.getCurrentClip() ?: return
        val idx = allClips.indexOf(current)
        val prev = allClips.getOrNull(idx - 1) ?: return
        player.seekTo(prev.audioResource, prev.start, skipEmit = false)
    }

    override fun togglePlayPause() {
        if (_isPlaying.value) {
            player.pause()
        } else {
            _isPlaying.value = true          // Optimistic: onClipChanged needs this true
            val pending = pendingUserLocator
            pendingUserLocator = null
            if (pending != null) {
                val clip = findClipForLocator(pending)
                logger.info {
                    "[komelia-epub] TOGGLE-PLAY: pending=${pending.href} " +
                    "fragments=${pending.locations.fragments} " +
                    "foundClip=${clip?.locator?.href} " +
                    "foundFragment=${clip?.locator?.locations?.fragments?.firstOrNull()} " +
                    "foundResource=${clip?.audioResource} foundStart=${clip?.start}"
                }
                if (clip != null) {
                    lastHighlightedClip = clip          // keep in sync since skipEmit=true won't fire onClipChanged
                    player.seekTo(clip.audioResource, clip.start, skipEmit = true)
                } else {
                    logger.info { "[komelia-epub] TOGGLE-PLAY: no clip found for pending locator — playing from current audio position" }
                }
                player.play(automaticRewind = false)
            } else {
                logger.info { "[komelia-epub] TOGGLE-PLAY: no pending locator — playing from current audio position" }
                player.play()                // Normal play with automatic rewind
            }
        }
    }

    /** F1: Called when the user navigates to a new page. Seeks audio to the first clip of that page. */
    fun handleUserLocatorChange(locator: Locator) {
        logger.info {
            "[komelia-epub] AUDIO-USER-LOC: locator=${locator.href} " +
            "progression=${locator.locations.progression} " +
            "isPlaying=${_isPlaying.value} " +
            "pendingUserLocator=${pendingUserLocator?.href}"
        }
        pageTurnJob?.cancel()
        lastScheduledClip = null

        // Removed: 500ms audio-driven suppression guard (no feedback loop path exists)
        if (!_isPlaying.value) {
            logger.info { "[EPUB-DIAG] [AUDIO-IGNORE] Reason: PAUSED | Action: Stored in pendingUserLocator -> ${locator.href}" }
            // Paused: remember where to start, don't highlight
            pendingUserLocator = locator
            logger.info { "[komelia-epub] AUDIO-USER-LOC: stored as pendingUserLocator (paused)" }
            return
        }
        // Playing: check for cross-page spanning paragraph
        handlePlayingLocatorChange(locator)
    }

    private fun handlePlayingLocatorChange(locator: Locator) {
        val currentClip = player.getCurrentClip()
        val newClip = findClipForLocator(locator)
        val allClips = BookService.getOverlayClips(bookUuid)
        val currentIdx = if (currentClip != null) allClips.indexOf(currentClip) else -1
        val newIdx = if (newClip != null) allClips.indexOf(newClip) else -1
        logger.info {
            "[komelia-epub] AUDIO-PLAY-LOC: locator=${locator.href} " +
            "currentClip=${currentClip?.locator?.href}(idx=$currentIdx) " +
            "newClip=${newClip?.locator?.href}(idx=$newIdx)"
        }

        if (currentClip != null && newClip != null) {
            if (newIdx == currentIdx + 1) {
                logger.info { "[komelia-epub] AUDIO-PLAY-LOC: adjacent clips, checking overflow for fragment=${currentClip.locator.locations.fragments.firstOrNull()}" }
                // New page's first clip is immediately adjacent — might be a spanning paragraph.
                // Check if the current fragment overflows (not entirely on screen).
                val fragmentId = currentClip.locator.locations.fragments.firstOrNull()
                logger.info { "[EPUB-DIAG] [SPAN-CHECK] CurrentIdx: $currentIdx | NewIdx: $newIdx | Fragment: $fragmentId" }
                if (fragmentId != null) {
                    epubView?.checkIsEntirelyOnScreen(fragmentId) { isOnScreen ->
                        if (!isOnScreen) {
                            logger.info { "[EPUB-DIAG] [AUDIO-IGNORE] Reason: SPANNING PARAGRAPH | Fragment: $fragmentId is not fully on screen." }
                            // Spanning paragraph — user just paged through the overflow. Don't seek.
                            audioNavigatingAt = System.currentTimeMillis()
                            return@checkIsEntirelyOnScreen
                        }
                        // Fully fits on previous page — user intentionally jumped. Seek.
                        doSeekToLocator(locator)
                    }
                    return   // wait for async callback
                }
            }
        }
        doSeekToLocator(locator)
    }

    private fun doSeekToLocator(locator: Locator) {
        val clip = findClipForLocator(locator)
        logger.info { "[EPUB-DIAG] [AUDIO-LOOKUP] TargetLocator: ${locator.href} | ClipFound: ${clip != null} | StartTime: ${clip?.start}" }
        if (clip == null) return
        player.seekTo(clip.audioResource, clip.start, skipEmit = true)
    }

    private fun schedulePageTurnIfNeeded(clip: OverlayPar) {
        if (clip == lastScheduledClip) return
        logger.info {
            "[komelia-epub] AUDIO-PAGE-TURN: scheduling for clip=${clip.locator.href} " +
            "fragment=${clip.locator.locations.fragments.firstOrNull()}"
        }
        val allClips = BookService.getOverlayClips(bookUuid)
        val currentIdx = allClips.indexOf(clip)
        val nextClip = allClips.getOrNull(currentIdx + 1) ?: return
        // href check removed — getFragmentVisibilityRatio handles "no overflow" via ratio >= 1.0
        val fragmentId = clip.locator.locations.fragments.firstOrNull() ?: return
        val clipDuration = (clip.end - clip.start).coerceAtLeast(0.1)

        lastScheduledClip = clip
        epubView?.getFragmentVisibilityRatio(fragmentId) { ratio ->
            logger.info { "[komelia-epub] AUDIO-PAGE-TURN: ratio=$ratio for fragment=$fragmentId" }
            if (ratio >= 1.0) return@getFragmentVisibilityRatio   // fully visible, nothing to do
            val delayMs = (clipDuration * ratio * 1000).toLong()
            logger.info { "[komelia-epub] AUDIO-PAGE-TURN: turning page in ${delayMs}ms to ${nextClip.locator.href}" }
            pageTurnJob = coroutineScope.launch {
                delay(delayMs)
                if (!_isPlaying.value) return@launch
                val view = epubView ?: return@launch
                audioNavigatingAt = System.currentTimeMillis()
                view.go(nextClip.locator)   // full locator so Readium finds the right column
            }
        }
    }

    /** F2: Called on double-tap. Seeks audio to that paragraph and starts playing. */
    fun handleDoubleTap(locator: Locator) {
        val clip = findClipForLocator(locator) ?: return
        player.seekTo(clip.audioResource, clip.start, skipEmit = false)
        if (!_isPlaying.value) player.play(automaticRewind = false)
    }

    private fun findClipForLocator(locator: Locator): OverlayPar? {
        val fragment = locator.locations.fragments.firstOrNull()
        val result = if (fragment != null) {
            runCatching { BookService.getClip(bookUuid, locator) }.getOrNull()
        } else {
            val prog = locator.locations.progression ?: 0.0
            BookService.getFragments(bookUuid, locator)
                .minByOrNull { kotlin.math.abs((it.locator.locations.progression ?: 0.0) - prog) }
        }
        logger.info {
            "[komelia-epub] FIND-CLIP: href=${locator.href} fragment=$fragment " +
            "→ ${if (result != null) "found ${result.locator.href}#${result.locator.locations.fragments.firstOrNull()}" else "null"}"
        }
        return result
    }

    private fun findChapterTitleFromToc(toc: List<Link>, href: Url): String? {
        val target = href.removeFragment()
        for (link in toc) {
            if (link.url()?.removeFragment() == target) return link.title
            findChapterTitleFromToc(link.children, href)?.let { return it }
        }
        return null
    }

    override fun release() {
        pageTurnJob?.cancel()
        player.unload()
        epubView = null
    }
}
