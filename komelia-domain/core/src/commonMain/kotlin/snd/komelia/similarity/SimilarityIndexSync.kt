package snd.komelia.similarity

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import snd.komga.client.library.KomgaLibrary
import snd.komga.client.library.KomgaLibraryId
import snd.komga.client.series.KomgaSeriesId
import snd.komga.client.sse.KomgaEvent
import kotlin.time.Clock

private val logger = KotlinLogging.logger {}

/**
 * Keeps the similarity index in step with the server, one series at a time.
 *
 * Until now the index was a snapshot: built once per library, and stale from
 * the next import until the user thought to press "Re-analyse library". Komga
 * already announces every change over SSE, so the index can follow — a series
 * whose metadata moved is re-read alone, which is one request instead of the
 * thirty a full rebuild costs.
 *
 * Three rules keep this from becoming a burst of traffic:
 *
 *  - **Only libraries that already have an index are followed** by the event
 *    path. Creating one from a change event would turn a single import into a
 *    full library crawl. Libraries that have no index at all are handled once,
 *    up front, by [bootstrapMissingIndexes].
 *  - **Events are batched.** A scan emits one event per series; reacting to
 *    each would mean hundreds of requests. Ids pile up for [QUIET_PERIOD] after
 *    the last one, then go out together.
 *  - **Past [FULL_REBUILD_THRESHOLD] series in one batch, a rebuild is
 *    cheaper.** That many changes at once is an import or a metadata pass, and
 *    paging the library beats fetching series one by one.
 */
