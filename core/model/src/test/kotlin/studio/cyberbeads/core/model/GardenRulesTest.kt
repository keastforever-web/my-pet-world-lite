package studio.cyberbeads.core.model
import org.junit.Test
import org.junit.Assert.*

class GardenRulesTest{
 @Test fun everyPortalHasReachableApproach(){for(scene in WorldAtlas.ids){val m=FlatParkMap(scene);val spawn=WorldAtlas.region(scene).spawn;for(p in m.portals){assertTrue("$scene ${p.id} approach",m.walkable(p.approach));val path=m.path(spawn,p.approach);assertTrue("$scene ${p.id} reachable",path.isNotEmpty());assertTrue(path.all{m.walkable(it)})}}}
 @Test fun fenceWaterAndHouseRejectMovement(){val m=FlatParkMap("garden");for(p in listOf(WorldPoint(.01f,.5f),WorldPoint(.75f,.65f),WorldPoint(.35f,.36f))){assertFalse(m.walkable(p));assertTrue(m.path(WorldPoint(.5f,.6f),p).isEmpty())}}
 @Test fun furnitureKeepsPortalsOpen(){val m=FlatParkMap("garden");assertFalse(m.canPlace(WorldBlock(.31f,.34f,.38f,.37f)));assertTrue(m.canPlace(WorldBlock(.47f,.66f,.50f,.68f)));val with=FlatParkMap("garden",listOf(WorldBlock(.47f,.66f,.50f,.68f)));assertFalse(with.walkable(WorldPoint(.485f,.67f)));assertTrue(with.path(WorldAtlas.region("garden").spawn,m.portals.first{it.id=="fish"}.approach).isNotEmpty())}
 @Test fun rewardIsIdempotentAndRedemptionConsumesCurrency(){val s=GardenProgress();val r=GardenRules.reward(s,"round-1",3);assertEquals(s.leaves+3,r.leaves);assertEquals(r,GardenRules.reward(r,"round-1",3));val b=GardenRules.redeem(r,"river-box");assertEquals(r.leaves-4,b.leaves);assertEquals(1,b.inventory["river-box"])}
 @Test(expected=IllegalArgumentException::class) fun cannotOverspend(){GardenRules.redeem(GardenProgress(leaves=0),"town-box")}
 private fun trip()=PetJourney("journey-1","pet-1","小伙伴","berry-box","forest",1000,61000)
 @Test fun departureAndCollectionArePerPetAndExactlyOnce(){val s=GardenRules.depart(GardenProgress(),trip());assertEquals(0,s.inventory["berry-box"]);assertEquals(1,s.journeys.size);val c=GardenRules.collect(s,"journey-1",61000,listOf(-1),1,1);assertEquals(1,c.cards.size);assertEquals("forest",c.cards.first().region);assertTrue(c.journeys.first().claimed);assertEquals(c,GardenRules.collect(c,"journey-1",62000,listOf(-1),1,1));assertEquals(s.leaves+1,c.leaves)}
 @Test(expected=IllegalArgumentException::class) fun cannotCollectBeforeArrival(){GardenRules.collect(GardenRules.depart(GardenProgress(),trip()),"journey-1",60000,listOf(-1),1,1)}
 @Test(expected=IllegalArgumentException::class) fun samePetCannotDepartTwice(){val s=GardenRules.depart(GardenProgress(inventory=mapOf("berry-box" to 2)),trip());GardenRules.depart(s,trip().copy(id="journey-2"))}
 @Test fun differentFoodChangesCardTheme(){val t=trip().copy(foodId="town-box",region="town");val s=GardenRules.depart(GardenProgress(inventory=mapOf("town-box" to 1)),t);val c=GardenRules.collect(s,t.id,61000,listOf(-1),1,1);assertEquals("蜂蜜小镇",c.cards.first().location)}
}
