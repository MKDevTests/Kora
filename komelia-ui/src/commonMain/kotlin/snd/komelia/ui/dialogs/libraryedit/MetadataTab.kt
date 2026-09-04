package snd.komelia.ui.dialogs.libraryedit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import snd.komelia.ui.StateHolder
import snd.komelia.ui.common.components.CheckboxWithLabel
import snd.komelia.ui.common.components.ChildSwitchingCheckboxWithLabel
import snd.komelia.ui.dialogs.tabs.DialogTab
import snd.komelia.ui.dialogs.tabs.TabItem
import snd.komelia.ui.LocalStrings

internal class MetadataTab(
    private val vm: LibraryEditDialogViewModel,
) : DialogTab {

    @Composable
    override fun options() = TabItem(
        title = LocalStrings.current.ui.metadata.uppercase(),
        icon = Icons.Default.Book
    )

    @Composable
    override fun Content() {
        MetadataTabContent(
            importComicInfoBook = StateHolder(vm.importComicInfoBook, vm::importComicInfoBook::set),
            importComicInfoSeries = StateHolder(vm.importComicInfoSeries, vm::importComicInfoSeries::set),
            importComicInfoSeriesAppendVolume = StateHolder(
                vm.importComicInfoSeriesAppendVolume,
                vm::importComicInfoSeriesAppendVolume::set
            ),
            importComicInfoCollection = StateHolder(
                vm.importComicInfoCollection,
                vm::importComicInfoCollection::set
            ),
            importComicInfoReadList = StateHolder(vm.importComicInfoReadList, vm::importComicInfoReadList::set),
            importEpubBook = StateHolder(vm.importEpubBook, vm::importEpubBook::set),
            importEpubSeries = StateHolder(vm.importEpubSeries, vm::importEpubSeries::set),
            importMylarSeries = StateHolder(vm.importMylarSeries, vm::importMylarSeries::set),
            importLocalArtwork = StateHolder(vm.importLocalArtwork, vm::importLocalArtwork::set),
            importBarcodeIsbn = StateHolder(vm.importBarcodeIsbn, vm::importBarcodeIsbn::set),
        )
    }
}


@Composable
private fun MetadataTabContent(
    importComicInfoBook: StateHolder<Boolean>,
    importComicInfoSeries: StateHolder<Boolean>,
    importComicInfoSeriesAppendVolume: StateHolder<Boolean>,
    importComicInfoCollection: StateHolder<Boolean>,
    importComicInfoReadList: StateHolder<Boolean>,
    importEpubBook: StateHolder<Boolean>,
    importEpubSeries: StateHolder<Boolean>,
    importMylarSeries: StateHolder<Boolean>,
    importLocalArtwork: StateHolder<Boolean>,
    importBarcodeIsbn: StateHolder<Boolean>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        ComicInfoSettings(
            importComicInfoBook = importComicInfoBook,
            importComicInfoSeries = importComicInfoSeries,
            importComicInfoSeriesAppendVolume = importComicInfoSeriesAppendVolume,
            importComicInfoCollection = importComicInfoCollection,
            importComicInfoReadList = importComicInfoReadList,
        )
        EpubSettings(
            importEpubBook = importEpubBook,
            importEpubSeries = importEpubSeries
        )
        MylarSettings(importMylarSeries)
        LocalArtworkSettings(importLocalArtwork)
        BarcodeISBNSettings(importBarcodeIsbn)


    }
}

@Composable
private fun ComicInfoSettings(
    importComicInfoBook: StateHolder<Boolean>,
    importComicInfoSeries: StateHolder<Boolean>,
    importComicInfoSeriesAppendVolume: StateHolder<Boolean>,
    importComicInfoCollection: StateHolder<Boolean>,
    importComicInfoReadList: StateHolder<Boolean>,
) {
    Column {
        ChildSwitchingCheckboxWithLabel(
            label = { Text(LocalStrings.current.ui.importMetadataForCbrCbz) },
            children = listOf(
                importComicInfoBook,
                importComicInfoSeries,
                importComicInfoSeriesAppendVolume,
                importComicInfoCollection,
                importComicInfoReadList
            ),
        )
        Column(
            modifier = Modifier.padding(start = 10.dp)
        ) {
            CheckboxWithLabel(
                label = { Text(LocalStrings.current.ui.bookMetadata) },
                checked = importComicInfoBook.value,
                onCheckedChange = importComicInfoBook.setValue,
            )

            CheckboxWithLabel(
                label = { Text(LocalStrings.current.ui.seriesMetadata) },
                checked = importComicInfoSeries.value,
                onCheckedChange = importComicInfoSeries.setValue,
            )

            CheckboxWithLabel(
                label = { Text(LocalStrings.current.ui.appendVolumeToSeriesTitle) },
                checked = importComicInfoSeriesAppendVolume.value,
                onCheckedChange = importComicInfoSeriesAppendVolume.setValue,
            )

            CheckboxWithLabel(
                label = { Text(LocalStrings.current.ui.collections) },
                checked = importComicInfoCollection.value,
                onCheckedChange = importComicInfoCollection.setValue,
            )

            CheckboxWithLabel(
                label = { Text(LocalStrings.current.ui.readLists2) },
                checked = importComicInfoReadList.value,
                onCheckedChange = importComicInfoReadList.setValue,
            )
        }
    }
}

@Composable
private fun EpubSettings(
    importEpubBook: StateHolder<Boolean>,
    importEpubSeries: StateHolder<Boolean>,
) {
    Column {
        ChildSwitchingCheckboxWithLabel(
            label = { Text(LocalStrings.current.ui.importMetadataFromEpubFiles) },
            children = listOf(
                importEpubBook,
                importEpubSeries,
            ),
        )
        Column(Modifier.padding(start = 10.dp)) {
            CheckboxWithLabel(
                label = { Text(LocalStrings.current.ui.bookMetadata) },
                checked = importEpubBook.value,
                onCheckedChange = importEpubBook.setValue,
            )
            CheckboxWithLabel(
                label = { Text(LocalStrings.current.ui.seriesMetadata) },
                checked = importEpubSeries.value,
                onCheckedChange = importEpubSeries.setValue,
            )
        }
    }
}

@Composable
private fun MylarSettings(
    importMylarSeries: StateHolder<Boolean>,
) {
    Column {
        Text(LocalStrings.current.ui.importMetadataGeneratedByMylar)
        Column(Modifier.padding(start = 10.dp)) {
            CheckboxWithLabel(
                label = { Text(LocalStrings.current.ui.seriesMetadata) },
                checked = importMylarSeries.value,
                onCheckedChange = importMylarSeries.setValue,
            )
        }
    }
}

@Composable
private fun LocalArtworkSettings(
    importLocalArtwork: StateHolder<Boolean>,
) {

    Column {
        Text(LocalStrings.current.ui.importLocalMediaAssets)
        Column(Modifier.padding(start = 10.dp)) {
            CheckboxWithLabel(
                label = { Text(LocalStrings.current.ui.localArtwork) },
                checked = importLocalArtwork.value,
                onCheckedChange = importLocalArtwork.setValue,
            )
        }
    }
}

@Composable
private fun BarcodeISBNSettings(
    importBarcodeIsbn: StateHolder<Boolean>,
) {

    Column {
        Text(LocalStrings.current.ui.importIsbnWithinBarcode)
        Column(Modifier.padding(start = 10.dp)) {
            CheckboxWithLabel(
                label = { Text(LocalStrings.current.ui.isbnBarcode) },
                checked = importBarcodeIsbn.value,
                onCheckedChange = importBarcodeIsbn.setValue,
            )
        }
    }
}