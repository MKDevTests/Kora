package snd.komelia.ui.strings

import snd.komelia.image.ReduceKernel
import snd.komelia.image.UpsamplingMode
import snd.komelia.image.UpscaleMode
import snd.komelia.settings.model.AppTheme
import snd.komelia.settings.model.ContinuousReadingDirection
import snd.komelia.settings.model.EpubReaderType
import snd.komelia.settings.model.LayoutScaleType
import snd.komelia.settings.model.PageDisplayLayout
import snd.komelia.settings.model.PagedReadingDirection
import snd.komelia.settings.model.ReaderType
import snd.komelia.ui.book.BooksFilterState.BooksSort
import snd.komelia.ui.dialogs.user.UserEditDialogViewModel
import snd.komelia.ui.dialogs.user.UserEditDialogViewModel.AgeRestriction.ALLOW_ONLY
import snd.komelia.ui.dialogs.user.UserEditDialogViewModel.AgeRestriction.EXCLUDE
import snd.komelia.ui.dialogs.user.UserEditDialogViewModel.AgeRestriction.NONE
import snd.komelia.ui.library.LibrarySeriesTabState
import snd.komelia.ui.series.SeriesFilterState
import snd.komf.api.KomfCoreProviders
import snd.komf.api.KomfProviders
import snd.komf.api.UnknownKomfProvider
import snd.komga.client.book.KomgaReadStatus
import snd.komga.client.book.KomgaReadStatus.IN_PROGRESS
import snd.komga.client.book.KomgaReadStatus.READ
import snd.komga.client.book.KomgaReadStatus.UNREAD
import snd.komga.client.common.KomgaReadingDirection
import snd.komga.client.common.KomgaReadingDirection.LEFT_TO_RIGHT
import snd.komga.client.common.KomgaReadingDirection.RIGHT_TO_LEFT
import snd.komga.client.common.KomgaReadingDirection.VERTICAL
import snd.komga.client.common.KomgaReadingDirection.WEBTOON
import snd.komga.client.library.ScanInterval
import snd.komga.client.library.ScanInterval.DAILY
import snd.komga.client.library.ScanInterval.DISABLED
import snd.komga.client.library.ScanInterval.EVERY_12H
import snd.komga.client.library.ScanInterval.EVERY_6H
import snd.komga.client.library.ScanInterval.HOURLY
import snd.komga.client.library.ScanInterval.WEEKLY
import snd.komga.client.library.SeriesCover
import snd.komga.client.library.SeriesCover.FIRST
import snd.komga.client.library.SeriesCover.FIRST_UNREAD_OR_FIRST
import snd.komga.client.library.SeriesCover.FIRST_UNREAD_OR_LAST
import snd.komga.client.library.SeriesCover.LAST
import snd.komga.client.series.KomgaSeriesStatus
import snd.komga.client.series.KomgaSeriesStatus.ABANDONED
import snd.komga.client.series.KomgaSeriesStatus.ENDED
import snd.komga.client.series.KomgaSeriesStatus.HIATUS
import snd.komga.client.series.KomgaSeriesStatus.ONGOING
import snd.komga.client.settings.KomgaThumbnailSize
import snd.komga.client.settings.KomgaThumbnailSize.DEFAULT
import snd.komga.client.settings.KomgaThumbnailSize.LARGE
import snd.komga.client.settings.KomgaThumbnailSize.MEDIUM
import snd.komga.client.settings.KomgaThumbnailSize.XLARGE

data class AppStrings(
    val seriesView: SeriesViewStrings,
    val filters: FilterStrings,
    val seriesFilter: SeriesFilterStrings,
    val booksFilter: BookFilterStrings,
    val seriesEdit: SeriesEditStrings,
    val bookEdit: BookEditStrings,
    val libraryEdit: LibraryEditStrings,
    val userEdit: UserEditStrings,
    val reader: ReaderStrings,
    val pagedReader: PagedReaderStrings,
    val continuousReader: ContinuousReaderStrings,
    val settings: SettingsStrings,
    val imageSettings: ImageSettingsStrings,
    val errorCodes: ErrorCodes,
    val komf: KomfStrings,
    val navigation: NavigationStrings,
    val suggestions: SuggestionStrings,
    val ui: UiStrings,
    val counts: CountStrings,
    val shelves: ShelfStrings,
    val roles: RoleStrings,
    val editions: EditionStrings,
    val shelfPatterns: ShelfPatternStrings,
)

/**
 * Names of the built-in Home shelves.
 *
 * These are not code literals: they are stored per user, in the home-filter
 * table, and the user can rename them. Translating them at display time — and
 * ONLY while they still read exactly as shipped — is what keeps a renamed shelf
 * called what its owner called it.
 */
/** What a link to another edition of the same work is called. */
data class EditionStrings(
    val heading: String,
    val otherVersion: String,
    val otherLanguage: String,
    val colourEdition: String,
    val chapters: String,
    val volumes: String,
)

data class RoleStrings(
    val writer: String,
    val penciller: String,
    val inker: String,
    val colorist: String,
    val letterer: String,
    val cover: String,
    val editor: String,
    val translator: String,
)

/**
 * Names of the shelf patterns offered in the Home editor.
 *
 * The dropdowns used to print the enum constants themselves — no literal in the
 * source, so nothing to translate and nothing to notice.
 */
data class ShelfPatternStrings(
    val custom: String,
    val onDeck: String,
    val forgotten: String,
    val recentlyAdded: String,
    val recentlyUpdated: String,
    val almostFinished: String,
    val favorites: String,
    val series: String,
    val book: String,
    val discover: String,
)

data class ShelfStrings(
    val keepReading: String,
    val onDeck: String,
    val recentlyReleasedBooks: String,
    val recentlyAddedBooks: String,
    val recentlyAddedSeries: String,
    val recentlyUpdatedSeries: String,
    val recentlyReadBooks: String,
    val discover: String,
    val forgotten: String,
    val almostFinished: String,
)

/**
 * The labels that carry a number.
 *
 * Functions, not format strings: French does not agree the way English does
 * ("1 tome" / "2 tomes"), and a `%d` lost in translation would only show up on
 * screen. The compiler checks the argument instead.
 */
data class CountStrings(
    val selected: (Int) -> String,
    val pages: (Int) -> String,
    val pagesLeft: (Int) -> String,
    val bookAndPages: (String, Int) -> String,
    val seriesCount: (Int) -> String,
    val booksCount: (Int) -> String,
    val rolesFor: (String) -> String,
    val inLibrary: (String) -> String,
    val releaseYear: (Int) -> String,
    val processedFiles: (Int) -> String,
)

/**
 * Everything else the interface says, in one group.
 *
 * One shared group rather than one per screen: the same words appear on five
 * screens ("Download", "Cancel", "Delete"), and a group per area would mean
 * translating each of them five times and letting them drift. Generated by
 * `scripts/i18n-apply.py` — the French lives in `scripts/i18n_fr.py`.
 */
class UiStrings(private val values: Map<String, String>) {
    /** Never throws: a missing entry shows its key instead of killing the screen. */
    private fun at(key: String): String = values[key] ?: key

