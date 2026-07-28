package snd.komelia.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import io.github.vinceglb.filekit.dialogs.compose.rememberDirectoryPickerLauncher
import io.github.vinceglb.filekit.path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
actual fun rememberGenreCoverFolderPicker(
    onPicked: (List<Pair<String, ByteArray>>) -> Unit,
): () -> Unit {
    val scope = rememberCoroutineScope()

    val launcher = rememberDirectoryPickerLauncher { directory ->
        if (directory == null) return@rememberDirectoryPickerLauncher
        scope.launch {
            val files = withContext(Dispatchers.IO) {
                File(directory.path).listFiles().orEmpty()
                    .filter { it.isFile }
                    .mapNotNull { file ->
                        val bytes = runCatching { file.readBytes() }.getOrNull() ?: return@mapNotNull null
                        file.name to bytes
                    }
            }
            onPicked(files)
        }
    }

    return { launcher.launch() }
}
