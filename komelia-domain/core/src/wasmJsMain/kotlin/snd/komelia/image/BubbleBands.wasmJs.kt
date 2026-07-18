package snd.komelia.image

// No-op: the bubble detector is Android-only (the platform this build ships to,
// and the only one with the ONNX runtime on the classpath).
actual fun configureBubbleDetector(@Suppress("UNUSED_PARAMETER") modelPath: () -> String?) = Unit

actual suspend fun detectBubbleBands(
    @Suppress("UNUSED_PARAMETER") image: KomeliaImage,
): List<ClosedFloatingPointRange<Float>> = emptyList()