    // region generated-ui-strings
    val aCollectionWithThisName: String get() = at("aCollectionWithThisName")
    val aReadListWithThis: String get() = at("aReadListWithThis")
    val aboutWhisper: String get() = at("aboutWhisper")
    val accentColorChipsTabs: String get() = at("accentColorChipsTabs")
    val accountSettings: String get() = at("accountSettings")
    val actions: String get() = at("actions")
    val active: String get() = at("active")
    val active2: String get() = at("active2")
    val add: String get() = at("add")
    val addANote: String get() = at("addANote")
    val addBook: String get() = at("addBook")
    val addCondition: String get() = at("addCondition")
    val addCustomRole: String get() = at("addCustomRole")
    val addField: String get() = at("addField")
    val addFilter: String get() = at("addFilter")
    val addLink: String get() = at("addLink")
    val addNewDiscordWebhook: String get() = at("addNewDiscordWebhook")
    val addNote: String get() = at("addNote")
    val addOtherAddressesThatReach: String get() = at("addOtherAddressesThatReach")
    val addProvider: String get() = at("addProvider")
    val addToCollection: String get() = at("addToCollection")
    val addToReadList: String get() = at("addToReadList")
    val addUrl: String get() = at("addUrl")
    val addUrl2: String get() = at("addUrl2")
    val addUser: String get() = at("addUser")
    val addWebhook: String get() = at("addWebhook")
    val addsADedicatedButtonTo: String get() = at("addsADedicatedButtonTo")
    val addsADedicatedStatsButton: String get() = at("addsADedicatedStatsButton")
    val addsAGenreTabTo: String get() = at("addsAGenreTabTo")
    val addsFeaturesAimedAtMetadata: String get() = at("addsFeaturesAimedAtMetadata")
    val admin: String get() = at("admin")
    val administrator: String get() = at("administrator")
    val age: String get() = at("age")
    val ageRating: String get() = at("ageRating")
    val aggregate: String get() = at("aggregate")
    val aggregateAndCombineMetadataFrom: String get() = at("aggregateAndCombineMetadataFrom")
    val aggregationSettings: String get() = at("aggregationSettings")
    val align: String get() = at("align")
    val all: String get() = at("all")
    val all2: String get() = at("all2")
    val allLibraries: String get() = at("allLibraries")
    val alreadyContainsThisBook: String get() = at("alreadyContainsThisBook")
    val alreadyContainsThisSeries: String get() = at("alreadyContainsThisSeries")
    val alternateTitle: String get() = at("alternateTitle")
    val alternateUrlsForThisServer: String get() = at("alternateUrlsForThisServer")
    val alternativeSeriesTitles: String get() = at("alternativeSeriesTitles")
    val alternativeTitleFallback: String get() = at("alternativeTitleFallback")
    val alternativeTitleLanguagesIso639: String get() = at("alternativeTitleLanguagesIso639")
    val alternativeTitles: String get() = at("alternativeTitles")
    val alwaysShowProgressBarWhile: String get() = at("alwaysShowProgressBarWhile")
    val analyze: String get() = at("analyze")
    val analyzeLibrary: String get() = at("analyzeLibrary")
    val analyzeWithAnilist: String get() = at("analyzeWithAnilist")
    val analyzing: String get() = at("analyzing")
    val anilistLinkSuggestionsOnline: String get() = at("anilistLinkSuggestionsOnline")
    val anilistSuggestions: String get() = at("anilistSuggestions")
    val announcements: String get() = at("announcements")
    val annuler: String get() = at("annuler")
    val apiForCrossPlatformEfficient: String get() = at("apiForCrossPlatformEfficient")
    val appRestartIsRequiredFor: String get() = at("appRestartIsRequiredFor")
    val appSettings: String get() = at("appSettings")
    val appUpdates: String get() = at("appUpdates")
    val appearance: String get() = at("appearance")
    val appendVolumeToSeriesTitle: String get() = at("appendVolumeToSeriesTitle")
    val appliedOnTheNextApp: String get() = at("appliedOnTheNextApp")
    val appliesToBookAndSeries: String get() = at("appliesToBookAndSeries")
    val applyAnyway: String get() = at("applyAnyway")
    val applyBackup: String get() = at("applyBackup")
    val appriseGithubPage: String get() = at("appriseGithubPage")
    val artistRoles: String get() = at("artistRoles")
    val attemptToOrderBooksUsing: String get() = at("attemptToOrderBooksUsing")
    val aucunTagPRimTout: String get() = at("aucunTagPRimTout")
    val aucune: String get() = at("aucune")
    val aucuneSRieMasquE: String get() = at("aucuneSRieMasquE")
    val audio: String get() = at("audio")
    val audioMetadata: String get() = at("audioMetadata")
    val authenticationActivity: String get() = at("authenticationActivity")
    val author: String get() = at("author")
    val authorRoles: String get() = at("authorRoles")
    val authors: String get() = at("authors")
    val autoDetectDirectionUseSeries: String get() = at("autoDetectDirectionUseSeries")
    val autoDetectReadingDirection: String get() = at("autoDetectReadingDirection")
    val autoDetectWebtoon: String get() = at("autoDetectWebtoon")
    val autoDetectWebtoonSwitchTo: String get() = at("autoDetectWebtoonSwitchTo")
    val autoIdentify: String get() = at("autoIdentify")
    val autoIdentifyKomf: String get() = at("autoIdentifyKomf")
    val autoRewind: String get() = at("autoRewind")
    val autoSkipBlankPages: String get() = at("autoSkipBlankPages")
    val automaticBackups: String get() = at("automaticBackups")
    val automaticallyScanPagesForText: String get() = at("automaticallyScanPagesForText")
    val back: String get() = at("back")
    val backToAuthors: String get() = at("backToAuthors")
    val backToOnline: String get() = at("backToOnline")
    val backToResults: String get() = at("backToResults")
    val backupFolder: String get() = at("backupFolder")
    val backupNow: String get() = at("backupNow")
    val backupRestore: String get() = at("backupRestore")
    val backupRestore2: String get() = at("backupRestore2")
    val bibliothQuesDansToutes: String get() = at("bibliothQuesDansToutes")
    val bibliothQuesInclusesDansToutes: String get() = at("bibliothQuesInclusesDansToutes")
    val biggerThanThisTheLists: String get() = at("biggerThanThisTheLists")
    val black: String get() = at("black")
    val blackBubbleWhiteTextArtwork: String get() = at("blackBubbleWhiteTextArtwork")
    val blackBubbleWhiteTextArtwork2: String get() = at("blackBubbleWhiteTextArtwork2")
    val body: String get() = at("body")
    val bookCount: String get() = at("bookCount")
    val bookCovers: String get() = at("bookCovers")
    val bookFilters: String get() = at("bookFilters")
    val bookMetadata: String get() = at("bookMetadata")
    val bookTitleExample: String get() = at("bookTitleExample")
    val bookmarks: String get() = at("bookmarks")
    val books: String get() = at("books")
    val booksFinished: String get() = at("booksFinished")
    val bottomMargin: String get() = at("bottomMargin")
    val browse: String get() = at("browse")
    val byDefaultBooksInA: String get() = at("byDefaultBooksInA")
    val byDefaultSeriesInA: String get() = at("byDefaultSeriesInA")
    val canBeSlowForHigh: String get() = at("canBeSlowForHigh")
    val cancel: String get() = at("cancel")
    val cancel2: String get() = at("cancel2")
    val cancelAllCurrentlyRunningTasks: String get() = at("cancelAllCurrentlyRunningTasks")
    val cancelAllTasks: String get() = at("cancelAllTasks")
    val cancelLoginAttempt: String get() = at("cancelLoginAttempt")
    val cardLayoutOverlayBackground: String get() = at("cardLayoutOverlayBackground")
    val cards: String get() = at("cards")
    val category: String get() = at("category")
    val changeLocation: String get() = at("changeLocation")
    val changePassword: String get() = at("changePassword")
    val channel: String get() = at("channel")
    val chapters: String get() = at("chapters")
    val character: String get() = at("character")
    val checkFoldersForNewOr: String get() = at("checkFoldersForNewOr")
    val checkForUpdates: String get() = at("checkForUpdates")
    val checkForUpdatesOnStartup: String get() = at("checkForUpdatesOnStartup")
    val choisirLaCouverture: String get() = at("choisirLaCouverture")
    val choose: String get() = at("choose")
    val chooseAnImageDragAnd: String get() = at("chooseAnImageDragAnd")
    val chooseOnnxRuntimeVersion: String get() = at("chooseOnnxRuntimeVersion")
    val chooseWhichAuthorRolesTo: String get() = at("chooseWhichAuthorRolesTo")
    val clear: String get() = at("clear")
    val clearAll: String get() = at("clearAll")
    val clearEpubCache: String get() = at("clearEpubCache")
    val clearImageCache: String get() = at("clearImageCache")
    val clickOnItemsToSelect: String get() = at("clickOnItemsToSelect")
    val clickToSelectDragTo: String get() = at("clickToSelectDragTo")
    val close: String get() = at("close")
    val closeBook: String get() = at("closeBook")
    val closeMenu: String get() = at("closeMenu")
    val collection: String get() = at("collection")
    val collection2: String get() = at("collection2")
    val collections: String get() = at("collections")
    val colorCorrection: String get() = at("colorCorrection")
    val columns: String get() = at("columns")
    val comicBookArchives: String get() = at("comicBookArchives")
    val comicvineClientId: String get() = at("comicvineClientId")
    val completed: String get() = at("completed")
    val computingSuggestions: String get() = at("computingSuggestions")
    val condition: String get() = at("condition")
    val confirm: String get() = at("confirm")
    val confirmerEtAppliquer: String get() = at("confirmerEtAppliquer")
    val connectToANewServer: String get() = at("connectToANewServer")
    val connected: String get() = at("connected")
    val connectedServers: String get() = at("connectedServers")
    val connection: String get() = at("connection")
    val continueReading: String get() = at("continueReading")
    val continueReading2: String get() = at("continueReading2")
    val continuous: String get() = at("continuous")
    val copiedToClipboard: String get() = at("copiedToClipboard")
    val copy: String get() = at("copy")
    val copyStacktraceToClipboard: String get() = at("copyStacktraceToClipboard")
    val corner: String get() = at("corner")
    val couldNotComputeSuggestions: String get() = at("couldNotComputeSuggestions")
    val couldNotConnectToThe: String get() = at("couldNotConnectToThe")
    val cover: String get() = at("cover")
    val coverSettings: String get() = at("coverSettings")
    val crashLogs: String get() = at("crashLogs")
    val create: String get() = at("create")
    val cropBorders: String get() = at("cropBorders")
    val cudaNvidiaGpusRequiresCuda12: String get() = at("cudaNvidiaGpusRequiresCuda12")
    val current: String get() = at("current")
    val current2: String get() = at("current2")
    val currentVersion: String get() = at("currentVersion")
    val curves: String get() = at("curves")
    val customTileAppearance: String get() = at("customTileAppearance")
    val customUrl: String get() = at("customUrl")
    val datasourceType: String get() = at("datasourceType")
    val days: String get() = at("days")
    val deepScanAllLibraries: String get() = at("deepScanAllLibraries")
    val default: String get() = at("default")
    val defaultSeriesLanguage: String get() = at("defaultSeriesLanguage")
    val defaultSeriesReadingDirection: String get() = at("defaultSeriesReadingDirection")
    val defaultValues: String get() = at("defaultValues")
    val delete: String get() = at("delete")
    val deleteAll: String get() = at("deleteAll")
    val deleteAnnotation: String get() = at("deleteAnnotation")
    val deleteBook: String get() = at("deleteBook")
    val deleteBookmark: String get() = at("deleteBookmark")
    val deleteBooks: String get() = at("deleteBooks")
    val deleteCollection: String get() = at("deleteCollection")
    val deleteDownloaded: String get() = at("deleteDownloaded")
    val deleteDownloadedBook: String get() = at("deleteDownloadedBook")
    val deleteDownloadedBooks: String get() = at("deleteDownloadedBooks")
    val deleteDownloadedLibrary: String get() = at("deleteDownloadedLibrary")
    val deleteDownloadedSeries: String get() = at("deleteDownloadedSeries")
    val deleteDownloadedSeries2: String get() = at("deleteDownloadedSeries2")
    val deleteFromServer: String get() = at("deleteFromServer")
    val deleteItemsMarkedAsUnavailable: String get() = at("deleteItemsMarkedAsUnavailable")
    val deleteLibrary: String get() = at("deleteLibrary")
    val deletePreset: String get() = at("deletePreset")
    val deleteReadList: String get() = at("deleteReadList")
    val deleteSeries: String get() = at("deleteSeries")
    val deleteSeries2: String get() = at("deleteSeries2")
    val deleteSeriesFromServer: String get() = at("deleteSeriesFromServer")
    val deleteServer: String get() = at("deleteServer")
    val deleteServerProfile: String get() = at("deleteServerProfile")
    val deleteUser: String get() = at("deleteUser")
    val description: String get() = at("description")
    val description4096CharactersMax: String get() = at("description4096CharactersMax")
    val diagnostics: String get() = at("diagnostics")
    val direction: String get() = at("direction")
    val directmlAnyGpu: String get() = at("directmlAnyGpu")
    val discard: String get() = at("discard")
    val discordMarkdownText101: String get() = at("discordMarkdownText101")
    val dismiss: String get() = at("dismiss")
    val display: String get() = at("display")
    val displayTheBottomNavigationBar: String get() = at("displayTheBottomNavigationBar")
    val displayedNameEmptyDefault: String get() = at("displayedNameEmptyDefault")
    val done: String get() = at("done")
    val download: String get() = at("download")
    val downloadComplete: String get() = at("downloadComplete")
    val downloadCuda12: String get() = at("downloadCuda12")
    val downloadCudnn9: String get() = at("downloadCudnn9")
    val downloadModel: String get() = at("downloadModel")
    val downloadModels: String get() = at("downloadModels")
    val downloadOnnxRuntime: String get() = at("downloadOnnxRuntime")
    val downloadTensorrt: String get() = at("downloadTensorrt")
    val downloaded: String get() = at("downloaded")
    val downloading: String get() = at("downloading")
    val downloadingMangabakaDatabase: String get() = at("downloadingMangabakaDatabase")
    val downloadingOnnxRuntime: String get() = at("downloadingOnnxRuntime")
    val downloadingTranscriptionModel: String get() = at("downloadingTranscriptionModel")
    val downloads: String get() = at("downloads")
    val edit: String get() = at("edit")
    val editModeClickToSelect: String get() = at("editModeClickToSelect")
    val editUser: String get() = at("editUser")
    val email: String get() = at("email")
    val emptyTrash: String get() = at("emptyTrash")
    val emptyTrashForAllLibraries: String get() = at("emptyTrashForAllLibraries")
    val emptyTrashForLibrary: String get() = at("emptyTrashForLibrary")
    val enableAutomaticBackups: String get() = at("enableAutomaticBackups")
    val enableIgnoreList: String get() = at("enableIgnoreList")
    val enableKomfIntegration: String get() = at("enableKomfIntegration")
    val enableMetadataUpdateJobsFor: String get() = at("enableMetadataUpdateJobsFor")
    val enableNcnnUpscalerMobileOnly: String get() = at("enableNcnnUpscalerMobileOnly")
    val enableNotificationJobsForLibraries: String get() = at("enableNotificationJobsForLibraries")
    val enableTextSelection: String get() = at("enableTextSelection")
    val enabled: String get() = at("enabled")
    val engine: String get() = at("engine")
    val enterANameForThe: String get() = at("enterANameForThe")
    val epub: String get() = at("epub")
    val epub3ReaderIsNotAvailable: String get() = at("epub3ReaderIsNotAvailable")
    val epubReader: String get() = at("epubReader")
    val epubReaderSettings: String get() = at("epubReaderSettings")
    val error: String get() = at("error")
    val errors: String get() = at("errors")
    val eventListener: String get() = at("eventListener")
    val exclusionMode: String get() = at("exclusionMode")
    val exit: String get() = at("exit")
    val experimental: String get() = at("experimental")
    val export: String get() = at("export")
    val exportLogs: String get() = at("exportLogs")
    val failed: String get() = at("failed")
    val failedToLoadMetadata: String get() = at("failedToLoadMetadata")
    val failedToParseUrl: String get() = at("failedToParseUrl")
    val favoris: String get() = at("favoris")
    val favoris2: String get() = at("favoris2")
    val female: String get() = at("female")
    val fermer: String get() = at("fermer")
    val fermer2: String get() = at("fermer2")
    val fieldName256CharactersMax: String get() = at("fieldName256CharactersMax")
    val fieldValue1024CharactersMax: String get() = at("fieldValue1024CharactersMax")
    val file: String get() = at("file")
    val fileDownload: String get() = at("fileDownload")
    val filter: String get() = at("filter")
    val finishYourFirstBookTo: String get() = at("finishYourFirstBookTo")
    val finished: String get() = at("finished")
    val flashDuration: String get() = at("flashDuration")
    val flashEvery: String get() = at("flashEvery")
    val flashOnPageChange: String get() = at("flashOnPageChange")
    val flashWith: String get() = at("flashWith")
    val floatingNavBarKeepReading: String get() = at("floatingNavBarKeepReading")
    val floatingNavigationBar: String get() = at("floatingNavigationBar")
    val fonctions: String get() = at("fonctions")
    val font: String get() = at("font")
    val fontSize: String get() = at("fontSize")
    val fontText: String get() = at("fontText")
    val footer2048CharactersMax: String get() = at("footer2048CharactersMax")
    val forYou: String get() = at("forYou")
    val forceTheScannerToCompare: String get() = at("forceTheScannerToCompare")
    val format: String get() = at("format")
    val forward30Seconds: String get() = at("forward30Seconds")
    val frequency: String get() = at("frequency")
    val fullFeaturedWebClientFor: String get() = at("fullFeaturedWebClientFor")
    val fuzzy: String get() = at("fuzzy")
    val gRer: String get() = at("gRer")
    val gamma: String get() = at("gamma")
    val general: String get() = at("general")
    val genre: String get() = at("genre")
    val genreTab: String get() = at("genreTab")
    val genreTileAppearance: String get() = at("genreTileAppearance")
    val genres: String get() = at("genres")
    val genres2: String get() = at("genres2")
    val giveGenreTilesTheirOwn: String get() = at("giveGenreTilesTheirOwn")
    val goBack: String get() = at("goBack")
    val goOffline: String get() = at("goOffline")
    val goOffline2: String get() = at("goOffline2")
    val goOfflineAsCurrentUser: String get() = at("goOfflineAsCurrentUser")
    val goOnline: String get() = at("goOnline")
    val gotIt: String get() = at("gotIt")
    val gridSize: String get() = at("gridSize")
    val group: String get() = at("group")
    val hideBooksFromTheseLibraries: String get() = at("hideBooksFromTheseLibraries")
    val hideParenthesesInNames: String get() = at("hideParenthesesInNames")
    val highPerformanceHardwareAcceleratedDirectx: String get() = at("highPerformanceHardwareAcceleratedDirectx")
    val highlight: String get() = at("highlight")
    val highlightCompleteSeries: String get() = at("highlightCompleteSeries")
    val home: String get() = at("home")
    val identifyKomf: String get() = at("identifyKomf")
    val ifAggregateOptionIsEnabled: String get() = at("ifAggregateOptionIsEnabled")
    val ifAggregateOptionIsEnabled2: String get() = at("ifAggregateOptionIsEnabled2")
    val ifEntryAlreadyHasA: String get() = at("ifEntryAlreadyHasA")
    val ifTheFirst3Pages: String get() = at("ifTheFirst3Pages")
    val ignoreList: String get() = at("ignoreList")
    val ignoredSeriesAndTheirBooks: String get() = at("ignoredSeriesAndTheirBooks")
    val image: String get() = at("image")
    val imageReader: String get() = at("imageReader")
    val imageSettings: String get() = at("imageSettings")
    val immersiveCardColor: String get() = at("immersiveCardColor")
    val import: String get() = at("import")
    val importAnImage: String get() = at("importAnImage")
    val importIsbnWithinBarcode: String get() = at("importIsbnWithinBarcode")
    val importLocalMediaAssets: String get() = at("importLocalMediaAssets")
    val importMetadataForCbrCbz: String get() = at("importMetadataForCbrCbz")
    val importMetadataFromEpubFiles: String get() = at("importMetadataFromEpubFiles")
    val importMetadataGeneratedByMylar: String get() = at("importMetadataGeneratedByMylar")
    val importSettings: String get() = at("importSettings")
    val importerDesCouverturesDossier: String get() = at("importerDesCouverturesDossier")
    val inTheContinuousReaderA: String get() = at("inTheContinuousReaderA")
    val includeLinks: String get() = at("includeLinks")
    val inclusionMode: String get() = at("inclusionMode")
    val info: String get() = at("info")
    val inline: String get() = at("inline")
    val input: String get() = at("input")
    val install: String get() = at("install")
    val installed: String get() = at("installed")
    val interruption: String get() = at("interruption")
    val invalidWebhookUrl: String get() = at("invalidWebhookUrl")
    val invertSpeechBubbles: String get() = at("invertSpeechBubbles")
    val isbn: String get() = at("isbn")
    val isbnBarcode: String get() = at("isbnBarcode")
    val jeton24CaractResMin: String get() = at("jeton24CaractResMin")
    val jobHistory: String get() = at("jobHistory")
    val kavitaApiKey: String get() = at("kavitaApiKey")
    val kavitaProcessing: String get() = at("kavitaProcessing")
    val kavitaUrl: String get() = at("kavitaUrl")
    val keepScreenOnWhileReading: String get() = at("keepScreenOnWhileReading")
    val hideChapterSeries: String get() = at("hideChapterSeries")
    val chapterManagement: String get() = at("chapterManagement")
    val chapterManagementReport: String get() = at("chapterManagementReport")
    val chapterManagementFailedCount: String get() = at("chapterManagementFailedCount")
    val chapterManagementErrors: String get() = at("chapterManagementErrors")
    val chapterManagementLocalOnly: String get() = at("chapterManagementLocalOnly")
    val chapterManagementDescription: String get() = at("chapterManagementDescription")
    val chooseLibrary: String get() = at("chooseLibrary")
    val chapterManagementUnlinkedOnly: String get() = at("chapterManagementUnlinkedOnly")
    val chapterManagementLinkedOnly: String get() = at("chapterManagementLinkedOnly")
    val chapterManagementMatchSelected: String get() = at("chapterManagementMatchSelected")
    val chapterManagementConfirmBulk: String get() = at("chapterManagementConfirmBulk")
    val chapterManagementEmpty: String get() = at("chapterManagementEmpty")
    val chapterManagementFindMatch: String get() = at("chapterManagementFindMatch")
    val chapterManagementUnlink: String get() = at("chapterManagementUnlink")
    val chapterManagementLinked: String get() = at("chapterManagementLinked")
    val chapterManagementUnlinked: String get() = at("chapterManagementUnlinked")
    val chapterManagementAmbiguous: String get() = at("chapterManagementAmbiguous")
    val chapterManagementNoMatch: String get() = at("chapterManagementNoMatch")
    val chapterManagementLinkedCount: String get() = at("chapterManagementLinkedCount")
    val chapterManagementAmbiguousCount: String get() = at("chapterManagementAmbiguousCount")
    val chapterManagementNoMatchCount: String get() = at("chapterManagementNoMatchCount")
    val key: String get() = at("key")
    val komfSeriesAutoIdentify: String get() = at("komfSeriesAutoIdentify")
    val komfSettings: String get() = at("komfSettings")
    val komfUrl: String get() = at("komfUrl")
    val komgaLogin: String get() = at("komgaLogin")
    val komgaPassword: String get() = at("komgaPassword")
    val komgaProcessing: String get() = at("komgaProcessing")
    val komgaToolkit: String get() = at("komgaToolkit")
    val komgaUrl: String get() = at("komgaUrl")
    val komgaUsername: String get() = at("komgaUsername")
    val komgaWebui: String get() = at("komgaWebui")
    val komgaWebuiEpubReaderAdapted: String get() = at("komgaWebuiEpubReaderAdapted")
    val label: String get() = at("label")
    val labels: String get() = at("labels")
    val lancer: String get() = at("lancer")
    val lancerEtAppliquer: String get() = at("lancerEtAppliquer")
    val language: String get() = at("language")
    val lastRead: String get() = at("lastRead")
    val latestCheckedVersion: String get() = at("latestCheckedVersion")
    val launchProcessingJobsWhenNew: String get() = at("launchProcessingJobsWhenNew")
    val leave: String get() = at("leave")
    val levels: String get() = at("levels")
    val libraries: String get() = at("libraries")
    val library: String get() = at("library")
    val librarySRootFolder: String get() = at("librarySRootFolder")
    val librarySwitcherInPageTitle: String get() = at("librarySwitcherInPageTitle")
    val libraryTypeAffectsSomeOptions: String get() = at("libraryTypeAffectsSomeOptions")
    val lifetime: String get() = at("lifetime")
    val limit: String get() = at("limit")
    val lineHeight: String get() = at("lineHeight")
    val linearLightDownsampling: String get() = at("linearLightDownsampling")
    val links: String get() = at("links")
    val lire: String get() = at("lire")
    val lire2: String get() = at("lire2")
    val list: String get() = at("list")
    val listening: String get() = at("listening")
    val loadFont: String get() = at("loadFont")
    val loadSmallPreviews: String get() = at("loadSmallPreviews")
    val loadSmallPreviewsWhenDragging: String get() = at("loadSmallPreviewsWhenDragging")
    val localArtwork: String get() = at("localArtwork")
    val localDownloadOutdated: String get() = at("localDownloadOutdated")
    val lockScreenRotation: String get() = at("lockScreenRotation")
    val logOut: String get() = at("logOut")
    val login: String get() = at("login")
    val login2: String get() = at("login2")
    val loginWithAnotherAccount: String get() = at("loginWithAnotherAccount")
    val logs: String get() = at("logs")
    val longBreak: String get() = at("longBreak")
    val maintenance: String get() = at("maintenance")
    val male: String get() = at("male")
    val manageConnectedServers: String get() = at("manageConnectedServers")
    val mangabakaOfflineDatabase: String get() = at("mangabakaOfflineDatabase")
    val mangajanaiOnnxModelsPreset: String get() = at("mangajanaiOnnxModelsPreset")
    val manualOrdering: String get() = at("manualOrdering")
    val margins: String get() = at("margins")
    val markAsRead: String get() = at("markAsRead")
    val markAsUnread: String get() = at("markAsUnread")
    val masquerPourTous: String get() = at("masquerPourTous")
    val maximumLogSize: String get() = at("maximumLogSize")
    val mediaManagement: String get() = at("mediaManagement")
    val mediaType: String get() = at("mediaType")
    val median2000MsTheServer: String get() = at("median2000MsTheServer")
    val menu: String get() = at("menu")
    val mergeAdjacentTextBlocksInto: String get() = at("mergeAdjacentTextBlocksInto")
    val mergeGenres: String get() = at("mergeGenres")
    val mergeTags: String get() = at("mergeTags")
    val mergeTextSegments: String get() = at("mergeTextSegments")
    val metadata: String get() = at("metadata")
    val metadataProvidersSettings: String get() = at("metadataProvidersSettings")
    val metadataUpdateJobs: String get() = at("metadataUpdateJobs")
    val model: String get() = at("model")
    val modelUrlSource: String get() = at("modelUrlSource")
    val modernTopAppBarAnd: String get() = at("modernTopAppBarAnd")
    val more: String get() = at("more")
    val morphingCoverImageThatFlies: String get() = at("morphingCoverImageThatFlies")
    val morphingImmersiveCover: String get() = at("morphingImmersiveCover")
    val myAccount: String get() = at("myAccount")
    val myAuthenticationActivity: String get() = at("myAuthenticationActivity")
    val myReadingStats: String get() = at("myReadingStats")
    val myanimelistClientId: String get() = at("myanimelistClientId")
    val name: String get() = at("name")
    val nameMatchingMode: String get() = at("nameMatchingMode")
    val nativeEpub3ReaderWith: String get() = at("nativeEpub3ReaderWith")
    val navigation: String get() = at("navigation")
    val ncnnUpscalerLogs: String get() = at("ncnnUpscalerLogs")
    val newLibraryUi: String get() = at("newLibraryUi")
    val newPassword: String get() = at("newPassword")
    val newUi2: String get() = at("newUi2")
    val newVersionIsAvailable: String get() = at("newVersionIsAvailable")
    val next: String get() = at("next")
    val next2: String get() = at("next2")
    val nextChapter: String get() = at("nextChapter")
    val nextPage: String get() = at("nextPage")
    val nextSegment: String get() = at("nextSegment")
    val nextVolume: String get() = at("nextVolume")
    val noAnnotationsYet: String get() = at("noAnnotationsYet")
    val noBookmarksYet: String get() = at("noBookmarksYet")
    val noBooks: String get() = at("noBooks")
    val noChaptersAvailable: String get() = at("noChaptersAvailable")
    val noGenresFoundInThis: String get() = at("noGenresFoundInThis")
    val noIgnoredSeriesLongPress: String get() = at("noIgnoredSeriesLongPress")
    val noLinksYetUseAdd: String get() = at("noLinksYetUseAdd")
    val noNextSeriesWithThe: String get() = at("noNextSeriesWithThe")
    val noReadingActivityYet: String get() = at("noReadingActivityYet")
    val noResults: String get() = at("noResults")
    val noWorksFoundForThis: String get() = at("noWorksFoundForThis")
    val nonDisponibleSurCettePlateforme: String get() = at("nonDisponibleSurCettePlateforme")
    val none: String get() = at("none")
    val notDownloaded: String get() = at("notDownloaded")
    val note: String get() = at("note")
    val notes: String get() = at("notes")
    val nothingToShow: String get() = at("nothingToShow")
    val notificationContext: String get() = at("notificationContext")
    val notificationSettings: String get() = at("notificationSettings")
    val notificationTemplate: String get() = at("notificationTemplate")
    val notifications: String get() = at("notifications")
    val number: String get() = at("number")
    val ocrEngine: String get() = at("ocrEngine")
    val offline: String get() = at("offline")
    val offlineMode: String get() = at("offlineMode")
    val offlineMode2: String get() = at("offlineMode2")
    val ok: String get() = at("ok")
    val oncePerLibrarySuggestionsAre: String get() = at("oncePerLibrarySuggestionsAre")
    val onnxModelPath: String get() = at("onnxModelPath")
    val onnxRuntimeSupportIsExperimental: String get() = at("onnxRuntimeSupportIsExperimental")
    val onnxruntimeUpscaleMode: String get() = at("onnxruntimeUpscaleMode")
    val openInKomga: String get() = at("openInKomga")
    val openMenu: String get() = at("openMenu")
    val openStats: String get() = at("openStats")
    val operator: String get() = at("operator")
    val orderBooks: String get() = at("orderBooks")
    val originalSeries: String get() = at("originalSeries")
    val otherOptions: String get() = at("otherOptions")
    val otherTags: String get() = at("otherTags")
    val otherwiseThePillSitsAt: String get() = at("otherwiseThePillSitsAt")
    val otherwiseTheTitleIsOverlaid: String get() = at("otherwiseTheTitleIsOverlaid")
    val output: String get() = at("output")
    val outputLevels: String get() = at("outputLevels")
    val ouvrirLeWebui: String get() = at("ouvrirLeWebui")
    val overrideExistingCovers: String get() = at("overrideExistingCovers")
    val overrideExistingPreset: String get() = at("overrideExistingPreset")
    val pRimTresBibliothQue: String get() = at("pRimTresBibliothQue")
    val page: String get() = at("page")
    val pageSpacing: String get() = at("pageSpacing")
    val pageSpacing2: String get() = at("pageSpacing2")
    val pageStreaming: String get() = at("pageStreaming")
    val paged: String get() = at("paged")
    val pages: String get() = at("pages")
    val pagesRead: String get() = at("pagesRead")
    val panelDetection: String get() = at("panelDetection")
    val panels: String get() = at("panels")
    val paraSpacing: String get() = at("paraSpacing")
    val parent: String get() = at("parent")
    val parody: String get() = at("parody")
    val password: String get() = at("password")
    val pauseAtTheLastPage: String get() = at("pauseAtTheLastPage")
    val pdf: String get() = at("pdf")
    val pendingTask: String get() = at("pendingTask")
    val periodicallyWriteTheSameBackup: String get() = at("periodicallyWriteTheSameBackup")
    val pickTheCorrectSeries: String get() = at("pickTheCorrectSeries")
    val pillAtBottomLeft: String get() = at("pillAtBottomLeft")
    val plusTard: String get() = at("plusTard")
    val pointType: String get() = at("pointType")
    val preferOriginalPublisherInsteadOf: String get() = at("preferOriginalPublisherInsteadOf")
    val presetWithThatNameAlready: String get() = at("presetWithThatNameAlready")
    val presets: String get() = at("presets")
    val preventTheApplicationScreenFrom: String get() = at("preventTheApplicationScreenFrom")
    val preventsGhostingOnEInk: String get() = at("preventsGhostingOnEInk")
    val preview: String get() = at("preview")
    val previous: String get() = at("previous")
    val previous2: String get() = at("previous2")
    val previousChapter: String get() = at("previousChapter")
    val previousPage: String get() = at("previousPage")
    val previousSegment: String get() = at("previousSegment")
    val previousVolume: String get() = at("previousVolume")
    val processing: String get() = at("processing")
    val processing2: String get() = at("processing2")
    val prochainesSorties: String get() = at("prochainesSorties")
    val prochainesSorties2: String get() = at("prochainesSorties2")
    val projectLink: String get() = at("projectLink")
    val projectOnGithub: String get() = at("projectOnGithub")
    val providers: String get() = at("providers")
    val publisher: String get() = at("publisher")
    val publisherStyles: String get() = at("publisherStyles")
    val purgerLeTag: String get() = at("purgerLeTag")
    val rAfficher: String get() = at("rAfficher")
    val rInitialiser: String get() = at("rInitialiser")
    val randomSeriesInLibrary: String get() = at("randomSeriesInLibrary")
    val rapidocrModel: String get() = at("rapidocrModel")
    val rapidocrModels: String get() = at("rapidocrModels")
    val rate: String get() = at("rate")
    val reAnalyseLibrary: String get() = at("reAnalyseLibrary")
    val reDownloadModel: String get() = at("reDownloadModel")
    val reDownloadModels: String get() = at("reDownloadModels")
    val reachedTheStartOfThe: String get() = at("reachedTheStartOfThe")
    val read: String get() = at("read")
    val readAloudHighlight: String get() = at("readAloudHighlight")
    val readFromStart: String get() = at("readFromStart")
    val readIncognito: String get() = at("readIncognito")
    val readIncognito2: String get() = at("readIncognito2")
    val readList: String get() = at("readList")
    val readList2: String get() = at("readList2")
    val readLists: String get() = at("readLists")
    val readLists2: String get() = at("readLists2")
    val readProgress: String get() = at("readProgress")
    val readProgressWillNotBe: String get() = at("readProgressWillNotBe")
    val readerSettings: String get() = at("readerSettings")
    val readerType: String get() = at("readerType")
    val reading: String get() = at("reading")
    val readingMode: String get() = at("readingMode")
    val readingMode2: String get() = at("readingMode2")
    val readingOrder: String get() = at("readingOrder")
    val readingStats: String get() = at("readingStats")
    val readsSharedSeriesRelationsFrom: String get() = at("readsSharedSeriesRelationsFrom")
    val rebuildTheReadingOrder: String get() = at("rebuildTheReadingOrder")
    val recentDownloads: String get() = at("recentDownloads")
    val recentlyRead: String get() = at("recentlyRead")
    val recolorsTheTopRightBadge: String get() = at("recolorsTheTopRightBadge")
    val refresh: String get() = at("refresh")
    val refreshMetadata: String get() = at("refreshMetadata")
    val refreshMetadataForLibrary: String get() = at("refreshMetadataForLibrary")
    val releaseDate: String get() = at("releaseDate")
    val releaseDate2: String get() = at("releaseDate2")
    val releaseDate3: String get() = at("releaseDate3")
    val releaseNotes: String get() = at("releaseNotes")
    val reload: String get() = at("reload")
    val remoteUnavailable: String get() = at("remoteUnavailable")
    val removeAnythingInParenthesesWhen: String get() = at("removeAnythingInParenthesesWhen")
    val removeComicinfoXml: String get() = at("removeComicinfoXml")
    val removeUrl: String get() = at("removeUrl")
    val renameGenre: String get() = at("renameGenre")
    val renommer: String get() = at("renommer")
    val repeatNewPassword: String get() = at("repeatNewPassword")
    val replaceTheBottomNavigationBar: String get() = at("replaceTheBottomNavigationBar")
    val requiresAddingThisHostAnd: String get() = at("requiresAddingThisHostAnd")
    val requiresAppRestartToTake: String get() = at("requiresAppRestartToTake")
    val requiresWriteAccessToFiles: String get() = at("requiresWriteAccessToFiles")
    val resetAll: String get() = at("resetAll")
    val resetChannel: String get() = at("resetChannel")
    val resetMetadataKomf: String get() = at("resetMetadataKomf")
    val resetToDefault: String get() = at("resetToDefault")
    val resetToInternal: String get() = at("resetToInternal")
    val respectPublisherColors: String get() = at("respectPublisherColors")
    val restart: String get() = at("restart")
    val restore: String get() = at("restore")
    val restoreAll: String get() = at("restoreAll")
    val retour: String get() = at("retour")
    val retry: String get() = at("retry")
    val returnBook: String get() = at("returnBook")
    val returnLibrary: String get() = at("returnLibrary")
    val returnSerie: String get() = at("returnSerie")
    val rewind10Seconds: String get() = at("rewind10Seconds")
    val rienAppliquer: String get() = at("rienAppliquer")
    val rocmAmdGpusRequiresRocm7: String get() = at("rocmAmdGpusRequiresRocm7")
    val role: String get() = at("role")
    val roles: String get() = at("roles")
    val roles2: String get() = at("roles2")
    val root: String get() = at("root")
    val rootFolder: String get() = at("rootFolder")
    val runInBackground: String get() = at("runInBackground")
    val running: String get() = at("running")
    val sRiesMasquEs: String get() = at("sRiesMasquEs")
    val save: String get() = at("save")
    val saveACopyOfYour: String get() = at("saveACopyOfYour")
    val saveChanges: String get() = at("saveChanges")
    val saveImage: String get() = at("saveImage")
    val savePreset: String get() = at("savePreset")
    val savedSettings: String get() = at("savedSettings")
    val scanAllLibraries: String get() = at("scanAllLibraries")
    val scanForExistingFiles: String get() = at("scanForExistingFiles")
    val scanForTheseFileTypes: String get() = at("scanForTheseFileTypes")
    val scanLibraryFiles: String get() = at("scanLibraryFiles")
    val scanLibraryFilesDeep: String get() = at("scanLibraryFilesDeep")
    val scanText: String get() = at("scanText")
    val scroll: String get() = at("scroll")
    val search: String get() = at("search")
    val searchASeriesByName: String get() = at("searchASeriesByName")
    val searchAll: String get() = at("searchAll")
    val searchCollections: String get() = at("searchCollections")
    val searchOrCreateCollection: String get() = at("searchOrCreateCollection")
    val searchOrCreateReadList: String get() = at("searchOrCreateReadList")
    val searchReadlists: String get() = at("searchReadlists")
    val searchResults: String get() = at("searchResults")
    val searchSeries: String get() = at("searchSeries")
    val searchSettings: String get() = at("searchSettings")
    val select: String get() = at("select")
    val selectAll: String get() = at("selectAll")
    val selectText: String get() = at("selectText")
    val selected: String get() = at("selected")
    val selectionModeClickOnItems: String get() = at("selectionModeClickOnItems")
    val series: String get() = at("series")
    val seriesCovers: String get() = at("seriesCovers")
    val seriesMetadata: String get() = at("seriesMetadata")
    val seriesTitle: String get() = at("seriesTitle")
    val seriesTitleLanguageIso639: String get() = at("seriesTitleLanguageIso639")
    val server: String get() = at("server")
    val serverManagement: String get() = at("serverManagement")
    val serverSettings: String get() = at("serverSettings")
    val serverUnavailable: String get() = at("serverUnavailable")
    val serverUrl: String get() = at("serverUrl")
    val serverUrlLoginAndReading: String get() = at("serverUrlLoginAndReading")
    val settings: String get() = at("settings")
    val shareLibraries: String get() = at("shareLibraries")
    val shareSeriesLinksViaKomga: String get() = at("shareSeriesLinksViaKomga")
    val sharedOnServer: String get() = at("sharedOnServer")
    val sharingLabel: String get() = at("sharingLabel")
    val showASemiTransparentBackground: String get() = at("showASemiTransparentBackground")
    val showDateTime: String get() = at("showDateTime")
    val showFullPage: String get() = at("showFullPage")
    val showLanguageOnCovers: String get() = at("showLanguageOnCovers")
    val showLocation: String get() = at("showLocation")
    val showNavigationBarInImmersive: String get() = at("showNavigationBarInImmersive")
    val showSeriesCount: String get() = at("showSeriesCount")
    val showStatsInBottomNavigation: String get() = at("showStatsInBottomNavigation")
    val showTheNumberOfSeries: String get() = at("showTheNumberOfSeries")
    val showTitleAndMetadataBelow: String get() = at("showTitleAndMetadataBelow")
    val showUpcomingReleasesInBottom: String get() = at("showUpcomingReleasesInBottom")
    val showsAThumbnailWhileDragging: String get() = at("showsAThumbnailWhileDragging")
    val shutDownServer: String get() = at("shutDownServer")
    val shutdown: String get() = at("shutdown")
    val sidePadding: String get() = at("sidePadding")
    val sidePadding2: String get() = at("sidePadding2")
    val similar: String get() = at("similar")
    val sinceV1010: String get() = at("sinceV1010")
    val size: String get() = at("size")
    val slowerButPotentiallyMoreAccurate: String get() = at("slowerButPotentiallyMoreAccurate")
    val smallFrEnPillOn: String get() = at("smallFrEnPillOn")
    val smooth: String get() = at("smooth")
    val sort: String get() = at("sort")
    val sortNumber: String get() = at("sortNumber")
    val sorties: String get() = at("sorties")
    val specialUserThatHasAccess: String get() = at("specialUserThatHasAccess")
    val speechEngine: String get() = at("speechEngine")
    val speechModelNotAvailableOn: String get() = at("speechModelNotAvailableOn")
    val speed: String get() = at("speed")
    val splitLandscapePages: String get() = at("splitLandscapePages")
    val startupScreen: String get() = at("startupScreen")
    val stats: String get() = at("stats")
    val status: String get() = at("status")
    val stopAtEndOfBook: String get() = at("stopAtEndOfBook")
    val stopKomgaApplicationProcess: String get() = at("stopKomgaApplicationProcess")
    val storageLocation: String get() = at("storageLocation")
    val streak: String get() = at("streak")
    val successful: String get() = at("successful")
    val suggestSameAuthorSimilarTitle: String get() = at("suggestSameAuthorSimilarTitle")
    val summary: String get() = at("summary")
    val switch: String get() = at("switch")
    val switchActiveUrl: String get() = at("switchActiveUrl")
    val switchLibrary: String get() = at("switchLibrary")
    val switchToThisServer: String get() = at("switchToThisServer")
    val tagScoreThreshold: String get() = at("tagScoreThreshold")
    val tagSizeLimit: String get() = at("tagSizeLimit")
    val tags: String get() = at("tags")
    val tapToZoom: String get() = at("tapToZoom")
    val tappingInAVerticalStrip: String get() = at("tappingInAVerticalStrip")
    val tensorrtNvidiaGpusRequiresCuda12: String get() = at("tensorrtNvidiaGpusRequiresCuda12")
    val testSend: String get() = at("testSend")
    val testTimeAugmentationSlowerBut: String get() = at("testTimeAugmentationSlowerBut")
    val tester: String get() = at("tester")
    val text: String get() = at("text")
    val textBelowCard: String get() = at("textBelowCard")
    val textDetectionLanguage: String get() = at("textDetectionLanguage")
    val theSearchReturnedNoResults: String get() = at("theSearchReturnedNoResults")
    val thereSNoNextBook: String get() = at("thereSNoNextBook")
    val thereSNoPreviousBook: String get() = at("thereSNoPreviousBook")
    val theseFeaturesAreExperimentalThey: String get() = at("theseFeaturesAreExperimentalThey")
    val thisMonth: String get() = at("thisMonth")
    val thisWeek: String get() = at("thisWeek")
    val thumbnail: String get() = at("thumbnail")
    val tileSize: String get() = at("tileSize")
    val tintTheDetailCardBackground: String get() = at("tintTheDetailCardBackground")
    val title: String get() = at("title")
    val title256CharactersMax: String get() = at("title256CharactersMax")
    val titleBelowCover: String get() = at("titleBelowCover")
    val titleSettings: String get() = at("titleSettings")
    val titleUrl: String get() = at("titleUrl")
    val toggleBookmark: String get() = at("toggleBookmark")
    val toggleTheme: String get() = at("toggleTheme")
    val toggleTranscript: String get() = at("toggleTranscript")
    val topMargin: String get() = at("topMargin")
    val total: String get() = at("total")
    val toutPurger: String get() = at("toutPurger")
    val toutRAfficher: String get() = at("toutRAfficher")
    val toutes: String get() = at("toutes")
    val toutes2: String get() = at("toutes2")
    val tracksBookCompletionsAndShows: String get() = at("tracksBookCompletionsAndShows")
    val transcription: String get() = at("transcription")
    val transcriptionSettings: String get() = at("transcriptionSettings")
    val translate: String get() = at("translate")
    val trySearchingForSomethingElse: String get() = at("trySearchingForSomethingElse")
    val ttaMode: String get() = at("ttaMode")
    val twoSequelsNoOrderBetween: String get() = at("twoSequelsNoOrderBetween")
    val type: String get() = at("type")
    val unavailable: String get() = at("unavailable")
    val unknownSeries: String get() = at("unknownSeries")
    val unlinkSeries: String get() = at("unlinkSeries")
    val unset: String get() = at("unset")
    val update: String get() = at("update")
    val updateModes: String get() = at("updateModes")
    val updateOnnxRuntime: String get() = at("updateOnnxRuntime")
    val updateOrDownloadAnotherVersion: String get() = at("updateOrDownloadAnotherVersion")
    val updateSeriesAlternativeTitleIf: String get() = at("updateSeriesAlternativeTitleIf")
    val updateSeriesTitleIfMatched: String get() = at("updateSeriesTitleIfMatched")
    val updates: String get() = at("updates")
    val updating: String get() = at("updating")
    val uploadBookCovers: String get() = at("uploadBookCovers")
    val uploadSeriesCover: String get() = at("uploadSeriesCover")
    val uploadSeriesCovers: String get() = at("uploadSeriesCovers")
    val upscalerSettings: String get() = at("upscalerSettings")
    val upscaling: String get() = at("upscaling")
    val url: String get() = at("url")
    val urlToolkitHttpHTe: String get() = at("urlToolkitHttpHTe")
    val urls: String get() = at("urls")
    val useFirstAvailableAlternativeTitle: String get() = at("useFirstAvailableAlternativeTitle")
    val useOriginalPublisher: String get() = at("useOriginalPublisher")
    val useSeriesMetadataManualFlips: String get() = at("useSeriesMetadataManualFlips")
    val userSettings: String get() = at("userSettings")
    val username: String get() = at("username")
    val users: String get() = at("users")
    val usesAppriseExecutableInstalledOn: String get() = at("usesAppriseExecutableInstalledOn")
    val usesGoogleSOnDevice: String get() = at("usesGoogleSOnDevice")
    val usesMarkdownSyntaxTemplatesAre: String get() = at("usesMarkdownSyntaxTemplatesAre")
    val usesTensorrtToCreateOptimized: String get() = at("usesTensorrtToCreateOptimized")
    val value: String get() = at("value")
    val velocityTemplateLanguageSyntaxReference: String get() = at("velocityTemplateLanguageSyntaxReference")
    val viewLogs: String get() = at("viewLogs")
    val viewLogs2: String get() = at("viewLogs2")
    val voiceSearch: String get() = at("voiceSearch")
    val volumeKeysNavigation: String get() = at("volumeKeysNavigation")
    val webgpuAnyGpu: String get() = at("webgpuAnyGpu")
    val webhookUrl: String get() = at("webhookUrl")
    val webhooks: String get() = at("webhooks")
    val webtoonSmartScroll: String get() = at("webtoonSmartScroll")
    val whatSNew: String get() = at("whatSNew")
    val whenCropBordersIsOn: String get() = at("whenCropBordersIsOn")
    val whenOnTappingHomeLibrary: String get() = at("whenOnTappingHomeLibrary")
    val whichScreenKoraOpensTo: String get() = at("whichScreenKoraOpensTo")
    val whisperModel: String get() = at("whisperModel")
    val white: String get() = at("white")
    val whiteAndBlack: String get() = at("whiteAndBlack")
    val widgetProchainTome: String get() = at("widgetProchainTome")
    val write: String get() = at("write")
    val wrongSeriesSearchAnilist: String get() = at("wrongSeriesSearchAnilist")
    val youAreEditingAuthorsFor: String get() = at("youAreEditingAuthorsFor")
    val youAreEditingTagsFor: String get() = at("youAreEditingTagsFor")
    val youAreEditingTagsFor2: String get() = at("youAreEditingTagsFor2")
    val youHaveUnsavedChanges: String get() = at("youHaveUnsavedChanges")
    val yourRating: String get() = at("yourRating")
    val yourReading: String get() = at("yourReading")
    // endregion
}

