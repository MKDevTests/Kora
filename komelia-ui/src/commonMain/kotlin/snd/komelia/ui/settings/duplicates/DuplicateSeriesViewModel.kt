package snd.komelia.ui.settings.duplicates

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import snd.komelia.AppNotifications
import snd.komelia.duplicates.DuplicateGroup
import snd.komelia.duplicates.DuplicateIgnoreRepository
import snd.komelia.duplicates.duplicatePairKey
import snd.komelia.duplicates.findDuplicateGroups
import snd.komelia.komga.api.KomgaSeriesApi
import snd.komelia.links.SeriesLinksRepository
import snd.komelia.similarity.SimilarityIndexRepository
import snd.komga.client.library.KomgaLibrary
import snd.komga.client.series.KomgaSeriesId

private val logger = KotlinLogging.logger {}

/**
 * How many groups are drawn before the "show more" button.
 *
 * Measured on the real catalogue: drawing all 230 groups at once froze the main
 * thread for about six seconds ("Skipped 356 frames"), because the settings
 * container is a plain scrolling Column — every row is composed, none is lazy.
 * Thirty cards compose in a fraction of that, and with the filter above them a
 * long scroll is no longer how this screen is used.
 */
private const val PAGE_SIZE = 30

/** One series inside an expanded group, with what tells it from its twin. */
data class DuplicateDetail(
    val seriesId: String,
    val line: String,
)

/**
 * One group as the screen shows it: the finder's result plus what it takes to
 * draw and act on it.
 */
data class DuplicateRow(
    val group: DuplicateGroup,
    val libraryName: String,
    val details: List<DuplicateDetail> = emptyList(),
    val loadingDetails: Boolean = false,
) {
    /** Stable across rescans, so expanded details survive one. */
    val key: String = group.members.joinToString("|") { it.seriesId }
}

/** A library and how many groups it holds, for the filter row. */
data class DuplicateLibraryFacet(
    val libraryId: String,
    val name: String,
    val count: Int,
)

/**
 * Finds series filed twice inside the same library.
 *
 * The sweep is local: it reads the persisted similarity index and issues no
 * request. Only the per-group "details" button talks to Komga, for the two or
 * three series of that one group.
 */
