package studio.cyberbeads.core.model
import org.junit.Test
import org.junit.Assert.*
class Canvas128Test {
 @Test fun fullSizeProjectAndAnimationAreAccepted(){val px=IntPixels(IntArray(128*128){0xffb4cc99.toInt()});val p=PixelProject("test-128","128",128,128,listOf(0xffb4cc99.toInt()),listOf(PixelFrame(px)));assertEquals(16384,p.frames.first().pixels.size);val c=MotionClip("test-motion","128","walk",128,128,listOf(PixelFrame(px,180),PixelFrame(px,120)));assertEquals(300,c.durationMs);assertSame(c.frames[1],c.frameAt(180));assertSame(c.frames[0],c.frameAt(300))}
 @Test(expected=IllegalArgumentException::class) fun dimensionsBeyond128AreRejected(){PixelProject("oversize","129",129,128,listOf(-1),listOf(PixelFrame(List(129*128){-1})))}
 @Test fun intPixelsKeepsValueSemantics(){val a=IntPixels(intArrayOf(1,2,3));assertEquals(listOf(1,2,3),a);assertEquals(listOf(1,2,3).hashCode(),a.hashCode());assertEquals(2,a[1])}
}
