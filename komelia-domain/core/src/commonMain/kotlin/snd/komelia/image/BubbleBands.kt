package snd.komelia.image

import snd.komelia.image.ReaderImage.PageId

/**
 * Vertical extents of the speech bubbles on a page, as fractions of its height.
 *
 * Used by the continuous reader so a screen tap never stops *through* a bubble.
 * Measured on 6 chapters across 3 series: without this, 51-66% of screens cut a
 * bubble and 7-17% of bubbles were never readable in a single screen; with it,
 * 12-23% and 1-6%, for ~3% more taps and no content skipped.
 *
 * Two sources feed the same cache:
 *  - [publish] — the bubble-invert processing step already runs this exact
 *    detector on every page it treats, so when that setting is on the boxes cost
 *    nothing extra.
 *  - [detectBubbleBands] — a fallback inference for when inversion is off. It is
 *    slow (~770 ms/page), so callers run it off the tap path and simply do
 *    without bubble alignment until it lands.
 */
object BubbleBands {
    private val cache = LinkedHashMap<PageId, List<ClosedFloatingPointRange<Float>>>()
    private const val MAX_ENTRIES = 24

    @Synchronized
    fun publish(pageId: PageId, bands: List<ClosedFloatingPointRange<Float>>) {
        cache[pageId] = bands
        while (cache.size > MAX_ENTRIES) cache.remove(cache.keys.first())
    }

    @Synchronized
    fun cached(pageId: PageId): List<ClosedFloatingPointRange<Float>>? = cache[pageId]

    @Synchronized
    fun clear() = cache.clear()
}

/**
 * Points the bubble detector at its model. Called once at startup; until it is,
 * [detectBubbleBands] returns nothing and callers degrade gracefully.
 */
expect fun configureBubbleDetector(modelPath: () -> String?)

/**
 * Runs the bubble detector on [image]. Expensive — never call this from a tap
 * handler. Returns an empty list when the model or runtime is unavailable.
 */
expect suspend fun detectBubbleBands(image: KomeliaImage): List<ClosedFloatingPointRange<Float>>
