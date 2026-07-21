package snd.komelia.hidden

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.createDirectories
import io.github.vinceglb.filekit.div
import io.github.vinceglb.filekit.filesDir
import io.github.vinceglb.filekit.readBytes
import io.github.vinceglb.filekit.write
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import snd.komelia.komga.api.KomgaApi
import snd.komga.client.common.KomgaPageRequest
import snd.komga.client.common.patchLists
import snd.komga.client.search.allOfSeries
import snd.komga.client.series.KomgaSeriesId
import snd.komga.client.series.KomgaSeriesMetadataUpdateRequest
import snd.komga.client.user.KomgaUser
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** Komga series tag that marks a series as hidden for every Kora client. */
const val HIDDEN_TAG = "kora:hidden"

/**
 * Tracks the set of series carrying [HIDDEN_TAG] on the server so every Kora
 * client can filter them out of all list responses (the admin "hide for
 * everyone" feature). The hidden set is **unconditional** — there is no per-user
 * opt-out — and is fed into [snd.komelia.ignore.withIgnoreFilter] alongside the
 * local Ignore List.
 *
 * Discovery MUST run against an **undecorated** [KomgaApi]: the very filter this
 * feeds would otherwise drop the kora:hidden series from its own lookup.
 *
 * The set is seeded from a small on-disk cache for instant cold-start filtering
 * (no flash of a hidden series before the network returns), then refreshed
 * whenever a user becomes authenticated (cookie ready / sign-in / server switch)
 * and on demand via [refresh] (e.g. after an admin hides/unhides a series, or on
 * pull-to-refresh).
 */
class HiddenSeriesController(
    private val rawApi: StateFlow<KomgaApi>,
    authenticatedUser: StateFlow<KomgaUser?>,
    private val scope: CoroutineScope,
    private val cacheKey: String? = null,
) {
    private val _hiddenIds = MutableStateFlow<Set<String>>(emptySet())
    val hiddenIds: StateFlow<Set<String>> = _hiddenIds.asStateFlow()

    init {
        scope.launch {
            // Seed from disk FIRST so a cold/offline start filters instantly,
            // THEN keep in sync with the server (sequential — no overwrite race).
            loadCache()?.let { _hiddenIds.value = it }
            // Only the FIRST authenticated emission triggers a (throttled) sync.
            // Later emissions (cookie refresh, transient re-auth) would otherwise
            // re-run the full paginated scan every time.
            authenticatedUser.collect { user -> if (user != null) refreshIfStale() }
        }
    }

    /**
     * Re-query only when the cached set is older than [REFRESH_TTL] (or never
     * fetched). The hidden set changes rarely — only when an admin hides or
     * unhides a series — and those paths call [refresh] directly, so a daily
     * background reconciliation is plenty. Scanning on every auth event was one
     * of the slow server round-trips on the hot path.
     */
    private suspend fun refreshIfStale() {
        val last = loadTimestamp()
        if (last != null && (Clock.System.now() - last) < REFRESH_TTL) return
        // Let the initial screen settle before spending server round-trips on a
        // background reconciliation.
        delay(STARTUP_DEFER)
        refresh()
    }

    /**
     * Best-effort re-query of every series tagged [HIDDEN_TAG]. Failures keep the
     * last set. Always runs (bypasses the TTL) — callers are the admin
     * hide/unhide paths and pull-to-refresh, which need an immediate sync.
     */
    suspend fun refresh() {
        val ids = runCatching { queryHiddenIds() }.getOrNull() ?: return
        _hiddenIds.value = ids
        saveCache(ids)
        saveTimestamp(Clock.System.now())
    }

    /** Admin: add [HIDDEN_TAG] to each series' tags (server-gated to admins). */
    suspend fun hide(ids: Collection<String>) = setHidden(ids, hidden = true)

    /** Admin: remove [HIDDEN_TAG] from each series' tags. */
    suspend fun unhide(ids: Collection<String>) = setHidden(ids, hidden = false)

    /**
     * Merge [HIDDEN_TAG] in/out of each series' existing tags via a metadata
     * update, **without touching tagsLock** — the user's kora:genre:* / kora:tag:*
     * tags are preserved. Komga rejects the write for non-admins (403). Re-queries
     * after so the filter set + lists reflect the change. Runs on the raw api.
     */
    private suspend fun setHidden(ids: Collection<String>, hidden: Boolean) {
        if (ids.isEmpty()) return
        val seriesApi = rawApi.value.seriesApi
        ids.forEach { value ->
            runCatching {
                val id = KomgaSeriesId(value)
                val current = seriesApi.getOneSeries(id).metadata.tags
                val updated = if (hidden) (current + HIDDEN_TAG).distinct()
                else current.filterNot { it == HIDDEN_TAG }
                if (updated.toSet() != current.toSet()) {
                    seriesApi.update(id, KomgaSeriesMetadataUpdateRequest(tags = patchLists(current, updated)))
                }
            }
        }
        refresh()
    }

    private suspend fun queryHiddenIds(): Set<String> {
        val seriesApi = rawApi.value.seriesApi
        val result = LinkedHashSet<String>()
        var pageIndex = 0
        while (true) {
            val page = seriesApi.getSeriesList(
                conditionBuilder = allOfSeries { tag { isEqualTo(HIDDEN_TAG) } },
                fulltextSearch = null,
                pageRequest = KomgaPageRequest(size = HIDDEN_PAGE_SIZE, pageIndex = pageIndex),
            )
            page.content.forEach { result += it.id.value }
            if (page.content.isEmpty() || pageIndex >= page.totalPages - 1) break
            pageIndex++
        }
        return result
    }

    // -- Cold-start cache (one id per line; no extra serialization dependency) --

    private suspend fun loadCache(): Set<String>? = cacheKey?.let { key ->
        runCatching {
            cacheFile(key).readBytes().decodeToString()
                .split('\n').filter { it.isNotBlank() }.toSet()
        }.getOrNull()
    }

    private suspend fun saveCache(ids: Set<String>) {
        val key = cacheKey ?: return
        runCatching {
            (FileKit.filesDir / CACHE_DIR).createDirectories()
            cacheFile(key).write(ids.joinToString("\n").encodeToByteArray())
        }
    }

    private fun cacheFile(key: String) =
        FileKit.filesDir / CACHE_DIR / "${key.replace(Regex("[^A-Za-z0-9_-]"), "_")}.ids"

    // -- Last-refresh timestamp (separate file, so the .ids parsing is untouched) --

    private suspend fun loadTimestamp(): Instant? = cacheKey?.let { key ->
        runCatching {
            Instant.fromEpochMilliseconds(timestampFile(key).readBytes().decodeToString().trim().toLong())
        }.getOrNull()
    }

    private suspend fun saveTimestamp(now: Instant) {
        val key = cacheKey ?: return
        runCatching {
            (FileKit.filesDir / CACHE_DIR).createDirectories()
            timestampFile(key).write(now.toEpochMilliseconds().toString().encodeToByteArray())
        }
    }

    private fun timestampFile(key: String) =
        FileKit.filesDir / CACHE_DIR / "${key.replace(Regex("[^A-Za-z0-9_-]"), "_")}.ts"

    private companion object {
        const val HIDDEN_PAGE_SIZE = 500
        const val CACHE_DIR = "hidden_series"
        /** Re-scan the hidden set at most this often in the background. */
        val REFRESH_TTL = 24.hours
        /** Wait this long after auth before a background reconciliation. */
        val STARTUP_DEFER = 3.seconds
    }
}
