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

interface GardenOwner{val garden:GardenStore}
/** Rewards, inventory, departures and postcards commit in one versioned atomic document. */
class GardenStore(context:Context){
    private val app=context.applicationContext
    suspend fun addBuiltinFurniture()=change{s->
        if("v16-furniture" in s.rewards)s else {
            val a=JSONArray(app.assets.open("furniture-v16.json").bufferedReader().use{it.readText()})
            val added=a.objects().map(::decodeFurniture).filter{f->s.furniture.none{it.id==f.id}}
            s.copy(furniture=s.furniture+added,rewards=s.rewards+"v16-furniture")
        }
    }
    private val file=AtomicFile(File(context.filesDir,"garden-world-v10.json"));private val mutex=Mutex()
    val state=MutableStateFlow(GardenProgress())
    private fun JSONArray.objects()=(0 until length()).map{getJSONObject(it)}
    private fun ints(a:JSONArray)=(0 until a.length()).map{a.getInt(it)}
    private fun encodeFurniture(f:GardenFurniture)=JSONObject().put("id",f.id).put("name",f.name).put("w",f.width).put("h",f.height).put("pixels",JSONArray(f.pixels)).put("span",f.span)
    private fun decodeFurniture(j:JSONObject)=GardenFurniture(j.getString("id"),j.getString("name"),j.getInt("w"),j.getInt("h"),ints(j.getJSONArray("pixels")),j.getInt("span"))
    private fun read(){if(!file.baseFile.exists()){state.value=GardenProgress();return};val j=JSONObject(String(file.readFully(),Charsets.UTF_8));require(j.getInt("schema")==1){"当前乐园数据版本不支持，原文件已保留"}
        val inv=j.getJSONObject("inventory");state.value=GardenProgress(j.getInt("leaves"),inv.keys().asSequence().associateWith{inv.getInt(it)},j.getJSONArray("furniture").objects().map(::decodeFurniture),j.getJSONArray("placed").objects().map{PlacedFurniture(it.getString("id"),it.getString("item"),it.getDouble("x").toFloat(),it.getDouble("y").toFloat())},j.getJSONArray("journeys").objects().map{PetJourney(it.getString("id"),it.getString("pet"),it.getString("name"),it.getString("food"),it.getString("region"),it.getLong("departure"),it.getLong("arrival"),it.getBoolean("claimed"),it.optString("lucky"),it.optString("place"))},j.getJSONArray("cards").objects().map{TravelPostcard(it.getString("id"),it.getString("pet"),it.getString("name"),it.getString("region"),it.getString("location"),it.getString("caption"),it.getLong("time"),it.getInt("w"),it.getInt("h"),ints(it.getJSONArray("pixels")),it.optString("place"),it.optJSONArray("notes")?.objects()?.map{n->NoteJson.decode(n)}?:emptyList(),it.optString("status","owned"),it.optBoolean("remote"),it.optInt("hop"))},j.getJSONArray("rewards").let{a->(0 until a.length()).map{a.getString(it)}.toSet()})
    }
    private fun write(s:GardenProgress){
        val j=JSONObject().put("schema",1).put("leaves",s.leaves).put("inventory",JSONObject(s.inventory)).put("furniture",JSONArray().apply{s.furniture.forEach{put(encodeFurniture(it))}})
        j.put("placed",JSONArray().apply{s.placed.forEach{put(JSONObject().put("id",it.id).put("item",it.itemId).put("x",it.x).put("y",it.y))}})
        j.put("journeys",JSONArray().apply{s.journeys.forEach{put(JSONObject().put("id",it.id).put("pet",it.petId).put("name",it.petName).put("food",it.foodId).put("region",it.region).put("departure",it.departure).put("arrival",it.arrival).put("claimed",it.claimed).put("lucky",it.luckyId).put("place",it.placeId))}})
        j.put("cards",JSONArray().apply{s.cards.forEach{put(JSONObject().put("id",it.id).put("pet",it.petId).put("name",it.petName).put("region",it.region).put("location",it.location).put("caption",it.caption).put("time",it.time).put("w",it.width).put("h",it.height).put("pixels",JSONArray(it.pixels)).put("place",it.placeId).put("notes",JSONArray().apply{it.notes.forEach{n->put(NoteJson.encode(n))}}).put("status",it.status).put("remote",it.remote).put("hop",it.hop))}}).put("rewards",JSONArray(s.rewards.toList()))
        val out=file.startWrite();try{out.write(j.toString().toByteArray(Charsets.UTF_8));file.finishWrite(out);state.value=s}catch(e:Exception){file.failWrite(out);throw e}
    }
    suspend fun refresh()=withContext(Dispatchers.IO){mutex.withLock{read()}}
    suspend fun migrateWorldLayout()=change{s->if("v14-layout" in s.rewards)s else s.copy(placed=WorldLayout.repair(s.placed,s.furniture),rewards=s.rewards+"v14-layout")}
    private suspend fun change(f:(GardenProgress)->GardenProgress)=withContext(Dispatchers.IO){mutex.withLock{read();val next=f(state.value);if(next!=state.value)write(next)}}
    suspend fun reward(run:String,n:Int)=change{GardenRules.reward(it,run,n)}
    suspend fun redeem(id:String)=change{GardenRules.redeem(it,id)}
    suspend fun depart(art:Artwork,foodId:String,minutes:Int,luckyId:String=""):PetJourney{
        require(art.isComplete);require(minutes in listOf(1,15,60));val now=System.currentTimeMillis();val f=ParkCatalog.foods.first{it.id==foodId};val j=PetJourney(UUID.randomUUID().toString(),art.id,art.name,foodId,f.region,now,now+minutes*60000L,luckyId=luckyId,placeId=TravelLife.choose(f.region,now,luckyId,state.value.cards.map{it.placeId}.toSet()).id)
        change{GardenRules.depart(it,j)};return j
    }
    suspend fun collect(id:String,arts:List<Artwork>){change{s->val j=s.journeys.first{it.id==id};val a=arts.firstOrNull{it.id==j.petId};val p=a?.pattern?:BuiltinPets.pattern("builtin-cat");GardenRules.collect(s,id,System.currentTimeMillis(),PixelOps.fromPattern(p),p.width,p.height)}}
    suspend fun place(itemId:String,p:WorldPoint){change{s->require(s.placed.size<40){"小园最多放置 40 件家饰"};val f=s.furniture.first{it.id==itemId};val next=PlacedFurniture(UUID.randomUUID().toString(),itemId,p.x,p.y);val map=FlatParkMap("garden",s.placed.mapNotNull{v->s.furniture.firstOrNull{it.id==v.itemId}?.block(v)});require(map.canPlace(f.block(next))){"这里挡住了建筑、栏杆或通道，请选一块空草地"};s.copy(placed=s.placed+next)}}
    suspend fun removePlacement(id:String)=change{it.copy(placed=it.placed.filter{p->p.id!=id})}
    suspend fun saveDrawing(p:PixelProject):GardenFurniture{
        require(p.frames.first().pixels.any{it ushr 24>0}){"家饰还是空白，先画一点吧"};var result:GardenFurniture?=null
        change{s->require(s.furniture.size<100||s.furniture.any{it.id==p.id}){"最多保存 100 种家饰"};val f=GardenFurniture(p.id,Names.unique(p.name,s.furniture.filter{it.id!=p.id}.map{it.name}),p.width,p.height,p.frames.first().pixels);result=f;s.copy(furniture=s.furniture.filter{it.id!=f.id}+f)};return result!!
    }
    suspend fun deleteFurniture(id:String)=change{s->require(id !in ParkCatalog.builtFurniture().map{it.id}){"内置家饰不能删除"};s.copy(furniture=s.furniture.filter{it.id!=id},placed=s.placed.filter{it.itemId!=id})}
    suspend fun deleteCard(id:String)=change{s->require(s.cards.first{it.id==id}.status!="sending"){"寄送状态待确认，请先同步"};s.copy(cards=s.cards.filter{c->c.id!=id})}
    suspend fun appendCard(id:String,n:CardNote)=change{s->s.copy(cards=s.cards.map{if(it.id==id)TravelLife.append(it,n)else it})}
    suspend fun cardStatus(id:String,status:String)=change{s->s.copy(cards=s.cards.map{if(it.id==id)it.copy(status=status)else it})}
    suspend fun importCard(c:TravelPostcard)=change{s->require(c.width in 1..128&&c.height in 1..128&&c.pixels.size==c.width*c.height&&c.notes.size<=40&&c.notes.sumOf{it.ink.sumOf{p->p.size}}<=4000&&c.notes.all{it.text.length<=240}){"明信片格式不支持"};require(s.cards.size<500||s.cards.any{it.id==c.id}){"相册已满，请整理后收信"};if(s.cards.any{it.id==c.id&&it.status=="owned"&&it.notes==c.notes&&it.hop==c.hop})s else s.copy(cards=s.cards.filter{it.id!=c.id}+c.copy(status="owned",remote=true))}
    suspend fun departCustom(art:Artwork,food:PixelFood,minutes:Int,luckyId:String,foods:FoodStore):PetJourney {
        require(art.isComplete&&minutes in listOf(1,15,60));val now=System.currentTimeMillis();val region=TravelLife.foodRegion(food);val j=PetJourney(UUID.randomUUID().toString(),art.id,art.name,food.id,region,now,now+minutes*60000L,luckyId=luckyId,placeId=TravelLife.choose(region,now,luckyId,state.value.cards.map{it.placeId}.toSet()).id)
        foods.reserveTravel(food.id,j.id)
        try {change{s->GardenRules.depart(s.copy(inventory=s.inventory+(food.id to 1)),j)};foods.settleTravel(j.id,true)}
        catch(e:Exception){withContext(NonCancellable){foods.settleTravel(j.id,state.value.journeys.any{it.id==j.id})};throw e};return j
    }
    suspend fun reconcileFood(foods:FoodStore){foods.pendingTravel().forEach{id->foods.settleTravel(id,state.value.journeys.any{it.id==id})}}
    suspend fun exportFurniture(ids:Set<String>):ByteArray=withContext(Dispatchers.IO){mutex.withLock{read();val selected=state.value.furniture.filter{it.id in ids};require(selected.size in 1..32);JSONObject().put("format","cyberbeads-furniture").put("schema",1).put("items",JSONArray().apply{selected.forEach{put(encodeFurniture(it))}}).toString().toByteArray().also{require(it.size<=4*1024*1024)}}}
    suspend fun importFurniture(bytes:ByteArray)=withContext(Dispatchers.IO){require(bytes.size<=4*1024*1024);val j=JSONObject(String(bytes,Charsets.UTF_8));require(j.getString("format")=="cyberbeads-furniture"&&j.getInt("schema")==1){"不是支持的家饰包"};val a=j.getJSONArray("items");require(a.length() in 1..32);val parsed=a.objects().map{decodeFurniture(it).copy(id="furniture-"+UUID.randomUUID().toString())}
        change{s->require(s.furniture.size+parsed.size<=100);val all=s.furniture.toMutableList();parsed.forEach{all+=it.copy(name=Names.unique(it.name,all.map{f->f.name}))};s.copy(furniture=all)}
    }
}
