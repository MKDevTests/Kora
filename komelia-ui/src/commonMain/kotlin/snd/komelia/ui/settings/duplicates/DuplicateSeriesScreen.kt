package snd.komelia.ui.settings.duplicates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import snd.komelia.ui.LocalStrings
import snd.komelia.ui.LocalViewModelFactory
import snd.komelia.ui.common.components.accentFilterChipColors
import snd.komelia.ui.common.images.SeriesThumbnail
import snd.komelia.ui.series.SeriesScreen
import snd.komelia.ui.settings.SettingsScreenContainer
import snd.komga.client.series.KomgaSeriesId

/**
 * Admin-only screen listing series stored twice inside one library.
 *
 * The sweep reads the local similarity index and issues no request. That is
 * deliberate: a duplicate hunt that re-listed every series would be the heaviest
 * screen in Kora, on the same endpoints that already make the genre tab slow.
 *
 * Nothing on a collapsed card touches the network either — the covers used to
 * sit there and cost several hundred thumbnail fetches on opening. They now live
 * inside the details block, loaded one group at a time on request.
 */
class DuplicateSeriesScreen : Screen {

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    override fun Content() {
        val viewModelFactory = LocalViewModelFactory.current
        val vm = rememberScreenModel { viewModelFactory.getDuplicateSeriesViewModel() }
        LaunchedEffect(Unit) { vm.initialize() }
        val strings = LocalStrings.current.ui

        SettingsScreenContainer(strings.duplicateSeries) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = strings.duplicateSeriesDescription,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                SummaryBanner(vm)

                if (vm.missingLibraries.isNotEmpty()) {
                    // An unindexed library is absent from the results, which
                    // looks exactly like a library with nothing to fix. Saying
                    // so is the difference between a clean sweep and a lie.
                    Notice(
                        text = "${strings.duplicateSeriesNotIndexed} " +
                            vm.missingLibraries.joinToString(", "),
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                if (vm.totalGroups > 0) {
                    OutlinedTextField(
                        value = vm.query,
                        onValueChange = vm::onQueryChange,
                        placeholder = { Text(strings.duplicateSeriesSearch) },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = {
                            if (vm.query.isNotBlank()) {
                                IconButton(onClick = { vm.onQueryChange("") }) {
                                    Icon(Icons.Filled.Close, contentDescription = strings.clear)
                                }
                            }
                        },
                        singleLine = true,
                        shape = CircleShape,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    // Only libraries that actually hold a group get a chip, so
                    // the row says where the work is instead of listing the
                    // whole server.
                    if (vm.libraryFacets.size > 1) {
                        // FlowRow: six libraries plus "all" do not fit one line
                        // on a phone, and wrapping beats clipping the last ones
                        // off the screen.
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            FilterChip(
                                selected = vm.selectedLibrary == null,
                                onClick = { vm.onLibrarySelected(null) },
                                label = { Text(strings.all) },
                                colors = accentFilterChipColors(),
                            )
                            vm.libraryFacets.forEach { facet ->
                                FilterChip(
                                    selected = vm.selectedLibrary == facet.libraryId,
                                    onClick = { vm.onLibrarySelected(facet.libraryId) },
                                    label = { Text("${facet.name} · ${facet.count}") },
                                    colors = accentFilterChipColors(),
                                )
                            }
                        }
                    }
                }

                if (!vm.scanning && vm.totalGroups == 0) {
                    Notice(strings.duplicateSeriesEmpty, MaterialTheme.colorScheme.onSurfaceVariant)
                } else if (!vm.scanning && vm.likely.isEmpty() && vm.unsure.isEmpty()) {
                    Notice(strings.duplicateSeriesNoMatch, MaterialTheme.colorScheme.onSurfaceVariant)
                }

                if (vm.likely.isNotEmpty()) {
                    SectionHeader("${strings.duplicateSeriesLikely} (${vm.likely.size})")
                    vm.likely.take(vm.visibleCount).forEach { row -> GroupCard(vm, row) }
                    // A page at a time. The settings container scrolls a plain
                    // Column, so every card drawn is a card composed — the whole
                    // list at once cost six seconds of frozen main thread.
                    val remaining = vm.likely.size - vm.visibleCount
                    if (remaining > 0) {
                        OutlinedButton(
                            onClick = vm::showMore,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("${strings.duplicateSeriesShowMore} ($remaining)") }
                    }
                }

                // Kept visible rather than dropped: on the real catalogue these
                // are collection names filed as titles, but the app cannot prove
                // that, so it says so instead of deciding for the admin. Never
                // paginated — there are three of them.
                if (vm.unsure.isNotEmpty()) {
                    SectionHeader("${strings.duplicateSeriesUnsure} (${vm.unsure.size})")
                    Notice(
                        text = strings.duplicateSeriesUnsureHint,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        icon = Icons.Default.Warning,
                    )
                    vm.unsure.forEach { row -> GroupCard(vm, row) }
                }
            }
        }
    }
}

