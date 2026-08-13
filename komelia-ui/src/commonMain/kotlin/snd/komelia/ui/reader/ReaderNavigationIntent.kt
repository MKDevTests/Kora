package snd.komelia.ui.reader

import kotlinx.coroutines.flow.MutableStateFlow
import snd.komga.client.library.KomgaLibraryId
import snd.komga.client.series.KomgaSeriesId

sealed class ReaderExitDestination {
    data class Series(val id: KomgaSeriesId) : ReaderExitDestination()
    data class Library(val id: KomgaLibraryId) : ReaderExitDestination()

    /**
     * The home screen. Unlike the two above it carries no id and is not pushed:
     * home is the root of the inner navigator, so it is reached by replacing the
     * stack, exactly like the navigation bar's home button does.
     */
    data object Home : ReaderExitDestination()
}

object ReaderNavigationIntent {
    val pending = MutableStateFlow<ReaderExitDestination?>(null)
}
