package snd.komelia.ui.series

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.until
import snd.komelia.AppNotifications
import snd.komelia.hidden.HIDDEN_TAG
import snd.komelia.komga.api.KomgaReferentialApi
import snd.komelia.ui.library.LibrarySeriesTabState.SeriesSort
import snd.komelia.ui.library.SeriesScreenFilter
import snd.komelia.ui.platform.ScreenSerializable
import snd.komelia.ui.series.SeriesFilter.Companion.DEFAULT
import snd.komelia.ui.series.SeriesFilterState.Completion
import snd.komelia.ui.series.SeriesFilterState.Format
import snd.komelia.ui.series.SeriesFilterState.TagExclusionMode
import snd.komelia.ui.series.SeriesFilterState.TagInclusionMode
import snd.komga.client.book.KomgaReadStatus
import snd.komga.client.common.KomgaAuthor
import snd.komga.client.library.KomgaLibrary
import snd.komga.client.library.KomgaLibraryId
import snd.komga.client.search.KomgaSearchCondition
import snd.komga.client.search.SeriesConditionBuilder
import snd.komga.client.series.KomgaSeriesStatus

/**
 * One criterion currently narrowing a series list, in the form the chips row
 * above the grid shows it.
 *
 * This exists because a filtered library was indistinguishable from an empty
 * one. Tapping an author on a series opens the library with that author
 * applied, and the screen it lands on is titled "BD", says "1 SERIES", and
 * names the author nowhere — so a narrow filter reads as a broken library.
 * The only signal was a tinted filter icon, which said *that* something was
 * filtered and never *what*.
 *
 * [value] is already display-ready text; the label in front of it is chosen by
 * the composable, which is the layer that owns translations.
 *
 * Sort order is deliberately absent: it never hides anything, and it has its
 * own dropdown in the header saying what it is.
 */
data class ActiveFilter(val kind: Kind, val value: String) {
    enum class Kind {
        SEARCH, LETTER, AUTHOR,
        GENRE, GENRE_EXCLUDED, TAG, TAG_EXCLUDED,
        PUBLISHER, LANGUAGE, AGE_RATING, RELEASE_DATE,
        READ_STATUS, PUBLICATION_STATUS, COMPLETION, FORMAT, MIN_RATING,
    }
}

/**
 * Every criterion that is currently narrowing the list, in the order they are
 * shown. Empty when the filter is untouched, which is what hides the row.
 *
 * Authors are collapsed by name: selecting one author actually selects every
 * role they hold on the series (writer, penciller…), and three chips saying
 * the same name would be noise.
 */
fun SeriesFilter.activeFilters(): List<ActiveFilter> = buildList {
    if (searchTerm.isNotBlank()) add(ActiveFilter(ActiveFilter.Kind.SEARCH, searchTerm))
    letterFilter?.let { add(ActiveFilter(ActiveFilter.Kind.LETTER, it)) }
    authors.map { it.name }.distinct().forEach { add(ActiveFilter(ActiveFilter.Kind.AUTHOR, it)) }
    includeGenres.forEach { add(ActiveFilter(ActiveFilter.Kind.GENRE, it)) }
    excludeGenres.forEach { add(ActiveFilter(ActiveFilter.Kind.GENRE_EXCLUDED, it)) }
    includeTags.forEach { add(ActiveFilter(ActiveFilter.Kind.TAG, it)) }
    excludeTags.forEach { add(ActiveFilter(ActiveFilter.Kind.TAG_EXCLUDED, it)) }
    publishers.forEach { add(ActiveFilter(ActiveFilter.Kind.PUBLISHER, it)) }
    languages.forEach { add(ActiveFilter(ActiveFilter.Kind.LANGUAGE, it)) }
    ageRatings.forEach { add(ActiveFilter(ActiveFilter.Kind.AGE_RATING, it)) }
    releaseDates.forEach { add(ActiveFilter(ActiveFilter.Kind.RELEASE_DATE, it)) }
    readStatus.forEach { add(ActiveFilter(ActiveFilter.Kind.READ_STATUS, it.name)) }
    publicationStatus.forEach { add(ActiveFilter(ActiveFilter.Kind.PUBLICATION_STATUS, it.name)) }
    if (complete != DEFAULT.complete) add(ActiveFilter(ActiveFilter.Kind.COMPLETION, complete.name))
    if (oneshot != DEFAULT.oneshot) add(ActiveFilter(ActiveFilter.Kind.FORMAT, oneshot.name))
    minStars?.let { add(ActiveFilter(ActiveFilter.Kind.MIN_RATING, it.toString())) }
}

