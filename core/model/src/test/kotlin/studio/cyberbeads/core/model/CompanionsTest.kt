package studio.cyberbeads.core.model
import org.junit.Assert.*
import org.junit.Test
class CompanionsTest {
    @Test fun eachBuiltinHasOpaqueColorsTransparentBorderAndActualFrameChanges(){
        BuiltinPets.names.keys.forEach{id->val base=BuiltinPets.pattern(id);assertTrue(base.beadCount>30);assertTrue(base.palette.all{it ushr 24==255});assertTrue(base.cells.any{it==-1})
            val frames=(0..11).map{BuiltinPets.pattern(id,it,"play").cells}.toSet();assertTrue("$id needs body animation",frames.size>1)
            assertNotEquals(base.cells,BuiltinPets.pattern(id,0,"sleep").cells)
        }
    }
    @Test fun builtinsCanBeAdoptedWithoutChangingCustomArtwork(){BuiltinPets.names.forEach{(id,name)->val art=Artwork(id,name,1,BuiltinPets.pattern(id)).complete();assertTrue(art.isComplete);assertEquals(art.pattern.beadCount,art.completedCount)}}
}