/** Bottom navigation bar. */
data class NavigationStrings(
    val libraries: String,
    val home: String,
    val search: String,
    val stats: String,
    val releases: String,
    val settings: String,
)

/**
 * The "For you" tab and the series "Similar" tab.
 *
 * The parameterised entries are functions rather than format strings: the
 * placeholder then cannot be lost in translation, and the compiler checks the
 * argument instead of a `%s` that silently disappears.
 */
data class SuggestionStrings(
    val closestMatches: String,
    val moreForYou: String,
    val becauseYouLiked: (String) -> String,
    val becauseYouRead: (String) -> String,
    val becauseYouAreReading: (String) -> String,
    val showRead: String,
    val buildingProfile: String,
    val analysingLibrary: (Int) -> String,
    val failed: String,
    val emptyNoProfile: String,
    val emptyAllRead: String,
    val profileSize: (Int) -> String,
    val resetDismissed: (Int) -> String,
    val markPlanned: String,
    val unmarkPlanned: String,
    val notInterested: String,
)

data class KomfStrings(
    val providerSettings: KomfProviderSettingsStrings
)

data class KomfProviderSettingsStrings(
    val providerAniList: String,
    val providerBangumi: String,
    val providerBookWalker: String,
    val providerComicVine: String,
    val providerHentag: String,
    val providerKodansha: String,
    val providerMal: String,
    val providerMangaBaka: String,
    val providerMangaUpdates: String,
    val providerMangaDex: String,
    val providerNautiljon: String,
    val providerYenPress: String,
    val providerViz: String,
    val providerWebtoons: String,

    ) {

    fun forProvider(provider: KomfProviders) =
        when (provider) {
            KomfCoreProviders.ANILIST -> providerAniList
            KomfCoreProviders.BANGUMI -> providerBangumi
            KomfCoreProviders.BOOK_WALKER -> providerBookWalker
            KomfCoreProviders.COMIC_VINE -> providerComicVine
            KomfCoreProviders.HENTAG -> providerHentag
            KomfCoreProviders.KODANSHA -> providerKodansha
            KomfCoreProviders.MAL -> providerMal
            KomfCoreProviders.MANGA_UPDATES -> providerMangaUpdates
            KomfCoreProviders.MANGADEX -> providerMangaDex
            KomfCoreProviders.NAUTILJON -> providerNautiljon
            KomfCoreProviders.YEN_PRESS -> providerYenPress
            KomfCoreProviders.VIZ -> providerViz
            KomfCoreProviders.MANGA_BAKA -> providerMangaBaka
            KomfCoreProviders.WEBTOONS -> providerWebtoons
            is UnknownKomfProvider -> provider.name
        }
}


