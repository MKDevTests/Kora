package snd.komelia.image.processing

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import snd.komelia.image.KomeliaImage
import snd.komelia.image.ReaderImage
import kotlin.time.measureTimedValue

private val logger = KotlinLogging.logger {}

class CropBordersStep(
    private val enabled: StateFlow<Boolean>,
    private val autoSkipBlankPages: StateFlow<Boolean>,
    private val blankPageDetector: BlankPageDetector,
) : ProcessingStep {
    override suspend fun process(pageId: ReaderImage.PageId, image: KomeliaImage): KomeliaImage? {
        if (!enabled.value) return null
        return try {
            val trim = image.findTrim()
            // findTrim returns an empty area when the whole page is in the
            // background colour — libvips's extractArea would then throw
            // "extract_area: parameter width not set". Treat as blank page:
            // skip the crop (graceful fallback), and notify the detector so
            // readers with the auto-skip setting can exclude it from spreads.
            if (trim.width <= 0 || trim.height <= 0) {
                logger.info { "page ${pageId.pageNumber} appears blank; skipping crop" }
                if (autoSkipBlankPages.value) blankPageDetector.report(pageId)
                return null
            }
            val result = measureTimedValue { image.extractArea(trim) }
            logger.info { "page ${pageId.pageNumber} completed border crop in ${result.duration}" }
            result.value
        } catch (e: Exception) {
            // Defensive: also catch any VipsException that slips through despite
            // the explicit empty-trim check. Don't crash the reader.
            logger.warn(e) { "crop borders failed for page ${pageId.pageNumber}; using original image" }
            if (autoSkipBlankPages.value) blankPageDetector.report(pageId)
            null
        }
    }

    override suspend fun addChangeListener(callback: () -> Unit) {
        enabled.drop(1).collect { callback() }
    }
}
