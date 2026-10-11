package studio.cyberbeads.core.model

import org.junit.Assert.*
import org.junit.Test

class WorldV15Test {
 @Test fun sweptJoystickCannotCrossThinFenceAndSlidesAlongIt(){
  val map=FlatParkMap("garden",listOf(WorldBlock(.49f,.64f,.495f,.70f)))
  val start=WorldPoint(.47f,.66f);assertTrue(map.walkable(start))
  val hit=map.step(start,64f,0f);assertTrue(hit.x<.49f);assertTrue(map.walkable(hit))
  val slide=map.step(hit,16f,15f);assertTrue(slide.x<.49f);assertTrue(slide.y>hit.y);assertTrue(map.walkable(slide))
  assertEquals(start,map.step(start,Float.NaN,0f))
 }
 @Test fun everyBiomeHasUniqueTerrainAndMultiplePairedRoutes(){
  assertTrue(WorldAtlas.region("forest").tiles.flatten().any{it==28})
  assertTrue(WorldAtlas.region("autumn").tiles.flatten().any{it==16})
  assertTrue(WorldAtlas.region("snow").tiles.flatten().any{it==20})
  assertTrue(WorldAtlas.region("coast").tiles.flatten().any{it==24})
  WorldAtlas.regions.values.filter{it.id!="house"}.forEach{r->assertTrue(r.portals.count{it.destination.isNotBlank()}>=3)}
 }
 @Test fun interactionRequiresProximityAndRoomRemainsCompact(){
  val map=FlatParkMap("forest");val portal=map.portals.first()
  assertEquals(portal,map.nearby(portal.approach));assertNull(map.nearby(WorldPoint(.5f,.5f)))
  val house=WorldAtlas.region("house");assertFalse(house.props.any{it.tile==32});assertTrue(house.props.filter{it.tile!=55}.all{it.width<=84})
 }
 @Test fun horizontalAndVerticalUseSameOnePointFiveScale(){
  assertEquals(1.5f,ParkMetrics.scale(560,1000),.0001f);assertEquals(ParkMetrics.scale(560,1000),ParkMetrics.scale(1000,560),.0001f)
 }
}
