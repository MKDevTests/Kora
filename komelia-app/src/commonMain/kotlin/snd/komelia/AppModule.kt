package snd.komelia

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.io.files.Path
import kotlinx.serialization.json.Json
import okio.Path.Companion.toPath
import snd.komelia.api.RemoteActuatorApi
import snd.komelia.api.RemoteAnnouncementsApi
import snd.komelia.api.RemoteApi
import snd.komelia.api.RemoteBookApi
import snd.komelia.api.RemoteCollectionsApi
import snd.komelia.api.RemoteFileSystemApi
import snd.komelia.api.RemoteLibraryApi
import snd.komelia.api.RemoteReadListApi
import snd.komelia.api.RemoteReferentialApi
import snd.komelia.api.RemoteSeriesApi
import snd.komelia.api.RemoteSettingsApi
import snd.komelia.api.RemoteTaskApi
import snd.komelia.api.RemoteUserApi
import snd.komelia.backup.BackupService
import snd.komelia.http.RememberMePersistingCookieStore
import snd.komelia.image.BookImageLoader
import snd.komelia.image.KomeliaImageDecoder
import snd.komelia.image.KomeliaPanelDetector
import snd.komelia.image.KomeliaUpscaler
import snd.komelia.image.ReaderImageFactory
import snd.komelia.image.coil.CoilAwareDecoder
import snd.komelia.image.coil.CoilDecoder
import snd.komelia.image.coil.FileMapper
import snd.komelia.image.coil.KomeliaFetcherFactory
import snd.komelia.image.processing.ColorCorrectionStep
import snd.komelia.image.processing.CropBordersStep
import snd.komelia.image.processing.SplitPageStep
import snd.komelia.image.processing.ImageProcessingPipeline
import snd.komelia.komga.api.KomgaApi
import snd.komelia.komga.api.KomgaBookApi
import snd.komelia.komga.api.LocalFileApiProvider
import snd.komelia.offline.OfflineDependencies
import snd.komelia.offline.OfflineModule
import snd.komelia.offline.OfflineRepositories
import snd.komelia.offline.book.repository.OfflineBookRepository
import snd.komelia.onnxruntime.OnnxRuntime
import snd.komelia.settings.ImageReaderSettingsRepository
import snd.komelia.stats.withStatsTracking
import snd.komelia.ignore.withIgnoreFilter
import snd.komelia.hidden.HiddenSeriesController
import snd.komelia.ui.DependencyContainer
import snd.komelia.ui.strings.EnStrings
import snd.komelia.updates.AppUpdater
import snd.komelia.updates.OnnxModelDownloader
import snd.komelia.updates.OnnxRuntimeInstaller
import snd.komelia.updates.RapidOcrModelDownloader
import snd.komelia.updates.UpdateClient
import snd.komelia.updates.WhisperModelDownloader
import snd.komf.client.KomfClientFactory
import snd.komga.client.KomgaClientFactory
import snd.komga.client.sse.KomgaEvent
import snd.komga.client.user.KomgaUser
import kotlin.time.measureTimedValue

import snd.komelia.sync.ReaderSyncService

private val logger = KotlinLogging.logger { }

/**
 * Filename of the speech-bubble detector inside the ONNX models directory
 * (ogkalu/comic-text-and-bubble-detector, RT-DETR, Apache-2.0, ~11 MB).
 * Absent = bubble inversion silently stays off.
 */
const val BUBBLE_DETECTOR_MODEL = "comic-bubble-detector.onnx"

