package studio.cyberbeads.core.model
import org.junit.Test
import org.junit.Assert.*
class TravelLifeTest{
 @Test fun cloverPrefersUncollectedAndAllPlacesHaveTiles(){val forest=TravelLife.places.filter{it.region=="forest"};val p=TravelLife.choose("forest",3,"lucky-clover",forest.dropLast(1).map{it.id}.toSet());assertEquals(forest.last().id,p.id);assertEquals((0..11).toList(),TravelLife.places.map{it.tile})}
 @Test fun luckyTicketConsumesItemAndReturnsTwoDistinctCards(){val s=GardenProgress(inventory=mapOf("berry-box" to 1,"lucky-ticket" to 1));val j=PetJourney("trip","pet","猫","berry-box","forest",1,60001,luckyId="lucky-ticket",placeId="huangshan");val d=GardenRules.depart(s,j);assertEquals(0,d.inventory["lucky-ticket"]);val r=GardenRules.collect(d,j.id,60001,listOf(-1),1,1);assertEquals(2,r.cards.size);assertEquals(2,r.cards.map{it.id}.distinct().size);assertEquals("黄山",r.cards[0].location);assertEquals(r,GardenRules.collect(r,j.id,70000,listOf(-1),1,1))}
 @Test fun appendKeepsHistoryAndSentCardCannotBeWritten(){val c=TravelPostcard("c","p","猫","town","西安","风景",1,1,1,listOf(-1));val a=TravelLife.append(c,CardNote("我","第一句",1));val b=TravelLife.append(a,CardNote("你","第二句",2));assertEquals(a.notes,b.notes.take(1));assertTrue(runCatching{TravelLife.append(b.copy(status="sent"),CardNote("他","第三句",3))}.isFailure)}
 @Test fun noHungerPenaltyAndCustomFoodHasRoute(){assertEquals(35,PetState(affection=35,lastUpdated=1).tick(86400001).affection);assertEquals("forest",TravelLife.foodRegion(PixelFood("pink","粉色",1,1,listOf(0xffff55aa.toInt()))));assertEquals("river",TravelLife.foodRegion(PixelFood("blue","蓝色",1,1,listOf(0xff3388dd.toInt()))))}
}
