package snd.komelia.ui.reader.epub

import coil3.PlatformContext
import kotlinx.coroutines.CoroutineScope
import snd.komelia.AppNotifications
import snd.komelia.AppWindowState
import snd.komelia.fonts.UserFontsRepository
import snd.komelia.komga.api.KomgaBookApi
import snd.komelia.komga.api.KomgaReadListApi
import snd.komelia.komga.api.KomgaSeriesApi
import snd.komelia.komga.api.model.KomeliaBook
import snd.komelia.settings.CommonSettingsRepository
import snd.komelia.settings.EpubReaderSettingsRepository
import snd.komelia.ui.BookSiblingsContext
import snd.komelia.ui.platform.PlatformType
import snd.komga.client.book.KomgaBookId

// Unreachable in practice — EPUB3_READER option is hidden on Web.
actual fun createEpub3ReaderState(
    bookId: KomgaBookId,
    book: KomeliaBook?,
    platformContext: PlatformContext,
    markReadProgress: Boolean,
    bookApi: KomgaBookApi,
    seriesApi: KomgaSeriesApi,
    readListApi: KomgaReadListApi,
    settingsRepository: CommonSettingsRepository,
    epubSettingsRepository: EpubReaderSettingsRepository,
    epubBookmarkRepository: snd.komelia.bookmarks.EpubBookmarkRepository,
    bookAnnotationRepository: snd.komelia.annotations.BookAnnotationRepository,
    readerSyncService: snd.komelia.sync.ReaderSyncService,
    komgaEvents: snd.komelia.ManagedKomgaEvents,
    fontsRepository: UserFontsRepository,
    notifications: AppNotifications,
    windowState: AppWindowState,
    platformType: PlatformType,
    coroutineScope: CoroutineScope,
    bookSiblingsContext: BookSiblingsContext,
    onExit: (KomeliaBook) -> Unit,
): EpubReaderState = KomgaEpubReaderState(
    bookId = bookId,
    book = book,
    bookApi = bookApi,
    seriesApi = seriesApi,
    readListApi = readListApi,
    settingsRepository = settingsRepository,
    notifications = notifications,
    markReadProgress = markReadProgress,
    epubSettingsRepository = epubSettingsRepository,
    windowState = windowState,
    platformType = platformType,
    coroutineScope = coroutineScope,
    bookSiblingsContext = bookSiblingsContext,
    onExit = onExit,
)
