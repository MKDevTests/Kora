package snd.komelia.ui.settings.chapters

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import snd.komelia.ui.LocalStrings
import snd.komelia.ui.LocalViewModelFactory
import snd.komelia.ui.common.components.DropdownChoiceMenu
import snd.komelia.ui.common.components.LabeledEntry
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

        SettingsScreenContainer(strings.chapterManagement) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = strings.chapterManagementDescription,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

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
                                "${outcome.notFound} ${strings.chapterManagementNoMatchCount}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
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
                    // The score is shown, not merely the order: "88%" tells the
                    // admin how far apart the two titles actually are, which a
                    // position in a list does not.
                    Text(
                        text = "${candidate.score}% · ${candidate.series.metadata.title} · " +
                            "${candidate.series.booksCount} ${strings.books}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { vm.onPickCandidate(row, candidate) }
                            .padding(vertical = 4.dp),
                    )
                }
            }
        }
    }
}