abstract class AppModule(
    val serverId: Long? = null
) {
    protected val initScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    protected val appNotifications = AppNotifications()
    protected val readerSyncService = ReaderSyncService()
    protected var ktor: HttpClient? = null
    protected var ktorWithoutCache: HttpClient? = null
    protected var coil: ImageLoader? = null
    private var offlineModuleRef: OfflineModule? = null

    suspend fun initDependencies(): DependencyContainer {
        beforeInit()

        // Hoisted up so the repos that need to tag rows with the current
        // user's id (reading_events, series_ratings) can read .value at
        // every write. The flow is populated below once currentUserFlow
        // is wired (around the auth section), and re-evaluates whenever
        // the user signs in / out.
        val currentUserIdFlow = MutableStateFlow<snd.komga.client.user.KomgaUserId?>(null)

        val appRepositories = createAppRepositories(currentUserIdFlow)
        val offlineRepositories = createOfflineRepositories()
        val ktor = createKtorClient()
        val ktorWithoutCache = createKtorClientWithoutCache()
        this.ktor = ktor
        this.ktorWithoutCache = ktorWithoutCache

        val updateClient = UpdateClient(
            ktor = ktor.config {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            },
            ktorWithoutCache = ktorWithoutCache.config {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            }
        )

        val baseUrl = appRepositories.settingsRepository.getServerUrl().stateIn(initScope)
        val komfUrl = appRepositories.komfSettingsRepository.getKomfUrl().stateIn(initScope)

        val cookiesStorage = RememberMePersistingCookieStore(
            baseUrl.map { Url(it) }.stateIn(initScope),
            appRepositories.secretsRepository
        )
        cookiesStorage.loadRememberMeCookie()

        val komgaClientFactory = KomgaClientFactory.Builder()
            .ktor(ktor)
            .baseUrl { baseUrl.value }
            .cookieStorage(cookiesStorage)
            .build()

        val komgaClientFactoryNoCache = KomgaClientFactory.Builder()
            .ktor(ktor)
            .baseUrl { baseUrl.value }
            .cookieStorage(cookiesStorage)
            .build()

        val komfClientFactory = KomfClientFactory.Builder()
            .baseUrl { komfUrl.value }
            .ktor(ktor)
            .build()

        val imageDecoder = createImageDecoder()

        val isOffline = offlineRepositories.offlineSettingsRepository.getOfflineMode().stateIn(initScope)
        val currentUserFlow = MutableStateFlow<KomgaUser?>(null)
        val currentServerUrl = appRepositories.settingsRepository.getServerUrl().stateIn(initScope)

        // Wire the user-id flow that user-tagged repos read at write time.
        // Collector lifecycle: tied to initScope, so it survives for the
        // app lifetime and re-fires on sign-in / sign-out. Backfill of
        // legacy (pre-v1.0.10) NULL rows happens once on first non-null
        // emission via UserScopeBackfillJob below.
        initScope.launch {
            currentUserFlow.collect { user -> currentUserIdFlow.value = user?.id }
        }
        initScope.launch {
            UserScopeBackfillJob(
                readingEvents = appRepositories.readingEventsRepository,
                seriesRatings = appRepositories.seriesRatingsRepository,
                currentUserId = currentUserIdFlow,
            ).run()
        }

        val androidContext = createCoilContext()
        val offlineModuleInstance = createOfflineModule(
            repositories = offlineRepositories,
            komgaClientFactory = komgaClientFactory,
            onlineUser = currentUserFlow
                .combine(isOffline) { user, isOffline -> if (isOffline) null else user }
                .stateIn(initScope),
            onlineServerUrl = appRepositories.settingsRepository.getServerUrl().stateIn(initScope),
            isOffline = isOffline,
        )
        offlineModuleRef = offlineModuleInstance
        val offlineModule: OfflineDependencies = offlineModuleInstance.initDependencies()

        // Toggle gating the Reading Stats completion-event log. Read once at
        // module init and exposed as a StateFlow so the decorators below can
        // poll synchronously without re-collecting on every `markReadProgress`.
        val statsEnabledFlow = appRepositories.settingsRepository
            .getStatsEnabled()
            .stateIn(initScope)

        // Single in-process broadcast of book completions used by the
        // "Just finished?" modal. Shared between every wrapped KomgaApi
        // (online / no-cache / offline / local-file) so all completion
        // paths converge to the same listener at the UI layer.
        val bookCompletionEvents = snd.komelia.stats.BookCompletionEvents()

        // Experimental local Ignore List: empty (pass-through) unless the
        // feature is enabled AND the user has ignored some series.
        val ignoredSeriesFlow = combine(
            appRepositories.settingsRepository.getIgnoreListEnabled(),
            appRepositories.settingsRepository.getIgnoredSeriesIds(),
        ) { enabled, ids -> if (enabled) ids else emptySet() }.stateIn(initScope)

        // Raw (undecorated) api the hidden-series discovery query runs against —
        // the ignore/hidden filter would otherwise drop kora:hidden series from
        // its own lookup. The app-facing decorated api is derived from this below.
        val rawKomgaApi: StateFlow<KomgaApi> = isOffline.map { offline ->
            if (offline) offlineModule.komgaApi
            else createRemoteApi(
                komgaClientFactory = komgaClientFactory,
                offlineRepositories = offlineRepositories,
                offlineEvents = offlineModule.komgaEvents
            )
        }.stateIn(initScope)

        // Admin "hide for everyone": series carrying the kora:hidden tag are
        // filtered out for every Kora user, unconditionally. Re-queried on sign-in.
        val hiddenSeriesController = HiddenSeriesController(
            rawApi = rawKomgaApi,
            authenticatedUser = currentUserFlow,
            scope = initScope,
            cacheKey = serverId?.toString() ?: "default",
        )

        // Series removed from every list response = locally ignored ∪ server-hidden.
        val filterIds = combine(ignoredSeriesFlow, hiddenSeriesController.hiddenIds) { ignored, hidden ->
            ignored + hidden
        }.stateIn(initScope)

        val komgaApi = rawKomgaApi.map { source ->
            source.withStatsTracking(
                readingEvents = appRepositories.readingEventsRepository,
                statsEnabled = statsEnabledFlow,
                completionEvents = bookCompletionEvents,
            ).withIgnoreFilter(filterIds)
        }.stateIn(initScope)

        val komgaNoRemoteCacheApi = isOffline.map { offline ->
            val source = if (offline) offlineModule.komgaApi
            else createRemoteApi(
                komgaClientFactory = komgaClientFactoryNoCache,
                offlineRepositories = offlineRepositories,
                offlineEvents = offlineModule.komgaEvents
            )
            source.withStatsTracking(
                readingEvents = appRepositories.readingEventsRepository,
                statsEnabled = statsEnabledFlow,
                completionEvents = bookCompletionEvents,
            ).withIgnoreFilter(filterIds)
        }.stateIn(initScope)

        val komgaSharedState = KomgaAuthenticationState(
            userApi = komgaApi.map { it.userApi }.stateIn(initScope),
            libraryApi = komgaApi.map { it.libraryApi }.stateIn(initScope),
            currentUserFlow = currentUserFlow,
            serverUrl = currentServerUrl
        )


        val colorCorrectionStep = ColorCorrectionStep(appRepositories.bookColorCorrectionRepository)
        val blankPageDetector = snd.komelia.image.processing.BlankPageDetector()
        val imagePipeline = createImagePipeline(
            cropBorders = appRepositories.imageReaderSettingsRepository.getCropBorders().stateIn(initScope),
            colorCorrectionStep = colorCorrectionStep,
            autoSkipBlankPages = appRepositories.imageReaderSettingsRepository.getPagedAutoSkipBlankPages().stateIn(initScope),
            blankPageDetector = blankPageDetector,
            invertSpeechBubbles = appRepositories.imageReaderSettingsRepository.getInvertSpeechBubbles(),
        )
        val onnxRuntimeInstaller = createOnnxRuntimeInstaller(updateClient)
        val onnxModelDownloader = createOnnxModelDownloader(updateClient)
        val whisperModelDownloader = createWhisperModelDownloader(updateClient)
        val rapidOcrModelDownloader = createRapidOcrModelDownloader(updateClient)
        val onnxRuntime = createOnnxRuntime()

        val upscaler = if (onnxRuntime != null && onnxModelDownloader != null) {
            createUpscaler(
                onnxRuntime,
                onnxModelDownloader,
                appRepositories.imageReaderSettingsRepository
            )
        } else null

        val panelDetector = if (onnxRuntime != null && onnxModelDownloader != null) {
            createPanelDetector(
                onnxRuntime,
                onnxModelDownloader,
                appRepositories.imageReaderSettingsRepository
            )
        } else null

        val localFileApiProvider = createLocalFileApiProvider()?.withStatsTracking(
            readingEvents = appRepositories.readingEventsRepository,
            statsEnabled = statsEnabledFlow,
            completionEvents = bookCompletionEvents,
        )

        val coil = createCoil(
            komgaApi = komgaApi,
            context = androidContext,
            decoder = imageDecoder,
            offlineBookRepository = offlineRepositories.bookRepository,
            offlineBookApi = offlineModule.komgaApi.bookApi,
            localFileApiProvider = localFileApiProvider,
        )
        this.coil = coil

        val komgaEvents = ManagedKomgaEvents(
            komgaApi = komgaApi,
            memoryCache = coil.memoryCache,
            diskCache = coil.diskCache,
            libraryApi = komgaApi.map { it.libraryApi },
            komgaSharedState = komgaSharedState
        )

        val readerImageFactory = createReaderImageFactory(
            imageDecoder = imageDecoder,
            pipeline = imagePipeline,
            settings = appRepositories.imageReaderSettingsRepository,
            onnxRuntimeUpscaler = upscaler,
            onnxModelDownloader = onnxModelDownloader
        )

        return DependencyContainer(
            appStrings = MutableStateFlow(EnStrings),
            appRepositories = appRepositories,
            backupService = createBackupService(appRepositories, currentUserFlow),
            readerSyncService = readerSyncService,

            komgaApi = komgaApi,
            hiddenSeriesController = hiddenSeriesController,
            isOffline = isOffline,
            komfClientFactory = komfClientFactory,
            appNotifications = appNotifications,
            komgaSharedState = komgaSharedState,
            komgaEvents = komgaEvents,
            appUpdater = createAppUpdater(updateClient),
            releaseNotesService = snd.komelia.updates.ReleaseNotesService(
                updateClient = updateClient,
                settingsRepository = appRepositories.settingsRepository,
            ),
            aniListClient = snd.komelia.anilist.AniListClient(
                ktor = ktor.config { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } },
            ),
            bookCompletionEvents = bookCompletionEvents,

            coilContext = androidContext,
            coilImageLoader = coil,
            imageDecoder = imageDecoder,
            bookImageLoader = createReaderImageLoader(
                bookApi = komgaNoRemoteCacheApi.map { it.bookApi }.stateIn(initScope),
                imageFactory = readerImageFactory,
                imageDecoder = createImageDecoder(),
                offlineBookRepository = offlineRepositories.bookRepository,
                offlineBookApi = offlineModule.komgaApi.bookApi,
                cacheSizeLimitMb = appRepositories.imageReaderSettingsRepository.getImageCacheSizeLimitMb().first(),
                localFileApiProvider = localFileApiProvider,
            ),
            readerImageFactory = readerImageFactory,
            ocrService = snd.komelia.image.OcrService(),
            windowState = createWindowState(),
            colorCorrectionStep = colorCorrectionStep,
            blankPageDetector = blankPageDetector,
            onnxRuntimeInstaller = onnxRuntimeInstaller,
            onnxModelDownloader = onnxModelDownloader,
            whisperModelDownloader = whisperModelDownloader,
            rapidOcrModelDownloader = rapidOcrModelDownloader,
            onnxRuntime = onnxRuntime,
            upscaler = upscaler,
            panelDetector = panelDetector,
            offlineDependencies = offlineModule,
            nextBookService = snd.komelia.nextbook.NextBookService(komgaApi),
            toolkitApi = snd.komelia.toolkit.ToolkitApi(ktor, createToolkitConfigProvider()),
            widgetBookToOpenFlow = createWidgetBookToOpenFlow(komgaApi),
            onBookChange = createOnBookChange(),
            onEpubCacheClear = createOnEpubCacheClear(),
            localFileApiProvider = localFileApiProvider,
            runAutobackupNow = createRunAutobackupNow(),
            extractPersistableFolderUri = createPersistableFolderUriExtractor(),
            diagnostics = createDiagnosticsDataSource(coil),
        )
    }

    /**
     * Provides the Toolkit base URL + bearer token, or null when unset. Read
     * fresh on every automation call so a settings change takes effect at once.
     * Android reads an encrypted store; other platforms have no Toolkit UI.
     */
    protected open fun createToolkitConfigProvider(): () -> snd.komelia.toolkit.ToolkitConfig? = { null }

    protected open fun createOnBookChange(): () -> Unit = {}

    protected open fun createOnEpubCacheClear(): () -> Unit = {}

    protected open fun createLocalFileApiProvider(): LocalFileApiProvider? = null

    protected open fun createRunAutobackupNow(): () -> Unit = {}

    /**
     * Platform diagnostics data source for the Diagnostics screen. Defaults to
     * a no-op; only Android provides a real implementation. [coilImageLoader]
     * is the shared loader, used to clear the image cache safely.
     */
    protected open fun createDiagnosticsDataSource(
        coilImageLoader: coil3.ImageLoader,
    ): snd.komelia.ui.settings.diagnostics.DiagnosticsDataSource =
        snd.komelia.ui.settings.diagnostics.EmptyDiagnosticsDataSource

    protected open fun createPersistableFolderUriExtractor(): (io.github.vinceglb.filekit.PlatformFile) -> String? = { null }

    /**
     * Resolves "open this book" requests from the home-screen widget into
     * a stream of [snd.komelia.komga.api.model.KomeliaBook]s ready to be
     * pushed onto the reader. Null on platforms without the widget.
     */
    protected open fun createWidgetBookToOpenFlow(
        komgaApi: StateFlow<snd.komelia.komga.api.KomgaApi>,
    ): SharedFlow<snd.komelia.komga.api.model.KomeliaBook>? = null

    protected open suspend fun beforeInit() = Unit

    protected fun createRemoteApi(
        komgaClientFactory: KomgaClientFactory,
        offlineRepositories: OfflineRepositories,
        offlineEvents: SharedFlow<KomgaEvent>,
    ) = RemoteApi(
        actuatorApi = RemoteActuatorApi(komgaClientFactory.actuatorClient()),
        announcementsApi = RemoteAnnouncementsApi(komgaClientFactory.announcementClient()),
        bookApi = RemoteBookApi(
            bookClient = komgaClientFactory.bookClient(),
            offlineBookRepository = offlineRepositories.bookRepository
        ),
        collectionsApi = RemoteCollectionsApi(komgaClientFactory.collectionClient()),
        fileSystemApi = RemoteFileSystemApi(komgaClientFactory.fileSystemClient()),
        libraryApi = RemoteLibraryApi(komgaClientFactory.libraryClient()),
        readListApi = RemoteReadListApi(
            readListClient = komgaClientFactory.readListClient(),
            offlineBookRepository = offlineRepositories.bookRepository
        ),
        referentialApi = RemoteReferentialApi(komgaClientFactory.referentialClient()),
        seriesApi = RemoteSeriesApi(komgaClientFactory.seriesClient()),
        settingsApi = RemoteSettingsApi(komgaClientFactory.settingsClient()),
        tasksApi = RemoteTaskApi(komgaClientFactory.taskClient()),
        userApi = RemoteUserApi(komgaClientFactory.userClient()),
        komgaClientFactory = komgaClientFactory,
        offlineEvents = offlineEvents
    )

    protected fun createCoil(
        komgaApi: StateFlow<KomgaApi>,
        context: PlatformContext,
        decoder: KomeliaImageDecoder,
        offlineBookRepository: OfflineBookRepository? = null,
        offlineBookApi: KomgaBookApi? = null,
        localFileApiProvider: LocalFileApiProvider? = null,
    ): ImageLoader {

        val timed = measureTimedValue {
            val diskCache = getCoilCacheDirectory()?.let { kotlinxPath ->
                DiskCache.Builder()
                    // kotlinx -> okio path
                    .directory(kotlinxPath.toString().toPath())
                    .build()
            }
            // NOTE: upstream cleared this disk cache here on every startup
            // (0e8727a3 "offline mode") — almost certainly a debug leftover: it
            // built a disk cache and immediately wiped it, demoting it to a
            // session-only cache. Every cold start re-downloaded every cover from
            // Komga. On this fork that meant Home painting its 11 shelves from the
            // local snapshot in ~145ms and then sitting on grey placeholders for
            // seconds while ~200 covers were re-fetched over a 5-connection cap.
            // The reader's own disk cache (createReaderImageLoader below) was never
            // cleared — the asymmetry is what gives the accident away.
            // Users can still wipe it on demand via Settings -> Clear image cache.
            val coilAwareDecoder = CoilAwareDecoder(decoder)

            ImageLoader.Builder(context)
                .components {
                    add(FileMapper())
                    add(CoilDecoder.Factory(coilAwareDecoder))
                    add(KomeliaFetcherFactory(
                        komgaApi,
                        coilAwareDecoder,
                        offlineBookRepository = offlineBookRepository,
                        offlineBookApi = offlineBookApi,
                        localFileApiProvider = localFileApiProvider,
                    ))
                }
                .memoryCache(createCoilMemoryCache())
                .diskCache { diskCache }
                .build()
                .also { loader -> SingletonImageLoader.setUnsafe(loader) }
        }
        logger.info { "initialized Coil in ${timed.duration}" }
        return timed.value
    }

    protected fun createReaderImageLoader(
        bookApi: StateFlow<KomgaBookApi>,
        imageFactory: ReaderImageFactory,
        imageDecoder: KomeliaImageDecoder,
        offlineBookRepository: OfflineBookRepository,
        offlineBookApi: KomgaBookApi,
        cacheSizeLimitMb: Long,
        localFileApiProvider: LocalFileApiProvider? = null,
    ): BookImageLoader {
        val diskCache = getReaderCacheDirectory()?.let { kotlinxPath ->
            DiskCache.Builder()
                .directory(kotlinxPath.toString().toPath())
                .maxSizeBytes(cacheSizeLimitMb * 1024 * 1024)
                .build()
        }
        return BookImageLoader(
            bookClient = bookApi,
            readerImageFactory = imageFactory,
            imageDecoder = imageDecoder,
            diskCache = diskCache,
            offlineBookRepository = offlineBookRepository,
            offlineBookApi = offlineBookApi,
            localFileApiProvider = localFileApiProvider,
        )
    }

    protected fun createImagePipeline(
        cropBorders: StateFlow<Boolean>,
        colorCorrectionStep: ColorCorrectionStep,
        autoSkipBlankPages: StateFlow<Boolean>,
        blankPageDetector: snd.komelia.image.processing.BlankPageDetector,
        invertSpeechBubbles: Flow<Boolean>,
    ): ImageProcessingPipeline {
        val pipeline = ImageProcessingPipeline()
        pipeline.addStep(colorCorrectionStep)

        pipeline.addStep(CropBordersStep(cropBorders, autoSkipBlankPages, blankPageDetector))
        pipeline.addStep(SplitPageStep())
        // MUST stay last: on Android this hands back a Bitmap-backed image, and
        // AndroidBitmapBackedImage.mapLookupTable throws — so it has to run after
        // ColorCorrectionStep, the only step that calls it.
        pipeline.addStep(
            snd.komelia.image.processing.BubbleInvertStep(invertSpeechBubbles) {
                getOnnxModelsDirectoryPath()?.let { "$it/$BUBBLE_DETECTOR_MODEL" }
            }
        )
        return pipeline
    }


    /**
     * @param currentUserId polled by user-tagged repositories (reading_events,
     *   series_ratings) at write time. The platform module just forwards this
     *   into the repo constructors that need it.
     */
    protected abstract suspend fun createAppRepositories(
        currentUserId: StateFlow<snd.komga.client.user.KomgaUserId?>,
    ): AppRepositories
    protected abstract suspend fun createOfflineRepositories(): OfflineRepositories

    /**
     * Build the platform-specific [BackupService]. Android reaches into the
     * wrapper classes to snapshot in-memory state; other platforms may
     * return a no-op implementation if the feature is unsupported there.
     */
    protected abstract fun createBackupService(
        repositories: AppRepositories,
        currentUser: StateFlow<KomgaUser?>,
    ): BackupService

    protected abstract fun createKtorClient(): HttpClient
    protected abstract fun createKtorClientWithoutCache(): HttpClient

    protected abstract fun createAppUpdater(updateClient: UpdateClient): AppUpdater?

    protected abstract fun createImageDecoder(): KomeliaImageDecoder
    protected abstract suspend fun createReaderImageFactory(
        imageDecoder: KomeliaImageDecoder,
        pipeline: ImageProcessingPipeline,
        settings: ImageReaderSettingsRepository,
        onnxRuntimeUpscaler: KomeliaUpscaler?,
        onnxModelDownloader: OnnxModelDownloader?,
    ): ReaderImageFactory

    protected abstract fun createWindowState(): AppWindowState
    protected abstract fun createCoilContext(): PlatformContext
    protected abstract fun createOnnxRuntimeInstaller(updateClient: UpdateClient): OnnxRuntimeInstaller?
    protected abstract fun createOnnxModelDownloader(updateClient: UpdateClient): OnnxModelDownloader?
    protected abstract fun createWhisperModelDownloader(updateClient: UpdateClient): WhisperModelDownloader?
    protected abstract fun createRapidOcrModelDownloader(updateClient: UpdateClient): RapidOcrModelDownloader?
    protected abstract fun createOnnxRuntime(): OnnxRuntime?
    protected abstract suspend fun createUpscaler(
        onnxRuntime: OnnxRuntime,
        modelDownloader: OnnxModelDownloader,
        settings: ImageReaderSettingsRepository,
    ): KomeliaUpscaler?

    protected abstract suspend fun createPanelDetector(
        onnxRuntime: OnnxRuntime,
        modelDownloader: OnnxModelDownloader,
        settings: ImageReaderSettingsRepository,
    ): KomeliaPanelDetector?

    /**
     * Directory holding downloaded ONNX models (the panel detector lives here
     * too), or null on platforms that don't ship them. Used to locate the
     * speech-bubble detector; a missing file just disables bubble inversion.
     */
    protected open fun getOnnxModelsDirectoryPath(): String? = null

    protected abstract fun getCoilCacheDirectory(): Path?
    protected abstract fun createCoilMemoryCache(): MemoryCache?
    protected abstract fun getReaderCacheDirectory(): Path?

    abstract fun createOfflineModule(
        repositories: OfflineRepositories,
        onlineUser: StateFlow<KomgaUser?>,
        onlineServerUrl: StateFlow<String>,
        isOffline: StateFlow<Boolean>,
        komgaClientFactory: KomgaClientFactory,
    ): OfflineModule

    open suspend fun close() {
        offlineModuleRef?.close()
        initScope.cancel()
        initScope.coroutineContext[Job]?.join()
        ktor?.close()
        ktorWithoutCache?.close()
        coil?.shutdown()
    }
    }
