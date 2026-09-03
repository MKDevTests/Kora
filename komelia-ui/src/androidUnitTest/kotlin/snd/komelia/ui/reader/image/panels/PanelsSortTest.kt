package snd.komelia.ui.reader.image.panels

import androidx.compose.ui.unit.IntSize
import snd.komelia.image.ImageRect
import snd.komelia.settings.model.PagedReadingDirection.LEFT_TO_RIGHT
import snd.komelia.settings.model.PagedReadingDirection.RIGHT_TO_LEFT
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Reading order, on layouts taken from the measured pages rather than invented.
 *
 * The two named pages are the ones that decided the algorithm: 011 is where a
 * strict XY-cut turned three rows into two columns, and 151 is where the old
 * pairwise sort jumped to the bottom of the page and came back up.
 */
class PanelsSortTest {

    private fun rect(left: Int, top: Int, width: Int, height: Int) =
        ImageRect(left, top, left + width, top + height)

    private fun order(panels: List<ImageRect>, size: IntSize) =
        sortPanels(panels, size, RIGHT_TO_LEFT).map { it.left to it.top }

    @Test
    fun `three plain rows are read row by row, right to left`() {
        // Gintama page 011, real detector output. The middle-left panel hangs
        // 211 px into the bottom row, which is what blocks a strict row split.
        val banner = rect(0, -1, 1592, 1097)
        val middleRight = rect(591, 1146, 1004, 772)
        val middleLeft = rect(0, 1149, 568, 1022)
        val bottomRight = rect(590, 1960, 1004, 627)
        val bottomLeft = rect(0, 2145, 563, 443)

        val ordered = order(
            listOf(banner, middleRight, middleLeft, bottomRight, bottomLeft),
            IntSize(1700, 2587),
        )

        assertEquals(
            listOf(0 to -1, 591 to 1146, 0 to 1149, 590 to 1960, 0 to 2145),
            ordered,
        )
    }

    @Test
    fun `a tall right-hand panel does not drag the order down the page`() {
        // Gintama page 151. The old sort read the tall right panel second —
        // from the top of the page straight to the bottom, then back up.
        val topRight = rect(913, 205, 600, 780)
        val topLeft = rect(60, 200, 380, 500)
        val secondLeft = rect(457, 725, 380, 270)
        val secondFarLeft = rect(60, 728, 380, 270)
        val tallRight = rect(1151, 1023, 420, 1500)
        val third = rect(462, 1013, 600, 480)
        val bottomRight = rect(868, 1564, 260, 700)
        val bottomLeft = rect(64, 1570, 760, 700)

        val ordered = order(
            listOf(topRight, tallRight, topLeft, secondLeft, third, bottomRight, bottomLeft, secondFarLeft),
            IntSize(1700, 2400),
        )

        // The top row is finished before anything below it is read.
        assertEquals(913 to 205, ordered[0])
        assertEquals(60 to 200, ordered[1])
    }

    @Test
    fun `left to right mirrors the order`() {
        val left = rect(0, 0, 400, 400)
        val right = rect(500, 0, 400, 400)
        val size = IntSize(1000, 400)

        assertEquals(
            listOf(500 to 0, 0 to 0),
            sortPanels(listOf(left, right), size, RIGHT_TO_LEFT).map { it.left to it.top },
        )
        assertEquals(
            listOf(0 to 0, 500 to 0),
            sortPanels(listOf(left, right), size, LEFT_TO_RIGHT).map { it.left to it.top },
        )
    }

    @Test
    fun `a panel emitted twice is read once`() {
        val panel = rect(0, 0, 400, 400)
        val nearDuplicate = rect(4, 3, 398, 402)
        val other = rect(500, 0, 400, 400)

        val ordered = sortPanels(
            listOf(panel, nearDuplicate, other),
            IntSize(1000, 400),
            RIGHT_TO_LEFT,
        )

        assertEquals(2, ordered.size)
    }

    @Test
    fun `a single panel and an empty page are returned untouched`() {
        val only = listOf(rect(10, 10, 100, 100))
        assertEquals(only, sortPanels(only, IntSize(500, 500), RIGHT_TO_LEFT))
        assertEquals(emptyList(), sortPanels(emptyList(), IntSize(500, 500), RIGHT_TO_LEFT))
    }
}
