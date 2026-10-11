package studio.cyberbeads.core.model
import org.junit.Test
import org.junit.Assert.*
class WorldV12Test{
 @Test fun rotationRetainsWorldAndPetScale(){assertEquals(ParkMetrics.scale(1080,2400),ParkMetrics.scale(2400,1080),.0001f);assertEquals(26,ParkMetrics.petWidth)}
 @Test fun oldMapsAndGamesAreRetired(){assertTrue(WorldAtlas.ids.containsAll(listOf("garden","house")));assertFalse(WorldAtlas.regions.containsKey("friends"));assertFalse(WorldAtlas.regions.containsKey("trail"));assertTrue(FlatParkMap("garden").portals.any{it.id=="fish"});assertTrue(WorldAtlas.regions.values.none{r->r.portals.any{it.id.startsWith("chime-")}})}
 @Test fun oneParkHasOneGeography(){assertEquals(WorldAtlas.region("garden"),FlatParkMap("garden").region);assertEquals("house",FlatParkMap("garden").portals.first{it.id=="house"}.destination)}
 @Test fun feedingActionAndEnergyAreAbsent(){assertFalse("feed" in MotionDefaults.actions);assertTrue(PetState::class.java.declaredFields.none{it.name=="energy"});assertEquals(100,PetState(affection=100).pet(50).affection)}
 @Test fun inkAppendsCannotMutateEarlierLetters(){val old=CardNote("甲","保留",1,listOf(listOf(InkPoint(0,0),InkPoint(1000,1000))));val c=TravelPostcard("1","p","猫","river","西湖","",1,1,1,listOf(-1),notes=listOf(old));val n=CardNote("乙","",2,listOf(listOf(InkPoint(30,40),InkPoint(50,60))));assertEquals(old,TravelLife.append(c,n).notes.first());assertTrue(runCatching{InkPoint(1001,0)}.isFailure)}
 @Test fun fishingRequiresARealHitAndReelsBeforeReward(){val g=FishingRound();repeat(300){g.advance(.033f)};assertNull(g.collect());var hits=0;repeat(6000){if(g.phase==0)g.cast();g.advance(.033f);g.collect()?.let{hits++}};assertTrue("Deterministic swings must land actual fish",hits>0);assertNull(g.collect())}
}
