package snd.komelia.ui.common.immersive

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap

fun normalizePublisherName(name: String): String =
    name.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')

// The pack now tops out at 384x320, so a cached entry is at most 0.4 MB and a
// full cache under 10 MB. Misses are cached too: most publishers ship no logo
// at all, and without this every visit to their series re-read the resource
// just to fail again.
private const val LOGO_CACHE_SIZE = 24

private val logoCache = LinkedHashMap<String, ImageBitmap?>()
private val logoCacheMutex = Mutex()

@OptIn(ExperimentalResourceApi::class)
private suspend fun loadPublisherLogo(key: String): ImageBitmap? {
    logoCacheMutex.withLock {
        if (logoCache.containsKey(key)) {
            // Re-insert so eviction is least-recently-used, not insertion order.
            val cached = logoCache.remove(key)
            logoCache[key] = cached
            return cached
        }
    }

    val bitmap = runCatching {
        Res.readBytes("files/publishers/$key.png").decodeToImageBitmap()
    }.getOrNull()

    logoCacheMutex.withLock {
        logoCache[key] = bitmap
        while (logoCache.size > LOGO_CACHE_SIZE) {
            logoCache.remove(logoCache.keys.first())
        }
    }
    return bitmap
}

@Composable
fun rememberPublisherLogo(publisher: String?): ImageBitmap? {
    var bitmap by remember(publisher) { mutableStateOf<ImageBitmap?>(null) }
    if (!publisher.isNullOrBlank()) {
        LaunchedEffect(publisher) {
            bitmap = loadPublisherLogo(normalizePublisherName(publisher))
        }
    }
    return bitmap
}
