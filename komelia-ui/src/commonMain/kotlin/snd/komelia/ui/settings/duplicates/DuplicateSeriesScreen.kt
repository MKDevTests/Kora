package snd.komelia.ui.settings.duplicates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import snd.komelia.ui.LocalStrings
import snd.komelia.ui.LocalViewModelFactory
import snd.komelia.ui.common.images.SeriesThumbnail
import snd.komelia.ui.settings.SettingsScreenContainer
import snd.komga.client.series.KomgaSeriesId

/**
 * Admin-only screen listing series stored twice inside one library.
 *
 * The sweep reads the local similarity index and issues no request. That is
 * deliberate: a duplicate hunt that re-listed every series would be the heaviest
 * screen in Kora, on the same endpoints that already make the genre tab slow.
 *
 * Nothing on a collapsed row touches the network either — the covers used to sit
 * there and cost several hundred thumbnail fetches on opening. They now live
 * inside the details block, which is loaded one group at a time on request.
 */
class DuplicateSeriesScreen : Screen {

    @Composable
    override fun Content() {
        val viewModelFactory = LocalViewModelFactory.current
        val vm = rememberScreenModel { viewModelFactory.getDuplicateSeriesViewModel() }
        LaunchedEffect(Unit) { vm.initialize() }
        val strings = LocalStrings.current.ui

        SettingsScreenContainer(strings.duplicateSeries) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = strings.duplicateSeriesDescription,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(onClick = vm::rescan, enabled = !vm.scanning) {
                        Text(strings.duplicateSeriesRescan)
                    }
                    if (vm.ignoredCount > 0) {
                        TextButton(onClick = vm::clearIgnored) {
                            Text("${strings.duplicateSeriesClearIgnored} (${vm.ignoredCount})")
                        }
                    }
                    if (vm.scanning) CircularProgressIndicator(Modifier.padding(4.dp))
                }

                // Says what the sweep could actually see. An unindexed library is
                // simply absent from the results, and a screen that answered "no
                // duplicates" would be lying about the libraries it never read.
                if (vm.scannedSeries > 0) {
                    Text(
                        text = "${vm.scannedSeries} ${strings.duplicateSeriesScanned}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (vm.missingLibraries.isNotEmpty()) {
                    Text(
                        text = "${strings.duplicateSeriesNotIndexed} " +
                            vm.missingLibraries.joinToString(", "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                if (!vm.scanning && vm.likely.isEmpty() && vm.unsure.isEmpty()) {
                    Text(
                        text = strings.duplicateSeriesEmpty,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (vm.likely.isNotEmpty()) {
                    SectionHeader("${strings.duplicateSeriesLikely} (${vm.likely.size})")
                    vm.likely.take(vm.visibleCount).forEach { row ->
                        GroupCard(vm, row)
                        HorizontalDivider()
                    }
                    // A page at a time. The settings container scrolls a plain
                    // Column, so every row drawn is a row composed — the whole
                    // list at once cost six seconds of frozen main thread.
                    val remaining = vm.likely.size - vm.visibleCount
                    if (remaining > 0) {
                        TextButton(onClick = vm::showMore) {
                            Text("${strings.duplicateSeriesShowMore} ($remaining)")
                        }
                    }
                }

                // Kept visible rather than dropped: on the real catalogue these
                // are collection names filed as titles, but the app cannot prove
                // that, so it says so instead of deciding for the admin. Never
                // paginated — there are three of them.
                if (vm.unsure.isNotEmpty()) {
                    SectionHeader("${strings.duplicateSeriesUnsure} (${vm.unsure.size})")
                    Text(
                        text = strings.duplicateSeriesUnsureHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    vm.unsure.forEach { row ->
                        GroupCard(vm, row)
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
private fun GroupCard(vm: DuplicateSeriesViewModel, row: DuplicateRow) {
    val strings = LocalStrings.current.ui
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = row.group.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${row.libraryName} · ${row.group.members.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { vm.onToggleDetails(row) }, enabled = !row.loadingDetails) {
                if (row.loadingDetails) {
                    CircularProgressIndicator(Modifier.width(20.dp).height(20.dp))
                } else {
                    Icon(Icons.Default.Info, contentDescription = strings.duplicateSeriesDetails)
                }
            }
            TextButton(onClick = { vm.onIgnoreGroup(row) }) {
                Text(strings.duplicateSeriesNotDuplicates)
            }
        }

        // Cover, book count, language and publisher together: on the real
        // catalogue that is what settles a group in one look — "7th Garden · 8 ·
        // FR · Delcourt" against "7th Garden · 8 · EN · VIZ Media" is two
        // editions, not a duplicate. None of it is in the local index, so this
        // block is the only part of the screen that goes to the server.
        row.details.forEach { detail ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
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
                )
            }
        }
    }
}
