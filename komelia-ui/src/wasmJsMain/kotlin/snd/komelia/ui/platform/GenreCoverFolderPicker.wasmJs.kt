package snd.komelia.ui.platform

import androidx.compose.runtime.Composable

/** No folder access in the browser sandbox — the import button stays hidden. */
@Composable
actual fun rememberGenreCoverFolderPicker(
    onPicked: (List<Pair<String, ByteArray>>) -> Unit,
): () -> Unit = {}
