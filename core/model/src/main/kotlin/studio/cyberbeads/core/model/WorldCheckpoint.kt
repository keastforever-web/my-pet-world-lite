package studio.cyberbeads.core.model

data class WorldResident(val scene:String,val position:WorldPoint)
data class WorldCheckpoint(val scene:String="garden",val controlled:String="",val residents:Map<String,WorldResident> = emptyMap(),val updated:Long=0){
 fun repaired()=copy(scene=scene.takeIf{it in WorldAtlas.ids}?:"garden",residents=residents.mapValues{(_,r)->val id=r.scene.takeIf{it in WorldAtlas.ids}?:"garden";val p=if(r.position.x.isFinite()&&r.position.y.isFinite())r.position else WorldAtlas.region(id).spawn;WorldResident(id,FlatParkMap(id).safe(p))})
}
