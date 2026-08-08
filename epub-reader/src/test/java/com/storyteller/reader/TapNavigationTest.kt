package com.storyteller.reader

import com.storyteller.reader.TapDecision.BACKWARD
import com.storyteller.reader.TapDecision.FORWARD
import com.storyteller.reader.TapDecision.NONE
import com.storyteller.reader.TapNavigationMode.HORIZONTAL_SPLIT
import com.storyteller.reader.TapNavigationMode.LEFT_RIGHT
import com.storyteller.reader.TapNavigationMode.REVERSED_HORIZONTAL_SPLIT
import com.storyteller.reader.TapNavigationMode.RIGHT_LEFT
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The right-to-left mirroring is the part of [tapDecision] that is easy to get
 * backwards and impossible to notice on a French library, so it is covered mode
 * by mode rather than by a happy path.
 */
class TapNavigationTest {

    private val width = 300
    private val height = 600

    /** Left, middle and right columns of a 300px-wide view cut in thirds. */
    private val left = 20f
    private val middle = 150f
    private val right = 280f

    private val top = 50f
    private val bottom = 550f

    private fun decide(
        mode: TapNavigationMode,
        x: Float,
        y: Float = height / 2f - 10f,
        rtl: Boolean = false,
    ) = tapDecision(mode = mode, x = x, y = y, width = width, height = height, rtl = rtl)

    @Test
    fun `middle column never navigates`() {
        for (mode in TapNavigationMode.entries) {
            assertEquals(NONE, decide(mode, middle, top), "$mode, top of the middle column")
            assertEquals(NONE, decide(mode, middle, bottom), "$mode, bottom of the middle column")
            assertEquals(NONE, decide(mode, middle, y = top, rtl = true), "$mode, middle column in RTL")
        }
    }

    @Test
    fun `left right puts the previous page on the left`() {
        assertEquals(BACKWARD, decide(LEFT_RIGHT, left))
        assertEquals(FORWARD, decide(LEFT_RIGHT, right))
    }

    @Test
    fun `left right mirrors for a right to left publication`() {
        assertEquals(FORWARD, decide(LEFT_RIGHT, left, rtl = true))
        assertEquals(BACKWARD, decide(LEFT_RIGHT, right, rtl = true))
    }

    @Test
    fun `right left is the reverse of left right`() {
        assertEquals(FORWARD, decide(RIGHT_LEFT, left))
        assertEquals(BACKWARD, decide(RIGHT_LEFT, right))
    }

    @Test
    fun `right left mirrors for a right to left publication`() {
        assertEquals(BACKWARD, decide(RIGHT_LEFT, left, rtl = true))
        assertEquals(FORWARD, decide(RIGHT_LEFT, right, rtl = true))
    }

    @Test
    fun `horizontal split works in both outer columns`() {
        for (x in listOf(left, right)) {
            assertEquals(BACKWARD, decide(HORIZONTAL_SPLIT, x, top), "top at x=$x")
            assertEquals(FORWARD, decide(HORIZONTAL_SPLIT, x, bottom), "bottom at x=$x")
        }
    }

    @Test
    fun `reversed horizontal split swaps top and bottom`() {
        assertEquals(FORWARD, decide(REVERSED_HORIZONTAL_SPLIT, left, top))
        assertEquals(BACKWARD, decide(REVERSED_HORIZONTAL_SPLIT, left, bottom))
    }

    @Test
    fun `the vertical modes are not mirrored`() {
        // A right-to-left publication does not put the next page above the
        // current one, so reading progression must not touch these two.
        assertEquals(BACKWARD, decide(HORIZONTAL_SPLIT, left, top, rtl = true))
        assertEquals(FORWARD, decide(HORIZONTAL_SPLIT, left, bottom, rtl = true))
        assertEquals(FORWARD, decide(REVERSED_HORIZONTAL_SPLIT, right, top, rtl = true))
        assertEquals(BACKWARD, decide(REVERSED_HORIZONTAL_SPLIT, right, bottom, rtl = true))
    }

    @Test
    fun `the split is at half the height, not a third`() {
        // Matches the image reader, which has no dead band on this axis: just
        // above the midpoint is still "previous".
        assertEquals(BACKWARD, decide(HORIZONTAL_SPLIT, left, y = height / 2f - 1f))
        assertEquals(FORWARD, decide(HORIZONTAL_SPLIT, left, y = height / 2f))
    }
}
