package snd.komelia.image.processing

import kotlinx.coroutines.flow.Flow
import snd.komelia.image.KomeliaImage
import snd.komelia.image.ReaderImage.PageId

/**
 * Accessibility step: finds speech bubbles on a page and inverts **only their
 * pixels** — a white bubble with black text becomes a black bubble with white
 * text, while the artwork around it is left untouched. Aimed at readers who
 * find the bright white of bubbles glaring.
 *
 * Detection is pure computer vision, deliberately: this runs on every page
 * automatically when enabled, so an OCR pass (hundreds of ms) is too slow, and
 * the ONNX panel model only yields bounding boxes — a box would invert the
 * artwork in the corners around an oval bubble. What is needed is the bubble's
 * actual pixel mask.
 *
 * The heuristic that makes this viable rather than a false-positive mess is the
 * **contour hierarchy**: a speech bubble is a bright, closed, roughly convex
 * blob that *contains holes* — the letters. A white shirt, a gutter, or a blank
 * panel background contains no holes. See the Android implementation for the
 * full filter chain.
 *
 * Only implemented on Android (the platform this build ships to, and the only
 * one with OpenCV on the classpath). A no-op elsewhere.
 *
 * @param enabled emits the user's opt-in setting; when false [process] returns
 *   null so the pipeline keeps the untouched image.
 */
expect class BubbleInvertStep(enabled: Flow<Boolean>) : ProcessingStep {
    override suspend fun process(pageId: PageId, image: KomeliaImage): KomeliaImage?
    override suspend fun addChangeListener(callback: () -> Unit)
}
