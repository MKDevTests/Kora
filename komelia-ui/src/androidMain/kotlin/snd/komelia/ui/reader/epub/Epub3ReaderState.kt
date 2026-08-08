package snd.komelia.ui.reader.epub

import android.content.Context
import cafe.adriel.voyager.navigator.Navigator
import com.storyteller.reader.BookService
import com.storyteller.reader.CustomFont
import com.storyteller.reader.EpubView
import com.storyteller.reader.EpubViewListener
import com.storyteller.reader.OverlayPar
import com.storyteller.reader.TapNavigationMode
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.name
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.readium.r2.navigator.preferences.ColumnCount
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.navigator.preferences.TextAlign
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.mediatype.MediaType
import snd.komelia.AppNotifications
import snd.komelia.AppWindowState
import snd.komelia.ManagedKomgaEvents
import snd.komelia.fonts.UserFont
import snd.komelia.fonts.UserFontsRepository
import snd.komelia.komga.api.KomgaBookApi
import snd.komelia.komga.api.model.KomeliaBook
import snd.komelia.settings.EpubReaderSettingsRepository
import snd.komelia.settings.model.Epub3ColumnCount
import snd.komelia.settings.model.Epub3NativeSettings
import snd.komelia.settings.model.Epub3TextAlign
import snd.komelia.settings.model.ReaderTapNavigationMode
import snd.komelia.ui.BookSiblingsContext
import snd.komelia.ui.LoadState
import snd.komelia.ui.MainScreen
import snd.komelia.ui.book.BookScreen
import snd.komelia.ui.book.bookScreen
import snd.komelia.ui.platform.PlatformType
import snd.komelia.ui.reader.epub.audio.AudiobookFolderController
import snd.komelia.ui.reader.epub.audio.EpubAudioController
import snd.komelia.ui.reader.epub.audio.MediaOverlayController
import snd.komga.client.book.KomgaBookId
import snd.komga.client.book.R2Device
import snd.komga.client.book.R2Location
import snd.komga.client.book.R2Locator
import snd.komga.client.book.R2Progression
import snd.komga.client.sse.KomgaEvent
import snd.webview.KomeliaWebview
import kotlinx.coroutines.flow.first
import snd.komelia.sync.CompactAnnotation
import snd.komelia.sync.CompactAudioBookmark
import snd.komelia.sync.CompactBookmark
import snd.komelia.sync.CompactAudioPosition
import snd.komelia.sync.ReaderSyncService
import snd.komelia.sync.SyncBlob
import snd.komelia.audiobook.AudioPosition
import snd.komelia.audiobook.AudioBookmark
import snd.komelia.audiobook.AudioBookmarkRepository
import snd.komelia.audiobook.AudioPositionRepository
import snd.komelia.annotations.BookAnnotation
import snd.komelia.annotations.AnnotationLocation
import snd.komelia.annotations.BookAnnotationRepository
import snd.komelia.bookmarks.EpubBookmark
import snd.komelia.bookmarks.EpubBookmarkRepository
import java.io.File
import java.net.URL
import kotlin.time.Clock
import org.json.JSONObject
import java.util.UUID
import com.storyteller.reader.Highlight
import org.readium.r2.shared.publication.services.search.search
import org.readium.r2.shared.util.Try

private val logger = KotlinLogging.logger {}

