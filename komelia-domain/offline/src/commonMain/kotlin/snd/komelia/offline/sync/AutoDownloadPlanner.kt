package snd.komelia.offline.sync

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import snd.komelia.offline.book.model.DownloadOrigin
import snd.komelia.offline.book.repository.OfflineBookRepository
import snd.komelia.offline.settings.OfflineSettingsRepository
import snd.komelia.offline.tasks.OfflineTaskEmitter
import snd.komga.client.book.KomgaBookClient
import snd.komga.client.book.KomgaBookSearch
import snd.komga.client.book.KomgaReadStatus
import snd.komga.client.common.KomgaPageRequest
import snd.komga.client.common.KomgaSort.KomgaBooksSort
import snd.komga.client.search.allOfBooks
import snd.komga.client.search.anyOfBooks
import snd.komga.client.series.KomgaSeriesId
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private val logger = KotlinLogging.logger {}

/**
 * Decides what to download ahead of the reader.
 *
 * The rule the user asked for: follow the series being read, bounded — pinned
 * series first, then the most recently read, capped at a handful, a few
 * volumes deep each, filtered by library and by an exclusion list. Unbounded,
 * "download what I'm reading" is fifteen gigabytes on this catalogue; every
 * bound below is what makes it a feature rather than a disk-filling accident.
 *
 * Nothing here forces a download through: it emits ordinary DownloadBook
 * tasks marked [DownloadOrigin.AUTOMATIC], and [TaskHandler] still checks them
 * against the storage cap. The planner proposes; the policy disposes.
 */
class AutoDownloadPlanner(
    private val bookClient: KomgaBookClient,
    private val bookRepository: OfflineBookRepository,
    private val settingsRepository: OfflineSettingsRepository,
    private val taskEmitter: OfflineTaskEmitter,
    private val scope: CoroutineScope,
) {
    /**
     * Shortest gap between two passes.
     *
     * The trigger is closing a book, and a reading session closes several in a
     * row. Each pass is one query plus one per followed series against a
     * server that has been measured at one to three seconds for exactly this
     * kind of request — running it per page turn would be its own denial of
     * service.
     */
    private val minimumInterval = 10.minutes

    private val mutex = Mutex()
    private var lastRun: Instant? = null

    /**
     * Asks for a pass, from anywhere — including a composition.
     *
     * The work runs on the planner's own scope, so a caller that navigates
     * away does not cancel it. This is the mistake the upcoming-releases scan
     * made and paid for: a scan tied to a `LaunchedEffect` essentially never
     * finished.
     */
    fun requestRun(force: Boolean = false) {
        scope.launch { runIfDue(force) }
    }

    private suspend fun runIfDue(force: Boolean) {
        mutex.withLock {
            val now = Clock.System.now()
            val previous = lastRun
            if (!force && previous != null && now - previous < minimumInterval) return
            lastRun = now
        }
        try {
            run()
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            logger.warn(e) { "automatic download pass failed" }
        }
    }

    /** One pass. Public so a "download now" button can drive it directly. */
    suspend fun run() {
        if (!settingsRepository.getAutoDownloadEnabled().first()) return

        val maxSeries = settingsRepository.getAutoDownloadMaxSeries().first()
        val booksAhead = settingsRepository.getAutoDownloadBooksAhead().first()
        if (maxSeries <= 0 || booksAhead <= 0) return

        val libraryIds = settingsRepository.getAutoDownloadLibraryIds().first()
        val pinned = settingsRepository.getAutoDownloadPinnedSeriesIds().first()
        val excluded = settingsRepository.getAutoDownloadExcludedSeriesIds().first()

        val chosen = chooseSeries(maxSeries, libraryIds, pinned, excluded)
        if (chosen.isEmpty()) return

        var queued = 0
        for (seriesId in chosen) {
            currentCoroutineContext().ensureActive()
            queued += queueSeries(seriesId, booksAhead)
        }
        logger.info { "automatic download: ${chosen.size} series followed, $queued book(s) queued" }
    }

    private suspend fun chooseSeries(
        maxSeries: Int,
        libraryIds: Set<String>,
        pinned: Set<String>,
        excluded: Set<String>,
    ): List<KomgaSeriesId> {
        // One request for the whole reading history, not one per library: the
        // ordering that matters is "most recently read", and that ranking only
        // exists across libraries. The library filter is applied to the answer
        // — a pinned series is followed wherever it lives.
        val inProgress = bookClient.getBookList(
            KomgaBookSearch(
                allOfBooks {
                    readStatus { isEqualTo(KomgaReadStatus.IN_PROGRESS) }
                }.toBookCondition()
            ),
            KomgaPageRequest(pageIndex = 0, size = 100, sort = KomgaBooksSort.byReadDateDesc())
        ).content

        val recent = inProgress
            .filter { libraryIds.isEmpty() || it.libraryId.value in libraryIds }
            .map { it.seriesId }

        return (pinned.map { KomgaSeriesId(it) } + recent)
            .distinct()
            .filterNot { it.value in excluded }
            .take(maxSeries)
    }

    /** Queues the next unread volumes of one series. Returns how many. */
    private suspend fun queueSeries(seriesId: KomgaSeriesId, booksAhead: Int): Int {
        val books = bookClient.getBookList(
            conditionBuilder = anyOfBooks { seriesId { isEqualTo(seriesId) } },
            pageRequest = KomgaPageRequest(pageIndex = 0, size = 500)
        ).content

        // Sorted here rather than by the server: the ordering wanted is reading
        // order, and asking for it as a sort parameter would tie this to a
        // sort name the server may not expose.
        val wanted = books
            .sortedBy { it.number }
            .filter { it.readProgress?.completed != true }
            .take(booksAhead)

        var queued = 0
        for (book in wanted) {
            if (bookRepository.exists(book.id)) continue
            taskEmitter.downloadBook(book.id, origin = DownloadOrigin.AUTOMATIC)
            queued++
        }
        return queued
    }
}
