package snd.komelia.ui.reader.epub

import coil3.PlatformContext
import kotlinx.coroutines.CoroutineScope
import snd.komelia.AppNotifications
import snd.komelia.AppWindowState
import snd.komelia.ManagedKomgaEvents
import snd.komelia.fonts.UserFontsRepository
import snd.komelia.komga.api.KomgaBookApi
import snd.komelia.komga.api.KomgaReadListApi
import snd.komelia.komga.api.KomgaSeriesApi
import snd.komelia.komga.api.model.KomeliaBook
import snd.komelia.settings.CommonSettingsRepository
import snd.komelia.settings.EpubReaderSettingsRepository
import snd.komelia.sync.ReaderSyncService
import snd.komelia.ui.BookSiblingsContext
import snd.komelia.ui.platform.PlatformType
import snd.komga.client.book.KomgaBookId

expect fun createEpub3ReaderState(
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
    readerSyncService: ReaderSyncService,
    komgaEvents: ManagedKomgaEvents,
    fontsRepository: UserFontsRepository,
    notifications: AppNotifications,
    windowState: AppWindowState,
    platformType: PlatformType,
    coroutineScope: CoroutineScope,
    bookSiblingsContext: BookSiblingsContext,
    onExit: (KomeliaBook) -> Unit,
): EpubReaderState
