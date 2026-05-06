package snd.komelia.ui.reader

import kotlinx.coroutines.flow.MutableStateFlow
import snd.komga.client.library.KomgaLibraryId
import snd.komga.client.series.KomgaSeriesId

sealed class ReaderExitDestination {
    data class Series(val id: KomgaSeriesId) : ReaderExitDestination()
    data class Library(val id: KomgaLibraryId) : ReaderExitDestination()
}

object ReaderNavigationIntent {
    val pending = MutableStateFlow<ReaderExitDestination?>(null)
}
