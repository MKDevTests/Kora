package snd.komelia

import snd.komelia.color.repository.BookColorCorrectionRepository
import snd.komelia.color.repository.ColorCurvePresetRepository
import snd.komelia.color.repository.ColorLevelsPresetRepository
import snd.komelia.fonts.UserFontsRepository
import snd.komelia.homefilters.HomeScreenFilterRepository
import snd.komelia.libraryfilters.LibrarySeriesFiltersRepository
import snd.komelia.links.SeriesLinksRepository
import snd.komelia.offline.OfflineModule
import snd.komelia.ratings.SeriesRatingsRepository
import snd.komelia.reader.SeriesReaderOverridesRepository
import snd.komelia.readingorder.ReadingOrderRepository
import snd.komelia.settings.CommonSettingsRepository
import snd.komelia.settings.EpubReaderSettingsRepository
import snd.komelia.settings.ImageReaderSettingsRepository
import snd.komelia.settings.KomfSettingsRepository
import snd.komelia.settings.SecretsRepository
import snd.komelia.settings.TranscriptionSettingsRepository
import snd.komelia.similarity.SimilarityIndexRepository
import snd.komelia.similarity.SuggestionFeedbackRepository
import snd.komelia.stats.ReadingEventsRepository

import snd.komelia.sync.ReaderSyncService

class CoreModule(
    val appRepositories: AppRepositories,
    private val offlineModule: OfflineModule
) {
    val readerSyncService = ReaderSyncService()
}

data class AppRepositories(
    val settingsRepository: CommonSettingsRepository,
    val epubReaderSettingsRepository: EpubReaderSettingsRepository,
    val epubBookmarkRepository: snd.komelia.bookmarks.EpubBookmarkRepository,
    val audioPositionRepository: snd.komelia.audiobook.AudioPositionRepository,
    val audioBookmarkRepository: snd.komelia.audiobook.AudioBookmarkRepository,
    val audioChapterRepository: snd.komelia.audiobook.AudioChapterRepository,
    val bookAnnotationRepository: snd.komelia.annotations.BookAnnotationRepository,
    val imageReaderSettingsRepository: ImageReaderSettingsRepository,
    val fontsRepository: UserFontsRepository,
    val colorCurvesPresetsRepository: ColorCurvePresetRepository,
    val colorLevelsPresetRepository: ColorLevelsPresetRepository,
    val bookColorCorrectionRepository: BookColorCorrectionRepository,
    val secretsRepository: SecretsRepository,
    val komfSettingsRepository: KomfSettingsRepository,
    val homeScreenFilterRepository: HomeScreenFilterRepository,
    val librarySeriesFiltersRepository: LibrarySeriesFiltersRepository,
    val transcriptionSettingsRepository: TranscriptionSettingsRepository,
    val seriesReaderOverridesRepository: SeriesReaderOverridesRepository,
    val readingEventsRepository: ReadingEventsRepository,
    val seriesRatingsRepository: SeriesRatingsRepository,
    val seriesLinksRepository: SeriesLinksRepository,
    /** Local term index behind "Similar series" — derived data, never backed up. */
    val similarityIndexRepository: SimilarityIndexRepository,
    /** Designated original series + cached reading-order graphs. */
    val readingOrderRepository: ReadingOrderRepository,
    /** "Not interested" answers, which also weigh on the taste profile. */
    val suggestionFeedbackRepository: SuggestionFeedbackRepository,
    /** Remembered tab counts, so a library's chips don't wait on the server. */
    val libraryCountsRepository: snd.komelia.library.LibraryCountsRepository,
    /** Remembered "Keep reading" row, for the same reason. */
    val keepReadingRepository: snd.komelia.library.KeepReadingRepository,
    /** Remembered Links tab: resolving each link is a request of its own. */
    val seriesLinksCacheRepository: snd.komelia.library.SeriesLinksCacheRepository,
    /** Remembered first page of a series' books. */
    val seriesBooksCacheRepository: snd.komelia.library.SeriesBooksCacheRepository,
    /** Terms the page translator is not allowed to decide for itself. */
    val translationGlossaryRepository: snd.komelia.translation.TranslationGlossaryRepository,
)
