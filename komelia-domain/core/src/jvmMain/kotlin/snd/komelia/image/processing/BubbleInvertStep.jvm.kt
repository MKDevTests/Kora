package snd.komelia.image.processing

import kotlinx.coroutines.flow.Flow
import snd.komelia.image.KomeliaImage
import snd.komelia.image.ReaderImage.PageId

// No-op: the bubble detector (ONNX) and OpenCV are only on the Android
// classpath, and Android is the platform this accessibility option ships to.
// Returning null leaves the page untouched.
actual class BubbleInvertStep actual constructor(
    @Suppress("UNUSED_PARAMETER") enabled: Flow<Boolean>,
    @Suppress("UNUSED_PARAMETER") modelPath: () -> String?,
) : ProcessingStep {
    actual override suspend fun process(pageId: PageId, image: KomeliaImage): KomeliaImage? = null
    actual override suspend fun addChangeListener(callback: () -> Unit) {}
}
