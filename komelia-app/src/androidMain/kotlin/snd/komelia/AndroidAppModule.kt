package snd.komelia

import android.app.Activity
import android.content.Context
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.dataStoreFile
import coil3.memory.MemoryCache
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.snd_r.komelia.BuildConfig
import io.github.vinceglb.filekit.PlatformFile
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.stateIn
import kotlinx.io.files.Path
import okhttp3.Cache
import okhttp3.OkHttpClient
import io.github.snd_r.komelia.infra.ncnn.NcnnSharedLibraries
import snd.komelia.backup.BackupService
import snd.komelia.backup.DefaultBackupService
import snd.komelia.db.AppSettings
import snd.komelia.db.EpubReaderSettings
import snd.komelia.db.ExposedTransactionTemplate
import snd.komelia.db.ImageReaderSettings
import snd.komelia.db.KomeliaDatabase
import snd.komelia.db.KomfSettings
import snd.komelia.db.OfflineSettings
import snd.komelia.db.SettingsStateWrapper
import snd.komelia.db.color.ExposedBookColorCorrectionRepository
import snd.komelia.db.color.ExposedColorCurvesPresetRepository
import snd.komelia.db.color.ExposedColorLevelsPresetRepository
import snd.komelia.db.fonts.ExposedUserFontsRepository
import snd.komelia.db.homescreen.ExposedHomeScreenFilterRepository
import snd.komelia.db.offline.ExposedLogJournalRepository
import snd.komelia.db.offline.ExposedMediaRepository
import snd.komelia.db.offline.ExposedOfflineBookMetadataAggregationRepository
import snd.komelia.db.offline.ExposedOfflineBookMetadataRepository
import snd.komelia.db.offline.ExposedOfflineBookRepository
import snd.komelia.db.offline.ExposedOfflineLibraryRepository
import snd.komelia.db.offline.ExposedOfflineMediaServerRepository
import snd.komelia.db.offline.ExposedOfflineReadProgressRepository
import snd.komelia.db.offline.ExposedOfflineSeriesMetadataRepository
import snd.komelia.db.offline.ExposedOfflineSeriesRepository
import snd.komelia.db.offline.ExposedOfflineSettingsRepository
import snd.komelia.db.offline.ExposedOfflineTasksRepository
import snd.komelia.db.offline.ExposedOfflineThumbnailBookRepository
import snd.komelia.db.offline.ExposedOfflineThumbnailSeriesRepository
import snd.komelia.db.offline.ExposedOfflineUserRepository
import snd.komelia.db.offline.dto.ExposedOfflineBookDtoRepository
import snd.komelia.db.offline.dto.ExposedOfflineReferentialRepository
import snd.komelia.db.offline.dto.ExposedSeriesDtoRepository
import snd.komelia.db.repository.EpubReaderSettingsRepositoryWrapper
import snd.komelia.db.repository.HomeScreenFilterRepositoryWrapper
import snd.komelia.db.repository.KomfSettingsRepositoryWrapper
import snd.komelia.db.repository.OfflineSettingsRepositoryWrapper
import snd.komelia.db.repository.ReaderSettingsRepositoryWrapper
import snd.komelia.db.repository.SettingsRepositoryWrapper
import snd.komelia.db.repository.TranscriptionSettingsRepositoryWrapper
import snd.komelia.db.settings.ExposedEpubReaderSettingsRepository
import snd.komelia.db.settings.ExposedImageReaderSettingsRepository
import snd.komelia.db.settings.ExposedKomfSettingsRepository
import snd.komelia.db.settings.ExposedSettingsRepository
import snd.komelia.db.settings.ExposedTranscriptionSettingsRepository
import snd.komelia.fonts.fontsDirectory
import snd.komelia.homefilters.homeScreenDefaultFilters
import snd.komelia.http.komeliaUserAgent
import snd.komelia.komga.api.LocalFileApiProvider
import snd.komelia.localfile.LocalFileApiProviderImpl
import snd.komelia.db.localfile.LocalFileReadProgressRepository
import snd.komelia.image.AndroidNcnnUpscaler
import snd.komelia.image.AndroidPanelDetector
import snd.komelia.image.AndroidReaderImageFactory
import snd.komelia.image.KomeliaImageDecoder
import snd.komelia.image.KomeliaPanelDetector
import snd.komelia.image.KomeliaUpscaler
import snd.komelia.image.ReaderImageFactory
import snd.komelia.image.UpsamplingMode
import snd.komelia.image.VipsImageDecoder
import snd.komelia.image.VipsSharedLibrariesLoader
import snd.komelia.image.processing.ImageProcessingPipeline
import snd.komelia.offline.AndroidOfflineModule
import snd.komelia.offline.OfflineModule
import snd.komelia.offline.OfflineRepositories
import snd.komelia.onnxruntime.JvmOnnxRuntime
import snd.komelia.onnxruntime.JvmOnnxRuntimeRfDetr
import snd.komelia.onnxruntime.OnnxRuntime
import snd.komelia.onnxruntime.OnnxRuntimeExecutionProvider
import snd.komelia.onnxruntime.OnnxRuntimeSharedLibraries
import snd.komelia.settings.AndroidSecretsRepository
import snd.komelia.settings.AppSettingsSerializer
import snd.komelia.settings.ImageReaderSettingsRepository
import snd.komelia.updates.AndroidAppUpdater
import snd.komelia.updates.AndroidOnnxModelDownloader
import snd.komelia.updates.AndroidRapidOcrModelDownloader
import snd.komelia.updates.AndroidWhisperModelDownloader
import snd.komelia.updates.AppUpdater
import snd.komelia.updates.OnnxModelDownloader
import snd.komelia.updates.RapidOcrModelDownloader
import snd.komelia.updates.UpdateClient
import snd.komelia.updates.WhisperModelDownloader
import snd.komga.client.KomgaClientFactory
import snd.komga.client.user.KomgaUser
import java.util.concurrent.TimeUnit
import kotlin.io.path.createDirectories
import kotlin.time.measureTime

