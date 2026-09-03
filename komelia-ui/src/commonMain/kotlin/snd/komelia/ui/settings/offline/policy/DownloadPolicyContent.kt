package snd.komelia.ui.settings.offline.policy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import snd.komelia.ui.LocalStrings
import snd.komelia.ui.common.components.DropdownChoiceMenu
import snd.komelia.ui.common.components.LabeledEntry
import snd.komelia.ui.common.components.SwitchWithLabel

/** Gigabytes, as the cap is presented. Stored as megabytes. */
private val storageLimitOptionsGb = listOf(1, 2, 4, 8, 16, 32)

/** 0 is "never" — the age rule off, leaving only the cap. */
private val cleanupDaysOptions = listOf(0, 7, 14, 30, 90)

@Composable
fun DownloadPolicyContent(
    wifiOnly: Boolean,
    onWifiOnlyChange: (Boolean) -> Unit,
    whileChargingOnly: Boolean,
    onWhileChargingOnlyChange: (Boolean) -> Unit,
    storageLimitMb: Int,
    onStorageLimitChange: (Int) -> Unit,
    cleanupReadAfterDays: Int,
    onCleanupReadAfterDaysChange: (Int) -> Unit,
    cleanupIncludeManual: Boolean,
    onCleanupIncludeManualChange: (Boolean) -> Unit,
    usedBytes: Long,
    isCleaning: Boolean,
    onCleanupNow: () -> Unit,
) {
    val strings = LocalStrings.current.ui
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

        SwitchWithLabel(
            checked = wifiOnly,
            onCheckedChange = onWifiOnlyChange,
            label = { Text(strings.downloadWifiOnly) },
            supportingText = { Text(strings.downloadWifiOnlyDescription) },
            modifier = Modifier.fillMaxWidth(),
        )

        SwitchWithLabel(
            checked = whileChargingOnly,
            onCheckedChange = onWhileChargingOnlyChange,
            label = { Text(strings.downloadWhileChargingOnly) },
            supportingText = { Text(strings.downloadWhileChargingOnlyDescription) },
            modifier = Modifier.fillMaxWidth(),
        )

        HorizontalDivider()

        DropdownChoiceMenu(
            selectedOption = storageLimitEntry(storageLimitMb),
            options = storageLimitOptionsGb.map { storageLimitEntry(it * 1024) },
            onOptionChange = { onStorageLimitChange(it.value) },
            label = { Text(strings.downloadStorageLimit) },
            inputFieldModifier = Modifier.widthIn(min = 150.dp),
        )
        Text(
            strings.downloadStorageLimitDescription,
            style = MaterialTheme.typography.bodySmall,
            color = LocalContentColor.current.copy(alpha = 0.6f),
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("${strings.downloadStorageUsed} : ${formatMegabytes(usedBytes)}")
            Button(onClick = onCleanupNow, enabled = !isCleaning) { Text(strings.cleanupNow) }
        }

        HorizontalDivider()

        DropdownChoiceMenu(
            selectedOption = cleanupDaysEntry(cleanupReadAfterDays, strings.never, strings.days),
            options = cleanupDaysOptions.map { cleanupDaysEntry(it, strings.never, strings.days) },
            onOptionChange = { onCleanupReadAfterDaysChange(it.value) },
            label = { Text(strings.cleanupReadAfterDays) },
            inputFieldModifier = Modifier.widthIn(min = 150.dp),
        )
        Text(
            strings.cleanupReadAfterDaysDescription,
            style = MaterialTheme.typography.bodySmall,
            color = LocalContentColor.current.copy(alpha = 0.6f),
            modifier = Modifier.padding(bottom = 4.dp),
        )

        SwitchWithLabel(
            checked = cleanupIncludeManual,
            onCheckedChange = onCleanupIncludeManualChange,
            label = { Text(strings.cleanupIncludeManual) },
            supportingText = { Text(strings.cleanupIncludeManualDescription) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun storageLimitEntry(limitMb: Int): LabeledEntry<Int> =
    LabeledEntry(limitMb, "${limitMb / 1024} GB")

private fun cleanupDaysEntry(days: Int, neverLabel: String, daysLabel: String): LabeledEntry<Int> =
    if (days <= 0) LabeledEntry(0, neverLabel)
    else LabeledEntry(days, "$days ${daysLabel.lowercase()}")

private fun formatMegabytes(bytes: Long): String {
    val mb = bytes / 1024 / 1024
    return if (mb >= 1024) "${mb / 1024} GB" else "$mb MB"
}
