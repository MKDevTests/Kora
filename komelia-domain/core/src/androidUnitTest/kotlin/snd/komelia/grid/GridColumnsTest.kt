package snd.komelia.grid

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GridColumnsTest {

    @Test
    fun `adaptive count matches the tablet measured in portrait`() {
        // 753 dp wide at density 2.125, 10 dp of padding each side, 7 dp between
        // cards, card width set to 150 dp. The device lays out 4 columns.
        val available = ((753 - 20) * 2.125f).toInt()
        val minSize = (150 * 2.125f).toInt()
        val spacing = (7 * 2.125f).toInt()
        assertEquals(4, adaptiveColumnCount(available, minSize, spacing))
    }

    @Test
    fun `adaptive count never returns zero on a degenerate width`() {
        assertEquals(1, adaptiveColumnCount(0, 300, 16))
        assertEquals(1, adaptiveColumnCount(-10, 300, 16))
        assertEquals(1, adaptiveColumnCount(100, 300, 16))
    }

    @Test
    fun `a count that already divides the page is left alone`() {
        assertEquals(4, completeRowColumnCount(adaptive = 4, pageSize = 60))
        assertEquals(5, completeRowColumnCount(adaptive = 5, pageSize = 60))
        assertEquals(6, completeRowColumnCount(adaptive = 6, pageSize = 60))
    }

    @Test
    fun `seven columns drop to six so the page fills whole rows`() {
        // The tablet in landscape: 1205 dp gives 7 columns, and 60 items over 7
        // leaves 4 holes. Six columns divide it exactly.
        assertEquals(6, completeRowColumnCount(adaptive = 7, pageSize = 60))
    }

    @Test
    fun `columns are never added, only removed`() {
        // 50 over 4 columns is the bug that started this; the only divisor next
        // to 4 is 5, which would make every card narrower than the width the
        // user chose. We keep 4 and live with the ragged row.
        assertEquals(4, completeRowColumnCount(adaptive = 4, pageSize = 50))
    }

    @Test
    fun `a divisor more than one column away is not worth the size change`() {
        // 8 columns, 60 items: 6 would divide, but two columns of extra card
        // width is a bigger visual change than the hole it removes.
        assertEquals(8, completeRowColumnCount(adaptive = 8, pageSize = 60))
    }

    @Test
    fun `no page size means no adjustment`() {
        // Screens that show everything on one page pass no size.
        assertEquals(7, completeRowColumnCount(adaptive = 7, pageSize = 0))
    }

    @Test
    fun `a single column is never touched`() {
        assertEquals(1, completeRowColumnCount(adaptive = 1, pageSize = 60))
    }

    @Test
    fun `every offered page size divides the usual column counts`() {
        for (size in pageLoadSizes) {
            for (columns in 2..6) {
                assertTrue(
                    size % columns == 0,
                    "page size $size leaves ${size % columns} holes on $columns columns",
                )
            }
        }
    }

    @Test
    fun `stored sizes from the previous list snap to an offered one`() {
        assertEquals(60, snapPageLoadSize(50))
        assertEquals(60, snapPageLoadSize(20))
        assertEquals(120, snapPageLoadSize(100))
        assertEquals(240, snapPageLoadSize(200))
        assertEquals(480, snapPageLoadSize(500))
    }

    @Test
    fun `snapping an offered size returns it unchanged`() {
        for (size in pageLoadSizes) assertEquals(size, snapPageLoadSize(size))
    }
}
