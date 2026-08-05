package snd.komelia.ui.settings.maintenance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.core.screen.Screen
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.datetime.LocalDate
import snd.komelia.AppNotification
import snd.komelia.AppNotifications
import snd.komelia.komga.api.KomgaSeriesApi
import snd.komelia.ui.LocalLibraries
import snd.komelia.ui.LocalViewModelFactory
import snd.komelia.ui.library.NextReleaseLabels
import snd.komelia.ui.nextreleases.NextReleasesService
import snd.komelia.ui.settings.SettingsScreenContainer
import snd.komga.client.common.KomgaPageRequest
import snd.komga.client.common.patchLists
import snd.komga.client.library.KomgaLibrary
import snd.komga.client.search.allOfSeries
import snd.komga.client.series.KomgaSeries
import snd.komga.client.series.KomgaSeriesMetadataUpdateRequest
import snd.komga.client.series.KomgaSeriesSearch
import snd.komelia.ui.LocalStrings

/**
 * Admin-only maintenance tools. First (and so far only) tool: cleanup of
 * expired `nextrelease:*` tags — the user tags upcoming volumes by hand in
 * Komga and past dates otherwise pile up silently, since the calendar simply
 * stops showing them.
 *
 * The settings entry AND the calendar banner that lead here are both gated on
 * roleAdmin(); Komga additionally rejects the tag write server-side (403) for
 * non-admins, so the gate is defence-in-depth, not the only barrier.
 */
class MaintenanceScreen : Screen {

    @Composable
    override fun Content() {
        val viewModelFactory = LocalViewModelFactory.current
        val vm = rememberScreenModel { viewModelFactory.getMaintenanceViewModel() }
        val libraries = LocalLibraries.current.collectAsState().value
        LaunchedEffect(libraries) { if (libraries.isNotEmpty()) vm.initialize(libraries) }

        SettingsScreenContainer(LocalStrings.current.ui.maintenance) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Tags nextrelease périmés : la date de sortie est passée, le " +
                        "calendrier ne les affiche plus. Purger un tag le retire de la " +
                        "série dans Komga (les autres tags sont préservés).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 10.dp),
                )

                HorizontalDivider()

                if (vm.loading) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) { CircularProgressIndicator() }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${vm.expired.size} tag(s) périmé(s)",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                        )
                        if (vm.expired.isNotEmpty()) {
                            TextButton(onClick = vm::purgeAll) { Text(LocalStrings.current.ui.toutPurger) }
                        }
                    }

                    if (vm.expired.isEmpty()) {
                        Text(
                            LocalStrings.current.ui.aucunTagPRimTout,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 20.dp),
                        )
                    } else {
                        vm.expired.forEach { entry ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(start = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        entry.series?.metadata?.title ?: "Série introuvable",
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        "Tome ${entry.volume} — ${NextReleaseLabels.formatDate(entry.date)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (entry.series != null) {
                                    IconButton(onClick = { vm.purge(entry) }) {
                                        Icon(Icons.Default.Delete, contentDescription = LocalStrings.current.ui.purgerLeTag)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

class MaintenanceViewModel(
    private val service: NextReleasesService,
    private val seriesApi: KomgaSeriesApi,
    private val notifications: AppNotifications,
) : ScreenModel {

    data class ExpiredEntry(
        val tag: String,
        val series: KomgaSeries?,
        val volume: String,
        val date: LocalDate,
    )

    var loading by mutableStateOf(true)
        private set
    var expired by mutableStateOf<List<ExpiredEntry>>(emptyList())
        private set

    private var initialized = false

    fun initialize(libraries: List<KomgaLibrary>) {
        if (initialized) return
        initialized = true
        screenModelScope.launch {
            notifications.runCatchingToNotifications {
                // Discovery-only scan (one call per library). compute() would
                // also resolve every FUTURE tag for the calendar — minutes of
                // wasted queries on a slow server, which froze this screen.
                val expiredTags = service.findExpiredTags(libraries)
                // Resolve each expired tag to its series, a few at a time —
                // never stampede the server pool (see reference: Semaphore rule).
                val limit = Semaphore(4)
                expired = coroutineScope {
                    expiredTags.mapNotNull { tag ->
                        NextReleaseLabels.parseTag(tag)?.let { release -> Triple(tag, release.volume, release.date) }
                    }.map { (tag, volume, date) ->
                        async {
                            val series = limit.withPermit {
                                runCatching {
                                    seriesApi.getSeriesList(
                                        KomgaSeriesSearch(
                                            condition = allOfSeries { tag { isEqualTo(tag) } }.toSeriesCondition()
                                        ),
                                        KomgaPageRequest(pageIndex = 0, size = 1),
                                    ).content.firstOrNull()
                                }.getOrNull()
                            }
                            ExpiredEntry(tag, series, volume, date)
                        }
                    }.awaitAll()
                }.sortedBy { it.date }
            }
            loading = false
        }
    }

    /** Remove ONLY this tag from the series' tag list; everything else is kept. */
    fun purge(entry: ExpiredEntry) {
        val series = entry.series ?: return
        screenModelScope.launch {
            notifications.runCatchingToNotifications {
                removeTag(series, entry.tag)
                expired = expired.filterNot { it.tag == entry.tag }
                notifications.add(AppNotification.Success("Tag purgé"))
            }
        }
    }

    fun purgeAll() {
        screenModelScope.launch {
            notifications.runCatchingToNotifications {
                // Sequential on purpose: a purge is a metadata write per series,
                // and the expired set is small — no reason to pressure the pool.
                val purgeable = expired.filter { it.series != null }
                purgeable.forEach { entry -> removeTag(entry.series!!, entry.tag) }
                expired = expired.filterNot { it.series != null }
                notifications.add(AppNotification.Success("${purgeable.size} tag(s) purgé(s)"))
            }
        }
    }

    private suspend fun removeTag(series: KomgaSeries, tag: String) {
        // Re-read the live tags right before writing so we never clobber a tag
        // added since our scan (kora:hidden, kora:genre:*, a fresh nextrelease).
        val current = seriesApi.getOneSeries(series.id).metadata.tags
        val updated = current.filterNot { it == tag }
        if (updated.size != current.size) {
            seriesApi.update(
                series.id,
                KomgaSeriesMetadataUpdateRequest(tags = patchLists(current, updated)),
            )
        }
    }
}
