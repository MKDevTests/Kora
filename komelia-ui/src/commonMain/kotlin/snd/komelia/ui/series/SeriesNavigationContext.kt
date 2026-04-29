package snd.komelia.ui.series

import snd.komga.client.library.KomgaLibraryId
import snd.komga.client.series.KomgaSeriesId

object SeriesNavigationContext {
    data class SeriesListContext(
        val libraryId: KomgaLibraryId?,
        val filter: SeriesFilter,
        val pageSize: Int,
        val currentPage: Int,
        val seriesIndexInPage: Int,
    )

    private val contexts = mutableMapOf<KomgaSeriesId, SeriesListContext>()

    fun put(seriesId: KomgaSeriesId, context: SeriesListContext) {
        contexts[seriesId] = context
    }

    fun get(seriesId: KomgaSeriesId): SeriesListContext? {
        return contexts[seriesId]
    }
}
