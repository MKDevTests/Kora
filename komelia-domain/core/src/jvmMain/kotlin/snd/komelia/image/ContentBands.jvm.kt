package snd.komelia.image

// No-op: pixel analysis for the webtoon smart scroll is implemented on Android
// only (the platform this ships to). An empty list makes the continuous reader
// fall back to a fixed-distance tap scroll.
actual suspend fun detectContentBands(
    @Suppress("UNUSED_PARAMETER") image: KomeliaImage,
): List<ClosedFloatingPointRange<Float>> = emptyList()