data class SeriesViewStrings(
    val statusEnded: String,
    val statusOngoing: String,
    val statusAbandoned: String,
    val statusHiatus: String,

    val readingDirectionLeftToRight: String,
    val readingDirectionRightToLeft: String,
    val readingDirectionVertical: String,
    val readingDirectionWebtoon: String,
) {
    fun forSeriesStatus(status: KomgaSeriesStatus): String {
        return when (status) {
            ENDED -> statusEnded
            ONGOING -> statusOngoing
            ABANDONED -> statusAbandoned
            HIATUS -> statusHiatus
        }
    }

    fun forReadingDirection(direction: KomgaReadingDirection): String {
        return when (direction) {
            LEFT_TO_RIGHT -> readingDirectionLeftToRight
            RIGHT_TO_LEFT -> readingDirectionRightToLeft
            VERTICAL -> readingDirectionVertical
            WEBTOON -> readingDirectionWebtoon
        }
    }

}

data class SeriesEditStrings(
    val title: String,
    val sortTitle: String,
    val summary: String,
    val language: String,

    val status: String,
    val statusEnded: String,
    val statusOngoing: String,
    val statusAbandoned: String,
    val statusHiatus: String,

    val readingDirection: String,
    val readingDirectionLeftToRight: String,
    val readingDirectionRightToLeft: String,
    val readingDirectionVertical: String,
    val readingDirectionWebtoon: String,

    val publisher: String,
    val ageRating: String,
    val totalBookCount: String,
) {

    fun forSeriesStatus(status: KomgaSeriesStatus): String {
        return when (status) {
            ENDED -> statusEnded
            ONGOING -> statusOngoing
            ABANDONED -> statusAbandoned
            HIATUS -> statusHiatus
        }
    }

    fun forReadingDirection(direction: KomgaReadingDirection): String {
        return when (direction) {
            LEFT_TO_RIGHT -> readingDirectionLeftToRight
            RIGHT_TO_LEFT -> readingDirectionRightToLeft
            VERTICAL -> readingDirectionVertical
            WEBTOON -> readingDirectionWebtoon
        }
    }
}

