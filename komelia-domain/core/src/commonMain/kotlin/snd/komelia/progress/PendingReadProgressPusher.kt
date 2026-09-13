package snd.komelia.progress

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import snd.komelia.AppForegroundState
import snd.komelia.NetworkState
import snd.komelia.komga.api.KomgaBookApi
import snd.komga.client.book.KomgaBookId
import snd.komga.client.book.R2Device
import snd.komga.client.book.R2Location
import snd.komga.client.book.R2Locator
import snd.komga.client.book.R2Progression

private val logger = KotlinLogging.logger {}

/**
 * Delivers the read progress that a reading session could not.
 *
 * Runs once per server module ([attach] replaces the previous one): a pass
 * at attach time, then on every network comeback and every return to the
 * foreground — the two moments a failed push has a new chance. One pass at a
 * time; a row is removed only once the server has answered.
 *
 * The reader itself removes a book's row when a later push for that book
 * succeeds, so a pass here only ever sees positions nothing else delivered.
 */
object PendingReadProgressPusher {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private val passMutex = Mutex()

    fun attach(bookApi: KomgaBookApi, repository: PendingReadProgressRepository) {
        job?.cancel()
        job = scope.launch {
            runPass(bookApi, repository)
            merge(
                NetworkState.comebacks.drop(1),
                AppForegroundState.isForeground.drop(1).filter { it },
            )
                .onEach { runPass(bookApi, repository) }
                .launchIn(this)
        }
    }

    private suspend fun runPass(bookApi: KomgaBookApi, repository: PendingReadProgressRepository) {
        if (!passMutex.tryLock()) return
        try {
            val pending = repository.getAll()
            if (pending.isEmpty()) return
            logger.info { "pending read progress: ${pending.size} position(s) to deliver" }
            for (item in pending) {
                currentCoroutineContext().ensureActive()
                val delivered = runCatching {
                    bookApi.updateReadiumProgression(KomgaBookId(item.bookId), item.toProgression())
                }.isSuccess
                if (delivered) {
                    repository.delete(item.bookId)
                    logger.info { "pending read progress: delivered page ${item.page}/${item.totalPages} of ${item.bookId}" }
                } else {
                    // Still no server: the next comeback tries again. Stop
                    // the pass rather than fail every row in turn.
                    logger.warn { "pending read progress: still undeliverable for ${item.bookId}" }
                    return
                }
            }
        } finally {
            passMutex.unlock()
        }
    }

    /** The same shape the reader sends, so the server treats both alike. */
    fun PendingReadProgress.toProgression(): R2Progression = R2Progression(
        modified = modified,
        device = R2Device("komelia-android", "Komelia"),
        locator = R2Locator(
            href = "p$page",
            type = "image/jpeg",
            locations = R2Location(
                position = page,
                progression = page.toFloat() / totalPages.coerceAtLeast(1),
            ),
        ),
    )
}
