package snd.komelia.ui.platform

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
actual fun rememberGenreCoverFolderPicker(
    onPicked: (List<Pair<String, ByteArray>>) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // OpenDocumentTree, so the user can reach ANY folder — Download included,
    // which the photo picker never showed.
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val files = withContext(Dispatchers.IO) {
                DocumentFile.fromTreeUri(context, uri)
                    ?.listFiles().orEmpty()
                    .filter { it.isFile }
                    .mapNotNull { document ->
                        val name = document.name ?: return@mapNotNull null
                        val bytes = runCatching {
                            context.contentResolver.openInputStream(document.uri)?.use { it.readBytes() }
                        }.getOrNull() ?: return@mapNotNull null
                        name to bytes
                    }
            }
            onPicked(files)
        }
    }

    return { launcher.launch(null) }
}
