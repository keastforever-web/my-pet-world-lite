package studio.cyberbeads.core.data

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.*
import studio.cyberbeads.core.model.*
import java.io.File
import java.util.UUID

interface FoodOwner{val foods:FoodStore}
class FoodStore(context:Context){
    private val file=AtomicFile(File(context.filesDir,"food-inventory-v1.json"));private val lock=Mutex()
    val items=MutableStateFlow(FoodRecipe.builtins());private var reservations=emptyMap<String,String>()
    private fun decode(j:JSONObject)=PixelFood(j.getString("id"),j.getString("name"),j.getInt("width"),j.getInt("height"),j.getJSONArray("pixels").let{a->(0 until a.length()).map{a.getInt(it)}},j.optInt("quantity",5),false)
    private fun encode(f:PixelFood)=JSONObject().put("id",f.id).put("name",f.name).put("width",f.width).put("height",f.height).put("pixels",JSONArray(f.pixels)).put("quantity",f.quantity).put("category","food")
    private fun read(){val custom=if(file.baseFile.exists()){val j=JSONObject(String(file.readFully(),Charsets.UTF_8));require(j.getInt("schema")==1);reservations=j.optJSONObject("travelReservations")?.let{o->o.keys().asSequence().associateWith{o.getString(it)}}?:emptyMap();val a=j.getJSONArray("foods");(0 until a.length()).map{decode(a.getJSONObject(it))}}else emptyList();items.value=FoodRecipe.builtins()+custom}
    private fun write(all:List<PixelFood>){require(all.count{!it.builtin}<=100){"仓库最多保存 100 种自制食物"};val a=JSONArray();all.filter{!it.builtin}.forEach{a.put(encode(it))};val out=file.startWrite();try{out.write(JSONObject().put("schema",1).put("foods",a).put("travelReservations",JSONObject(reservations)).toString().toByteArray());file.finishWrite(out);items.value=all}catch(e:Exception){file.failWrite(out);throw e}}
    suspend fun refresh()=withContext(Dispatchers.IO){lock.withLock{read()}}
    suspend fun saveDrawing(p:PixelProject):PixelFood=withContext(Dispatchers.IO){lock.withLock{
        require(p.frames.first().pixels.any{it ushr 24>0}){"食谱还是空白，先画一点美味吧"}
        read();val old=items.value.firstOrNull{it.id==p.id};val name=Names.unique(p.name,items.value.filter{it.id!=p.id}.map{it.name})
        val food=PixelFood(p.id,name,p.width,p.height,p.frames.first().pixels,old?.quantity?:5)
        write(if(old==null)items.value+food else items.value.map{if(it.id==food.id)food else it});food
    }}
    suspend fun delete(id:String)=withContext(Dispatchers.IO){lock.withLock{read();require(items.value.none{it.id==id&&it.builtin});write(items.value.filter{it.id!=id})}}
    suspend fun reserveTravel(id:String,run:String)=withContext(Dispatchers.IO){lock.withLock{read();if(run !in reservations){val f=items.value.first{it.id==id};require(f.quantity!=0){"这份食物已用完"};reservations=reservations+(run to id);write(items.value.map{if(it.id==id&&!it.builtin)it.copy(quantity=it.quantity-1)else it})}}}
    suspend fun pendingTravel()=withContext(Dispatchers.IO){lock.withLock{read();reservations.keys.toList()}}
    suspend fun settleTravel(run:String,committed:Boolean)=withContext(Dispatchers.IO){lock.withLock{read();val id=reservations[run];if(id!=null){reservations=reservations-run;write(items.value.map{if(!committed&&it.id==id&&!it.builtin)it.copy(quantity=(it.quantity+1).coerceAtMost(9999))else it})}}}
    suspend fun export(ids:Set<String>):ByteArray=withContext(Dispatchers.IO){lock.withLock{
        read();val selected=items.value.filter{it.id in ids};require(selected.isNotEmpty()&&selected.size<=32){"一次选择 1–32 种食物"}
        val a=JSONArray();selected.forEach{a.put(encode(it.copy(id=UUID.randomUUID().toString(),quantity=5,builtin=false)))}
        JSONObject().put("format","cyberbeads-food").put("schema",1).put("items",a).toString().toByteArray().also{require(it.size<=MAX_BYTES)}
    }}
    suspend fun import(bytes:ByteArray):Int=withContext(Dispatchers.IO){lock.withLock{
        require(bytes.size<=MAX_BYTES){"食物包不能超过 4 MB"};val j=JSONObject(String(bytes,Charsets.UTF_8));require(j.getString("format")=="cyberbeads-food"&&j.getInt("schema")==1){"不是支持的食物包"}
        val a=j.getJSONArray("items");require(a.length() in 1..32);val parsed=(0 until a.length()).map{val o=a.getJSONObject(it);require(o.getString("category")=="food");decode(o).copy(id="food-draft-"+UUID.randomUUID().toString(),quantity=5)}
        read();val all=items.value.toMutableList();parsed.forEach{f->all+=f.copy(name=Names.unique(f.name,all.map{it.name}))};write(all);parsed.size
    }}
    companion object{const val MAX_BYTES=4*1024*1024}
}
