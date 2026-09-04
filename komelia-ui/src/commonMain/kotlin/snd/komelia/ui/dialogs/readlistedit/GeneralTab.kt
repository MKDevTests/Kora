package snd.komelia.ui.dialogs.readlistedit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import snd.komelia.ui.common.components.CheckboxWithLabel
import snd.komelia.ui.dialogs.tabs.DialogTab
import snd.komelia.ui.dialogs.tabs.TabItem
import snd.komelia.ui.LocalStrings

internal class GeneralTab(
    private val vm: ReadListEditDialogViewModel,
) : DialogTab {

    @Composable
    override fun options() = TabItem(
        title = LocalStrings.current.ui.general.uppercase(),
        icon = Icons.Default.FormatAlignCenter
    )

    @Composable
    override fun Content() {
        Column(
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            TextField(
                value = vm.name,
                onValueChange = vm::name::set,
                label = { Text(LocalStrings.current.ui.name) },
                supportingText = {
                    vm.nameValidationError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                },
                isError = vm.nameValidationError != null,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth()
            )

            TextField(
                value = vm.summary,
                onValueChange = vm::summary::set,
                label = { Text(LocalStrings.current.ui.summary) },
                minLines = 6,
                maxLines = 12,
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider()
            Column {
                Text(
                    LocalStrings.current.ui.byDefaultBooksInA,
                    style = MaterialTheme.typography.bodyMedium
                )
                CheckboxWithLabel(
                    checked = vm.manualOrdering,
                    onCheckedChange = vm::manualOrdering::set,
                    label = { Text(LocalStrings.current.ui.manualOrdering) }
                )

            }
        }
    }

}