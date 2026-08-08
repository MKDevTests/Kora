package com.storyteller.reader

import org.readium.r2.navigator.OverflowableNavigator
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.navigator.preferences.ReadingProgression

/**
 * Where a tap has to land to turn a page.
 *
 * Named after the image reader's `ReaderTapNavigationMode` so the two map one
 * for one and the settings screens can say the same thing. `HORIZONTAL_SPLIT`
 * is inherited naming and means a **horizontal cut** — top half against bottom
 * half — not horizontal zones.
 *
 * This module cannot see the komelia settings model (it depends only on Readium
 * and AndroidX), hence a local copy rather than a shared enum. The mapping lives
 * in `Epub3ReaderState.applySettingsToView`.
 */
enum class TapNavigationMode {
    /** Left goes back, right goes forward. Mirrored when reading right-to-left. */
    LEFT_RIGHT,

    /** The reverse of [LEFT_RIGHT], also mirrored for right-to-left. */
    RIGHT_LEFT,

    /** Top goes back, bottom goes forward. */
    HORIZONTAL_SPLIT,

    /** The reverse of [HORIZONTAL_SPLIT]. */
    REVERSED_HORIZONTAL_SPLIT,
}

/**
 * Turns pages on edge taps, and leaves the middle alone.
 *
 * Replaces Readium's own `DirectionalNavigationAdapter`, which was doing the job
 * with its default settings: `TapEdge.Horizontal` only, hence left and right and
 * nothing else. Its `tapEdges` parameter can add a vertical edge, but the
 * direction is fixed — top is always "back" — so the two reversed modes were out
 * of reach without writing this.
 *
 * The zones are the image reader's, so the two readers behave alike and the
 * shared settings diagram is accurate for both: the width is cut in thirds and
 * the middle third is never navigation — the tap falls through (this returns
 * `false`) so the reader's own listener can show or hide the toolbars. The
 * vertical modes then split the two outer thirds in half, top against bottom.
 *
 * [mode] is read on every tap rather than captured, so changing the setting
 * takes effect without rebuilding the navigator — which would reload the book
 * and lose the reading position.
 */
internal class TapNavigationAdapter(
    private val navigator: OverflowableNavigator,
    private val mode: () -> TapNavigationMode,
    private val animatedTransition: Boolean = true,
) : InputListener {

    override fun onTap(event: TapEvent): Boolean {
        val view = navigator.publicationView
        val width = view.width
        val height = view.height
        if (width <= 0 || height <= 0) return false

        val decision = tapDecision(
            mode = mode(),
            x = event.point.x,
            y = event.point.y,
            width = width,
            height = height,
            rtl = navigator.overflow.value.readingProgression == ReadingProgression.RTL,
        )
        return when (decision) {
            TapDecision.NONE -> false
            // True even when the navigator refuses: at the last page it has
            // nowhere to go, and letting the tap through would pop the toolbars
            // up on every attempt to turn past the end.
            TapDecision.FORWARD -> {
                navigator.goForward(animated = animatedTransition)
                true
            }

            TapDecision.BACKWARD -> {
                navigator.goBackward(animated = animatedTransition)
                true
            }
        }
    }
}

/** What a tap should do. [NONE] means "not navigation, let it through". */
internal enum class TapDecision { NONE, FORWARD, BACKWARD }

/**
 * The zone logic on its own, with no Readium and no Android in sight, so the
 * right-to-left mirroring can be tested without a view or a publication.
 *
 * [x] and [y] are in view pixels, [rtl] is the publication's reading
 * progression.
 */
internal fun tapDecision(
    mode: TapNavigationMode,
    x: Float,
    y: Float,
    width: Int,
    height: Int,
    rtl: Boolean,
): TapDecision {
    val column = zone(x, width)
    if (column == Zone.MIDDLE) return TapDecision.NONE

    return when (mode) {
        // Right-to-left publications put the next page on the left, and the
        // reader expects the physical side to keep meaning the same thing.
        TapNavigationMode.LEFT_RIGHT -> turn(atStart = column == Zone.START, reversed = rtl)
        TapNavigationMode.RIGHT_LEFT -> turn(atStart = column == Zone.START, reversed = !rtl)
        // Halves, not thirds, and no mirroring: reading right-to-left does not
        // put the next page above the current one. Same split as the image
        // reader, which has no dead band on this axis.
        TapNavigationMode.HORIZONTAL_SPLIT ->
            turn(atStart = y < height / 2f, reversed = false)

        TapNavigationMode.REVERSED_HORIZONTAL_SPLIT ->
            turn(atStart = y < height / 2f, reversed = true)
    }
}

private fun turn(atStart: Boolean, reversed: Boolean): TapDecision =
    if (atStart xor reversed) TapDecision.BACKWARD else TapDecision.FORWARD

private enum class Zone { START, MIDDLE, END }

private fun zone(position: Float, extent: Int): Zone {
    val third = extent / 3f
    return when {
        position < third -> Zone.START
        position > extent - third -> Zone.END
        else -> Zone.MIDDLE
    }
}
