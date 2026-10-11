package studio.cyberbeads.core.model

import org.junit.Assert.*
import org.junit.Test

class BeadsTest {
    @Test fun transparentBackgroundIsNotCountedAndSubjectKeepsAspect() {
        val pixels = IntArray(40 * 40)
        for (y in 12..27) for (x in 16..23) pixels[y * 40 + x] = 0xffff4400.toInt()
        val pattern = BeadQuantizer.convert(pixels, 40, 40, 32, 8)
        assertEquals(16, pattern.width); assertEquals(32, pattern.height)
        assertEquals(512, pattern.beadCount); assertEquals(1, pattern.palette.size)
    }
    @Test fun paletteRespectsBudget() {
        val pixels = IntArray(1024) { 0xff000000.toInt() or ((it * 12931) and 0xffffff) }
        val result = BeadQuantizer.convert(pixels, 32, 32, 32, 6)
        assertTrue(result.palette.size <= 6); assertEquals(1024, result.beadCount)
        assertEquals(1024, result.colorCounts.values.sum())
    }
    @Test(expected = IllegalArgumentException::class) fun emptySubjectIsExplicitFailure() {
        BeadQuantizer.convert(IntArray(16), 4, 4, 16, 4)
    }
    @Test fun transparencyAndPaintStayValid() {
        val pattern = BeadPattern(2, 2, listOf(0xffeeff00.toInt()), listOf(0, -1, 0, -1))
        assertEquals(2, pattern.beadCount)
        assertEquals(1, pattern.paint(0, -1).beadCount)
    }
    @Test fun onlyActualBeadsCompleteAnArtwork() {
        val pattern = BeadPattern(2, 2, listOf(-1), listOf(0, -1, 0, -1))
        val item = Artwork("id", "pet", 0, pattern)
        assertFalse(item.place(1).isComplete)
        assertFalse(item.place(0).isComplete)
        assertTrue(item.fillColor(0).isComplete)
        assertTrue(item.complete().isComplete)
        assertFalse(item.complete().place(0).isComplete)
    }
    @Test fun petStateBoundsAndClockChanges() {
        val state = PetState(99, false, 100000)
        assertEquals(100, state.pet(100001).affection)
        assertEquals(100000L, state.tick(0).lastUpdated)
        assertTrue(state.rest(100001).sleeping)
    }
}
