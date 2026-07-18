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
 * Detection is a **bubble-detection ONNX model** (ogkalu/comic-text-and-bubble
 * -detector, RT-DETR, Apache-2.0), not the hand-tuned contour heuristic this
 * started as. The heuristic was measured on real volumes and plateaued: it
 * needed a bubble to be a bright, closed, roughly convex blob containing holes,
 * so it missed every bubble with a long tail or spiky outline, every bubble cut
 * by a panel edge, all borderless captions, and effectively all of Wunderwaffen
 * (0 of 12 bubbles on a sample page, where the model finds 12 of 12).
 *
 * The model gives a *box*; a box alone would invert the artwork in the corners
 * around an oval bubble. So the Android implementation refines a pixel mask
 * inside each box — localisation from the model, precision from a local
 * threshold that is reliable precisely because a bubble is known to be there.
 *
 * Only implemented on Android (the platform this build ships to, and the only
 * one with OpenCV and the ONNX runtime on the classpath). A no-op elsewhere.
 *
 * @param enabled emits the user's opt-in setting; when false [process] returns
 *   null so the pipeline keeps the untouched image.
 * @param modelPath resolves the on-disk detector. Evaluated lazily so a model
 *   installed after startup is picked up; when it is missing the step simply
 *   does nothing.
 */
expect class BubbleInvertStep(
    enabled: Flow<Boolean>,
    modelPath: () -> String?,
) : ProcessingStep {
    override suspend fun process(pageId: PageId, image: KomeliaImage): KomeliaImage?
    override suspend fun addChangeListener(callback: () -> Unit)
}
