package snd.komelia.ui.settings.komf.processing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import snd.komelia.ui.common.components.ChipFieldWithSuggestions
import snd.komelia.ui.common.components.DropdownChoiceMenu
import snd.komelia.ui.common.components.DropdownMultiChoiceMenu
import snd.komelia.ui.common.components.LabeledEntry
import snd.komelia.ui.common.components.SwitchWithLabel
import snd.komelia.ui.settings.komf.LanguageSelectionField
import snd.komelia.ui.settings.komf.LibraryTabs
import snd.komelia.ui.settings.komf.komfLanguageTagsSuggestions
import snd.komelia.ui.settings.komf.processing.KomfProcessingSettingsViewModel.ProcessingConfigState
import snd.komf.api.KomfMediaType
import snd.komf.api.KomfReadingDirection
import snd.komf.api.KomfUpdateMode
import snd.komf.api.MediaServer
import snd.komf.api.MediaServer.KOMGA
import snd.komf.api.mediaserver.KomfMediaServerLibrary
import snd.komf.api.mediaserver.KomfMediaServerLibraryId
import snd.komelia.ui.LocalStrings

@Composable
fun KomfProcessingSettingsContent(
    defaultProcessingState: ProcessingConfigState,
    libraryProcessingState: Map<KomfMediaServerLibraryId, ProcessingConfigState>,

    onLibraryConfigAdd: (libraryId: KomfMediaServerLibraryId) -> Unit,
    onLibraryConfigRemove: (libraryId: KomfMediaServerLibraryId) -> Unit,
    libraries: List<KomfMediaServerLibrary>,
    serverType: MediaServer,
) {
    LibraryTabs(
        defaultProcessingState,
        libraryProcessingState,
        onLibraryConfigAdd, onLibraryConfigRemove, libraries
    ) {

        ProcessingConfigContent(it, serverType)
    }
}

