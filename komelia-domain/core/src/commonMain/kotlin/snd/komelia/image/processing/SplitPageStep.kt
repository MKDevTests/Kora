package snd.komelia.image.processing

import io.github.oshai.kotlinlogging.KotlinLogging
import snd.komelia.image.ImageRect
import snd.komelia.image.KomeliaImage
import snd.komelia.image.ReaderImage

private val logger = KotlinLogging.logger {}

/**
 * Crops the image to its left or right half based on [ReaderImage.PageId.halfTag].
 *
 * Used by the paged reader's "split landscape pages" feature: when a page is
 * a wide double-page spread (width > height), the reader generates two
 * synthetic page entries with halfTag = "LEFT" / "RIGHT" sharing the same
 * pageNumber. This step performs the actual extraction. When halfTag is
 * null the image is left untouched.
 */
class SplitPageStep : ProcessingStep {
    override suspend fun process(pageId: ReaderImage.PageId, image: KomeliaImage): KomeliaImage? {
        val tag = pageId.halfTag ?: return null
        val width = image.width
        val height = image.pageHeight
        val mid = width / 2
        val rect = when (tag) {
            "LEFT" -> ImageRect(left = 0, top = 0, right = mid, bottom = height)
            "RIGHT" -> ImageRect(left = mid, top = 0, right = width, bottom = height)
            else -> {
                logger.warn { "Unknown halfTag '$tag' on page ${pageId.pageNumber}, skipping split" }
                return null
            }
        }
        return image.extractArea(rect)
    }

    override suspend fun addChangeListener(callback: () -> Unit) {
        // The split is driven by per-page metadata (halfTag), not by a global setting,
        // so there is nothing to broadcast: a switch of the user-level toggle triggers
        // a full spreads rebuild via PagedReaderState.onLayoutChange, which already
        // invalidates the image cache.
    }
}
