package snd.komelia.ui.platform

import androidx.compose.runtime.Composable

/**
 * Asks the user for a FOLDER and hands back every file in it as
 * (fileName, bytes) — used to bulk-import genre covers.
 *
 * A folder rather than a multi-file selection on purpose. Picking files went
 * through the system pickers, and both let the user down: the photo picker only
 * lists the media gallery (a pack copied into Download is invisible), and the
 * document picker's extension filter maps to MIME types and silently hid files
 * that were sitting right there. Reading a folder wholesale sidesteps the lot —
 * and beats tapping fifty-odd files.
 *
 * Returns the launcher to call on click. Non-images are simply reported as
 * unrecognised by the import, never hidden from the user.
 */
@Composable
expect fun rememberGenreCoverFolderPicker(
    onPicked: (List<Pair<String, ByteArray>>) -> Unit,
): () -> Unit
