package studio.cyberbeads.core.model
import org.junit.Assert.*
import org.junit.Test
class FoodRecipeTest{
 @Test fun scoreIsDeterministicAndIgnoresPaddingAndTransparentRgb(){
  val pixels=listOf(0xffffaa44.toInt(),0,0xff88dd66.toInt(),0xff88dd66.toInt());val padded=MutableList(36){0x00123456};for(y in 0..1)for(x in 0..1)padded[(y+2)*6+x+2]=pixels[y*2+x]
  assertEquals(FoodRecipe.normalized(pixels,2,2),FoodRecipe.normalized(padded,6,6))
 }
 @Test fun legacyCharacterFallbackRetainsDistinctInteractions(){MemePets.names.forEach{(id,name)->val art=Artwork(id,name,1,BuiltinPets.pattern(id)).complete();val clip=MotionDefaults.clip(art,"pet");assertEquals(48,clip.width);assertEquals(16,clip.frames.size);assertTrue(clip.frames.all{it.durationMs==80});assertTrue(clip.frames.map{it.pixels}.distinct().size>2);assertNotEquals(MotionDefaults.clip(art,"sleep").frames,clip.frames)}}
}