package snd.komelia.ui.dialogs.book.editbulk

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import snd.komelia.ui.StateHolder
import snd.komelia.ui.common.components.LabeledEntry.Companion.stringEntry
import snd.komelia.ui.common.components.LockableChipTextFieldWithSuggestions
import snd.komelia.ui.dialogs.tabs.DialogTab
import snd.komelia.ui.dialogs.tabs.TabItem
import snd.komelia.ui.LocalStrings

class TagsTab(
    private val vm: BookBulkEditDialogViewModel
) : DialogTab {
    @Composable
    override fun options() = TabItem(
        title = LocalStrings.current.ui.tags.uppercase(),
        icon = Icons.Default.LocalOffer
    )

    @Composable
    override fun Content() {
        TagsTabContent(
            tags = StateHolder(vm.tags, vm::tags::set),
            tagsLock = StateHolder(vm.tagsLock, vm::tagsLock::set),
            allTags = vm.allTags
        )
    }
}

@Composable
private fun TagsTabContent(
    tags: StateHolder<List<String>>,
    tagsLock: StateHolder<Boolean>,
    allTags: List<String>,
) {

    Column(
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val warningColor = MaterialTheme.colorScheme.tertiary
        Row(Modifier.border(Dp.Hairline, warningColor).padding(20.dp)) {
            Icon(Icons.Default.PriorityHigh, null, tint = warningColor)
            Text(
                text = LocalStrings.current.ui.youAreEditingTagsFor2,
                color = warningColor
            )
        }
        LockableChipTextFieldWithSuggestions(
            values = tags.value,
            onValuesChange = { tags.setValue(it) },
            label = LocalStrings.current.ui.tags,
            suggestions = remember(allTags) { allTags.map { stringEntry(it) } },
            locked = tagsLock.value,
            onLockChange = { tagsLock.setValue(it) }
        )
    }
}