package studio.cyberbeads.core.data

import android.content.Context
import android.util.AtomicFile
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.*
import studio.cyberbeads.core.model.*

/** Independent from garden-world-v10: updating map locations never rewrites inventory or letters. */
class WorldSaveStore(context:Context){
 private val file=AtomicFile(File(context.filesDir,"world-save-v13.json"))
 private var loaded=false
 private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
 companion object{private val mutex=Mutex()}
 suspend fun load():WorldCheckpoint=withContext(Dispatchers.IO){mutex.withLock{
  val value=if(!file.baseFile.exists())WorldCheckpoint()else{
   val j=JSONObject(String(file.readFully(),Charsets.UTF_8));require(j.getInt("schema")==1&&j.optInt("map",13)<=WorldAtlas.version){"探索存档版本暂不支持，原件已保留"}
   if(j.optInt("map",13)<14){val backup=File(file.baseFile.parentFile,"world-save-before-v14.json");if(!backup.exists())backup.writeBytes(file.readFully())}
   val rs=j.getJSONObject("residents");WorldCheckpoint(j.optString("scene","garden"),j.optString("controlled"),rs.keys().asSequence().associateWith{id->val r=rs.getJSONObject(id);WorldResident(if(j.optInt("map",13)<14)"garden"else r.getString("scene"),if(j.optInt("map",13)<14)FlatParkMap("garden").safe(WorldPoint(.35f+Math.floorMod(id.hashCode(),240)/1000f,.47f+Math.floorMod(id.hashCode()/97,170)/1000f)) else WorldPoint(r.getDouble("x").toFloat(),r.getDouble("y").toFloat()))},j.optLong("updated"))
  };loaded=true;value.repaired()
 }}
 fun save(value:WorldCheckpoint){if(!loaded)return;scope.launch{mutex.withLock{
  // A late dispose from an older screen cannot replace a newer checkpoint.
  if(file.baseFile.exists()){val old=runCatching{JSONObject(String(file.readFully(),Charsets.UTF_8))}.getOrNull()?:return@withLock;if(old.optInt("schema")!=1||old.optInt("map",13)>WorldAtlas.version||old.optLong("updated")>value.updated)return@withLock}
  val j=JSONObject().put("schema",1).put("map",WorldAtlas.version).put("scene",value.scene).put("controlled",value.controlled).put("updated",value.updated).put("residents",JSONObject().apply{value.residents.forEach{(id,r)->put(id,JSONObject().put("scene",r.scene).put("x",r.position.x).put("y",r.position.y))}})
  val out=file.startWrite();try{out.write(j.toString().toByteArray(Charsets.UTF_8));file.finishWrite(out)}catch(e:Exception){file.failWrite(out)}
 }}}
}