data class BookEditStrings(
    val title: String,
    val number: String,
    val sortNumber: String,
    val summary: String,
    val releaseDate: String,
    val isbn: String,
)

data class LibraryEditStrings(
    val emptyTrashAfterScan: String,
    val scanForceModifiedTime: String,
    val scanOnStartup: String,
    val oneshotsDirectory: String,
    val excludeDirectories: String,
    val scanInterval: String,
    val scanIntervalDisabled: String,
    val scanIntervalHourly: String,
    val scanIntervalEvery6H: String,
    val scanIntervalEvery12H: String,
    val scanIntervalDaily: String,
    val scanIntervalWeekly: String,


    val hashFiles: String,
    val hashPages: String,
    val analyzeDimensions: String,
    val repairExtensions: String,
    val convertToCbz: String,
    val seriesCover: String,

    val coverFirst: String,
    val coverFirstUnreadOrFirst: String,
    val coverFirstUnreadOrLast: String,
    val coverLast: String,
) {

    fun forSeriesCover(cover: SeriesCover): String {
        return when (cover) {
            FIRST -> coverFirst
            FIRST_UNREAD_OR_FIRST -> coverFirstUnreadOrFirst
            FIRST_UNREAD_OR_LAST -> coverFirstUnreadOrLast
            LAST -> coverLast
        }
    }

    fun forScanInterval(scanInterval: ScanInterval): String {
        return when (scanInterval) {
            DISABLED -> scanIntervalDisabled
            HOURLY -> scanIntervalHourly
            EVERY_6H -> scanIntervalEvery6H
            EVERY_12H -> scanIntervalEvery12H
            DAILY -> scanIntervalDaily
            WEEKLY -> scanIntervalWeekly
        }
    }
}

