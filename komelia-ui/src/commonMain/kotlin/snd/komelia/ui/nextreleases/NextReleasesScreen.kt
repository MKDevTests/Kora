package snd.komelia.ui.nextreleases

import snd.komelia.ui.strings.UiStrings
import snd.komelia.ui.LocalTransparentNavBarPadding
import kotlinx.datetime.isoDayNumber
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import snd.komelia.ui.KoraShapes
import snd.komelia.ui.common.components.KoraChipDefaults
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
import snd.komelia.ui.pushUnique

private val logger = KotlinLogging.logger {}

/**
 * Cross-library release calendar: every series across every library
 * carrying a parseable `nextrelease:*` tag (see [NextReleaseLabels]).
 * Refonte 2: grouped by day, as covers with the volume on them, and split
 * in two -- what is coming, and what came out in the last
 * [NextReleasesService.RECENT_DAYS] days, which used to vanish the day it
 * was released.
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
        val strings = LocalStrings.current

        var selectedLibraryIds by remember { mutableStateOf<Set<KomgaLibraryId>>(emptySet()) }
        var showRecent by remember { mutableStateOf(false) }

        val statusBarHeight = LocalRawStatusBarHeight.current

        Column(Modifier.fillMaxSize().padding(top = statusBarHeight)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { navigator.pop() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.ui.back)
                }
                Text(
                    strings.ui.prochainesSorties,
                    style = MaterialTheme.typography.headlineSmall.copy(fontFamily = MaterialTheme.typography.titleLarge.fontFamily),
                    modifier = Modifier.padding(start = 4.dp),
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
                            strings.counts.expiredNextReleaseTags(expiredTags.size),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { navigator.pushUnique(MaintenanceScreen()) }) { Text(strings.ui.gRer) }
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
                            label = { Text(strings.ui.toutes2) },
                            colors = KoraChipDefaults.filterChipColors(),
                            border = KoraChipDefaults.border,
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
                            colors = KoraChipDefaults.filterChipColors(),
                            border = KoraChipDefaults.border,
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
                    val today = todayForLabel()
                    val inSelection = state.value.filter {
                        selectedLibraryIds.isEmpty() || it.libraryId in selectedLibraryIds
                    }
                    val upcoming = inSelection.filter { it.date >= today }.sortedBy { it.date }
                    val recent = inSelection.filter { it.date < today }.sortedByDescending { it.date }
                    val shown = if (showRecent) recent else upcoming
                    val libraryNames = libraries.associate { it.id to it.name }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    ) {
                        FilterChip(
                            selected = !showRecent,
                            onClick = { showRecent = false },
                            label = { Text("${strings.ui.releasesUpcoming} · ${upcoming.size}") },
                            colors = KoraChipDefaults.filterChipColors(),
                            border = KoraChipDefaults.border,
                        )
                        FilterChip(
                            selected = showRecent,
                            onClick = { showRecent = true },
                            label = { Text("${strings.ui.releasesRecent} · ${recent.size}") },
                            colors = KoraChipDefaults.filterChipColors(),
                            border = KoraChipDefaults.border,
                        )
                    }

                    if (shown.isEmpty()) {
                        Text(
                            when {
                                showRecent -> strings.ui.releasesRecentEmpty
                                state.value.isEmpty() -> strings.ui.releasesNoneTagged
                                else -> strings.ui.releasesNoneInSelection
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
                        )
                    } else {
                        val byDay = shown.groupBy { it.date }
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(112.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(
                                start = 16.dp, end = 16.dp, top = 4.dp,
                                bottom = 24.dp + LocalTransparentNavBarPadding.current,
                            ),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            if (!showRecent) {
                                item(span = { GridItemSpan(maxLineSpan) }, key = "summary") {
                                    ReleasesSummary(upcoming, today)
                                }
                            }
                            byDay.forEach { (date, dayReleases) ->
                                item(span = { GridItemSpan(maxLineSpan) }, key = "day_$date") {
                                    DayHeader(date, dayReleases.size, today)
                                }
                                items(dayReleases, key = { "${it.seriesId.value}_${it.volume}_${it.date}" }) { release ->
                                    ReleaseCard(
                                        release = release,
                                        libraryName = libraryNames[release.libraryId],
                                        past = release.date < today,
                                    ) {
                                        // SeriesScreen resolves the full series (incl. the
                                        // oneshot check + self-redirect) from the id alone.
                                        navigator.pushUnique(SeriesScreen(release.seriesId))
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
private fun ReleasesSummary(upcoming: List<NextReleasesService.UpcomingRelease>, today: LocalDate) {
    val s = LocalStrings.current.ui
    val next = upcoming.firstOrNull() ?: return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
        ) {
            Icon(Icons.Rounded.Event, null, tint = MaterialTheme.colorScheme.primary)
        }
        Column(Modifier.weight(1f)) {
            Text(s.releasesAnnounced(upcoming.size), style = MaterialTheme.typography.titleMedium)
            Text(
                s.releasesNextOn(dayMonthLabel(next.date, s, today)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DayHeader(date: LocalDate, count: Int, today: LocalDate) {
    val s = LocalStrings.current.ui
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(top = 12.dp),
    ) {
        Text(
            dayHeading(date, s, today),
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            s.volumesCount(count),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun ReleaseCard(
    release: NextReleasesService.UpcomingRelease,
    libraryName: String?,
    past: Boolean,
    onClick: () -> Unit,
) {
    val s = LocalStrings.current.ui
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .alpha(if (past) 0.7f else 1f)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(0.7f).clip(RoundedCornerShape(12.dp))) {
            SeriesThumbnail(
                release.seriesId,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Text(
                s.volumeShort(release.volume),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.72f))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        Text(
            release.seriesTitle,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (libraryName != null) {
            Text(
                libraryName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** "2027-01-12" -> "12 janvier" / "12 January", with the year when it is not this one. */
internal fun dayMonthLabel(date: LocalDate, s: UiStrings, today: LocalDate = todayForLabel()): String {
    val base = "${date.dayOfMonth} ${s.monthName(date.monthNumber)}"
    return if (date.year == today.year) base else "$base ${date.year}"
}

/** "Jeudi 25 septembre" / "Thursday 25 September"; "Aujourd'hui" and "Demain" when they apply. */
private fun dayHeading(date: LocalDate, s: UiStrings, today: LocalDate): String {
    val days = (date.toEpochDays() - today.toEpochDays()).toLong()
    val weekday = s.weekdayName(date.dayOfWeek.isoDayNumber)
    val label = when (days) {
        0L -> s.today
        1L -> s.tomorrow
        -1L -> s.yesterday
        else -> "$weekday ${dayMonthLabel(date, s, today)}"
    }
    return label.replaceFirstChar { it.uppercase() }
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
fun NextReleasesHomeCard(modifier: Modifier = Modifier) {
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
    // The list now also holds what came out in the last days; the teaser is
    // about what is coming.
    val today = todayForLabel()
    val current = (releases ?: return).filter { it.date >= today }
    if (current.isEmpty()) return
    val next = current.first()

    Card(
        modifier = modifier
            .clickable { navigator.pushUnique(NextReleasesScreen()) },
        shape = KoraShapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Event,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 12.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = LocalStrings.current.counts.nextReleaseLine(
                        next.seriesTitle, next.volume, dayMonthLabel(next.date, LocalStrings.current.ui),
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = LocalStrings.current.ui.prochainesSorties2 +
                        if (current.size > 1) " · +${current.size - 1}" else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