data class SeriesFilter(
    val isChanged: Boolean = false,
    val searchTerm: String = "",
    val sortOrder: SeriesSort = SeriesSort.TITLE_ASC,
    val readStatus: List<KomgaReadStatus> = emptyList(),
    val publicationStatus: List<KomgaSeriesStatus> = emptyList(),

    val includeGenres: List<String> = emptyList(),
    val includeTags: List<String> = emptyList(),
    val excludeGenres: List<String> = emptyList(),
    val excludeTags: List<String> = emptyList(),
    val inclusionMode: TagInclusionMode = TagInclusionMode.INCLUDE_IF_ALL_MATCH,
    val exclusionMode: TagExclusionMode = TagExclusionMode.EXCLUDE_IF_ANY_MATCH,

    val authors: List<KomgaAuthor> = emptyList(),
    val releaseDates: List<String> = emptyList(),
    val ageRatings: List<String> = emptyList(),
    val publishers: List<String> = emptyList(),
    val languages: List<String> = emptyList(),
    val complete: Completion = Completion.ANY,
    val oneshot: Format = Format.ANY,
    // Single-letter prefix filter (A-Z, "#" for digit-starting titles, null = no filter).
    // Server-side via SeriesConditionBuilder.titleSort { beginsWith(letter) } — fast
    // and accurate; uses Komga's indexed titleSort column.
    val letterFilter: String? = null,
    /**
     * Keep only series rated at least this many stars; null = no rating filter.
     *
     * Unlike every other field here it has NO server-side expression, and it
     * never will: the rating is this user's own, stored in a local table, and
     * must never become a shared Komga value. So it is deliberately absent from
     * [addConditionTo] — the library screen switches to a local data source when
     * it is set. See [snd.komelia.ui.library.LibrarySeriesTabState].
     */
    val minStars: Int? = null,
) {

    companion object {
        val DEFAULT = SeriesFilter()
    }

    /**
     * True when the list can no longer be expressed as a Komga query, because
     * the rating — a purely local, per-user value — takes part in it.
     */
    val isRatingScoped: Boolean
        get() = minStars != null || sortOrder == SeriesSort.RATING_DESC

    fun addConditionTo(builder: SeriesConditionBuilder) {
        // Letter filter: narrow to series whose titleSort starts with a given
        // letter (or any digit for "#"). titleSort is Komga's indexed
        // sort-friendly title column, so the lookup is fast and case-correct.
        when (val letter = letterFilter) {
            null -> {}
            "#" -> builder.anyOf {
                ('0'..'9').forEach { d ->
                    titleSort { beginsWith(d.toString()) }
                }
            }
            else -> builder.titleSort { beginsWith(letter) }
        }

        if (publicationStatus.isNotEmpty()) {
            builder.anyOf {
                publicationStatus.forEach { seriesStatus { isEqualTo(it) } }
            }
        }

        if (readStatus.isNotEmpty()) {
            builder.anyOf {
                readStatus.forEach { readStatus { isEqualTo(it) } }
            }
        }

        if (publishers.isNotEmpty()) {
            builder.anyOf {
                publishers.forEach { publisher { isEqualTo(it) } }
            }
        }

        if (languages.isNotEmpty()) {
            builder.anyOf {
                languages.forEach { language { isEqualTo(it) } }
            }
        }

        if (includeTags.isNotEmpty() || includeGenres.isNotEmpty()) {
            when (inclusionMode) {
                TagInclusionMode.INCLUDE_IF_ALL_MATCH -> builder.allOf {
                    includeGenres.forEach { genre { isEqualTo(it) } }
                    includeTags.forEach { tag { isEqualTo(it) } }
                }

                TagInclusionMode.INCLUDE_IF_ANY_MATCH -> builder.anyOf {
                    includeGenres.forEach { genre { isEqualTo(it) } }
                    includeTags.forEach { tag { isEqualTo(it) } }
                }
            }
        }
        if (excludeTags.isNotEmpty() || excludeGenres.isNotEmpty()) {
            when (exclusionMode) {
                TagExclusionMode.EXCLUDE_IF_ANY_MATCH -> builder.allOf {
                    excludeGenres.forEach { genre { isNotEqualTo(it) } }
                    excludeTags.forEach { tag { isNotEqualTo(it) } }
                }

                TagExclusionMode.EXCLUDE_IF_ALL_MATCH -> builder.anyOf {
                    excludeGenres.forEach { genre { isNotEqualTo(it) } }
                    excludeTags.forEach { tag { isNotEqualTo(it) } }
                }
            }
        }

        if (ageRatings.isNotEmpty()) {
            builder.anyOf {
                ageRatings.forEach {
                    ageRating {
                        if (it == "None") isNull()
                        else isEqualTo(it.toInt())
                    }
                }
            }
        }

        if (releaseDates.isNotEmpty()) {
            builder.anyOf {
                releaseDates.forEach {
                    allOf {
                        releaseDate {
                            isAfter(dateAtLastDayInYear(it.toInt() - 1).atStartOfDayIn(TimeZone.UTC))
                        }
                        releaseDate {
                            isBefore(LocalDate(it.toInt() + 1, 1, 1).atStartOfDayIn(TimeZone.UTC))
                        }

                    }
                }

            }
        }

        authors.forEach {
            builder.author { isEqualTo(KomgaSearchCondition.AuthorMatch(it.name, null)) }
        }
        when (complete) {
            Completion.ANY -> {}
            Completion.COMPLETE -> builder.isCompleted()
            Completion.INCOMPLETE -> builder.isNotCompleted()
        }
        when (oneshot) {
            Format.ANY -> {}
            Format.ONESHOT -> builder.isOneshot()
            Format.NOT_ONESHOT -> builder.isNotOneshot()
        }
    }

    private fun dateAtLastDayInYear(year: Int): LocalDate {
        val start = LocalDate(year, 12, 1)
        val end = start.plus(1, DateTimeUnit.MONTH)
        val day = start.until(end, DateTimeUnit.DAY)
        return LocalDate(year, 12, day.toInt())
    }
}

