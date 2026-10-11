package studio.cyberbeads.core.model
import org.junit.Test
import org.junit.Assert.*

class WorldV13Test{
 @Test fun migrationKeepsFurnitureAndPlacementIdentity(){val f=ParkCatalog.builtFurniture();val old=listOf(PlacedFurniture("my-lamp",f[2].id,.35f,.36f),PlacedFurniture("my-bench",f[0].id,.5f,.66f));val next=WorldLayout.repair(old,f);assertEquals(old.map{it.id},next.map{it.id});assertEquals(old.map{it.itemId},next.map{it.itemId});assertNotEquals(old[0],next[0]);assertEquals(next,WorldLayout.repair(next,f))}
 @Test fun tileDimensionsAndIndependentProps(){assertEquals(6,WorldAtlas.regions.size);WorldAtlas.regions.values.forEach{r->assertEquals(120,r.tiles.size);assertTrue(r.tiles.all{it.size==80&&it.all{t->t in 0..31}});assertTrue(r.props.isNotEmpty());assertTrue(r.props.all{it.tile in 0..71&&it.width>0&&it.height>0})}}
 @Test fun everyInteractionAndReturnIsReachable(){WorldAtlas.regions.values.forEach{r->val m=FlatParkMap(r.id);assertTrue(m.walkable(r.spawn));r.portals.forEach{p->val path=m.path(r.spawn,p.approach);assertTrue("${r.id}/${p.id}",path.isNotEmpty());assertTrue(path.all{m.walkable(it)});if(p.destination.isNotBlank()){assertTrue(p.destination in WorldAtlas.ids);val arrival=WorldAtlas.arrival(r.id,p.destination);assertTrue(FlatParkMap(p.destination).walkable(arrival));assertTrue(WorldAtlas.region(p.destination).portals.any{it.id==p.spawnId&&it.destination==r.id})}}}}
 @Test fun allRegionsCanReturnHome(){val seen=mutableSetOf("garden");repeat(5){seen.toList().forEach{r->WorldAtlas.region(r).portals.filter{it.destination.isNotBlank()}.forEach{seen.add(it.destination)}}};assertEquals(WorldAtlas.ids.toSet(),seen)}
 @Test fun waterCannotBeWalkedButPierCan(){val m=FlatParkMap("garden");assertFalse(m.walkable(WorldPoint(.75f,.65f)));assertTrue(m.walkable(WorldPoint(.58f,.61f)));assertTrue(m.path(WorldAtlas.region("garden").spawn,WorldPoint(.58f,.61f)).isNotEmpty())}
 @Test fun corruptLocationsRepairWithoutDuplicatingPets(){val s=WorldCheckpoint("removed",residents=mapOf("cat" to WorldResident("missing",WorldPoint(Float.NaN,1f)),"dog" to WorldResident("lake",WorldPoint(.75f,.65f)))).repaired();assertEquals("garden",s.scene);assertEquals(setOf("cat","dog"),s.residents.keys);s.residents.values.forEach{assertTrue(FlatParkMap(it.scene).walkable(it.position))}}
 @Test fun furnitureMustKeepEveryExitConnected(){val map=FlatParkMap("garden");assertFalse(map.canPlace(WorldBlock(.05f,.40f,.95f,.50f)));assertFalse(map.canPlace(WorldBlock(.31f,.34f,.38f,.37f)));assertTrue(map.canPlace(WorldBlock(.47f,.66f,.50f,.68f)))}
}
