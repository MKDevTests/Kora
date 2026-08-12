package snd.komelia.ui.settings.chapters

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import snd.komelia.ui.LocalStrings
import snd.komelia.ui.LocalViewModelFactory
import snd.komelia.ui.common.components.DropdownChoiceMenu
import snd.komelia.ui.common.components.LabeledEntry
import snd.komelia.ui.common.images.SeriesThumbnail
import snd.komelia.ui.dialogs.AppDialog
import snd.komelia.ui.dialogs.ConfirmationDialog
import snd.komelia.ui.settings.SettingsScreenContainer
import snd.komga.client.library.KomgaLibrary

/**
 * Admin-only screen for pairing chapter series with the volumes they belong to.
 *
 * Chapter series are the ones the "(Chap)" filter hides, so once that filter is
 * on there is nowhere else in the app to reach them — same reason the hidden
 * series have a screen of their own.
 */
class ChapterManagementScreen : Screen {

    @Composable
    override fun Content() {
        val viewModelFactory = LocalViewModelFactory.current
        val vm = rememberScreenModel { viewModelFactory.getChapterManagementViewModel() }
        LaunchedEffect(Unit) { vm.initialize() }
        val strings = LocalStrings.current.ui
        var confirmBulk by remember { mutableStateOf(false) }

        // Bulk matching writes links with no further prompt, so the prompt comes
        // first: an unwanted link is silent once written.
        if (confirmBulk) {
            ConfirmationDialog(
                title = strings.chapterManagementMatchSelected,
                body = strings.chapterManagementConfirmBulk,
                onDialogConfirm = {
                    confirmBulk = false
                    vm.onMatchSelected()
                },
                onDialogDismiss = { confirmBulk = false },
            )
        }

        // What just happened, series by series. A bulk run acts on rows that
        // scroll off screen, and it is the ones it could NOT settle that need
        // to be seen — a counter does not name them.
        if (vm.showReport && vm.report.isNotEmpty()) {
            AppDialog(
                modifier = Modifier.widthIn(max = 640.dp),
                onDismissRequest = vm::dismissReport,
                header = {
                    Text(
                        text = strings.chapterManagementReport,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(16.dp),
                    )
                },
                content = {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        vm.report.forEach { entry -> ReportRow(entry) }
                    }
                },
                controlButtons = {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        FilledTonalButton(onClick = vm::dismissReport) { Text(strings.close) }
                    }
                },
            )
        }

        SettingsScreenContainer(strings.chapterManagement) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = strings.chapterManagementDescription,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // Says where the links will land before any is written: without
                // sharing on, an admin can pair a whole library into a table
                // that never leaves this install.
                if (!vm.sharesToServer) {
                    Text(
                        text = strings.chapterManagementLocalOnly,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                // One library at a time: the listing is one request per library
                // and matching only ever looks inside the same one.
                DropdownChoiceMenu(
                    selectedOption = LabeledEntry<KomgaLibrary?>(
                        vm.selectedLibrary,
                        vm.selectedLibrary?.name ?: strings.chooseLibrary,
                    ),
                    options = vm.libraries.map { LabeledEntry<KomgaLibrary?>(it, it.name) },
                    onOptionChange = { entry -> entry.value?.let { vm.onLibrarySelected(it) } },
                    label = { Text(strings.library) },
                    modifier = Modifier.fillMaxWidth(),
                )

                if (vm.selectedLibrary != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = vm.listFilter == ChapterListFilter.UNLINKED,
                            onClick = { vm.onListFilterChange(ChapterListFilter.UNLINKED) },
                            label = { Text(strings.chapterManagementUnlinkedOnly) },
                        )
                        FilterChip(
                            selected = vm.listFilter == ChapterListFilter.LINKED,
                            onClick = { vm.onListFilterChange(ChapterListFilter.LINKED) },
                            label = { Text(strings.chapterManagementLinkedOnly) },
                        )
                        FilterChip(
                            selected = vm.listFilter == ChapterListFilter.ALL,
                            onClick = { vm.onListFilterChange(ChapterListFilter.ALL) },
                            label = { Text(strings.all) },
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = vm::onSelectAll) { Text(strings.selectAll) }
                        TextButton(
                            onClick = { confirmBulk = true },
                            enabled = vm.selectedIds.isNotEmpty() && !vm.matching,
                        ) { Text(strings.chapterManagementMatchSelected) }
                        if (vm.loading || vm.matching) CircularProgressIndicator(Modifier.padding(4.dp))
                    }

                    vm.lastOutcome?.let { outcome ->
                        Text(
                            text = "${outcome.linked} ${strings.chapterManagementLinkedCount} · " +
                                "${outcome.ambiguous} ${strings.chapterManagementAmbiguousCount} · " +
                                "${outcome.notFound} ${strings.chapterManagementNoMatchCount} · " +
                                "${outcome.failed} ${strings.chapterManagementFailedCount}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                    // Stays until the next run. The app's error toast is gone
                    // long before you can read which series it was about.
                    if (vm.errors.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = strings.chapterManagementErrors,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                            vm.errors.forEach { message ->
                                Text(
                                    text = message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }

                    HorizontalDivider()

                    if (!vm.loading && vm.visibleRows.isEmpty()) {
                        Text(
                            text = strings.chapterManagementEmpty,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    vm.visibleRows.forEach { row ->
                        ChapterRow(vm, row)
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

/**
 * One possible volumes series.
 *
 * A title alone does not decide it: the whole reason there are several is that
 * they share it — the French edition, the English one, the colour one. The
 * cover, the language, the publisher and the volume count are what tell them
 * apart, and all four are already in the search result. Same row as the link
 * suggestions on a series page, on purpose.
 *
 * The score is shown too: "88%" says how far apart the two titles are, which a
 * position in a list does not.
 */
@Composable
private fun CandidateRow(candidate: ChapterCandidate, onClick: () -> Unit) {
    val strings = LocalStrings.current
    val series = candidate.series
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SeriesThumbnail(
            seriesId = series.id,
            modifier = Modifier.width(40.dp).height(60.dp),
            contentScale = ContentScale.Crop,
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = series.metadata.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val details = listOfNotNull(
                "${candidate.score}%",
                series.metadata.language.takeIf { it.isNotBlank() }?.uppercase(),
                series.metadata.publisher.takeIf { it.isNotBlank() },
                strings.counts.booksCount(series.booksCount),
            ).joinToString(" · ")
            Text(
                text = details,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** One line of the end-of-run report: what happened to one series. */
@Composable
private fun ReportRow(entry: MatchReportEntry) {
    val strings = LocalStrings.current.ui
    val (label, color) = when (entry.result) {
        MatchReportResult.LINKED -> strings.chapterManagementLinked to MaterialTheme.colorScheme.primary
        MatchReportResult.AMBIGUOUS ->
            strings.chapterManagementAmbiguous to MaterialTheme.colorScheme.tertiary
        MatchReportResult.NOT_FOUND ->
            strings.chapterManagementNoMatch to MaterialTheme.colorScheme.onSurfaceVariant
        MatchReportResult.FAILED -> strings.chapterManagementFailedCount to MaterialTheme.colorScheme.error
    }
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = entry.title,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = listOfNotNull(label, entry.detail).joinToString(" — "),
            style = MaterialTheme.typography.bodySmall,
            color = color,
        )
    }
}

@Composable
private fun ChapterRow(vm: ChapterManagementViewModel, row: ChapterSeriesRow) {
    val strings = LocalStrings.current.ui
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = row.series.id.value in vm.selectedIds,
                onCheckedChange = { vm.onSelectionToggle(row.series.id.value) },
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = row.series.metadata.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = when {
                        row.linked -> strings.chapterManagementLinked
                        row.candidates.isNotEmpty() -> strings.chapterManagementAmbiguous
                        row.searched -> strings.chapterManagementNoMatch
                        else -> strings.chapterManagementUnlinked
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (row.linked) {
                TextButton(onClick = { vm.onUnlink(row) }) { Text(strings.chapterManagementUnlink) }
            } else {
                TextButton(
                    onClick = { vm.onFindMatch(row) },
                    enabled = !vm.matching,
                ) { Text(strings.chapterManagementFindMatch) }
            }
        }

        // Several exact matches: the app has no ground to prefer one, so the
        // choice is handed over rather than guessed.
        if (row.candidates.isNotEmpty() && !row.linked) {
            Column(Modifier.padding(start = 48.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                row.candidates.forEach { candidate ->
                    CandidateRow(candidate) { vm.onPickCandidate(row, candidate) }
                }
            }
        }
    }
}