@Composable
private fun ProcessingConfigContent(
    state: ProcessingConfigState,
    serverType: MediaServer,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        DropdownMultiChoiceMenu(
            selectedOptions = state.updateModes.map { LabeledEntry(it, it.name) },
            options = remember { KomfUpdateMode.entries.map { LabeledEntry(it, it.name) } },
            onOptionSelect = { state.onUpdateModeSelect(it.value) },
            label = { Text(LocalStrings.current.ui.updateModes) },
            placeholder = LocalStrings.current.ui.none,
            inputFieldModifier = Modifier.fillMaxWidth()
        )

        DropdownChoiceMenu(
            selectedOption = LabeledEntry(state.libraryType, state.libraryType.name),
            options = remember { KomfMediaType.entries.map { LabeledEntry(it, it.name) } },
            onOptionChange = { state.onLibraryTypeChange(it.value) },
            label = { Text(LocalStrings.current.ui.libraryTypeAffectsSomeOptions) },
            inputFieldModifier = Modifier.fillMaxWidth(),
        )

        SwitchWithLabel(
            checked = state.orderBooks,
            onCheckedChange = state::onOrderBooksChange,
            label = { Text(LocalStrings.current.ui.orderBooks) },

            supportingText = {
                Text(
                    LocalStrings.current.ui.attemptToOrderBooksUsing,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        )
        HorizontalDivider()

        Text(LocalStrings.current.ui.aggregationSettings, style = MaterialTheme.typography.titleLarge)
        SwitchWithLabel(
            checked = state.aggregate,
            onCheckedChange = state::onAggregateChange,
            label = { Text(LocalStrings.current.ui.aggregate) },
            supportingText = {
                Text(
                    LocalStrings.current.ui.aggregateAndCombineMetadataFrom,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        )

        SwitchWithLabel(
            checked = state.mergeGenres,
            onCheckedChange = state::onMergeGenresChange,
            enabled = state.aggregate,
            label = { Text(LocalStrings.current.ui.mergeGenres) },
            supportingText = {
                Text(
                    LocalStrings.current.ui.ifAggregateOptionIsEnabled,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        )

        SwitchWithLabel(
            checked = state.mergeTags,
            onCheckedChange = state::onMergeTagsChange,
            enabled = state.aggregate,
            label = { Text(LocalStrings.current.ui.mergeTags) },

            supportingText = {
                Text(
                    LocalStrings.current.ui.ifAggregateOptionIsEnabled2,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        )

        HorizontalDivider()
        Text(LocalStrings.current.ui.coverSettings, style = MaterialTheme.typography.titleLarge)
        SwitchWithLabel(
            checked = state.seriesCovers,
            onCheckedChange = state::onSeriesCoversChange,
            label = { Text(LocalStrings.current.ui.seriesCovers) },

            supportingText = {
                Text(
                    LocalStrings.current.ui.uploadSeriesCovers,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        )

        SwitchWithLabel(
            checked = state.bookCovers,
            onCheckedChange = state::onBookCoversChange,
            label = { Text(LocalStrings.current.ui.bookCovers) },

            supportingText = {
                Text(
                    LocalStrings.current.ui.uploadBookCovers,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        )

        SwitchWithLabel(
            checked = state.overrideExistingCovers,
            onCheckedChange = state::onOverrideExistingCoversChange,
            label = { Text(LocalStrings.current.ui.overrideExistingCovers) },

            supportingText = {
                Text(
                    LocalStrings.current.ui.ifEntryAlreadyHasA,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        )

        HorizontalDivider()
        Text(LocalStrings.current.ui.titleSettings, style = MaterialTheme.typography.titleLarge)
        SwitchWithLabel(
            checked = state.seriesTitle,
            onCheckedChange = state::onSeriesTitleChange,
            label = { Text(LocalStrings.current.ui.seriesTitle) },

            supportingText = {
                Text(
                    LocalStrings.current.ui.updateSeriesTitleIfMatched,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        )
        SwitchWithLabel(
            checked = state.alternativeSeriesTitles,
            onCheckedChange = state::onAlternativeSeriesTitlesChange,
            label = { Text(LocalStrings.current.ui.alternativeSeriesTitles) },

            supportingText = {
                Text(
                    LocalStrings.current.ui.updateSeriesAlternativeTitleIf,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        )
        SwitchWithLabel(
            checked = state.fallbackToAltTitle,
            onCheckedChange = state::onFallbackToAltTitleChange,
            label = { Text(LocalStrings.current.ui.alternativeTitleFallback) },

            supportingText = {
                Text(
                    LocalStrings.current.ui.useFirstAvailableAlternativeTitle,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        )
        LanguageSelectionField(
            label = LocalStrings.current.ui.seriesTitleLanguageIso639,
            languageValue = state.seriesTitleLanguage,
            onLanguageValueChange = state::onSeriesTitleLanguageChange,
            onLanguageValueSave = state::onSeriesTitleLanguageSave
        )
        ChipFieldWithSuggestions(
            label = { Text(LocalStrings.current.ui.alternativeTitleLanguagesIso639) },
            values = state.alternativeSeriesTitleLanguages,
            onValuesChange = state::onAlternativeTitleLanguagesChange,
            suggestions = komfLanguageTagsSuggestions
        )
        HorizontalDivider()
        Text(LocalStrings.current.ui.defaultValues, style = MaterialTheme.typography.titleLarge)
        if (serverType == KOMGA) {
            DropdownChoiceMenu(
                selectedOption = LabeledEntry(state.readingDirectionValue, state.readingDirectionValue?.name ?: "None"),
                options = remember {
                    listOf(LabeledEntry<KomfReadingDirection?>(null, "None")) +
                            KomfReadingDirection.entries.map { LabeledEntry(it, it.name) }
                },
                onOptionChange = { state.onReadingDirectionChange(it.value) },
                label = { Text(LocalStrings.current.ui.defaultSeriesReadingDirection) },
                inputFieldModifier = Modifier.fillMaxWidth(),
            )
        }
        LanguageSelectionField(
            label = LocalStrings.current.ui.defaultSeriesLanguage,
            languageValue = state.defaultLanguageValue ?: "",
            onLanguageValueChange = state::onDefaultLanguageChange,
            onLanguageValueSave = state::onDefaultLanguageSave
        )

        Spacer(Modifier.height(30.dp))
    }
}

