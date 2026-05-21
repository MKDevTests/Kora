package snd.komelia.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

private val logger = KotlinLogging.logger { }

@Serializable
data class WidgetCachedBook(
    val bookId: String,
    val seriesTitle: String,
    val bookTitle: String,
    val coverBitmapPath: String,
)

/**
 * Small persistent cache so the "Next book up" widget never goes blank
 * even when the network is flaky, the user has just rebooted, or Kora's
 * dependency graph hasn't finished bootstrapping yet.
 *
 * Storage: `cacheDir/widget_covers/index.json` for the metadata list and
 * `cacheDir/widget_covers/<bookId>.webp` for each compressed cover. The
 * widget reads from cache on cold start, then asynchronously refreshes
 * and overwrites on success.
 */
class WidgetCache(private val context: Context) {
    private val dir: File = File(context.cacheDir, "widget_covers").apply { mkdirs() }
    private val indexFile: File = File(dir, "index.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun load(): List<WidgetCachedBook> {
        if (!indexFile.exists()) return emptyList()
        return runCatching {
            json.decodeFromString<List<WidgetCachedBook>>(indexFile.readText())
                .filter { File(it.coverBitmapPath).exists() }
        }.getOrElse {
            logger.warn(it) { "Could not read widget cache index" }
            emptyList()
        }
    }

    fun save(items: List<WidgetCachedBook>) {
        runCatching {
            indexFile.writeText(json.encodeToString(items))
        }.onFailure { logger.warn(it) { "Could not write widget cache index" } }
    }

    /**
     * Compresses [bitmap] to WebP at quality 85 and writes it under the
     * given [bookId]. Returns the absolute path of the written file (or
     * null on failure). Bitmaps stay reasonably small — ~30kB each at the
     * 240×360 cover cap we use in [NextBookWidget].
     */
    fun writeCover(bookId: String, bitmap: Bitmap): String? {
        val target = File(dir, "$bookId.webp")
        return runCatching {
            target.outputStream().use { out ->
                @Suppress("DEPRECATION")
                bitmap.compress(Bitmap.CompressFormat.WEBP, 85, out)
            }
            target.absolutePath
        }.onFailure { logger.warn(it) { "Could not write widget cover for $bookId" } }
            .getOrNull()
    }

    fun readCover(path: String): Bitmap? = runCatching {
        BitmapFactory.decodeFile(path)
    }.getOrNull()

    /** Prune cover files that aren't referenced by [keep]. */
    fun pruneOrphans(keep: List<WidgetCachedBook>) {
        val referenced = keep.map { File(it.coverBitmapPath).absolutePath }.toSet()
        dir.listFiles { f -> f.extension == "webp" }
            ?.filter { it.absolutePath !in referenced }
            ?.forEach { runCatching { it.delete() } }
    }
}
