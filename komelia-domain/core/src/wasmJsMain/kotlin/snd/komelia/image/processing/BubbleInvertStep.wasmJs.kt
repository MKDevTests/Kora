package snd.komelia.image.processing

import kotlinx.coroutines.flow.Flow
import snd.komelia.image.KomeliaImage
import snd.komelia.image.ReaderImage.PageId

// No-op: no OpenCV in the browser build. See the commonMain expect for why this
// option is Android-only.
actual class BubbleInvertStep actual constructor(
    @Suppress("UNUSED_PARAMETER") enabled: Flow<Boolean>,
) : ProcessingStep {
    actual override suspend fun process(pageId: PageId, image: KomeliaImage): KomeliaImage? = null
    actual override suspend fun addChangeListener(callback: () -> Unit) {}
}
