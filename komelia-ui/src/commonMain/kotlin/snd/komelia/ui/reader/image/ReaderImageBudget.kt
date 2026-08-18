package snd.komelia.ui.reader.image

import androidx.compose.ui.unit.IntSize

/**
 * How much decoded page data a reader may hold, and what one page costs.
 *
 * The caches underneath count entries, not bytes, and that is what took the
 * reader down on 2026-08-18: `reason=3 (LOW_MEMORY)`, `importance=100`, killed
 * in the foreground four seconds after a scan started. The log shows why the
 * same build had been fine all morning — the reader moved from a manga
 * (1400x1993) to a comic (1988x3056), which is **2.18x the pixels for the same
 * sixteen entries**. A limit expressed in pages cannot see that.
 *
 * There is no way to recover from this at runtime: page bitmaps live in the
 * native heap, so `largeHeap` does not cover them and no OutOfMemoryError is
 * ever thrown to back off from. The process is simply killed. The budget has to
 * be kept under the limit rather than discovered by hitting it.
 */
object ReaderImageBudget {

    /**
     * Deliberately conservative, and deliberately not derived from the device:
     * the number that matters is total RSS across bitmaps, the ONNX arenas, the
     * translation model and Compose's own textures, and only the last of those
     * is knowable from here.
     *
     * Sized so the behaviour only changes where it went wrong. A manga page is
     * ~11MB, so seventeen of them fit and the entry ceiling still governs —
     * nothing about reading manga moves. A comic page is ~24MB and settles at
     * eight, which still holds the page on screen, the one being prefetched and
     * six turns of history.
     */
    const val BYTES = 192L * 1024 * 1024

    /**
     * Four bytes a pixel. An upper bound rather than a measurement: the decoder
     * can hand back a denser or a sparser format depending on the source, and a
     * budget that guesses low is the one failure mode that puts us back where
     * we started.
     */
    fun estimateBytes(size: IntSize?): Long =
        if (size == null || size.width <= 0 || size.height <= 0) UNKNOWN_BYTES
        else size.width.toLong() * size.height.toLong() * 4L

    /**
     * A page whose size has not arrived yet still has to weigh something, or a
     * run of them would slip past the budget entirely. Roughly one comic page.
     */
    private const val UNKNOWN_BYTES = 24L * 1024 * 1024
}
