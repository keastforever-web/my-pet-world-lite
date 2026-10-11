package studio.cyberbeads.core.model

import org.junit.Test
import org.junit.Assert.*

class CreationRulesTest {
    @Test fun duplicateNamesAreTrimmedCaseInsensitiveAndBounded(){
        assertEquals("猫 (3)",Names.unique(" 猫 ",listOf("猫","猫 (2)")))
        assertEquals("Cat (2)",Names.unique("Cat",listOf("cat")))
        assertTrue(Names.unique("猫".repeat(40),listOf("猫".repeat(40))).length<=40)
    }
    @Test fun fullAndSelectiveOutlinesPreserveSubjectAndCanvas(){
        val p=BeadPattern(5,5,listOf(-1),List(25){if(it==12)0 else -1})
        val full=OutlineOps.apply(p,1,0xff000000.toInt(),false)
        val half=OutlineOps.apply(p,1,0xff000000.toInt(),true)
        assertEquals(9,full.beadCount);assertEquals(4,half.beadCount)
        assertEquals(-1,full.palette[full.cells[12]]);assertEquals(-1,half.cells[6])
        assertEquals(25,OutlineOps.apply(p,4,0xff000000.toInt(),false).beadCount)
        assertEquals(p,OutlineOps.apply(p,0,0,false))
    }
    @Test fun customPaletteMappingCarriesIdentityWithoutPretendingToBeBrand(){
        val bank=ColorBank("custom","test",listOf(-1,0xff000000.toInt()))
        val mapped=CanvasOps.brand(BeadPattern(1,1,listOf(0xffeeeeee.toInt()),listOf(0)),bank)
        assertEquals("custom",mapped.paletteId);assertNull(mapped.brandId);assertTrue(mapped.colorCodes.isEmpty());assertEquals(listOf(-1),mapped.palette)
    }
}
