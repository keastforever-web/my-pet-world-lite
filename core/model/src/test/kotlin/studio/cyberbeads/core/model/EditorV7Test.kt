package studio.cyberbeads.core.model

import org.junit.Assert.*
import org.junit.Test

class EditorV7Test {
    @Test fun rectangleCopyRotatePasteClipsWithoutTouchingOutside(){
        val pixels=(1..20).toList();val region=PixelRegion(1,1,3,2)
        val copied=SelectionOps.copy(pixels,5,region)
        assertEquals(listOf(7,8,9,12,13,14),copied.pixels)
        val rotated=SelectionOps.transform(copied,"rotate")
        assertEquals(2,rotated.width);assertEquals(listOf(12,7,13,8,14,9),rotated.pixels)
        val pasted=SelectionOps.paste(pixels,5,rotated,4,2)
        assertEquals(12,pasted[14]);assertEquals(13,pasted[19]);assertEquals(14,pasted[13]);assertEquals(pixels.take(10),pasted.take(10))
    }
    @Test fun symmetricEvenBrushAndEraserLeaveExistingPixelsAlone(){
        val original=MutableList(64){0}.also{it[27]=7}
        val drawn=SelectionOps.stroke(original,8,0,9,9,2,"four")
        for(i in drawn.indices)if(drawn[i]==9){assertEquals(9,drawn[i/8*8+7-i%8]);assertEquals(9,drawn[(7-i/8)*8+i%8])}
        assertEquals(7,drawn[27])
        val erased=SelectionOps.stroke(drawn,8,0,0,0,1,"four")
        listOf(0,7,56,63).forEach{assertEquals(0,erased[it])}
    }
    @Test fun sequentialNamesAvoidExistingAndSubjectHasMargin(){
        assertEquals("我的宠物-3",SelectionOps.nextName(listOf("我的宠物-1","我的宠物-2")))
        val fit=CanvasOps.fit(BeadPattern(8,4,listOf(-1),List(32){0}),32,32,.75)
        val occupied=fit.cells.indices.filter{fit.cells[it]>=0}
        assertEquals(4,occupied.minOf{it%32});assertEquals(27,occupied.maxOf{it%32})
        assertEquals(10,occupied.minOf{it/32});assertEquals(21,occupied.maxOf{it/32})
    }
}