class Epub3ReaderState(
    bookId: KomgaBookId,
    book: KomeliaBook?,
    private val context: Context,
    private val bookApi: KomgaBookApi,
    private val epubSettingsRepository: EpubReaderSettingsRepository,
    private val epubBookmarkRepository: EpubBookmarkRepository,
    private val fontsRepository: UserFontsRepository,
    private val notifications: AppNotifications,
    private val markReadProgress: Boolean,
    private val windowState: AppWindowState,
    private val platformType: PlatformType,
    private val coroutineScope: CoroutineScope,
    private val bookSiblingsContext: BookSiblingsContext,
    private val audioPositionRepository: AudioPositionRepository,
    private val audioBookmarkRepository: AudioBookmarkRepository,
    private val audioChapterRepository: snd.komelia.audiobook.AudioChapterRepository,
    private val bookAnnotationRepository: BookAnnotationRepository,
    private val readerSyncService: ReaderSyncService,
    private val komgaEvents: ManagedKomgaEvents,
    private val settingsRepository: snd.komelia.settings.CommonSettingsRepository,
    private val transcriptionSettingsRepository: snd.komelia.settings.TranscriptionSettingsRepository,
    private val whisperModelDownloader: snd.komelia.updates.WhisperModelDownloader?,
    override val onExit: (KomeliaBook) -> Unit,
) : EpubReaderState {

    private val currentSyncBlob = MutableStateFlow<String?>(null)
    override val state = MutableStateFlow<LoadState<Unit>>(LoadState.Uninitialized)
    override val book = MutableStateFlow(book)
    override val loadingSteps = MutableStateFlow<List<EpubLoadingStep>>(emptyList())

    private val mutableSteps = mutableListOf<EpubLoadingStep>()

    private fun startStep(label: String) {
        mutableSteps.add(EpubLoadingStep(label, EpubLoadingStepStatus.InProgress))
        loadingSteps.value = mutableSteps.toList()
    }

    private fun completeLastStep() {
        if (mutableSteps.isNotEmpty()) {
            mutableSteps[mutableSteps.lastIndex] = mutableSteps.last().copy(status = EpubLoadingStepStatus.Complete)
            loadingSteps.value = mutableSteps.toList()
        }
    }

    private suspend fun initialSync() {
        val r2Prog = bookApi.getReadiumProgression(bookId.value)
        val remoteSyncBlob = readerSyncService.decode(r2Prog?.locator?.koboSpan)
        val localBookmarks = epubBookmarkRepository.getBookmarks(bookId.value).first()
        val localAnnotations = bookAnnotationRepository.getAnnotations(bookId.value).first()
        val localAudioBookmarks = audioBookmarkRepository.getBookmarks(bookId.value).first()
        val localAudioPosition = audioPositionRepository.getPosition(bookId.value)

        val currentLocalBlob = readerSyncService.decode(currentSyncBlob.value)
        val localLastSyncTime = currentLocalBlob?.lastModified ?: 0L

        val localSyncBlob = SyncBlob(
            bookmarks = localBookmarks.map {
                CompactBookmark(it.id, it.locatorJson, it.createdAt)
            },
            annotations = localAnnotations.map {
                CompactAnnotation(
                    id = it.id,
                    type = if (it.location is AnnotationLocation.EpubLocation) 0 else 1,
                    loc = when (val loc = it.location) {
                        is AnnotationLocation.EpubLocation -> loc.locatorJson
                        is AnnotationLocation.ComicLocation -> "${loc.page},${loc.x},${loc.y}"
                        else -> ""
                    },
                    color = it.highlightColor,
                    note = it.note,
                    createdAt = it.createdAt,
                    updatedAt = it.updatedAt,
                    selectedText = (it.location as? AnnotationLocation.EpubLocation)?.selectedText,
                )
            },
            audioBookmarks = localAudioBookmarks.map {
                CompactAudioBookmark(it.id, it.trackIndex, it.positionSeconds, it.createdAt)
            },
            audioPosition = localAudioPosition?.let {
                CompactAudioPosition(it.trackIndex, it.positionSeconds, it.savedAt)
            },
            lastModified = localLastSyncTime
        )

        val merged = if (remoteSyncBlob != null) {
            readerSyncService.merge(localSyncBlob, remoteSyncBlob, localLastSyncTime)
        } else localSyncBlob

        // Update local repositories with merged data
        val mergedAudioPos = merged.audioPosition
        if (mergedAudioPos != null && (localAudioPosition == null || mergedAudioPos.savedAt > localAudioPosition.savedAt)) {
            audioPositionRepository.savePosition(
                AudioPosition(
                    bookId = bookId.value,
                    trackIndex = mergedAudioPos.track,
                    positionSeconds = mergedAudioPos.pos,
                    savedAt = mergedAudioPos.savedAt
                )
            )
        }

        // Handle additions
        merged.bookmarks.forEach { compact ->
            if (localBookmarks.none { it.id == compact.id }) {
                epubBookmarkRepository.saveBookmark(
                    EpubBookmark(
                        id = compact.id,
                        bookId = bookId.value,
                        locatorJson = compact.locatorJson,
                        createdAt = compact.createdAt
                    )
                )
            }
        }
        merged.annotations.forEach { compact ->
            val existing = localAnnotations.find { it.id == compact.id }
            if (existing == null) {
                val location = if (compact.type == 0) {
                    AnnotationLocation.EpubLocation(compact.loc, compact.selectedText)
                } else {
                    val parts = compact.loc.split(",")
                    AnnotationLocation.ComicLocation(
                        page = parts[0].toInt(),
                        x = parts[1].toFloat(),
                        y = parts[2].toFloat()
                    )
                }
                bookAnnotationRepository.saveAnnotation(
                    BookAnnotation(
                        id = compact.id,
                        bookId = bookId.value,
                        location = location,
                        highlightColor = compact.color,
                        note = compact.note,
                        createdAt = compact.createdAt,
                        updatedAt = compact.updatedAt,
                    )
                )
            } else if (compact.updatedAt > existing.updatedAt) {
                // Remote edit is newer — update note/color, preserve local selectedText
                bookAnnotationRepository.deleteAnnotation(existing.id)
                bookAnnotationRepository.saveAnnotation(
                    existing.copy(
                        note = compact.note,
                        highlightColor = compact.color,
                        updatedAt = compact.updatedAt,
                    )
                )
            }
        }
        merged.audioBookmarks.forEach { compact ->
            if (localAudioBookmarks.none { it.id == compact.id }) {
                audioBookmarkRepository.saveBookmark(
                    AudioBookmark(
                        id = compact.id,
                        bookId = bookId.value,
                        trackIndex = compact.track,
                        positionSeconds = compact.pos,
                        trackTitle = "",
                        createdAt = compact.createdAt
                    )
                )
            }
        }

        // Handle local deletions (items present locally but missing in merged blob)
        localBookmarks.forEach { local ->
            if (merged.bookmarks.none { it.id == local.id }) {
                epubBookmarkRepository.deleteBookmark(local.id)
            }
        }
        localAnnotations.forEach { local ->
            if (merged.annotations.none { it.id == local.id }) {
                bookAnnotationRepository.deleteAnnotation(local.id)
            }
        }
        localAudioBookmarks.forEach { local ->
            if (merged.audioBookmarks.none { it.id == local.id }) {
                audioBookmarkRepository.deleteBookmark(local.id)
            }
        }

        currentSyncBlob.value = readerSyncService.encode(merged)
    }

    private suspend fun updateCacheAndPush() {
        val bookmarks = epubBookmarkRepository.getBookmarks(bookId.value).first()
        val annotations = bookAnnotationRepository.getAnnotations(bookId.value).first()
        val audioBookmarks = audioBookmarkRepository.getBookmarks(bookId.value).first()
        val audioPosition = audioPositionRepository.getPosition(bookId.value)

        val syncBlob = SyncBlob(
            bookmarks = bookmarks.map {
                CompactBookmark(it.id, it.locatorJson, it.createdAt)
            },
            annotations = annotations.map {
                CompactAnnotation(
                    id = it.id,
                    type = if (it.location is AnnotationLocation.EpubLocation) 0 else 1,
                    loc = when (val loc = it.location) {
                        is AnnotationLocation.EpubLocation -> loc.locatorJson
                        is AnnotationLocation.ComicLocation -> "${loc.page},${loc.x},${loc.y}"
                        else -> ""
                    },
                    color = it.highlightColor,
                    note = it.note,
                    createdAt = it.createdAt,
                    updatedAt = it.updatedAt,
                    selectedText = (it.location as? AnnotationLocation.EpubLocation)?.selectedText,
                )
            },
            audioBookmarks = audioBookmarks.map {
                CompactAudioBookmark(it.id, it.trackIndex, it.positionSeconds, it.createdAt)
            },
            audioPosition = audioPosition?.let {
                CompactAudioPosition(it.trackIndex, it.positionSeconds, it.savedAt)
            },
            lastModified = Clock.System.now().toEpochMilliseconds()
        )
        val encoded = readerSyncService.encode(syncBlob)
        currentSyncBlob.value = encoded

        if (!markReadProgress) return
        val locator = currentLocator.value ?: return
        val r2Prog = R2Progression(
            modified = Clock.System.now(),
            device = R2Device("komelia-android", "Komelia"),
            locator = R2Locator(
                href = locator.href.toString(),
                type = locator.mediaType.toString(),
                title = locator.title,
                locations = R2Location(
                    fragment = locator.locations.fragments,
                    position = locator.locations.position,
                    progression = locator.locations.progression?.toFloat(),
                    totalProgression = locator.locations.totalProgression?.toFloat(),
                ),
                koboSpan = encoded
            )
        )
        runCatching { bookApi.updateReadiumProgression(bookId.value, r2Prog) }
            .onFailure { logger.catching(it) }
    }

    val bookId = MutableStateFlow(bookId)
    val showControls = MutableStateFlow(false)
    val showSettings = MutableStateFlow(false)
    val showContentDialog = MutableStateFlow(false)
    var initialContentTab = 0
    val searchQuery = MutableStateFlow("")
    val searchResults = MutableStateFlow<List<Locator>>(emptyList())
    val isSearching = MutableStateFlow(false)
    val bookmarks = MutableStateFlow<List<EpubBookmark>>(emptyList())
    val annotations = MutableStateFlow<List<BookAnnotation>>(emptyList())
    val lastHighlightColor = MutableStateFlow(0xFFFFEB3B.toInt())
    val showAnnotationContextMenu = MutableStateFlow(false)
    val showAnnotationDialog = MutableStateFlow(false)
    val pendingSelectionLocator = MutableStateFlow<Locator?>(null)
    val pendingSelectionX = MutableStateFlow(0)
    val pendingSelectionY = MutableStateFlow(0)
    val editingAnnotation = MutableStateFlow<BookAnnotation?>(null)
    val tableOfContents = MutableStateFlow<List<Link>>(emptyList())
    val settings = MutableStateFlow(Epub3NativeSettings())
    val userFonts = MutableStateFlow<List<UserFont>>(emptyList())
    val mediaOverlayController = MutableStateFlow<EpubAudioController?>(null)
    val positions = MutableStateFlow<List<Locator>>(emptyList())
    val currentLocator = MutableStateFlow<Locator?>(null)
    private var epubView: EpubView? = null
    private val navigator = MutableStateFlow<Navigator?>(null)
    private val bookUuid: String get() = this.bookId.value.value
    // Directory the epub is extracted to; set during initialize().
    private var extractedDir: File? = null

    // Populated once the EPUB is extracted and ready; handed to EpubView in onEpubViewCreated.
    private var savedLocator: Locator? = null

    fun toggleControls() {
        showControls.value = !showControls.value
    }

    fun toggleSettings() {
        showSettings.value = !showSettings.value
    }

    fun openContentDialog(tab: Int) {
        initialContentTab = tab
        showContentDialog.value = true
    }

    fun performSearch(query: String) {
        searchQuery.value = query
        if (query.isBlank()) {
            searchResults.value = emptyList()
            return
        }
        isSearching.value = true
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val publication = com.storyteller.reader.BookService.getPublication(bookUuid)
                if (publication == null) {
                    isSearching.value = false
                    return@launch
                }
                
                val iterator = publication.search(query)
                if (iterator == null) {
                    searchResults.value = emptyList()
                    isSearching.value = false
                    return@launch
                }
                
                val results = mutableListOf<Locator>()
                while (true) {
                    val pageTry = iterator.next()
                    val page = (pageTry as? Try.Success)?.value ?: break
                    val locators = page.locators
                    if (locators.isEmpty()) break
                    results.addAll(locators)
                }
                searchResults.value = results
            } catch (e: Exception) {
                logger.error(e) { "Failed to perform search" }
                searchResults.value = emptyList()
            } finally {
                isSearching.value = false
            }
        }
    }

    fun clearSearch() {
        searchQuery.value = ""
        searchResults.value = emptyList()
    }

    fun toggleBookmark(locator: Locator) {
        val existing = findBookmark(locator)
        if (existing != null) {
            deleteBookmark(existing)
        } else {
            addBookmark(locator)
        }
    }

    fun isBookmarked(locator: Locator?): Boolean {
        if (locator == null) return false
        return findBookmark(locator) != null
    }

    private fun findBookmark(locator: Locator): EpubBookmark? {
        val position = locator.locations.position
        val progression = locator.locations.progression
        val href = locator.href.toString()

        return bookmarks.value.find { b ->
            val bLocator = runCatching { Locator.fromJSON(JSONObject(b.locatorJson)) }.getOrNull() ?: return@find false
            if (bLocator.href.toString() != href) return@find false

            if (position != null && bLocator.locations.position != null) {
                return@find position == bLocator.locations.position
            }

            if (progression != null) {
                val bProgression = bLocator.locations.progression
                if (bProgression != null) {
                    // Allow small difference in progression due to floating point or Readium versions
                    return@find kotlin.math.abs(progression - bProgression) < 0.0001
                }
            }

            false
        }
    }

    fun addBookmark(locator: Locator) {
        val bookmark = EpubBookmark(
            id = UUID.randomUUID().toString(),
            bookId = bookId.value,
            locatorJson = locator.toJSON().toString(),
            createdAt = Clock.System.now().toEpochMilliseconds()
        )
        coroutineScope.launch {
            epubBookmarkRepository.saveBookmark(bookmark)
            updateCacheAndPush()
        }
    }

    fun deleteBookmark(bookmark: EpubBookmark) {
        coroutineScope.launch {
            epubBookmarkRepository.deleteBookmark(bookmark.id)
            updateCacheAndPush()
        }
    }

    fun saveAnnotation(locator: Locator, selectedText: String?, color: Int, note: String?) {
        val annotation = BookAnnotation(
            id = UUID.randomUUID().toString(),
            bookId = bookId.value,
            location = AnnotationLocation.EpubLocation(
                locatorJson = locator.toJSON().toString(),
                selectedText = selectedText,
            ),
            highlightColor = color,
            note = note,
            createdAt = Clock.System.now().toEpochMilliseconds(),
        )
        coroutineScope.launch {
            bookAnnotationRepository.saveAnnotation(annotation)
            settingsRepository.putLastHighlightColor(color)
            lastHighlightColor.value = color
            updateCacheAndPush()
        }
    }

    fun updateAnnotation(existing: BookAnnotation, note: String?, color: Int) {
        val updated = existing.copy(highlightColor = color, note = note, updatedAt = Clock.System.now().toEpochMilliseconds())
        coroutineScope.launch {
            bookAnnotationRepository.deleteAnnotation(existing.id)
            bookAnnotationRepository.saveAnnotation(updated)
            settingsRepository.putLastHighlightColor(color)
            lastHighlightColor.value = color
            updateCacheAndPush()
        }
    }

    fun deleteAnnotation(annotation: BookAnnotation) {
        coroutineScope.launch {
            bookAnnotationRepository.deleteAnnotation(annotation.id)
            updateCacheAndPush()
        }
    }

    fun navigateToLocator(locator: Locator) {
        epubView?.go(locator)
    }

    fun navigateToLink(link: Link) {
        val locator = BookService.locateLink(bookUuid, link) ?: run {
            logger.info { "[komelia-epub] NAV-LINK: locateLink returned null for ${link.href}" }
            return
        }
        logger.info {
            "[komelia-epub] NAV-LINK: link=${link.href} → locator=${locator.href} " +
            "currentLocator_before=${currentLocator.value?.href}"
        }
        epubView?.go(locator)
        (mediaOverlayController.value as? MediaOverlayController)?.handleUserLocatorChange(locator)
        // onLocatorChange will fire after go() and call handleUserLocatorChange,
        // exactly like swipe navigation — the locator from EpubView has proper
        // position data, not a TOC anchor fragment.
    }

    fun updateSettings(new: Epub3NativeSettings) {
        settings.value = new
        applySettingsToView(new)
        mediaOverlayController.value?.applyAudioSettings(new)
        coroutineScope.launch { epubSettingsRepository.putEpub3NativeSettings(new) }
    }

    private fun applySettingsToView(s: Epub3NativeSettings) {
        val view = epubView ?: return
        val ta = when (s.textAlign) {
            Epub3TextAlign.JUSTIFY -> TextAlign.JUSTIFY
            Epub3TextAlign.LEFT    -> TextAlign.LEFT
            Epub3TextAlign.CENTER  -> TextAlign.CENTER
            Epub3TextAlign.RIGHT   -> TextAlign.RIGHT
        }
        view.pendingProps.foreground       = s.theme.foreground
        view.pendingProps.background       = s.theme.background
        view.pendingProps.fontFamily       = FontFamily(s.fontFamily)
        view.pendingProps.fontSize         = s.fontSize
        view.pendingProps.lineHeight       = s.lineHeight
        view.pendingProps.paragraphSpacing = s.paragraphSpacing
        view.pendingProps.textAlign        = ta
        view.pendingProps.readaloudColor   = s.readAloudColor.colorInt
        // Not a prop: finalizeProps() rebuilds the navigator, and moving a tap
        // zone must not reload the book. See EpubView.tapNavigationMode.
        view.tapNavigationMode = when (s.tapNavigationMode) {
            ReaderTapNavigationMode.LEFT_RIGHT -> TapNavigationMode.LEFT_RIGHT
            ReaderTapNavigationMode.RIGHT_LEFT -> TapNavigationMode.RIGHT_LEFT
            ReaderTapNavigationMode.HORIZONTAL_SPLIT -> TapNavigationMode.HORIZONTAL_SPLIT
            ReaderTapNavigationMode.REVERSED_HORIZONTAL_SPLIT ->
                TapNavigationMode.REVERSED_HORIZONTAL_SPLIT
        }
        view.pendingProps.scroll           = s.scroll
        view.pendingProps.columnCount      = when (s.columnCount) {
            Epub3ColumnCount.AUTO -> ColumnCount.AUTO
            Epub3ColumnCount.ONE  -> ColumnCount.ONE
            Epub3ColumnCount.TWO  -> ColumnCount.TWO
        }
        view.pendingProps.pageMargins      = s.pageMargins
        view.pendingProps.publisherStyles  = s.publisherStyles
        view.pendingProps.respectPublisherColors = s.respectPublisherColors
        view.pendingProps.customFonts      = buildCustomFontList()
        view.finalizeProps()
    }

    private fun buildCustomFontList(): List<CustomFont> {
        return userFonts.value.map { font ->
            val ext = font.path.name.substringAfterLast(".", "ttf")
            val fontFileName = "${font.canonicalName}.$ext"
            // Readium serves publication resources at https://readium/publication/<path>.
            // Using a relative path would resolve against https://readium/assets/ (wrong).
            // Using the absolute publication URL routes the font request to DirectoryContainer.
            // Percent-encode the filename so spaces and special chars don't produce invalid URLs.
            val encodedFileName = java.net.URLEncoder.encode(fontFileName, "UTF-8").replace("+", "%20")
            val uri = "https://readium/publication/komelia-user-fonts/$encodedFileName"
            logger.debug { "[epub3-fonts] registering font: name=${font.canonicalName} uri=$uri" }
            CustomFont(uri = uri, name = font.canonicalName, type = ext)
        }
    }

    private suspend fun loadUserFontsIntoCache(fonts: List<UserFont>) {
        copyFontsToEpubDir(fonts)
        userFonts.value = fonts
    }

    private suspend fun copyFontsToEpubDir(fonts: List<UserFont>) {
        val dir = extractedDir ?: run {
            logger.warn { "[epub3-fonts] copyFontsToEpubDir: extractedDir is null, skipping" }
            return
        }
        withContext(Dispatchers.IO) {
            val fontsDir = File(dir, "komelia-user-fonts").also { it.mkdirs() }
            logger.debug { "[epub3-fonts] font staging dir: $fontsDir" }
            for (font in fonts) {
                val ext = font.path.name.substringAfterLast(".", "ttf")
                val destFile = File(fontsDir, "${font.canonicalName}.$ext")
                if (!destFile.exists()) {
                    File(font.path.toString()).copyTo(destFile)
                    logger.debug { "[epub3-fonts] copied ${font.path} -> $destFile" }
                } else {
                    logger.debug { "[epub3-fonts] already exists: $destFile" }
                }
            }
        }
    }

    fun loadFont(file: PlatformFile) {
        coroutineScope.launch {
            val name = file.name.substringBeforeLast(".")
            val userFont = UserFont.saveFontToAppDirectory(name, file) ?: return@launch
            fontsRepository.putFont(userFont)
            loadUserFontsIntoCache(fontsRepository.getAllFonts())
            applySettingsToView(settings.value)
        }
    }

    fun deleteFont(font: UserFont) {
        coroutineScope.launch {
            fontsRepository.deleteFont(font)
            font.deleteFontFile()
            loadUserFontsIntoCache(fontsRepository.getAllFonts())
            applySettingsToView(settings.value)
        }
    }

    override suspend fun initialize(navigator: Navigator) {
        this.navigator.value = navigator
        if (state.value !is LoadState.Uninitialized) return

        komgaEvents.events.onEach { event ->
            if (event is KomgaEvent.ReadProgressChanged && event.bookId == bookId.value) {
                runCatching { initialSync() }
            }
        }.launchIn(coroutineScope)

        logger.debug { "[epub3-init] starting for bookId=${bookId.value.value}" }
        state.value = LoadState.Loading
        notifications.runCatchingToNotifications {
            if (book.value == null) {
                logger.debug { "[epub3-init] fetching book metadata" }
                startStep("Fetching book info")
                book.value = bookApi.getOne(bookId.value)
                completeLastStep()
            }

            logger.debug { "[epub3-init] preparing epub directory" }
            val extractedDir = prepareEpubDirectory()
            this.extractedDir = extractedDir
            logger.debug { "[epub3-init] epub directory ready: $extractedDir" }

            // One-time cleanup: delete stale overlay_clips.json cache from old builds
            withContext(Dispatchers.IO) {
                File(extractedDir, "overlay_clips.json").delete()
            }

            // Copy user fonts into the epub directory BEFORE openPublication() so that
            // DirectoryContainer.entries (built via root.walk() inside openPublication) includes them.
            val allFonts = fontsRepository.getAllFonts()
            copyFontsToEpubDir(allFonts)
            userFonts.value = allFonts

            logger.debug { "[epub3-init] opening publication" }
            startStep("Opening")
            val openResult = runCatching {
                withContext(Dispatchers.IO) {
                    BookService.openPublication(bookUuid, extractedDir.toURI().toURL(), clips = null)
                }
            }

            if (openResult.isFailure) {
                logger.warn { "[epub3-init] failed to open publication, attempting to clear cache and retry" }
                val retryDir = prepareEpubDirectory(forceRefresh = true)
                this.extractedDir = retryDir
                withContext(Dispatchers.IO) {
                    BookService.openPublication(bookUuid, retryDir.toURI().toURL(), clips = null)
                }
            }

            completeLastStep()
            logger.debug { "[epub3-init] publication opened" }
            tableOfContents.value = BookService.getPublication(bookUuid)?.tableOfContents ?: emptyList()

            val clips: List<OverlayPar> = BookService.getOverlayClips(bookUuid)

            startStep("Syncing")
            initialSync()
            completeLastStep()

            val r2Prog = bookApi.getReadiumProgression(bookId.value)
            if (r2Prog != null) {
                val href = Url(r2Prog.locator.href)
                if (href != null) {
                    savedLocator = Locator(
                        href = href,
                        mediaType = MediaType(r2Prog.locator.type) ?: MediaType.XHTML,
                        locations = Locator.Locations(
                            fragments = r2Prog.locator.locations?.fragment ?: emptyList(),
                            position = r2Prog.locator.locations?.position,
                            progression = r2Prog.locator.locations?.progression?.toDouble(),
                            totalProgression = r2Prog.locator.locations?.totalProgression?.toDouble(),
                        )
                    )
                }
            }
            logger.info { "[EPUB-DIAG] [INIT] BookID: ${bookId.value.value} | SavedLocator: ${savedLocator?.href}#${savedLocator?.locations?.fragments?.firstOrNull()}" }

            logger.debug { "[epub3-init] succeeded" }
            state.value = LoadState.Success(Unit)
            settings.value = epubSettingsRepository.getEpub3NativeSettings()
            coroutineScope.launch {
                val rt = Runtime.getRuntime()
                val pre = (rt.totalMemory() - rt.freeMemory()) / 1_048_576
                logger.info { "[epub3-diag] getPositions-coroutine START heap=${pre}MB clips=${clips.size}" }
                runCatching { positions.value = BookService.getPositions(bookUuid) }
                    .onFailure { logger.catching(it) }
                val post = (rt.totalMemory() - rt.freeMemory()) / 1_048_576
                logger.info { "[epub3-diag] getPositions-coroutine END heap=${post}MB delta=${post - pre}MB" }
            }

            coroutineScope.launch {
                epubBookmarkRepository.getBookmarks(bookId.value).collect {
                    bookmarks.value = it
                }
            }
            coroutineScope.launch {
                bookAnnotationRepository.getAnnotations(bookId.value).collect { list ->
                    annotations.value = list
                }
            }
            coroutineScope.launch {
                settingsRepository.getLastHighlightColor().collect { color ->
                    lastHighlightColor.value = color
                }
            }

            if (clips.isNotEmpty()) {
                coroutineScope.launch {
                    logger.debug { "[epub3-init] initializing media overlay controller in background (${clips.size} clips)" }
                    runCatching {
                        val controller = MediaOverlayController(context, coroutineScope, bookUuid, extractedDir)
                        controller.initialize(clips, savedLocator)
                        controller.applyAudioSettings(settings.value)
                        mediaOverlayController.value = controller
                        epubView?.let { view ->
                            (controller as? MediaOverlayController)?.let { smilController ->
                                smilController.attachView(view)
                                savedLocator?.let { smilController.handleUserLocatorChange(it) }
                            }
                        }
                        logger.debug { "[epub3-init] media overlay controller ready" }
                    }.onFailure { e ->
                        logger.error { "[epub3-init] media overlay controller FAILED: ${e::class.qualifiedName}: ${e.message}\n${e.stackTraceToString()}" }
                    }
                }
            } else {
                val audioFiles = AudiobookFolderController.detectAudioFiles(extractedDir)
                logger.info { "[epub3-init] folder detection: found ${audioFiles.size} audio files in $extractedDir" }
                if (audioFiles.isNotEmpty()) {
                    coroutineScope.launch {
                        logger.info { "[epub3-init] audiobook folder controller coroutine START (${audioFiles.size} files)" }
                        runCatching {
                            val controller = AudiobookFolderController(
                                context = context,
                                coroutineScope = coroutineScope,
                                bookUuid = bookUuid,
                                bookId = bookId.value,
                                extractedDir = extractedDir,
                                audioPositionRepository = audioPositionRepository,
                                audioBookmarkRepository = audioBookmarkRepository,
                                audioChapterRepository = audioChapterRepository,
                                onBookmarkChange = { coroutineScope.launch { updateCacheAndPush() } },
                                transcriptionSettingsRepository = transcriptionSettingsRepository,
                                whisperModelDownloader = whisperModelDownloader,
                    )
                    controller.initialize()
                            logger.info { "[epub3-init] audiobook folder controller initialize() completed" }
                            controller.applyAudioSettings(settings.value)
                            mediaOverlayController.value = controller
                            logger.info { "[epub3-init] audiobook folder controller READY — mediaOverlayController set" }
                        }.onFailure { e ->
                            logger.error { "[epub3-init] audiobook folder controller FAILED: ${e::class.qualifiedName}: ${e.message}\n${e.stackTraceToString()}" }
                        }
                    }
                }
            }
        }.onFailure { e ->
            logger.error { "[epub3-init] FAILED: ${e::class.qualifiedName}: ${e.message}\n${e.stackTraceToString()}" }
            state.value = LoadState.Error(e)
        }
    }

    fun onEpubViewCreated(view: EpubView) {
        logger.info { "[EPUB-DIAG] [VIEW-READY] SavedLocator: ${savedLocator?.href} | ControllerReady: ${mediaOverlayController.value != null}" }
        logger.info { "[komelia-epub] INIT: savedLocator=${savedLocator?.href} currentLocator=${currentLocator.value?.href}" }
        view.listener = object : EpubViewListener {
            override fun onRawLocatorChange(locator: Locator) {
                logger.info { "[EPUB-DIAG] [RAW-LOCATOR-CB] incoming=${locator.href}#${locator.locations.fragments.firstOrNull()}" }
                currentLocator.value = locator
            }

            override fun onLocatorChange(locator: Locator) {
                logger.info { "[EPUB-DIAG] [TEXT-MOVE] NewLocator: ${locator.href}#${locator.locations.fragments.firstOrNull()} | ControllerReady: ${mediaOverlayController.value != null}" }
                logger.info {
                    "[komelia-epub] LOCATOR-CB: incoming=${locator.href} title=${locator.title} " +
                    "currentLocator=${currentLocator.value?.href}"
                }
                savedLocator = locator
                // Keep EpubView.props.locator in sync with the current reading position.
                // emitCurrentLocator() uses props?.locator as a reference to decide whether
                // to suppress false locator emissions during WebView reflow (orientation change).
                // Without this, props.locator stays at the initial/server-fetched locator forever,
                // causing isPropLocatorOnPage=false on every reflow and corrupting savedLocator.
                view.props = view.props?.copy(locator = locator)
                currentLocator.value = locator
                (mediaOverlayController.value as? MediaOverlayController)?.handleUserLocatorChange(locator)
                if (!markReadProgress) return
                coroutineScope.launch {
                    val r2Prog = R2Progression(
                        modified = Clock.System.now(),
                        device = R2Device("komelia-android", "Komelia"),
                        locator = R2Locator(
                            href = locator.href.toString(),
                            type = locator.mediaType.toString(),
                            title = locator.title,
                            locations = R2Location(
                                fragment = locator.locations.fragments,
                                position = locator.locations.position,
                                progression = locator.locations.progression?.toFloat(),
                                totalProgression = locator.locations.totalProgression?.toFloat(),
                            ),
                            koboSpan = currentSyncBlob.value
                        )
                    )
                    runCatching { bookApi.updateReadiumProgression(bookId.value, r2Prog) }
                        .onFailure { logger.catching(it) }
                }
            }

            override fun onMiddleTouch() {
                if (showSettings.value || showContentDialog.value || showControls.value) {
                    showSettings.value = false
                    showContentDialog.value = false
                    showControls.value = false
                } else {
                    showControls.value = true
                }
            }

            override fun onDoubleTouch(locator: Locator) {
                // F2: double-tap → seek audio to that paragraph and play
                (mediaOverlayController.value as? MediaOverlayController)?.handleDoubleTap(locator)
            }

            override fun onSelection(locator: Locator, x: Int, y: Int) {
                pendingSelectionLocator.value = locator
                pendingSelectionX.value = x
                pendingSelectionY.value = y
                showAnnotationContextMenu.value = true
            }

            override fun onSelectionCleared() {
                showAnnotationContextMenu.value = false
                pendingSelectionLocator.value = null
            }

            override fun onHighlightTap(decorationId: String, x: Int, y: Int) {
                val annotation = annotations.value.find { it.id == decorationId }
                if (annotation != null) {
                    editingAnnotation.value = annotation
                    showAnnotationDialog.value = true
                }
            }
        }
        view.pendingProps.bookUuid = bookUuid
        view.pendingProps.locator = savedLocator
        view.finalizeProps()
        coroutineScope.launch {
            annotations.collect { list ->
                val highlights = list
                    .filter { it.location is AnnotationLocation.EpubLocation && it.highlightColor != null }
                    .mapNotNull { annotation ->
                        val loc = annotation.location as AnnotationLocation.EpubLocation
                        val locator = runCatching {
                            org.readium.r2.shared.publication.Locator.fromJSON(org.json.JSONObject(loc.locatorJson))
                        }.getOrNull()
                        locator?.let { Highlight(annotation.id, annotation.highlightColor!!, it) }
                    }
                epubView?.let { v ->
                    v.props = v.props?.copy(highlights = highlights)
                    v.decorateHighlights()
                }
            }
        }
        this.epubView = view
        currentLocator.value = savedLocator
        applySettingsToView(settings.value)
        (mediaOverlayController.value as? MediaOverlayController)?.attachView(view)
        // Pre-seed pendingUserLocator so first play starts from reading position,
        // not from audio track position 0, even if Readium hasn't fired onLocatorChange yet.
        savedLocator?.let { (mediaOverlayController.value as? MediaOverlayController)?.handleUserLocatorChange(it) }
    }

    /** No-op: this reader does not use a WebView. */
    override fun onWebviewCreated(webview: KomeliaWebview) = Unit

    override fun onBackButtonPress() = closeWebview()

    fun navigateToPosition(positionIndex: Int) {
        val locator = positions.value.getOrNull(positionIndex) ?: return
        logger.info {
            "[komelia-epub] NAV-POS: index=$positionIndex → locator=${locator.href} " +
            "currentLocator_before=${currentLocator.value?.href}"
        }
        epubView?.go(locator)
        (mediaOverlayController.value as? MediaOverlayController)?.handleUserLocatorChange(locator)
    }

    override fun closeWebview() {
        this.epubView = null
        mediaOverlayController.value?.release()
        mediaOverlayController.value = null
        if (platformType == PlatformType.MOBILE) windowState.setFullscreen(false)
        book.value?.let { onExit(it) }
        navigator.value?.let { nav ->
            if (nav.canPop) nav.pop()
            else {
                val screen = book.value
                    ?.let { bookScreen(book = it, bookSiblingsContext = bookSiblingsContext) }
                    ?: BookScreen(bookId = bookId.value, bookSiblingsContext = bookSiblingsContext)
                nav.replaceAll(MainScreen(screen))
            }
        }
    }

    /**
     * Downloads the EPUB zip (if not already cached) and extracts it to
     * `context.cacheDir/epub3/<bookUuid>/`.
     */
    private suspend fun prepareEpubDirectory(forceRefresh: Boolean = false): File {
        val epubCacheLimitMb = epubSettingsRepository.getEpubCacheSizeLimitMb().first()
        withContext(Dispatchers.IO) {
            trimEpubCache(epubCacheLimitMb * 1024 * 1024, context.cacheDir)
        }

        val extractedDir = File(context.cacheDir, "epub3/$bookUuid").also { it.mkdirs() }
        extractedDir.setLastModified(System.currentTimeMillis())
        val containerXml = File(extractedDir, "META-INF/container.xml")

        if (containerXml.exists() && !forceRefresh) {
            startStep("Loading from cache")
            completeLastStep()
        } else {
            withContext(Dispatchers.IO) {
                extractedDir.listFiles()?.forEach { it.deleteRecursively() }
            }
            val localPath = bookApi.getBookLocalFilePath(bookId.value)
            if (localPath != null) {
                // Offline: extract directly from already-local file — zero heap allocation
                startStep("Loading from local file")
                withContext(Dispatchers.IO) {
                    BookService.extractArchive(
                        URL("file://$localPath"),
                        extractedDir.toURI().toURL()
                    )
                }
                completeLastStep()
            } else {
                val isLocal = bookApi.hasLocalFile(bookId.value)
                startStep(if (isLocal) "Loading from local file" else "Downloading from server")
                val zipFile = File(context.cacheDir, "epub3/$bookUuid.epub")
                withContext(Dispatchers.IO) {
                    zipFile.outputStream().buffered().use { out ->
                        bookApi.downloadBookRawFile(bookId.value) { chunk -> out.write(chunk) }
                    }
                }
                completeLastStep()
                startStep("Extracting")
                withContext(Dispatchers.IO) {
                    BookService.extractArchive(
                        URL("file://${zipFile.absolutePath}"),
                        extractedDir.toURI().toURL()
                    )
                    zipFile.delete()
                }
                completeLastStep()
            }
        }
        return extractedDir
    }

    private fun trimEpubCache(limitBytes: Long, cacheDir: File) {
        val epub3Dir = File(cacheDir, "epub3")
        if (!epub3Dir.exists()) return

        val bookDirs = epub3Dir.listFiles { file -> file.isDirectory } ?: return

        // Calculate total size
        val dirSizes = bookDirs.associateWith { dir ->
            dir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
        }
        var totalSize = dirSizes.values.sum()

        if (totalSize <= limitBytes) return

        val sortedDirs = bookDirs.sortedBy { it.lastModified() }
        for (dir in sortedDirs) {
            dir.deleteRecursively()
            totalSize -= dirSizes[dir] ?: 0L
            if (totalSize <= limitBytes) break
        }
    }
}
