package snd.komelia.image.processing

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import snd.komelia.image.ReaderImage

/**
 * Pipeline-level signal that a page rendered through the image processing
 * pipeline was detected as entirely blank (libvips findTrim returned an
 * empty area). Used by readers to optionally skip such pages from the
 * spread map when the "auto-skip blank pages" setting is on.
 *
 * Held as a single instance and shared between the (singleton) image
 * processing pipeline and per-book reader states. Subscribers must filter
 * by their current bookId to avoid acting on emissions for other books.
 */
class BlankPageDetector {
    private val _detected = MutableSharedFlow<ReaderImage.PageId>(
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val detected: SharedFlow<ReaderImage.PageId> = _detected.asSharedFlow()

    fun report(pageId: ReaderImage.PageId) {
        _detected.tryEmit(pageId)
    }
}
