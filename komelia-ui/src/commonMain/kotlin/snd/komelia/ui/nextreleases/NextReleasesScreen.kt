package snd.komelia.ui.nextreleases

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import androidx.compose.material3.TextButton
import snd.komelia.ui.LoadState
import snd.komelia.ui.LocalKomgaState
import snd.komelia.ui.LocalLibraries
import snd.komelia.ui.settings.maintenance.MaintenanceScreen
import snd.komelia.ui.LocalRawStatusBarHeight
import snd.komelia.ui.LocalViewModelFactory
import snd.komelia.ui.common.components.ErrorContent
import snd.komelia.ui.common.components.LoadingMaxSizeIndicator
import snd.komelia.ui.common.images.SeriesThumbnail
import snd.komelia.ui.library.NextReleaseLabels
import snd.komelia.ui.platform.BackPressHandler
import snd.komelia.ui.series.SeriesScreen
import snd.komga.client.library.KomgaLibrary
import snd.komga.client.library.KomgaLibraryId
import snd.komelia.ui.LocalStrings

private val logger = KotlinLogging.logger {}

/**
 * Cross-library "upcoming releases" calendar: every series across every
 * library carrying a parseable, future `nextrelease:*` tag (see
 * [NextReleaseLabels]), sorted by date ascending. Purely a read of the
 * user's existing tagging convention — nothing new to maintain.
 */
class NextReleasesScreen : Screen {
    override val key: String = "next_releases"

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModelFactory = LocalViewModelFactory.current
        val vm = rememberScreenModel { viewModelFactory.getNextReleasesViewModel() }
        val libraries = LocalLibraries.current.collectAsState().value
        LaunchedEffect(libraries) { if (libraries.isNotEmpty()) vm.load(libraries) }

        var selectedLibraryIds by remember { mutableStateOf<Set<KomgaLibraryId>>(emptySet()) }

        val statusBarHeight = LocalRawStatusBarHeight.current

        Column(Modifier.fillMaxSize().padding(top = statusBarHeight)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { navigator.pop() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = LocalStrings.current.ui.back)
                }
                Icon(Icons.Rounded.Event, contentDescription = null, modifier = Modifier.padding(start = 4.dp))
                Text(
                    LocalStrings.current.ui.prochainesSorties,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }

            // Admin-only, shown only when there is something to clean: the
            // no-spam nudge towards Settings → Admin → Maintenance. Non-admins
            // never see it (they couldn't purge anyway — Komga 403s the write).
            val isAdmin = LocalKomgaState.current.authenticatedUser.collectAsState().value?.roleAdmin() ?: false
            val expiredTags = NextReleasesScanner.expiredTags.collectAsState().value
            if (isAdmin && expiredTags.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${expiredTags.size} tag(s) nextrelease périmé(s)",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { navigator.push(MaintenanceScreen()) }) { Text(LocalStrings.current.ui.gRer) }
                    }
                }
            }

            if (libraries.size > 1) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    item {
                        FilterChip(
                            selected = selectedLibraryIds.isEmpty(),
                            onClick = { selectedLibraryIds = emptySet() },
                            label = { Text(LocalStrings.current.ui.toutes2) },
                        )
                    }
                    items(libraries) { library ->
                        FilterChip(
                            selected = library.id in selectedLibraryIds,
                            onClick = {
                                selectedLibraryIds = if (library.id in selectedLibraryIds) {
                                    selectedLibraryIds - library.id
                                } else {
                                    selectedLibraryIds + library.id
                                }
                            },
                            label = { Text(library.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        )
                    }
                }
            }

            when (val state = vm.state.collectAsState().value) {
                is LoadState.Error -> ErrorContent(
                    message = state.exception.message ?: "Unknown Error",
                    onReload = { vm.load(libraries) },
                )

                LoadState.Uninitialized, LoadState.Loading -> LoadingMaxSizeIndicator()

                is LoadState.Success -> {
                    val releases = state.value.filter {
                        selectedLibraryIds.isEmpty() || it.libraryId in selectedLibraryIds
                    }
                    if (releases.isEmpty()) {
                        Text(
                            if (state.value.isEmpty())
                                "Aucune sortie à venir. Les séries taguées « nextrelease:<tome>-<jj.mm.aaaa> » apparaîtront ici."
                            else
                                "Aucune sortie à venir dans les bibliothèques sélectionnées.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
                        )
                    } else {
                        val byMonth = releases.groupBy { it.date.year to it.date.monthNumber }
                        LazyColumn {
                            byMonth.forEach { (yearMonth, monthReleases) ->
                                item(key = "header_${yearMonth.first}_${yearMonth.second}") {
                                    Text(
                                        monthYearLabel(monthReleases.first().date),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    )
                                }
                                items(monthReleases, key = { "${it.seriesId.value}_${it.volume}" }) { release ->
                                    NextReleaseRow(release = release) {
                                        // SeriesScreen resolves the full series (incl. the
                                        // oneshot check + self-redirect) from the id alone.
                                        navigator.push(SeriesScreen(release.seriesId))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            BackPressHandler { navigator.pop() }
        }
    }
}

@Composable
private fun NextReleaseRow(
    release: NextReleasesService.UpcomingRelease,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SeriesThumbnail(
            release.seriesId,
            modifier = Modifier.size(width = 48.dp, height = 68.dp),
        )
        Column(Modifier.padding(start = 12.dp).fillMaxWidth()) {
            Text(
                release.seriesTitle,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "Tome ${release.volume} — ${dayMonthLabel(release.date)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val frenchMonths = listOf(
    "janvier", "février", "mars", "avril", "mai", "juin",
    "juillet", "août", "septembre", "octobre", "novembre", "décembre",
)

private fun frenchMonthName(monthNumber: Int): String = frenchMonths[monthNumber - 1]

/** "2027-01-12" -> "Janvier 2027". */
private fun monthYearLabel(date: LocalDate): String {
    val month = frenchMonthName(date.monthNumber).replaceFirstChar { it.uppercase() }
    return "$month ${date.year}"
}

/** "2027-01-12" -> "12 janvier" (same year as today) or "12 janvier 2027" otherwise. */
private fun dayMonthLabel(date: LocalDate, today: LocalDate = todayForLabel()): String {
    val base = "${date.dayOfMonth} ${frenchMonthName(date.monthNumber)}"
    return if (date.year == today.year) base else "$base ${date.year}"
}

private fun todayForLabel(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())

class NextReleasesViewModel(
    private val service: NextReleasesService,
) : StateScreenModel<LoadState<List<NextReleasesService.UpcomingRelease>>>(LoadState.Uninitialized) {

    /**
     * Shows the cached calendar immediately and asks [NextReleasesScanner] for a
     * fresh scan. The scan itself is process-scoped, so leaving this screen no
     * longer cancels it — only the observation below stops.
     */
    fun load(libraries: List<KomgaLibrary>) {
        screenModelScope.launch {
            NextReleasesScanner.primeFromDisk()
            // Opening the calendar is an explicit ask, so bypass the TTL.
            NextReleasesScanner.ensureFresh(service, libraries, force = true)
            NextReleasesScanner.releases.collect { list ->
                mutableState.value = when {
                    list != null -> LoadState.Success(list)
                    // Nothing cached yet and a scan is under way.
                    else -> LoadState.Loading
                }
            }
        }
    }
}

/**
 * Compact upcoming-releases summary, embedded near the top of the Home
 * screen. Renders nothing while there is no data (fresh users / nobody
 * tagging nextrelease yet), consistent with [snd.komelia.ui.stats.HomeStatsCard].
 * Tapping the card pushes the full [NextReleasesScreen].
 */
@Composable
fun NextReleasesHomeCard() {
    val factory = LocalViewModelFactory.current
    val libraries = LocalLibraries.current.collectAsState().value
    // The card runs the service directly rather than through a Voyager
    // ScreenModel — `rememberScreenModel` is only legal inside
    // Screen.Content(), and this composable is nested under HomeContent.
    val service = remember { factory.createNextReleasesService() }
    // Memory, then disk, show instantly; the effects below still refresh
    // silently so the teaser (and the shared cache) stay current.
    // Observed, not driven: the scan lives in NextReleasesScanner so that
    // scrolling away from the card (or leaving Home) can't cancel it.
    val releases by NextReleasesScanner.releases.collectAsState()
    LaunchedEffect(libraries) {
        NextReleasesScanner.primeFromDisk()
        // Honours the 30-minute TTL — this card is entered on every return to
        // Home, and a scan is one Komga query per nextrelease tag.
        NextReleasesScanner.ensureFresh(service, libraries)
    }

    val navigator = LocalNavigator.currentOrThrow
    val current = releases ?: return
    if (current.isEmpty()) return
    val next = current.first()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clickable { navigator.push(NextReleasesScreen()) },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Event,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 12.dp),
            )
            Column(Modifier.fillMaxWidth()) {
                Text(
                    text = LocalStrings.current.ui.prochainesSorties2,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "${next.seriesTitle} — tome ${next.volume}, " +
                        dayMonthLabel(next.date) +
                        if (current.size > 1) " (+${current.size - 1})" else "",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
