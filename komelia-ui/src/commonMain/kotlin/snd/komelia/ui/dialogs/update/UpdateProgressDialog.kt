package snd.komelia.ui.dialogs.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import snd.komelia.ui.dialogs.AppDialog
import snd.komelia.ui.platform.cursorForHand
import snd.komelia.ui.LocalStrings

@Composable
fun UpdateProgressDialog(
    totalSize: Long,
    downloadedSize: Long,
    onCancel: () -> Unit,
) {
    AppDialog(
        modifier = Modifier.widthIn(max = 600.dp),
        header = {
            Text(
                LocalStrings.current.ui.updating,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(10.dp)
            )
        },
        controlButtons = {
            FilledTonalButton(
                onClick = onCancel,
                modifier = Modifier.cursorForHand().padding(10.dp),
                content = { Text(LocalStrings.current.ui.cancel) }
            )
        },
        content = { DialogContent(totalSize, downloadedSize) },
        onDismissRequest = {}
    )
}

@Composable
private fun DialogContent(
    totalSize: Long,
    downloadedSize: Long,
) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 30.dp)) {
        if (totalSize == 0L)
            LinearProgressIndicator(modifier = Modifier.fillMaxSize())
        else
            LinearProgressIndicator(
                progress = { downloadedSize / totalSize.toFloat() },
                modifier = Modifier.fillMaxSize()
            )
    }

}