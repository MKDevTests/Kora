package snd.komelia.image

/**
 * Vertical content blocks of a page, as fractions of its height (0f..1f), with
 * the empty gutters between them excluded.
 *
 * Used by the continuous reader to make a screen tap land on the next block of
 * artwork instead of advancing a blind fixed distance — the thing that makes
 * webtoon scrolling feel right, since a tall strip is mostly panels separated by
 * large blank bands.
 *
 * Deliberately NOT the ONNX panel detector: that costs ~770 ms per page and its
 * reading order falls apart on tall strips. This is a row-profile heuristic
 * measured at ~10-70 ms, which is all that "where is there ink" requires.
 *
 * Returns an empty list when the platform can't analyse pixels or nothing
 * conclusive is found — callers must then fall back to a fixed-distance scroll.
 */
expect suspend fun detectContentBands(image: KomeliaImage): List<ClosedFloatingPointRange<Float>>
