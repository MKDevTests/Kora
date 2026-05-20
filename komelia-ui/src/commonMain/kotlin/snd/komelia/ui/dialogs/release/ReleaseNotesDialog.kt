package snd.komelia.ui.dialogs.release

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.mohamedrejeb.richeditor.model.rememberRichTextState
import com.mohamedrejeb.richeditor.ui.material3.RichText
import snd.komelia.ui.dialogs.AppDialog
import snd.komelia.ui.platform.cursorForHand
import snd.komelia.updates.AppRelease

/**
 * "What's new in this release" modal shown once per upgrade on first
 * launch after the install. Renders the GitHub release body (markdown)
 * for the currently-running version.
 *
 * Structurally a stripped-down [snd.komelia.ui.dialogs.update.UpdateDialog]
 * — same `AppDialog` host, same `RichText` markdown renderer — but
 * positioned as informational rather than actionable: there's just one
 * "Got it" button which dismisses and persists "seen". No "skip this
 * version" because the modal doesn't reappear anyway.
 */
@Composable
fun ReleaseNotesDialog(
    release: AppRelease,
    onDismiss: () -> Unit,
) {
    AppDialog(
        modifier = Modifier.widthIn(max = 600.dp),
        header = { HeaderContent() },
        content = { DialogContent(release) },
        controlButtons = { ControlButtons(onDismiss = onDismiss) },
        // Tap-outside also counts as dismissal — same effect as "Got it".
        onDismissRequest = onDismiss,
    )
}

@Composable
private fun HeaderContent() {
    Column(
        modifier = Modifier.padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("What's new", style = MaterialTheme.typography.headlineSmall)
        HorizontalDivider()
    }
}

@Composable
private fun DialogContent(release: AppRelease) {
    Column(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Kora ${release.version}",
            style = MaterialTheme.typography.titleLarge,
        )
        val state = rememberRichTextState()
        state.config.apply {
            linkColor = MaterialTheme.colorScheme.secondary
            linkTextDecoration = TextDecoration.Underline
            codeSpanBackgroundColor = MaterialTheme.colorScheme.surfaceVariant
            codeSpanStrokeColor = MaterialTheme.colorScheme.surfaceVariant
        }
        state.setMarkdown(release.releaseNotesBody)
        RichText(state)
    }
}

@Composable
private fun ControlButtons(onDismiss: () -> Unit) {
    Row(
        modifier = Modifier.padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        FilledTonalButton(
            onClick = onDismiss,
            modifier = Modifier.cursorForHand(),
        ) {
            Text("Got it")
        }
    }
}
