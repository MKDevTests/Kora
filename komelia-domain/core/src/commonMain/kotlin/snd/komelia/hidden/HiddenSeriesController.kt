package snd.komelia.hidden

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import snd.komelia.komga.api.KomgaApi
import snd.komga.client.common.KomgaPageRequest
import snd.komga.client.search.allOfSeries
import snd.komga.client.user.KomgaUser

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
 * The set is refreshed whenever a user becomes authenticated (cookie ready /
 * sign-in / server switch) and on demand via [refresh] (e.g. after an admin
 * hides or unhides a series, or on pull-to-refresh).
 */
class HiddenSeriesController(
    private val rawApi: StateFlow<KomgaApi>,
    authenticatedUser: StateFlow<KomgaUser?>,
    private val scope: CoroutineScope,
) {
    private val _hiddenIds = MutableStateFlow<Set<String>>(emptySet())
    val hiddenIds: StateFlow<Set<String>> = _hiddenIds.asStateFlow()

    init {
        // (Re-)query on every authenticated state: the initial replay is null
        // (no query), then a real user triggers the first fetch once the session
        // is usable, and any sign-in / server switch refreshes it.
        scope.launch {
            authenticatedUser.collect { user -> if (user != null) refresh() }
        }
    }

    /** Best-effort re-query of every series tagged [HIDDEN_TAG]. Failures keep the last set. */
    suspend fun refresh() {
        val ids = runCatching { queryHiddenIds() }.getOrNull() ?: return
        _hiddenIds.value = ids
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

    private companion object {
        const val HIDDEN_PAGE_SIZE = 500
    }
}