class DuplicateSeriesViewModel(
    private val indexRepository: SimilarityIndexRepository,
    private val ignoreRepository: DuplicateIgnoreRepository,
    private val seriesApi: KomgaSeriesApi,
    private val linksRepository: SeriesLinksRepository,
    private val libraries: StateFlow<List<KomgaLibrary>>,
    private val notifications: AppNotifications,
) : ScreenModel {

    var scanning by mutableStateOf(false)
        private set
    var ignoredCount by mutableStateOf(0)
        private set

    /** Everything the sweep found, before the filter row narrows it. */
    private var allLikely: List<DuplicateRow> = emptyList()
    private var allUnsure: List<DuplicateRow> = emptyList()

    var likely by mutableStateOf<List<DuplicateRow>>(emptyList())
        private set
    var unsure by mutableStateOf<List<DuplicateRow>>(emptyList())
        private set

    /**
     * Groups in the "duplicates" list, whatever the filter shows.
     *
     * Deliberately excludes the "to check" ones. Counting both made every
     * number on the screen disagree with the one below it — the banner said
     * 233, the section header 230, the Comics chip 69 against a list of 68 —
     * because those three groups are counted here and shown in their own
     * section. They have their own header count instead.
     */
    var totalGroups by mutableStateOf(0)
        private set

    /** How many series those groups hold — the number worth acting on. */
    var totalSeries by mutableStateOf(0)
        private set

    var query by mutableStateOf("")
        private set

    /** Null means every library. */
    var selectedLibrary by mutableStateOf<String?>(null)
        private set

    var libraryFacets by mutableStateOf<List<DuplicateLibraryFacet>>(emptyList())
        private set

    /**
     * How many of [likely] are drawn.
     *
     * Held here rather than remembered in the composable: dismissing a group
     * changes the list, and a screen-local counter keyed on it would snap back
     * to the first page on every dismissal.
     */
    var visibleCount by mutableStateOf(PAGE_SIZE)
        private set

    /** How many series the sweep could actually read. */
    var scannedSeries by mutableStateOf(0)
        private set

    /**
     * Series the index holds no language for.
     *
     * The finder refuses to judge a pair it cannot compare, so while this is
     * above zero the screen may still be showing two editions of one work as a
     * duplicate. Worth saying out loud rather than under-reporting in silence.
     */
    var seriesWithoutLanguage by mutableStateOf(0)
        private set

    /**
     * Whether the index holds a language for anyone at all.
     *
     * The difference matters to the reader: none at all means the index predates
     * V104 and a rebuild fixes it, while some-but-not-all means those series
     * simply have no language on the server and no rebuild will change that.
     * Measured here: 5453 of 12686 series have none in the real catalogue.
     */
    var languageIndexed by mutableStateOf(false)
        private set

    /**
     * Libraries with no index row, by name.
     *
     * Named rather than counted: an unindexed library looks exactly like a
     * library with no duplicates, and "5 of 6" leaves the admin guessing which
     * one is missing.
     */
    var missingLibraries by mutableStateOf<List<String>>(emptyList())
        private set

    private var initialized = false

    suspend fun initialize() {
        if (initialized) return
        initialized = true
        scan()
    }

    fun rescan() {
        screenModelScope.launch { scan() }
    }

    fun showMore() {
        visibleCount += PAGE_SIZE
    }

    fun onQueryChange(value: String) {
        query = value
        visibleCount = PAGE_SIZE
        applyFilter()
    }

    fun onLibrarySelected(libraryId: String?) {
        selectedLibrary = libraryId
        visibleCount = PAGE_SIZE
        applyFilter()
    }

    private suspend fun scan() {
        if (scanning) return
        scanning = true
        try {
            val ignored = ignoreRepository.ignoredPairs()
            ignoredCount = ignored.size
            val titles = indexRepository.allTitles()
            // Every relation the admin recorded, in one local read. A pair the
            // two screens already linked — chapters to volumes, or one language
            // to another — has been ruled on; the finder must not ask again.
            val linked = linksRepository.getAllRelations()
                .mapTo(mutableSetOf()) { duplicatePairKey(it.from.value, it.to.value) }
            val groups = findDuplicateGroups(titles, ignored, linked)
            seriesWithoutLanguage = titles.count { it.language == null }
            languageIndexed = seriesWithoutLanguage < titles.size

            val libraryNames = libraries.value.associate { it.id.value to it.name }
            val indexedIds = titles.mapTo(mutableSetOf()) { it.libraryId }
            scannedSeries = titles.size
            missingLibraries = libraries.value.filter { it.id.value !in indexedIds }.map { it.name }

            fun row(group: DuplicateGroup) = DuplicateRow(
                group = group,
                libraryName = libraryNames[group.libraryId] ?: group.libraryId,
            )
            allLikely = groups.filter { it.likely }.map(::row)
            allUnsure = groups.filterNot { it.likely }.map(::row)
            visibleCount = PAGE_SIZE
            refreshTotals()
            applyFilter()
        } catch (e: Exception) {
            logger.catching(e)
            notifications.addErrorNotification(e)
        } finally {
            scanning = false
        }
    }

    private fun refreshTotals() {
        totalGroups = allLikely.size
        totalSeries = allLikely.sumOf { it.group.members.size }
        // Facets come from the groups, not from the library list: a library with
        // nothing to fix has no chip, so the row says where the work actually is.
        libraryFacets = allLikely
            .groupBy { it.group.libraryId }
            .map { (id, rows) -> DuplicateLibraryFacet(id, rows.first().libraryName, rows.size) }
            .sortedByDescending { it.count }
    }

    private fun applyFilter() {
        val needle = query.trim()
        fun keep(row: DuplicateRow): Boolean {
            if (selectedLibrary != null && row.group.libraryId != selectedLibrary) return false
            if (needle.isEmpty()) return true
            // Every member's title, not just the group's: pass B groups titles
            // that differ, and searching "Attaque des Titans" must find a group
            // the sweep happened to name "L'attaque des Titans".
            return row.group.members.any { it.title.contains(needle, ignoreCase = true) }
        }
        likely = allLikely.filter(::keep)
        unsure = allUnsure.filter(::keep)
    }

    /**
     * Marks every link inside [row] as "not the same work".
     *
     * Every pair, not the group: the finder assembles groups from pairs, and a
     * group that came back only because one of its three members matched has to
     * disappear entirely rather than shrink to something that reappears next
     * scan.
     */
    fun onIgnoreGroup(row: DuplicateRow) {
        screenModelScope.launch {
            try {
                val ids = row.group.members.map { it.seriesId }
                for (i in ids.indices) {
                    for (j in i + 1 until ids.size) {
                        ignoreRepository.ignore(duplicatePairKey(ids[i], ids[j]))
                    }
                }
                allLikely = allLikely.filterNot { it.key == row.key }
                allUnsure = allUnsure.filterNot { it.key == row.key }
                ignoredCount = ignoreRepository.ignoredPairs().size
                refreshTotals()
                applyFilter()
            } catch (e: Exception) {
                logger.catching(e)
                notifications.addErrorNotification(e)
            }
        }
    }

    fun clearIgnored() {
        screenModelScope.launch {
            try {
                ignoreRepository.clear()
                scan()
            } catch (e: Exception) {
                logger.catching(e)
                notifications.addErrorNotification(e)
            }
        }
    }

    /**
     * Loads, or hides, what tells the copies of one group apart.
     *
     * Sequential rather than fanned out: a group holds two or three series, and
     * the parallel version would only add a burst on an already strained server
     * for no measurable gain.
     */
    fun onToggleDetails(row: DuplicateRow) {
        if (row.details.isNotEmpty()) {
            update(row.key) { it.copy(details = emptyList()) }
            return
        }
        update(row.key) { it.copy(loadingDetails = true) }
        screenModelScope.launch {
            val lines = mutableListOf<DuplicateDetail>()
            for (member in row.group.members) {
                val line = try {
                    val series = seriesApi.getOneSeries(KomgaSeriesId(member.seriesId))
                    listOfNotNull(
                        series.metadata.title,
                        "${series.booksCount}",
                        series.metadata.language.takeIf { it.isNotBlank() }?.uppercase(),
                        series.metadata.publisher.takeIf { it.isNotBlank() },
                    ).joinToString(" · ")
                } catch (e: Exception) {
                    logger.catching(e)
                    "${member.title} — ${e.message}"
                }
                lines.add(DuplicateDetail(member.seriesId, line))
            }
            update(row.key) { it.copy(details = lines, loadingDetails = false) }
        }
    }

    private fun update(key: String, transform: (DuplicateRow) -> DuplicateRow) {
        allLikely = allLikely.map { if (it.key == key) transform(it) else it }
        allUnsure = allUnsure.map { if (it.key == key) transform(it) else it }
        applyFilter()
    }
}
