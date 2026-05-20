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
import kotlinx.coroutines.flow.MutableStateFlow
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
        val appRepositories = createAppRepositories()
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

        val komgaApi = isOffline.map { offline ->
            val source = if (offline) offlineModule.komgaApi
            else createRemoteApi(
                komgaClientFactory = komgaClientFactory,
                offlineRepositories = offlineRepositories,
                offlineEvents = offlineModule.komgaEvents
            )
            source.withStatsTracking(
                readingEvents = appRepositories.readingEventsRepository,
                statsEnabled = statsEnabledFlow,
            )
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
            )
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
            backupService = createBackupService(appRepositories),
            readerSyncService = readerSyncService,

            komgaApi = komgaApi,
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
            onBookChange = createOnBookChange(),
            onEpubCacheClear = createOnEpubCacheClear(),
            localFileApiProvider = localFileApiProvider,
        )
    }

    protected open fun createOnBookChange(): () -> Unit = {}

    protected open fun createOnEpubCacheClear(): () -> Unit = {}

    protected open fun createLocalFileApiProvider(): LocalFileApiProvider? = null

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
            diskCache?.clear()
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
    ): ImageProcessingPipeline {
        val pipeline = ImageProcessingPipeline()
        pipeline.addStep(colorCorrectionStep)

        pipeline.addStep(CropBordersStep(cropBorders, autoSkipBlankPages, blankPageDetector))
        pipeline.addStep(SplitPageStep())
        return pipeline
    }


    protected abstract suspend fun createAppRepositories(): AppRepositories
    protected abstract suspend fun createOfflineRepositories(): OfflineRepositories

    /**
     * Build the platform-specific [BackupService]. Android reaches into the
     * wrapper classes to snapshot in-memory state; other platforms may
     * return a no-op implementation if the feature is unsupported there.
     */
    protected abstract fun createBackupService(repositories: AppRepositories): BackupService

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
