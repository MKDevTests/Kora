package snd.komelia.ui.library

import kotlinx.serialization.Serializable
import snd.komelia.ui.library.LibrarySeriesTabState
import snd.komelia.ui.series.SeriesFilter
import snd.komelia.ui.series.SeriesFilterState
import snd.komga.client.book.KomgaReadStatus
import snd.komga.client.common.KomgaAuthor
import snd.komga.client.series.KomgaSeriesStatus

/**
 * Serializable mirror of [SeriesFilter] used to persist library filters as JSON.
 * Mirrors the source data class field-by-field so kotlinx.serialization stays
 * decoupled from any annotation requirement on [SeriesFilter] itself.
 */
@Serializable
data class SeriesFilterDto(
    val searchTerm: String = "",
    val sortOrder: LibrarySeriesTabState.SeriesSort = LibrarySeriesTabState.SeriesSort.TITLE_ASC,
    val readStatus: List<KomgaReadStatus> = emptyList(),
    val publicationStatus: List<KomgaSeriesStatus> = emptyList(),
    val includeGenres: List<String> = emptyList(),
    val includeTags: List<String> = emptyList(),
    val excludeGenres: List<String> = emptyList(),
    val excludeTags: List<String> = emptyList(),
    val inclusionMode: SeriesFilterState.TagInclusionMode = SeriesFilterState.TagInclusionMode.INCLUDE_IF_ALL_MATCH,
    val exclusionMode: SeriesFilterState.TagExclusionMode = SeriesFilterState.TagExclusionMode.EXCLUDE_IF_ANY_MATCH,
    val authors: List<KomgaAuthor> = emptyList(),
    val releaseDates: List<String> = emptyList(),
    val ageRatings: List<String> = emptyList(),
    val publishers: List<String> = emptyList(),
    val languages: List<String> = emptyList(),
    val complete: SeriesFilterState.Completion = SeriesFilterState.Completion.ANY,
    val oneshot: SeriesFilterState.Format = SeriesFilterState.Format.ANY,
    val letterFilter: String? = null,
) {
    fun toDomain(): SeriesFilter = SeriesFilter(
        isChanged = true,
        searchTerm = searchTerm,
        sortOrder = sortOrder,
        readStatus = readStatus,
        publicationStatus = publicationStatus,
        includeGenres = includeGenres,
        includeTags = includeTags,
        excludeGenres = excludeGenres,
        excludeTags = excludeTags,
        inclusionMode = inclusionMode,
        exclusionMode = exclusionMode,
        authors = authors,
        releaseDates = releaseDates,
        ageRatings = ageRatings,
        publishers = publishers,
        languages = languages,
        complete = complete,
        oneshot = oneshot,
        letterFilter = letterFilter,
    )

    companion object {
        fun from(filter: SeriesFilter): SeriesFilterDto = SeriesFilterDto(
            searchTerm = filter.searchTerm,
            sortOrder = filter.sortOrder,
            readStatus = filter.readStatus,
            publicationStatus = filter.publicationStatus,
            includeGenres = filter.includeGenres,
            includeTags = filter.includeTags,
            excludeGenres = filter.excludeGenres,
            excludeTags = filter.excludeTags,
            inclusionMode = filter.inclusionMode,
            exclusionMode = filter.exclusionMode,
            authors = filter.authors,
            releaseDates = filter.releaseDates,
            ageRatings = filter.ageRatings,
            publishers = filter.publishers,
            languages = filter.languages,
            complete = filter.complete,
            oneshot = filter.oneshot,
            letterFilter = filter.letterFilter,
        )
    }
}
