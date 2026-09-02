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
import snd.komelia.similarity.SimilarityIndexRepository
import snd.komga.client.library.KomgaLibrary
import snd.komga.client.series.KomgaSeriesId

private val logger = KotlinLogging.logger {}

/**
 * One group as the screen shows it: the finder's result plus what it takes to
 * draw and act on it.
 */
data class DuplicateRow(
    val group: DuplicateGroup,
    val libraryName: String,
    val details: List<String> = emptyList(),
    val loadingDetails: Boolean = false,
) {
    /** Stable across rescans, so expanded details survive one. */
    val key: String = group.members.joinToString("|") { it.seriesId }
}

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
    private val libraries: StateFlow<List<KomgaLibrary>>,
    private val notifications: AppNotifications,
) : ScreenModel {

    var scanning by mutableStateOf(false)
        private set
    var likely by mutableStateOf<List<DuplicateRow>>(emptyList())
        private set
    var unsure by mutableStateOf<List<DuplicateRow>>(emptyList())
        private set
    var ignoredCount by mutableStateOf(0)
        private set

    /** How many series the sweep could actually read. */
    var scannedSeries by mutableStateOf(0)
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

    private suspend fun scan() {
        if (scanning) return
        scanning = true
        try {
            val ignored = ignoreRepository.ignoredPairs()
            ignoredCount = ignored.size
            val titles = indexRepository.allTitles()
            val groups = findDuplicateGroups(titles, ignored)

            val libraryNames = libraries.value.associate { it.id.value to it.name }
            val indexedIds = titles.mapTo(mutableSetOf()) { it.libraryId }
            scannedSeries = titles.size
            missingLibraries = libraries.value.filter { it.id.value !in indexedIds }.map { it.name }

            fun row(group: DuplicateGroup) = DuplicateRow(
                group = group,
                libraryName = libraryNames[group.libraryId] ?: group.libraryId,
            )
            likely = groups.filter { it.likely }.map(::row)
            unsure = groups.filterNot { it.likely }.map(::row)
        } catch (e: Exception) {
            logger.catching(e)
            notifications.addErrorNotification(e)
        } finally {
            scanning = false
        }
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
                likely = likely.filterNot { it.key == row.key }
                unsure = unsure.filterNot { it.key == row.key }
                ignoredCount = ignoreRepository.ignoredPairs().size
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
            val lines = mutableListOf<String>()
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
                lines.add(line)
            }
            update(row.key) { it.copy(details = lines, loadingDetails = false) }
        }
    }

    private fun update(key: String, transform: (DuplicateRow) -> DuplicateRow) {
        likely = likely.map { if (it.key == key) transform(it) else it }
        unsure = unsure.map { if (it.key == key) transform(it) else it }
    }
}
