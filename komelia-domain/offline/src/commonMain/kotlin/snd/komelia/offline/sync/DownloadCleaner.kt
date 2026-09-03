package snd.komelia.offline.sync

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import snd.komelia.offline.book.model.DownloadOrigin
import snd.komelia.offline.book.model.OfflineBook
import snd.komelia.offline.book.repository.OfflineBookRepository
import snd.komelia.offline.readprogress.OfflineReadProgressRepository
import snd.komelia.offline.settings.OfflineSettingsRepository
import snd.komelia.offline.sync.model.OfflineLogEntry.Companion.logInfo
import snd.komelia.offline.sync.repository.LogJournalRepository
import snd.komelia.offline.tasks.OfflineTaskEmitter
import snd.komga.client.user.KomgaUserId
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

/**
 * Reclaims space taken by downloaded books.
 *
 * Two rules, both switchable, both asked for: drop a book some days after it
 * was finished, and drop the oldest finished books when the storage cap is
 * reached. They share one safety rule — a book is only ever a candidate once
 * it has been *read to the end*, so nothing the user is part-way through, and
 * nothing they have not opened, can disappear.
 *
 * Deletion goes through the ordinary [OfflineTaskEmitter.deleteBook] task, the
 * same path the "delete downloaded" menu item uses. That path already removes
 * the file and the row together and survives a restart; a second, private way
 * to delete books would be a second way to get it wrong.
 */
class DownloadCleaner(
    private val bookRepository: OfflineBookRepository,
    private val readProgressRepository: OfflineReadProgressRepository,
    private val settingsRepository: OfflineSettingsRepository,
    private val taskEmitter: OfflineTaskEmitter,
    private val logJournalRepository: LogJournalRepository,
    private val userId: StateFlow<KomgaUserId>,
) {

    /**
     * What one pass did, and where it leaves the cap.
     *
     * [remainingBytes] is projected rather than re-read: deletion is queued as
     * a task, so the database still shows the books this pass just condemned.
     * A caller that asked "is there room now?" needs the answer for after the
     * queue drains, not before.
     */
    data class Result(
        val deletedCount: Int,
        val freedBytes: Long,
        val remainingBytes: Long,
        val limitBytes: Long,
    ) {
        val isOverLimit get() = limitBytes > 0 && remainingBytes >= limitBytes
    }

    suspend fun downloadedBytes(): Long =
        bookRepository.findAllDownloaded().sumOf { it.sizeBytes }

    suspend fun clean(): Result {
        val books = bookRepository.findAllDownloaded()
        val limitBytes = settingsRepository.getDownloadStorageLimitMb().first().toLong() * 1024 * 1024
        val total = books.sumOf { it.sizeBytes }
        if (books.isEmpty()) return Result(0, 0, total, limitBytes)

        val cleanupAfterDays = settingsRepository.getCleanupReadAfterDays().first()
        val includeManual = settingsRepository.getCleanupIncludeManual().first()

        val progress = readProgressRepository
            .findAllByBookIdsAndUserId(books.map { it.id }, userId.value)
            .associateBy { it.bookId }

        // Oldest finished first: whichever rule fires, the book the user
        // finished longest ago is the one they are least likely to reopen.
        val candidates = books
            .mapNotNull { book ->
                val read = progress[book.id] ?: return@mapNotNull null
                if (!read.completed) return@mapNotNull null
                if (!includeManual && book.downloadOrigin != DownloadOrigin.AUTOMATIC) return@mapNotNull null
                book to read.readDate
            }
            .sortedBy { (_, readDate) -> readDate }
            .map { (book, readDate) -> Candidate(book, readDate) }

        val condemned = LinkedHashSet<Candidate>()

        if (cleanupAfterDays > 0) {
            val cutoff = Clock.System.now() - cleanupAfterDays.days
            candidates.filterTo(condemned) { it.readDate <= cutoff }
        }

        // Cap pressure runs whatever the age rule decided, and it runs even
        // when the age rule is off: a full cap has to resolve somehow, and
        // refusing every future download forever is the worse failure.
        if (limitBytes > 0) {
            var projected = total - condemned.sumOf { it.book.sizeBytes }
            for (candidate in candidates) {
                if (projected < limitBytes) break
                if (condemned.add(candidate)) projected -= candidate.book.sizeBytes
            }
        }

        if (condemned.isEmpty()) return Result(0, 0, total, limitBytes)

        condemned.forEach { taskEmitter.deleteBook(it.book.id) }
        val freed = condemned.sumOf { it.book.sizeBytes }
        logJournalRepository.logInfo {
            "Download cleanup: ${condemned.size} book(s), ${freed / 1024 / 1024} MB reclaimed"
        }

        return Result(
            deletedCount = condemned.size,
            freedBytes = freed,
            remainingBytes = total - freed,
            limitBytes = limitBytes,
        )
    }

    private data class Candidate(val book: OfflineBook, val readDate: kotlin.time.Instant)
}