data class UserEditStrings(
    val contentRestrictions: String,
    val age: String,
    val labelsAllow: String,
    val labelsExclude: String,
    val ageRestriction: String,
    val ageRestrictionNone: String,
    val ageRestrictionAllowOnly: String,
    val ageRestrictionExclude: String,
) {
    fun forAgeRestriction(ageRestriction: UserEditDialogViewModel.AgeRestriction): String {
        return when (ageRestriction) {
            NONE -> ageRestrictionNone
            ALLOW_ONLY -> ageRestrictionAllowOnly
            EXCLUDE -> ageRestrictionExclude
        }
    }
}

data class ReaderStrings(
    val zoom: String,
    val readerPaged: String,
    val readerPanels: String,
    val readerType: String,
    val readerContinuous: String,
    val stretchToFit: String,
    val decoder: String,
    val pagesInfo: String,
    val pageNumber: String,
    val memoryUsage: String,
    val pageDisplaySize: String,
    val pageOriginalSize: String,

    val tapNavigation: String,
    val modeLeftRight: String,
    val modeLeftRightDesc: String,
    val modeRightLeft: String,
    val modeRightLeftDesc: String,
    val modeHorizontalSplit: String,
    val modeHorizontalSplitDesc: String,
    val modeReversedHorizontalSplit: String,
    val modeReversedHorizontalSplitDesc: String,
) {
    fun forReaderType(type: ReaderType): String {
        return when (type) {
            ReaderType.PAGED -> readerPaged
            ReaderType.PANELS -> readerPanels
            ReaderType.CONTINUOUS -> readerContinuous
        }
    }

}

