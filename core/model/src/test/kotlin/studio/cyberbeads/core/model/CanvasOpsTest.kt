package studio.cyberbeads.core.model
import org.junit.Assert.*
import org.junit.Test
class CanvasOpsTest{
 @Test fun rectangularFitCentersWithoutChangingRatio(){val p=BeadPattern(2,4,listOf(-1),List(8){0});val out=CanvasOps.fit(p,8,8);assertEquals(8,out.width);assertEquals(8,out.height);assertEquals(32,out.beadCount);assertEquals(-1,out.cells[0]);assertEquals(0,out.cells[2]);assertEquals(-1,out.cells[7])}
 @Test fun selectionMovesOnlySelectedPixels(){val p=listOf(1,2,0,0,0,0);assertEquals(listOf(0,2,0,1,0,0),CanvasOps.move(p,3,PixelRegion(0,0,0,0),0,1))}
 @Test fun brushSizeCoversSquareAndClipsSafely(){val p=PixelOps.stroke(List(25){0},5,12,12,-1,3);assertEquals(9,p.count{it==-1});assertEquals(4,PixelOps.stroke(List(25){0},5,0,0,-1,3).count{it==-1})}
 @Test fun resizingPreservesCenterAndTransparency(){assertEquals(listOf(0,0,0,0,0,7,8,0,0,9,10,0,0,0,0,0),CanvasOps.resize(listOf(7,8,9,10),2,2,4,4))}
 @Test fun brandColorsAndCodesSurvivePatternCreation(){val bank=ColorBank("brand-test","Test",listOf(0xffff0000.toInt(),0xff0000ff.toInt()),listOf("R1","B2"),true);val p=CanvasOps.brand(BeadPattern(2,1,listOf(0xfffe0100.toInt()),listOf(0,-1)),bank);assertEquals(listOf(0xffff0000.toInt()),p.palette);assertEquals("R1",p.code(0));assertEquals(listOf(0,-1),p.cells);val project=PixelProject("test","test",2,1,p.palette,listOf(PixelFrame(PixelOps.fromPattern(p))),brandId=p.brandId,colorCodes=p.colorCodes);assertEquals(p,CanvasOps.pattern(project))}
}
