package studio.cyberbeads.core.data

import android.content.Context
import android.graphics.BitmapFactory
import kotlinx.coroutines.*
import org.json.*
import studio.cyberbeads.core.model.*
import java.io.DataInputStream
import java.util.zip.GZIPInputStream

/** Built-ins stay compressed in assets. No startup copies of multi-megabyte animation JSON. */
object SpriteCatalog {
 private var index:JSONArray?=null
 private val cache=object:LinkedHashMap<String,List<MotionClip>>(8,.75f,true){override fun removeEldestEntry(e:MutableMap.MutableEntry<String,List<MotionClip>>)=size>5}
 private val portraits=object:LinkedHashMap<String,MotionClip>(16,.75f,true){override fun removeEldestEntry(e:MutableMap.MutableEntry<String,MotionClip>)=size>12}
 @Synchronized private fun index(context:Context)=index?:JSONArray(context.assets.open("sprites-v16/index.json").bufferedReader().use{it.readText()}).also{index=it}
 private fun readClip(context:Context,pet:JSONObject,j:JSONObject):MotionClip{
  val w=pet.getInt("width");val h=pet.getInt("height");val ms=j.getJSONArray("ms")
  val frames=DataInputStream(GZIPInputStream(context.assets.open("sprites-v16/"+j.getString("file"))).buffered(65536)).use{input->List(ms.length()){f->PixelFrame(IntPixels(IntArray(w*h){input.readInt()}),ms.getInt(f))}}
  return MotionClip(j.getString("id"),j.getString("name"),j.getString("action"),w,h,frames,random=j.optBoolean("random"),actionName=j.getString("label"))
 }
 @Synchronized fun portrait(context:Context,id:String):MotionClip?{
  portraits[id]?.let{return it};cache[id]?.firstOrNull{it.action=="idle"}?.let{return it}
  val a=index(context);val pet=(0 until a.length()).map{a.getJSONObject(it)}.firstOrNull{it.getString("id")==id}?:return null
  val clips=pet.getJSONArray("clips");val idle=(0 until clips.length()).map{clips.getJSONObject(it)}.firstOrNull{it.getString("action")=="idle"}?:return null
  return readClip(context,pet,idle).also{portraits[id]=it}
 }
 @Synchronized fun motions(context:Context,id:String):List<MotionClip>{
  cache[id]?.let{return it}
  val a=index(context);val pet=(0 until a.length()).map{a.getJSONObject(it)}.firstOrNull{it.getString("id")==id}?:return emptyList()
  val c=pet.getJSONArray("clips");val result=(0 until c.length()).map{n->val j=c.getJSONObject(n);portraits[id]?.takeIf{it.id==j.getString("id")}?:readClip(context,pet,j)};cache[id]=result;return result
 }
 suspend fun seed(context:Context,store:ArtworkStore)=withContext(Dispatchers.IO){
  val a=index(context);for(n in 0 until a.length()){val j=a.getJSONObject(n);val id=j.getString("id");if(store.get(id)==null&&!store.wasDeleted(id)){
   val image=context.assets.open("sprites-v16/"+j.getString("preview")).use{BitmapFactory.decodeStream(it)};val pixels=IntArray(image.width*image.height);image.getPixels(pixels,0,image.width,0,0,image.width,image.height);image.recycle()
   val colors=pixels.filter{it ushr 24>0}.distinct();val ids=colors.withIndex().associate{it.value to it.index}
   store.save(Artwork(id,j.getString("name"),System.currentTimeMillis(),BeadPattern(j.getInt("width"),j.getInt("height"),colors,IntPixels(IntArray(pixels.size){if(pixels[it] ushr 24==0)-1 else ids.getValue(pixels[it])}))).complete())
  }}
 }
}