class SimilarityIndexSync(
    private val events: SharedFlow<KomgaEvent>,
    private val repository: SimilarityIndexRepository,
    private val builder: SimilarityIndexBuilder,
    private val scope: CoroutineScope,
    /** Called after the index moved, so cached suggestions are recomputed. */
    private val onIndexChanged: () -> Unit = {},
) {
    private val pending = HashSet<PendingChange>()
    private val lock = Mutex()
    private var flushJob: kotlinx.coroutines.Job? = null

    private data class PendingChange(
        val seriesId: KomgaSeriesId,
        val libraryId: KomgaLibraryId,
        val deleted: Boolean,
    )

    fun start() {
        scope.launch {
            events.collect { event ->
                when (event) {
                    is KomgaEvent.SeriesChanged -> record(event.seriesId, event.libraryId, deleted = false)
                    is KomgaEvent.SeriesAdded -> record(event.seriesId, event.libraryId, deleted = false)
                    is KomgaEvent.SeriesDeleted -> record(event.seriesId, event.libraryId, deleted = true)
                    else -> {}
                }
            }
        }
    }

    /**
     * Builds an index for every library that has none, once per app run.
     *
     * The index is what "Similar series", "For you" and the library genre
     * count read from, and until now it only existed for a library the user
     * had opened one of those tabs in. On this install that meant one library
     * out of six: the other five silently had no suggestions at all, and their
     * genre count fell back to downloading every tag of the library on each
     * visit — measured between 127 ms and 1026 ms, per visit, forever.
     *
     * This deliberately reverses the older rule that an index should never be
     * created unprompted. What made that rule right was the fear of a long
     * crawl on someone else's server; the crawl was then measured, on the real
     * one: **11 951 series across six libraries in 67 seconds**, because the
     * builder pages 100 series per request. That is a one-off cost, and the
     * event sync keeps them current afterwards, so nobody pays it twice.
     *
     * Sequential on purpose. [SimilarityIndexBuilder] already runs four page
     * requests at a time inside one library; letting several libraries do that
     * at once is exactly the unbounded burst the rest of this file avoids.
     *
     * A library that fails is simply left without an index and tried again on
     * the next run — the alternative, remembering the failure, would make a
     * transient network error permanent.
     */
    fun bootstrapMissingIndexes(libraries: StateFlow<List<KomgaLibrary>>) {
        scope.launch {
            // The list is empty until the server session is up; waiting for it
            // also means never crawling while logged out.
            val known = libraries.first { it.isNotEmpty() }

            // Stay out of the way of the screen the user is actually looking
            // at. Measured on this install, the home screen finishes its own
            // network refresh within 6 s of launch and a library switch settles
            // in about 2 s, so by 30 s nothing the user is waiting on is still
            // in flight.
            delay(BOOTSTRAP_DELAY_MILLIS)

            val missing = known.filter { repository.stateOf(it.id.value) == null }
            if (missing.isEmpty()) return@launch
            logger.info { "Similarity index: building ${missing.size} missing librar(y/ies)" }
            missing.forEach { library ->
                currentCoroutineContext().ensureActive()
                runCatching { builder.build(library.id) }
                    .onFailure {
                        currentCoroutineContext().ensureActive()
                        logger.warn(it) { "Similarity index bootstrap failed for ${library.id.value}" }
                    }
            }
            onIndexChanged()
        }
    }

    private suspend fun record(seriesId: KomgaSeriesId, libraryId: KomgaLibraryId, deleted: Boolean) {
        // A library with no index has nothing to keep in step, and building one
        // here would be a burst of requests the user never asked for.
        if (repository.stateOf(libraryId.value) == null) return

        lock.withLock {
            pending.removeAll { it.seriesId == seriesId }
            pending += PendingChange(seriesId, libraryId, deleted)
            flushJob?.cancel()
            flushJob = scope.launch {
                delay(QUIET_PERIOD_MILLIS)
                flush()
            }
        }
    }

    private suspend fun flush() {
        val batch = lock.withLock {
            val copy = pending.toList()
            pending.clear()
            copy
        }
        if (batch.isEmpty()) return

        try {
            val (deleted, changed) = batch.partition { it.deleted }
            if (deleted.isNotEmpty()) {
                builder.removeSeries(deleted.map { it.seriesId })
            }
            if (changed.size >= FULL_REBUILD_THRESHOLD) {
                // One rebuild per library touched: at this volume, paging the
                // library is fewer requests than fetching each series.
                changed.map { it.libraryId }.distinct().forEach { libraryId ->
                    logger.info { "Similarity index: ${changed.size} changes, rebuilding ${libraryId.value}" }
                    builder.build(libraryId)
                }
            } else if (changed.isNotEmpty()) {
                builder.refreshSeries(changed.map { it.seriesId })
                touchState(changed.map { it.libraryId }.distinct())
            }
            if (deleted.isNotEmpty() && changed.size < FULL_REBUILD_THRESHOLD) {
                touchState(deleted.map { it.libraryId }.distinct())
            }
            logger.debug { "Similarity index synced: ${changed.size} refreshed, ${deleted.size} removed" }
            onIndexChanged()
        } catch (t: Throwable) {
            currentCoroutineContext().ensureActive()
            // The index stays as it was, which is stale but usable — and the
            // next change, or an explicit re-analyse, fixes it.
            logger.warn(t) { "Similarity index sync failed for ${batch.size} change(s)" }
        }
    }

    /**
     * Records the new series count and the time, so the settings screen stops
     * claiming the index was built the day the library was first analysed.
     */
    private suspend fun touchState(libraryIds: List<KomgaLibraryId>) {
        libraryIds.forEach { libraryId ->
            val previous = repository.stateOf(libraryId.value) ?: return@forEach
            repository.putState(
                previous.copy(
                    builtAt = Clock.System.now(),
                    // A COUNT, not every row: this only ever needed the size.
                    seriesCount = repository.countOf(libraryId.value),
                    // Deliberately carried over rather than recomputed. An
                    // incremental sync touches a handful of series, so the
                    // genre count can only move when one of them introduces or
                    // retires a slug the library had nowhere else — and the
                    // cost of noticing is a full re-read of the index, which is
                    // exactly what this field exists to avoid. The next full
                    // build corrects it; being off by one genre in a chip in
                    // the meantime changes nothing the user can see.
                    genreCount = previous.genreCount,
                )
            )
        }
    }

    private companion object {
        /** A scan emits one event per series; wait for the storm to pass. */
        const val QUIET_PERIOD_MILLIS = 5_000L

        /** Past this, paging the library costs fewer requests than one-by-one. */
        const val FULL_REBUILD_THRESHOLD = 50

        /** Long enough for the first screen to have finished its own requests. */
        const val BOOTSTRAP_DELAY_MILLIS = 30_000L
    }
}