/**
 * The three numbers that frame the screen, plus the two actions that change
 * them.
 *
 * A count of groups alone understates the job: 230 groups is 470 series to look
 * at. And "12688 scanned" is what makes the other two trustworthy.
 */
@Composable
private fun SummaryBanner(vm: DuplicateSeriesViewModel) {
    val strings = LocalStrings.current.ui
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                Stat("${vm.totalGroups}", strings.duplicateSeriesGroups)
                Stat("${vm.totalSeries}", strings.duplicateSeriesInvolved)
                Stat("${vm.scannedSeries}", strings.duplicateSeriesScanned)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TextButton(onClick = vm::rescan, enabled = !vm.scanning) {
                    Text(strings.duplicateSeriesRescan)
                }
                if (vm.ignoredCount > 0) {
                    TextButton(onClick = vm::clearIgnored, enabled = !vm.scanning) {
                        Text("${strings.duplicateSeriesClearIgnored} (${vm.ignoredCount})")
                    }
                }
                if (vm.scanning) CircularProgressIndicator(Modifier.width(20.dp).height(20.dp))
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column {
        Text(value, style = MaterialTheme.typography.headlineSmall)
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A line of explanation that is meant to be read, not skimmed past. */
@Composable
private fun Notice(
    text: String,
    color: androidx.compose.ui.graphics.Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = color, modifier = Modifier.width(18.dp))
        Text(text = text, style = MaterialTheme.typography.bodySmall, color = color)
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun GroupCard(vm: DuplicateSeriesViewModel, row: DuplicateRow) {
    val strings = LocalStrings.current.ui
    val navigator = LocalNavigator.currentOrThrow
    val expanded = row.details.isNotEmpty()

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = row.group.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = row.libraryName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // The count is a badge, not a "· 2" tacked onto the library
                // name: it is the one number that says how big the problem is.
                CountBadge(row.group.members.size)
            }

            // Actions on their own line, left-aligned under the title. On a
            // tablet the old layout left half the width empty between the title
            // and two buttons pinned to the far edge.
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 2.dp),
            ) {
                TextButton(onClick = { vm.onToggleDetails(row) }, enabled = !row.loadingDetails) {
                    when {
                        row.loadingDetails -> CircularProgressIndicator(Modifier.width(16.dp).height(16.dp))
                        expanded -> Icon(Icons.Default.ExpandLess, contentDescription = null, modifier = Modifier.width(18.dp))
                        else -> Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.width(18.dp))
                    }
                    Text(strings.duplicateSeriesDetails, Modifier.padding(start = 6.dp))
                }
                TextButton(onClick = { vm.onIgnoreGroup(row) }) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.width(18.dp))
                    Text(strings.duplicateSeriesNotDuplicates, Modifier.padding(start = 6.dp))
                }
            }

            // Cover, book count, language and publisher together: on the real
            // catalogue that is what settles a group in one look — "7th Garden ·
            // 8 · FR · Delcourt" against "7th Garden · 8 · EN · VIZ Media" is
            // two editions, not a duplicate. None of it is in the local index,
            // so this block is the only part of the screen that goes to the
            // server.
            row.details.forEach { detail ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SeriesThumbnail(
                        seriesId = KomgaSeriesId(detail.seriesId),
                        modifier = Modifier.width(44.dp).height(66.dp),
                        contentScale = ContentScale.Crop,
                    )
                    Text(
                        text = detail.line,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    // Deciding which copy to delete means looking at it. Without
                    // this the admin has to leave, search the title by hand, and
                    // come back having lost their place in the list.
                    IconButton(onClick = { navigator.push(SeriesScreen(KomgaSeriesId(detail.seriesId))) }) {
                        Icon(
                            Icons.AutoMirrored.Default.OpenInNew,
                            contentDescription = strings.duplicateSeriesOpen,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CountBadge(count: Int) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.widthIn(min = 34.dp),
    ) {
        Text(
            text = "×$count",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
}