class SeriesFilterState(
    defaultSort: SeriesSort,
    private val library: StateFlow<KomgaLibrary?>,
    private val referentialApi: KomgaReferentialApi,
    private val appNotifications: AppNotifications,
) {

    private val mutableFilterState = MutableStateFlow(SeriesFilter(sortOrder = defaultSort))
    val state = mutableFilterState.asStateFlow()

    var isChanged by mutableStateOf(false)
        private set
    var genresOptions by mutableStateOf<List<String>>(emptyList())
        private set
    var tagOptions by mutableStateOf<List<String>>(emptyList())
        private set
    var authorsOptions by mutableStateOf<List<KomgaAuthor>>(emptyList())
        private set
    var releaseDateOptions by mutableStateOf<List<String>>(emptyList())
        private set
    var ageRatingsOptions by mutableStateOf<List<String>>(emptyList())
        private set
    var publishersOptions by mutableStateOf<List<String>>(emptyList())
        private set
    var languagesOptions by mutableStateOf<List<String>>(emptyList())
        private set

    /** Library the options currently in memory belong to, or the sentinel below. */
    private var optionsLoadedFor: KomgaLibraryId? = null
    private var optionsLoaded = false
    private val optionsLock = Mutex()

    /**
     * Fills the dropdowns of the filter panel. Six referential lookups, ~4 s of
     * server on a real library, and every one of them is only ever read by the
     * panel — so this is called by [SeriesFilterContent] when the panel first
     * composes, never by the screen that owns the grid. Firing it eagerly cost
     * the grid twice: once for the six requests themselves, and once more
     * because they were competing for the connection pool with the three
     * queries that actually paint something (measured queue up to 1058 ms).
     *
     * Idempotent, and keyed on the library: switching library invalidates the
     * options, calling it again on the same one is free.
     */
    suspend fun initialize() {
        val libraryId = library.value?.id
        optionsLock.withLock {
            if (optionsLoaded && optionsLoadedFor == libraryId) return
            optionsLoaded = true
            optionsLoadedFor = libraryId
        }
        appNotifications.runCatchingToNotifications {
            val libraryIds = libraryId?.let { listOf(it) }.orEmpty()
            // Independent referential lookups — fetch them concurrently instead
            // of one round-trip after another (six in series was a big chunk of
            // the latency when this ran on the critical path).
            coroutineScope {
                val genres = async { referentialApi.getGenres(libraryIds = libraryIds) }
                // getTags, not getSeriesTags: a tag carried by the BOOKS of a
                // series is still a tag of that series to the user, and leaving
                // those out made the filter miss most of them (upstream #133).
                val tags = async { referentialApi.getTags(libraryIds = libraryIds) }
                val releaseDates = async { referentialApi.getSeriesReleaseDates(libraryIds = libraryIds) }
                val ageRatings = async { referentialApi.getAgeRatings(libraryIds = libraryIds) }
                val publishers = async { referentialApi.getPublishers(libraryIds = libraryIds) }
                val languages = async { referentialApi.getLanguages(libraryIds = libraryIds) }
                genresOptions = genres.await()
                // Never surface kora:hidden as a filterable tag — it would let a
                // non-admin reveal series an admin hid for everyone.
                tagOptions = tags.await().filterNot { it == HIDDEN_TAG }
                releaseDateOptions = releaseDates.await()
                ageRatingsOptions = ageRatings.await()
                publishersOptions = publishers.await()
                languagesOptions = languages.await()
            }
        }.onFailure {
            // Let the next open retry instead of leaving the panel empty forever.
            optionsLoaded = false
        }
    }

    fun applyFilter(filter: SeriesScreenFilter) {
        mutableFilterState.value = SeriesFilter(
            publicationStatus = filter.publicationStatus ?: DEFAULT.publicationStatus,
            ageRatings = filter.ageRating?.map { it.toString() } ?: DEFAULT.ageRatings,
            languages = filter.language ?: DEFAULT.languages,
            publishers = filter.publisher ?: DEFAULT.publishers,
            includeGenres = filter.genres ?: DEFAULT.includeGenres,
            includeTags = filter.tags ?: DEFAULT.includeTags,
            authors = filter.authors ?: DEFAULT.authors
        )
        checkIfAllDefault()
    }

    /** Restore a previously-persisted filter as the current state. */
    fun restore(filter: SeriesFilter) {
        mutableFilterState.value = filter
        checkIfAllDefault()
    }

    fun onSortOrderChange(sortOrder: SeriesSort) {
        mutableFilterState.update {
            // Sorting by rating only makes sense over rated series, so picking it
            // implies "rated at all" unless a stricter threshold is already set.
            if (sortOrder == SeriesSort.RATING_DESC && it.minStars == null) {
                it.copy(sortOrder = sortOrder, minStars = 1)
            } else it.copy(sortOrder = sortOrder)
        }
        checkIfAllDefault()
    }

    fun onSearchTermChange(searchTerm: String) {
        mutableFilterState.update { current -> current.copy(searchTerm = searchTerm) }
        checkIfAllDefault()
    }

    fun onLetterFilterChange(letter: String?) {
        mutableFilterState.update { current -> current.copy(letterFilter = letter) }
        checkIfAllDefault()
    }

    /**
     * [stars] null clears the filter; 1 means "rated at all", which is what
     * sorting by rating implies — an unrated series has no place in a list
     * ordered by rating.
     */
    fun onMinStarsChange(stars: Int?) {
        mutableFilterState.update { current ->
            if (stars == null && current.sortOrder == SeriesSort.RATING_DESC) {
                // Dropping the filter while sorting by rating would ask for an
                // order over series that have none. Fall back to the title.
                current.copy(minStars = null, sortOrder = SeriesSort.TITLE_ASC)
            } else current.copy(minStars = stars)
        }
        checkIfAllDefault()
    }

    fun onReadStatusSelect(readStatus: KomgaReadStatus) {
        mutableFilterState.update { current ->
            current.copy(
                readStatus = if (current.readStatus.contains(readStatus)) {
                    current.readStatus.minus(readStatus)
                } else {
                    current.readStatus.plus(readStatus)
                }
            )
        }

        checkIfAllDefault()
    }

    fun onPublicationStatusSelect(publicationStatus: KomgaSeriesStatus) {
        mutableFilterState.update { current ->
            current.copy(
                publicationStatus = if (current.publicationStatus.contains(publicationStatus)) {
                    current.publicationStatus.minus(publicationStatus)
                } else {
                    current.publicationStatus.plus(publicationStatus)
                }
            )
        }
        checkIfAllDefault()
    }


    fun onGenreSelect(genre: String) {
        mutableFilterState.update { current ->
            if (current.includeGenres.contains(genre)) {
                current.copy(
                    includeGenres = current.includeGenres.minus(genre),
                    excludeGenres = current.excludeGenres.plus(genre)
                )
            } else if (current.excludeGenres.contains(genre)) {
                current.copy(
                    excludeGenres = current.excludeGenres.minus(genre)
                )
            } else current.copy(
                includeGenres = current.includeGenres.plus(genre)
            )
        }
        checkIfAllDefault()
    }

    fun onTagSelect(tag: String) {
        mutableFilterState.update { current ->
            if (current.includeTags.contains(tag)) {
                current.copy(
                    includeTags = current.includeTags.minus(tag),
                    excludeTags = current.excludeTags.plus(tag)
                )
            } else if (current.excludeTags.contains(tag)) {
                current.copy(
                    excludeTags = current.excludeTags.minus(tag)
                )
            } else current.copy(
                includeTags = current.includeTags.plus(tag)
            )
        }
        checkIfAllDefault()
    }

    fun onInclusionModeChange(mode: TagInclusionMode) {
        mutableFilterState.update { current -> current.copy(inclusionMode = mode) }
        checkIfAllDefault()
    }

    fun onExclusionModeChange(mode: TagExclusionMode) {
        mutableFilterState.update { current -> current.copy(exclusionMode = mode) }
        checkIfAllDefault()
    }

    suspend fun onAuthorsSearch(search: String) {
        if (search.isBlank()) this.authorsOptions = emptyList()
        else this.authorsOptions = referentialApi.getAuthors(search).content
    }

    fun onAuthorSelect(author: KomgaAuthor) {
        val authorsByName = authorsOptions.filter { it.name == author.name }
        mutableFilterState.update { current ->
            current.copy(
                authors = if (current.authors.contains(author))
                    current.authors.filter { it.name != author.name }
                else current.authors.plus(authorsByName)
            )
        }

        checkIfAllDefault()
    }

    fun onAgeRatingSelect(ageRating: String) {
        mutableFilterState.update { current ->
            current.copy(
                ageRatings = if (current.ageRatings.contains(ageRating))
                    current.ageRatings.minus(ageRating)
                else current.ageRatings.plus(ageRating)

            )
        }

        checkIfAllDefault()
    }

    fun onPublisherSelect(publisher: String) {
        mutableFilterState.update { current ->
            current.copy(
                publishers = if (current.publishers.contains(publisher))
                    current.publishers.minus(publisher)
                else current.publishers.plus(publisher)
            )
        }

        checkIfAllDefault()
    }

    fun onLanguageSelect(language: String) {
        mutableFilterState.update { current ->
            current.copy(
                languages = if (current.languages.contains(language)) current.languages.minus(language)
                else current.languages.plus(language)
            )
        }

        checkIfAllDefault()
    }

    fun onReleaseDateSelect(releaseDate: String) {
        mutableFilterState.update { current ->
            current.copy(
                releaseDates = if (current.releaseDates.contains(releaseDate))
                    current.releaseDates.minus(releaseDate)
                else current.releaseDates.plus(releaseDate)
            )
        }
        checkIfAllDefault()
    }

    fun onCompletionToggle() {
        mutableFilterState.update {
            it.copy(
                complete = when (it.complete) {
                    Completion.ANY -> Completion.COMPLETE
                    Completion.COMPLETE -> Completion.INCOMPLETE
                    Completion.INCOMPLETE -> Completion.ANY
                }
            )
        }
        checkIfAllDefault()
    }

    fun onFormatToggle() {
        mutableFilterState.update {
            it.copy(
                oneshot = when (it.oneshot) {
                    Format.ANY -> Format.ONESHOT
                    Format.ONESHOT -> Format.NOT_ONESHOT
                    Format.NOT_ONESHOT -> Format.ANY
                }
            )
        }
        checkIfAllDefault()
    }

    /**
     * Drops the single criterion [filter] and leaves the rest alone.
     *
     * Not routed through the `onXSelect` toggles the filter panel uses: those
     * are tri-state for genres and tags (include -> exclude -> off), so asking
     * one of them to remove an included tag would silently *exclude* it
     * instead. [onAuthorSelect] is worse — it reads `authorsOptions`, which is
     * only populated once the filter panel has composed, so from a chip it
     * would do nothing at all.
     */
    fun remove(filter: ActiveFilter) {
        mutableFilterState.update { c ->
            when (filter.kind) {
                ActiveFilter.Kind.SEARCH -> c.copy(searchTerm = DEFAULT.searchTerm)
                ActiveFilter.Kind.LETTER -> c.copy(letterFilter = DEFAULT.letterFilter)
                ActiveFilter.Kind.AUTHOR -> c.copy(authors = c.authors.filterNot { it.name == filter.value })
                ActiveFilter.Kind.GENRE -> c.copy(includeGenres = c.includeGenres - filter.value)
                ActiveFilter.Kind.GENRE_EXCLUDED -> c.copy(excludeGenres = c.excludeGenres - filter.value)
                ActiveFilter.Kind.TAG -> c.copy(includeTags = c.includeTags - filter.value)
                ActiveFilter.Kind.TAG_EXCLUDED -> c.copy(excludeTags = c.excludeTags - filter.value)
                ActiveFilter.Kind.PUBLISHER -> c.copy(publishers = c.publishers - filter.value)
                ActiveFilter.Kind.LANGUAGE -> c.copy(languages = c.languages - filter.value)
                ActiveFilter.Kind.AGE_RATING -> c.copy(ageRatings = c.ageRatings - filter.value)
                ActiveFilter.Kind.RELEASE_DATE -> c.copy(releaseDates = c.releaseDates - filter.value)
                ActiveFilter.Kind.READ_STATUS ->
                    c.copy(readStatus = c.readStatus.filterNot { it.name == filter.value })

                ActiveFilter.Kind.PUBLICATION_STATUS ->
                    c.copy(publicationStatus = c.publicationStatus.filterNot { it.name == filter.value })

                ActiveFilter.Kind.COMPLETION -> c.copy(complete = DEFAULT.complete)
                ActiveFilter.Kind.FORMAT -> c.copy(oneshot = DEFAULT.oneshot)
                // Sorting by rating implies "rated at all", so clearing the
                // threshold under that sort would immediately re-impose it.
                ActiveFilter.Kind.MIN_RATING ->
                    if (c.sortOrder == SeriesSort.RATING_DESC) c.copy(minStars = null, sortOrder = DEFAULT.sortOrder)
                    else c.copy(minStars = null)
            }
        }
        checkIfAllDefault()
    }

    fun reset() {
        isChanged = false
        mutableFilterState.value = DEFAULT
    }

    fun resetTagFilters() {
        mutableFilterState.update {
            it.copy(
                includeGenres = DEFAULT.includeGenres,
                includeTags = DEFAULT.includeTags,
                inclusionMode = DEFAULT.inclusionMode,
                excludeGenres = DEFAULT.excludeGenres,
                excludeTags = DEFAULT.excludeTags,
                exclusionMode = DEFAULT.exclusionMode

            )
        }
    }

    fun resetAuthors() {
        mutableFilterState.update { it.copy(authors = emptyList()) }
        checkIfAllDefault()
    }

    private fun checkIfAllDefault() {
        isChanged = state.value != DEFAULT
    }

    enum class Completion {
        ANY, COMPLETE, INCOMPLETE
    }

    enum class Format {
        ANY, ONESHOT, NOT_ONESHOT
    }

    enum class TagInclusionMode : ScreenSerializable {
        INCLUDE_IF_ALL_MATCH, INCLUDE_IF_ANY_MATCH
    }

    enum class TagExclusionMode : ScreenSerializable {
        EXCLUDE_IF_ANY_MATCH, EXCLUDE_IF_ALL_MATCH
    }
}