private val logger = KotlinLogging.logger { }

class AndroidAppModule(
    private val context: Context,
    private val mainActivity: StateFlow<Activity?>,
    serverId: Long? = null
) : AppModule(serverId) {
    private var ncnnUpscaler: AndroidNcnnUpscaler? = null
    private val databases = KomeliaDatabase(context.filesDir.absolutePath.toString(), serverId)

    private val okHttpLogger = KotlinLogging.logger("http.logging")
    private val okHttpClientWithoutCache: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(0, TimeUnit.SECONDS)
        .writeTimeout(0, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
//        .addInterceptor(HttpLoggingInterceptor { okHttpLogger.info { it } }
//            .setLevel(HttpLoggingInterceptor.Level.BASIC))
        .build()
    private val okHttpClient = okHttpClientWithoutCache.newBuilder().cache(
        Cache(
            directory = context.cacheDir.resolve("okhttp").let { if (serverId != null) it.resolve("server_$serverId") else it },
            maxSize = 64 * 1024L * 1024L // 64 MiB
        )
    ).build()

    override suspend fun beforeInit() {
        measureTime {
            try {
                VipsSharedLibrariesLoader.load()
            } catch (e: UnsatisfiedLinkError) {
                logger.error(e) { "Couldn't load vips shared libraries. reader image loading will not work" }
            }
        }.also { logger.info { "completed vips libraries load in $it" } }

        try {
            OnnxRuntimeSharedLibraries.load()
        } catch (e: UnsatisfiedLinkError) {
            logger.error(e) { "Failed to load onnxruntime " }
        }

        NcnnSharedLibraries.load()
        snd.komelia.image.OcrService.context = context

        fontsDirectory = Path(context.filesDir.resolve("fonts").absolutePath)
    }


    override suspend fun createAppRepositories(): AppRepositories {
        val datastore = DataStoreFactory.create(
            serializer = AppSettingsSerializer,
            produceFile = { context.dataStoreFile(if (serverId != null) "server_${serverId}_settings.pb" else "settings.pb") },
            corruptionHandler = null,
            scope = initScope,
        )

        return AppRepositories(
            settingsRepository = ExposedSettingsRepository(databases.app).let { repository ->
                SettingsRepositoryWrapper(
                    SettingsStateWrapper(
                        settings = repository.get() ?: AppSettings(cardWidth = 150),
                        saveSettings = repository::save
                    )
                )
            },
            epubReaderSettingsRepository = ExposedEpubReaderSettingsRepository(databases.app).let { repository ->
                EpubReaderSettingsRepositoryWrapper(
                    SettingsStateWrapper(
                        settings = repository.get() ?: EpubReaderSettings(),
                        saveSettings = repository::save
                    )
                )
            },
            epubBookmarkRepository = snd.komelia.db.bookmarks.ExposedEpubBookmarkRepository(databases.app),
            audioPositionRepository = snd.komelia.db.audiobook.ExposedAudioPositionRepository(databases.app),
            audioBookmarkRepository = snd.komelia.db.audiobook.ExposedAudioBookmarkRepository(databases.app),
            audioChapterRepository = snd.komelia.db.audiobook.ExposedAudioChapterRepository(databases.app),
            bookAnnotationRepository = snd.komelia.db.annotations.ExposedBookAnnotationRepository(databases.app),
            imageReaderSettingsRepository = ExposedImageReaderSettingsRepository(databases.app).let { repository ->
                ReaderSettingsRepositoryWrapper(
                    SettingsStateWrapper(
                        settings = repository.get() ?: ImageReaderSettings(upsamplingMode = UpsamplingMode.BILINEAR),
                        saveSettings = repository::save
                    )
                )
            },
            fontsRepository = ExposedUserFontsRepository(databases.app),
            colorCurvesPresetsRepository = ExposedColorCurvesPresetRepository(databases.app),
            colorLevelsPresetRepository = ExposedColorLevelsPresetRepository(databases.app),
            bookColorCorrectionRepository = ExposedBookColorCorrectionRepository(databases.app),
            secretsRepository = AndroidSecretsRepository(datastore),
            komfSettingsRepository = ExposedKomfSettingsRepository(databases.app).let { repository ->
                KomfSettingsRepositoryWrapper(
                    SettingsStateWrapper(
                        settings = repository.get() ?: KomfSettings(),
                        saveSettings = repository::save
                    )
                )
            },
            homeScreenFilterRepository = ExposedHomeScreenFilterRepository(databases.app).let { repository ->
                HomeScreenFilterRepositoryWrapper(
                    SettingsStateWrapper(
                        settings = repository.getFilters() ?: homeScreenDefaultFilters,
                        saveSettings = repository::putFilters
                    )
                )
            },
            librarySeriesFiltersRepository = snd.komelia.db.libraryfilters.ExposedLibrarySeriesFiltersRepository(databases.app),
            transcriptionSettingsRepository = ExposedTranscriptionSettingsRepository(databases.app).let { repository ->
                TranscriptionSettingsRepositoryWrapper(
                    SettingsStateWrapper(
                        settings = repository.get() ?: snd.komelia.db.TranscriptionSettings(),
                        saveSettings = repository::save
                    )
                )
            },
            seriesReaderOverridesRepository = snd.komelia.db.reader.ExposedSeriesReaderOverridesRepository(databases.app),
        )
    }

    override suspend fun createOfflineRepositories(): OfflineRepositories {
        return OfflineRepositories(
            mediaServerRepository = ExposedOfflineMediaServerRepository(databases.offline),
            mediaRepository = ExposedMediaRepository(databases.offline),
            bookRepository = ExposedOfflineBookRepository(databases.offline),
            bookMetadataRepository = ExposedOfflineBookMetadataRepository(databases.offline),
            bookMetadataAggregationRepository = ExposedOfflineBookMetadataAggregationRepository(databases.offline),
            libraryRepository = ExposedOfflineLibraryRepository(databases.offline),
            readProgressRepository = ExposedOfflineReadProgressRepository(databases.offline),
            seriesMetadataRepository = ExposedOfflineSeriesMetadataRepository(databases.offline),
            seriesRepository = ExposedOfflineSeriesRepository(databases.offline),
            thumbnailBookRepository = ExposedOfflineThumbnailBookRepository(databases.offline),
            thumbnailSeriesRepository = ExposedOfflineThumbnailSeriesRepository(databases.offline),
            userRepository = ExposedOfflineUserRepository(databases.offline),
            bookDtoRepository = ExposedOfflineBookDtoRepository(databases.offline),
            referentialRepository = ExposedOfflineReferentialRepository(databases.offline),
            seriesDtoRepository = ExposedSeriesDtoRepository(databases.offline),
            tasksRepository = ExposedOfflineTasksRepository(databases.offline),
            logJournalRepository = ExposedLogJournalRepository(databases.offline),
            offlineSettingsRepository = ExposedOfflineSettingsRepository(databases.offline).let { repo ->
                OfflineSettingsRepositoryWrapper(
                    SettingsStateWrapper(
                        settings = repo.get() ?: OfflineSettings(
                            downloadDirectory = PlatformFile(context.filesDir.resolve("offline"))
                        ),
                        saveSettings = repo::save
                    )
                )
            },


            transactionTemplate = ExposedTransactionTemplate(databases.offline),
        )
    }

    override fun createKtorClient(): HttpClient {
        return configureKtor(okHttpClient)
    }

    override fun createKtorClientWithoutCache(): HttpClient {
        return configureKtor(okHttpClientWithoutCache)
    }

    private fun configureKtor(okHttpClient: OkHttpClient): HttpClient {
        return HttpClient(OkHttp) {
            engine { preconfigured = okHttpClient }
            expectSuccess = true

            install(UserAgent) {
                agent = komeliaUserAgent
            }
            install(HttpTimeout) {
                requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
                connectTimeoutMillis = 30_000
                socketTimeoutMillis = 30_000
            }
        }

    }

    override fun createAppUpdater(updateClient: UpdateClient): AppUpdater? {
        @Suppress("KotlinConstantConditions")
        return if (BuildConfig.ENABLE_SELF_UPDATES) AndroidAppUpdater(updateClient, context) else null
    }

    override fun createImageDecoder() = VipsImageDecoder()

    override suspend fun createReaderImageFactory(
        imageDecoder: KomeliaImageDecoder,
        pipeline: ImageProcessingPipeline,
        settings: ImageReaderSettingsRepository,
        onnxRuntimeUpscaler: KomeliaUpscaler?,
        onnxModelDownloader: OnnxModelDownloader?,
    ): ReaderImageFactory {
        val ncnn = ncnnUpscaler ?: AndroidNcnnUpscaler(context, settings, onnxModelDownloader).also {
            it.initialize()
            ncnnUpscaler = it
        }

        return AndroidReaderImageFactory(
            imageDecoder = imageDecoder,
            downSamplingKernel = settings.getDownsamplingKernel().stateIn(initScope),
            upsamplingMode = settings.getUpsamplingMode().stateIn(initScope),
            linearLightDownSampling = settings.getLinearLightDownsampling().stateIn(initScope),
            processingPipeline = pipeline,
            stretchImages = settings.getStretchToFit().stateIn(initScope),
            ncnnUpscaler = ncnn
        )
    }

    override fun createOnBookChange(): () -> Unit = { AndroidNcnnUpscaler.cancelPendingRequests() }

    override fun createOnEpubCacheClear(): () -> Unit = {
        context.cacheDir.resolve("epub3").deleteRecursively()
    }

    override fun createWindowState() = AndroidWindowState(mainActivity)

    override fun createCoilContext() = context

    override fun createOnnxRuntimeInstaller(updateClient: UpdateClient) = null

    override fun createOnnxModelDownloader(updateClient: UpdateClient) =
        AndroidOnnxModelDownloader(
            updateClient = updateClient,
            appNotifications = appNotifications,
            dataDir = context.filesDir.toPath()
        )

    override fun createWhisperModelDownloader(updateClient: UpdateClient): WhisperModelDownloader =
        AndroidWhisperModelDownloader(
            updateClient = updateClient,
            filesDir = context.filesDir,
        )

    override fun createRapidOcrModelDownloader(updateClient: UpdateClient): RapidOcrModelDownloader =
        AndroidRapidOcrModelDownloader(
            updateClient = updateClient,
            appNotifications = appNotifications,
            dataDir = context.filesDir.toPath()
        )

    override fun createOnnxRuntime(): OnnxRuntime? {
        if (!OnnxRuntimeSharedLibraries.isAvailable) {
            logger.warn { "OnnxRuntime is not available" }
            return null
        }
        val dataDir = context.dataDir.resolve("onnxruntime").toPath().createDirectories()
        return JvmOnnxRuntime.create(dataDir.toString())
    }

    override suspend fun createUpscaler(
        onnxRuntime: OnnxRuntime,
        modelDownloader: OnnxModelDownloader,
        settings: ImageReaderSettingsRepository,
    ): KomeliaUpscaler? = null

    override suspend fun createPanelDetector(
        onnxRuntime: OnnxRuntime,
        modelDownloader: OnnxModelDownloader,
        settings: ImageReaderSettingsRepository,
    ): KomeliaPanelDetector {
        val rfDetr = JvmOnnxRuntimeRfDetr.create(onnxRuntime as JvmOnnxRuntime)
        val modelsDir = context.filesDir.resolve("onnx").toPath().createDirectories()
        val panelDetector = AndroidPanelDetector(
            rfDetr = rfDetr,
            executionProvider = OnnxRuntimeExecutionProvider.CPU,
            deviceId = MutableStateFlow(0),
            updateFlow = modelDownloader.downloadCompletionEvents.filterIsInstance(),
            dataDir = modelsDir,
        ).also { it.initialize() }

        return panelDetector
    }

    override fun getCoilCacheDirectory(): Path {
        val path = context.cacheDir.resolve("coil3_disk_cache")
        return Path(if (serverId != null) path.resolve("server_$serverId").toString() else path.toString())
    }

    override fun createCoilMemoryCache(): MemoryCache {
        return MemoryCache.Builder()
            .maxSizePercent(context)
            .maxSizeBytes(64 * 1024 * 1024) // 64 Mib
            .build()
    }

    override fun getReaderCacheDirectory(): Path {
        val path = context.cacheDir.resolve("komelia_reader_cache")
        return Path(if (serverId != null) path.resolve("server_$serverId").toString() else path.toString())
    }

    override fun createLocalFileApiProvider(): LocalFileApiProvider {
        return LocalFileApiProviderImpl(
            context = context,
            incomingUriFlow = incomingFileUriFlow,
            readProgressRepo = LocalFileReadProgressRepository(databases.app),
            scope = initScope,
        )
    }

    override fun createBackupService(repositories: AppRepositories): BackupService {
        return DefaultBackupService(
            appSettings = (repositories.settingsRepository as SettingsRepositoryWrapper).wrapper,
            imageReader = (repositories.imageReaderSettingsRepository as ReaderSettingsRepositoryWrapper).wrapper,
            epubReader = (repositories.epubReaderSettingsRepository as EpubReaderSettingsRepositoryWrapper).wrapper,
            komf = (repositories.komfSettingsRepository as KomfSettingsRepositoryWrapper).wrapper,
            transcription = (repositories.transcriptionSettingsRepository as TranscriptionSettingsRepositoryWrapper).wrapper,
            homeFilters = (repositories.homeScreenFilterRepository as HomeScreenFilterRepositoryWrapper).wrapper,
            librarySeriesFilters = repositories.librarySeriesFiltersRepository,
            seriesReaderOverrides = repositories.seriesReaderOverridesRepository,
        )
    }

override fun createOfflineModule(
    repositories: OfflineRepositories,
    onlineUser: StateFlow<KomgaUser?>,
    onlineServerUrl: StateFlow<String>,
    isOffline: StateFlow<Boolean>,
    komgaClientFactory: KomgaClientFactory
): OfflineModule {
    return AndroidOfflineModule(
        repositories = repositories,
        onlineUser = onlineUser,
        onlineServerUrl = onlineServerUrl,
        isOffline = isOffline,
        komgaClientFactory = komgaClientFactory,
        context = this.context,
    )
}

override suspend fun close() {
    okHttpClient.dispatcher.cancelAll()
    super.close()
    databases.close()
    okHttpClient.connectionPool.evictAll()
}
}