data class PagedReaderStrings(
    val scaleType: String,
    val scaleScreen: String,
    val scaleFitWidth: String,
    val scaleFitHeight: String,
    val scaleOriginal: String,

    val readingDirection: String,
    val readingDirectionLeftToRight: String,
    val readingDirectionRightToLeft: String,

    val layout: String,
    val layoutSinglePage: String,
    val layoutDoublePages: String,
    val layoutDoublePagesNoCover: String,
    val offsetPages: String,
    val adaptiveBackground: String,
) {
    fun forScaleType(type: LayoutScaleType): String {
        return when (type) {
            LayoutScaleType.SCREEN -> scaleScreen
            LayoutScaleType.FIT_WIDTH -> scaleFitWidth
            LayoutScaleType.FIT_HEIGHT -> scaleFitHeight
            LayoutScaleType.ORIGINAL -> scaleOriginal
        }
    }

    fun forReadingDirection(direction: PagedReadingDirection): String {
        return when (direction) {
            PagedReadingDirection.LEFT_TO_RIGHT -> readingDirectionLeftToRight
            PagedReadingDirection.RIGHT_TO_LEFT -> readingDirectionRightToLeft
        }
    }

    fun forLayout(layout: PageDisplayLayout): String {
        return when (layout) {
            PageDisplayLayout.SINGLE_PAGE -> layoutSinglePage
            PageDisplayLayout.DOUBLE_PAGES -> layoutDoublePages
            PageDisplayLayout.DOUBLE_PAGES_NO_COVER -> layoutDoublePagesNoCover
        }
    }
}

data class ContinuousReaderStrings(
    val sidePadding: String,
    val pageSpacing: String,

    val readingDirection: String,
    val readingDirectionTopToBottom: String,
    val readingDirectionLeftToRight: String,
    val readingDirectionRightToLeft: String,
) {

    fun forReadingDirection(direction: ContinuousReadingDirection): String {
        return when (direction) {
            ContinuousReadingDirection.TOP_TO_BOTTOM -> readingDirectionTopToBottom
            ContinuousReadingDirection.LEFT_TO_RIGHT -> readingDirectionLeftToRight
            ContinuousReadingDirection.RIGHT_TO_LEFT -> readingDirectionRightToLeft
        }
    }
}

data class SettingsStrings(
    val language: String,
    val languageSystem: String,
    val serverSettings: String,
    val thumbnailSize: String,
    val thumbnailSizeDefault: String,
    val thumbnailSizeMedium: String,
    val thumbnailSizeLarge: String,
    val thumbnailSizeXLarge: String,

    val thumbnailRegenTitle: String,
    val thumbnailRegenBody: String,
    val thumbnailRegenIfBigger: String,
    val thumbnailRegenAllBooks: String,
    val thumbnailRegenNo: String,


    val deleteEmptyCollections: String,
    val deleteEmptyReadLists: String,
    val taskPoolSize: String,
    val rememberMeDurationDays: String,
    val renewRememberMeKey: String,
    val serverPort: String,
    val serverContextPath: String,
    val requiresRestart: String,
    val serverSettingsDiscard: String,
    val serverSettingsSave: String,

    val appTheme: String,
    val appThemeDark: String,
    val appThemeLight: String,
    val appThemeOled: String,
    val appThemeLightModern: String,
    val appThemeDarkModern: String,
    val imageCardSize: String,
    val cardShadowLevel: String,
    val cardCornerRadius: String,
    val decoder: String,

    val epubReaderTypeKomga: String,
    val epubReaderTypeTtsu: String,
    val epubReaderTypeEpub3: String,
) {
    fun forThumbnailSize(size: KomgaThumbnailSize): String {
        return when (size) {
            DEFAULT -> thumbnailSizeDefault
            MEDIUM -> thumbnailSizeMedium
            LARGE -> thumbnailSizeLarge
            XLARGE -> thumbnailSizeXLarge
        }
    }

    fun forAppTheme(theme: AppTheme): String {
        return when (theme) {
            AppTheme.DARK -> appThemeDark
            AppTheme.LIGHT -> appThemeLight
            AppTheme.DARKER -> appThemeOled
            AppTheme.LIGHT_MODERN -> appThemeLightModern
            AppTheme.DARK_MODERN -> appThemeDarkModern
        }
    }

    fun forEpubReaderType(readerType: EpubReaderType): String {
        return when (readerType) {
            EpubReaderType.KOMGA_EPUB -> epubReaderTypeKomga
            EpubReaderType.TTSU_EPUB -> epubReaderTypeTtsu
            EpubReaderType.EPUB3_READER -> epubReaderTypeEpub3
        }
    }
}

data class FilterStrings(
    val anyValue: String,

    val filterTagsSearch: String,
    val filterTagsReset: String,
    val filterTagsGenreLabel: String,
    val filterTagsTagsLabel: String,
    val filterTagsShowMore: String,
    val filterTagsShowLess: String,

    val includeIfAllMatch: String,
    val includeIfAnyMatch: String,
    val excludeIfAllMatch: String,
    val excludeIfAnyMatch: String,
) {

    fun forInclusionMode(mode: SeriesFilterState.TagInclusionMode) = when (mode) {
        SeriesFilterState.TagInclusionMode.INCLUDE_IF_ALL_MATCH -> includeIfAllMatch
        SeriesFilterState.TagInclusionMode.INCLUDE_IF_ANY_MATCH -> includeIfAnyMatch
    }

    fun forExclusionMode(mode: SeriesFilterState.TagExclusionMode) = when (mode) {
        SeriesFilterState.TagExclusionMode.EXCLUDE_IF_ANY_MATCH -> excludeIfAnyMatch
        SeriesFilterState.TagExclusionMode.EXCLUDE_IF_ALL_MATCH -> excludeIfAllMatch
    }
}

data class SeriesFilterStrings(
    val resetFilters: String,
    val hideFilters: String,
    val anyValue: String,
    val search: String,
    val sort: String,
    val sortRandom: String,
    val sortTitleAsc: String,
    val sortTitleDesc: String,
    val sortDateAddedAsc: String,
    val sortDateAddedDesc: String,
    val sortReleaseDateAsc: String,
    val sortReleaseDateDesc: String,
    val sortUpdatedAsc: String,
    val sortUpdatedDesc: String,
    val sortFolderNameAsc: String,
    val sortFolderNameDesc: String,
    val sortBooksCountAsc: String,
    val sortBooksCountDesc: String,

    val filterTagsLabel: String,

    val readStatus: String,
    val readStatusUnread: String,
    val readStatusInProgress: String,
    val readStatusRead: String,

    val publicationStatus: String,
    val pubStatusEnded: String,
    val pubStatusOngoing: String,
    val pubStatusAbandoned: String,
    val pubStatusHiatus: String,

    val complete: String,
    val oneshot: String,
    val authors: String,
    val publisher: String,
    val language: String,
    val releaseDate: String,
    val ageRating: String,
) {

    fun forSeriesSort(sort: LibrarySeriesTabState.SeriesSort): String {
        return when (sort) {
            LibrarySeriesTabState.SeriesSort.TITLE_ASC -> sortTitleAsc
            LibrarySeriesTabState.SeriesSort.TITLE_DESC -> sortTitleDesc
            LibrarySeriesTabState.SeriesSort.DATE_ADDED_ASC -> sortDateAddedAsc
            LibrarySeriesTabState.SeriesSort.DATE_ADDED_DESC -> sortDateAddedDesc
            LibrarySeriesTabState.SeriesSort.RELEASE_DATE_ASC -> sortReleaseDateAsc
            LibrarySeriesTabState.SeriesSort.RELEASE_DATE_DESC -> sortReleaseDateDesc
            LibrarySeriesTabState.SeriesSort.UPDATED_DESC -> sortUpdatedDesc
            LibrarySeriesTabState.SeriesSort.UPDATED_ASC -> sortUpdatedAsc
            LibrarySeriesTabState.SeriesSort.RANDOM -> sortRandom
//            FOLDER_NAME_ASC -> sortFolderNameAsc
//            FOLDER_NAME_DESC -> sortFolderNameDesc
//            BOOKS_COUNT_ASC -> sortBooksCountAsc
//            BOOKS_COUNT_DESC -> sortBooksCountDesc
        }
    }

    fun forSeriesReadStatus(status: KomgaReadStatus): String {
        return when (status) {
            UNREAD -> readStatusUnread
            IN_PROGRESS -> readStatusInProgress
            READ -> readStatusRead
        }
    }

    fun forPublicationStatus(status: KomgaSeriesStatus): String {
        return when (status) {
            ENDED -> pubStatusEnded
            ONGOING -> pubStatusOngoing
            ABANDONED -> pubStatusAbandoned
            HIATUS -> pubStatusHiatus
        }
    }

}

data class BookFilterStrings(
    val sort: String,
    val sortNumberAsc: String,
    val sortNumberDesc: String,
    val sortFileNameAsc: String,
    val sortFileNameDesc: String,
    val sortReleaseDateAsc: String,
    val sortReleaseDateDesc: String,

    val readStatus: String,
    val readStatusUnread: String,
    val readStatusInProgress: String,
    val readStatusRead: String,

    val authors: String,
    val tags: String,
) {

    fun forReadStatus(status: KomgaReadStatus): String {
        return when (status) {
            UNREAD -> readStatusUnread
            IN_PROGRESS -> readStatusInProgress
            READ -> readStatusRead
        }
    }

    fun forBookSort(sort: BooksSort): String {
        return when (sort) {
            BooksSort.NUMBER_ASC -> sortNumberAsc
            BooksSort.NUMBER_DESC -> sortNumberDesc
//            BooksSort.FILENAME_ASC -> sortFileNameAsc
//            BooksSort.FILENAME_DESC -> sortFileNameDesc
//            BooksSort.RELEASE_DATE_ASC -> sortReleaseDateAsc
//            BooksSort.RELEASE_DATE_DESC -> sortReleaseDateDesc
        }

    }
}


data class ErrorCodes(
    val err1000: String,
    val err1001: String,
    val err1002: String,
    val err1003: String,
    val err1004: String,
    val err1005: String,
    val err1006: String,
    val err1007: String,
    val err1008: String,
    val err1009: String,
    val err1015: String,
    val err1016: String,
    val err1017: String,
    val err1018: String,
    val err1019: String,
    val err1020: String,
    val err1021: String,
    val err1022: String,
    val err1023: String,
    val err1024: String,
    val err1025: String,
    val err1026: String,
    val err1027: String,
    val err1028: String,
    val err1029: String,
    val err1030: String,
    val err1031: String,
    val err1032: String,
    val err1033: String,
    val err1034: String,
    val err1035: String,
    val err1036: String,
    val err1037: String,
    val err1038: String,
    val err1039: String,
) {
    private val codeMap: Map<String, String> = mapOf(
        "ERR_1000" to err1000,
        "ERR_1001" to err1001,
        "ERR_1002" to err1002,
        "ERR_1003" to err1003,
        "ERR_1004" to err1004,
        "ERR_1005" to err1005,
        "ERR_1006" to err1006,
        "ERR_1007" to err1007,
        "ERR_1008" to err1008,
        "ERR_1009" to err1009,
        "ERR_1015" to err1015,
        "ERR_1016" to err1016,
        "ERR_1017" to err1017,
        "ERR_1018" to err1018,
        "ERR_1019" to err1019,
        "ERR_1020" to err1020,
        "ERR_1021" to err1021,
        "ERR_1022" to err1022,
        "ERR_1023" to err1023,
        "ERR_1024" to err1024,
        "ERR_1025" to err1025,
        "ERR_1026" to err1026,
        "ERR_1027" to err1027,
        "ERR_1028" to err1028,
        "ERR_1029" to err1029,
        "ERR_1030" to err1030,
        "ERR_1031" to err1031,
        "ERR_1032" to err1032,
        "ERR_1033" to err1033,
        "ERR_1034" to err1034,
        "ERR_1035" to err1036,
        "ERR_1036" to err1036,
        "ERR_1037" to err1037,
        "ERR_1038" to err1038,
        "ERR_1039" to err1039,
    )

    fun getMessageForCode(code: String) = codeMap[code] ?: "Unknown error: $code"
}

data class ImageSettingsStrings(
    val upsamplingMode: String,
    val upsamplingModeNearest: String,
    val upsamplingModeBilinear: String,
    val upsamplingModeMitchell: String,
    val upsamplingModeCatmullRom: String,

    val downsamplingKernel: String,
    val downsamplingKernelNearest: String,
    val downsamplingKernelLinear: String,
    val downsamplingKernelCubic: String,
    val downsamplingKernelMitchell: String,
    val downsamplingKernelLanczos2: String,
    val downsamplingKernelLanczos3: String,
    val downsamplingKernelMKS2013: String,
    val downsamplingKernelMKS2021: String,
    val downsamplingKernelDefault: String,

    val ncnnUpscaleMode: String,
    val ncnnUpscaleModeNone: String,
    val ncnnUpscaleModeWaifu2x: String,
    val ncnnUpscaleModeRealCugan: String,
    val ncnnUpscaleModeRealSr: String,
    val ncnnUpscaleModeRealEsrgan: String,
    val ncnnUpscaleOnLoad: String,
    val ncnnUpscaleOnLoadThreshold: String,
    val ncnnUpscaleOnLoadTooltip: String,

    val onnxRuntimeExecutionProvider: String,
    val onnxRuntimeUpscaleMode: String,
    val onnxRuntimeUpscaleModeNone: String,
    val onnxRuntimeUpscaleModeUserModel: String,
    val onnxRuntimeUpscaleModeMangaJaNai: String,
) {
    fun forUpsamplingMode(mode: UpsamplingMode): String {
        return when (mode) {
            UpsamplingMode.NEAREST -> upsamplingModeNearest
            UpsamplingMode.BILINEAR -> upsamplingModeBilinear
            UpsamplingMode.MITCHELL -> upsamplingModeMitchell
            UpsamplingMode.CATMULL_ROM -> upsamplingModeCatmullRom
        }
    }

    fun forDownsamplingKernel(kernel: ReduceKernel): String {
        return when (kernel) {
            ReduceKernel.NEAREST -> downsamplingKernelNearest
            ReduceKernel.LINEAR -> downsamplingKernelLinear
            ReduceKernel.CUBIC -> downsamplingKernelCubic
            ReduceKernel.MITCHELL -> downsamplingKernelMitchell
            ReduceKernel.LANCZOS2 -> downsamplingKernelLanczos2
            ReduceKernel.LANCZOS3 -> downsamplingKernelLanczos3
            ReduceKernel.MKS2013 -> downsamplingKernelMKS2013
            ReduceKernel.MKS2021 -> downsamplingKernelMKS2021
            ReduceKernel.DEFAULT -> downsamplingKernelDefault
        }
    }

    fun forOnnxRuntimeUpscaleMode(mode: UpscaleMode): String {
        return when (mode) {
            UpscaleMode.USER_SPECIFIED_MODEL -> onnxRuntimeUpscaleModeUserModel
            UpscaleMode.MANGAJANAI_PRESET -> onnxRuntimeUpscaleModeMangaJaNai
            UpscaleMode.NONE -> onnxRuntimeUpscaleModeNone
        }
    }
}