package studio.cyberbeads.core.data

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.*
import studio.cyberbeads.core.model.*
import java.io.File
import java.util.UUID

interface CreativeOwner {val creative:CreativeStore}
class CreativeStore(context:Context){
    private val app=context.applicationContext
    private val motionCache=object:LinkedHashMap<String,Triple<Long,Long,List<MotionClip>>>(8,.75f,true){override fun removeEldestEntry(e:MutableMap.MutableEntry<String,Triple<Long,Long,List<MotionClip>>>)=size>5}
    private fun readProjects()=root.listFiles()?.filter{it.extension=="json"}?.mapNotNull{runCatching{decodeProject(JSONObject(String(AtomicFile(it).readFully(),Charsets.UTF_8)))}.getOrNull()}.orEmpty()
    val brands:List<ColorBank> = JSONArray(context.assets.open("brand-palettes.json").bufferedReader().use{it.readText()}).let{a->(0 until a.length()).map{a.getJSONObject(it).let{j->ColorBank(j.getString("id"),j.getString("name"),ints(j.getJSONArray("colors")),j.getJSONArray("codes").let{c->(0 until c.length()).map{c.getString(it)}},true)}}}
    val builtinBanks get()=PixelOps.banks+brands
    private val root=File(context.filesDir,"pixel-projects-v3").apply{mkdirs()}
    private val motionRoot=File(context.filesDir,"pet-motions-v3").apply{mkdirs()}
    private val colorFile=AtomicFile(File(context.filesDir,"palettes-v3.json"))
    private val lock=Mutex()
    val projects=MutableStateFlow<List<PixelSummary>>(emptyList())
    val palettes=MutableStateFlow<List<ColorBank>>(builtinBanks)
    val motionRevision=MutableStateFlow(0)
    private fun key(id:String):String {require(id.matches(Regex("[a-zA-Z0-9-]{1,80}")));return id}
    fun referenceFile(id:String)=File(root,"${key(id)}.png")
    suspend fun refresh()=withContext(Dispatchers.IO){lock.withLock{loadSummaries();loadPalettes()}}
    private fun loadSummaries(){projects.value=readProjects().map{PixelSummary(it.id,it.name,it.width,it.height,it.frames.size,it.updated,it.motionAction,it.frames.first().pixels)}.sortedByDescending{it.updated}}
    suspend fun availableName(p:PixelProject):String=withContext(Dispatchers.IO){lock.withLock{projectName(p)}}
    private fun projectName(p:PixelProject):String {
        val peers=readProjects().filter{it.id!=p.id && (if(p.motionAction==null)it.motionAction==null&&(p.artworkId==null||it.artworkId!=p.artworkId) else it.artworkId==p.artworkId&&it.motionAction==p.motionAction&&it.motionId!=p.motionId)}.map{it.name}
        val other=if(p.motionAction==null)(app as? ArtworkStoreOwner)?.artworkStore?.items?.value.orEmpty().filter{it.id!=p.artworkId}.map{it.name} else p.artworkId?.let{readMotions(it).filter{c->c.action==p.motionAction&&c.id!=p.motionId}.map{c->c.name}}.orEmpty()
        return if(p.artworkId==null&&p.name.matches(Regex("我的宠物-\\d+"))&&(peers+other).any{it.equals(p.name,true)})SelectionOps.nextName(peers+other)else Names.unique(p.name,peers+other)
    }
    private fun loadPalettes(){if(!colorFile.baseFile.exists()){palettes.value=builtinBanks;return};val a=JSONArray(String(colorFile.readFully(),Charsets.UTF_8));palettes.value=builtinBanks+(0 until a.length()).map{a.getJSONObject(it).let{j->ColorBank(j.getString("id"),j.getString("name"),ints(j.getJSONArray("colors")))}}}
    suspend fun savePalette(name:String,colors:List<Int>)=withContext(Dispatchers.IO){lock.withLock{
        val bank=ColorBank(UUID.randomUUID().toString(),name.trim().ifBlank{"我的色卡"}.take(30),colors.distinct());loadPalettes();val custom=palettes.value.filter{it.id !in builtinBanks.map{p->p.id}}+bank;require(custom.size<=60){"最多保存 60 张自定义色卡"}
        val a=JSONArray();custom.forEach{a.put(JSONObject().put("id",it.id).put("name",it.name).put("colors",JSONArray(it.colors)))};write(colorFile,a.toString());palettes.value=builtinBanks+custom;bank
    }}
    suspend fun deletePalette(id:String)=withContext(Dispatchers.IO){lock.withLock{loadPalettes();val custom=palettes.value.filter{it.id !in builtinBanks.map{p->p.id}&&it.id!=id};val a=JSONArray();custom.forEach{a.put(JSONObject().put("id",it.id).put("name",it.name).put("colors",JSONArray(it.colors)))};write(colorFile,a.toString());palettes.value=builtinBanks+custom}}
    suspend fun linked(artworkId:String,action:String?=null,clipId:String?=null):PixelProject?=withContext(Dispatchers.IO){lock.withLock{
        root.listFiles()?.filter{it.extension=="json"}?.mapNotNull{runCatching{decodeProject(JSONObject(String(AtomicFile(it).readFully(),Charsets.UTF_8)))}.getOrNull()}?.filter{it.artworkId==artworkId&&it.motionAction==action&&(action==null||it.motionId==clipId)}?.maxByOrNull{it.updated}
    }}
    suspend fun renameArtwork(id:String,name:String)=withContext(Dispatchers.IO){lock.withLock{
        root.listFiles()?.filter{it.extension=="json"}?.forEach{f->val p=runCatching{decodeProject(JSONObject(String(AtomicFile(f).readFully(),Charsets.UTF_8)))}.getOrNull();if(p?.artworkId==id&&p.motionAction==null)write(AtomicFile(f),encodeProject(p.copy(name=name,updated=System.currentTimeMillis())).toString())};loadSummaries()
    }}
    suspend fun get(id:String):PixelProject?=withContext(Dispatchers.IO){lock.withLock{val f=AtomicFile(File(root,"${key(id)}.json"));if(!f.baseFile.exists())null else decodeProject(JSONObject(String(f.readFully(),Charsets.UTF_8)))}}
    suspend fun save(p:PixelProject)=withContext(Dispatchers.IO){lock.withLock{val clean=p.copy(name=projectName(p),updated=System.currentTimeMillis());write(AtomicFile(File(root,"${key(p.id)}.json")),encodeProject(clean).toString());loadSummaries();clean}}
    suspend fun delete(id:String)=withContext(Dispatchers.IO){lock.withLock{AtomicFile(File(root,"${key(id)}.json")).delete();referenceFile(id).delete();loadSummaries()}}
    suspend fun motions(petId:String):List<MotionClip> = withContext(Dispatchers.IO){lock.withLock{readMotions(petId)}}
    /** Collection cards read one compressed idle clip, never all eight actions per visible pet. */
    suspend fun portraitMotion(art:Artwork):MotionClip? = withContext(Dispatchers.IO){lock.withLock{
        if(File(motionRoot,"${key(art.id)}.json").isFile){
            val clips=readMotions(art.id)
            clips.firstOrNull{it.action=="idle"&&it.triggered}?:clips.firstOrNull{it.triggered}?:clips.firstOrNull()
        }else SpriteCatalog.portrait(app,art.id)?:if(art.id in BuiltinPets.names&&art.pattern==BuiltinPets.pattern(art.id))MotionDefaults.clip(art,"idle")else null
    }}
    private fun readMotions(petId:String):List<MotionClip>{val f=AtomicFile(File(motionRoot,"${key(petId)}.json"));if(!f.baseFile.exists())return SpriteCatalog.motions(app,petId);val cached=motionCache[petId];if(cached!=null&&cached.first==f.baseFile.lastModified()&&cached.second==f.baseFile.length())return cached.third;val j=JSONObject(String(f.readFully(),Charsets.UTF_8));require(j.getInt("schema")==1);val a=j.getJSONArray("clips");return (0 until a.length()).map{decodeClip(a.getJSONObject(it))}.also{motionCache[petId]=Triple(f.baseFile.lastModified(),f.baseFile.length(),it)}}
    suspend fun saveMotion(petId:String,c:MotionClip)=withContext(Dispatchers.IO){lock.withLock{
        val previous=readMotions(petId);val old=previous.firstOrNull{it.id==c.id};require(old==null||old.action==c.action){"动作只能属于创建时的互动"};val clean=c.copy(actionName=previous.firstOrNull{it.action==c.action}?.actionName?:c.actionName,name=Names.unique(c.name,previous.filter{it.id!=c.id&&it.action==c.action}.map{it.name}));val all=if(old==null)previous+clean else previous.map{if(it.id==c.id)clean else it};require(all.any{it.action==c.action&&it.triggered}){"每种互动至少开启一个互动触发动作"};require(all.size<=32){"每只宠物最多 32 组动作"};require(all.sumOf{it.frames.size*it.width*it.height}<=4_194_304){"动画总量过大，请减少帧数或动作组"};writeMotions(petId,all)
        readProjects().filter{it.artworkId==petId&&it.motionId==clean.id}.forEach{draft->write(AtomicFile(File(root,"${key(draft.id)}.json")),encodeProject(draft.copy(name=clean.name,triggered=clean.triggered,random=clean.random)).toString())};loadSummaries();clean
    }}
    suspend fun deleteMotion(petId:String,id:String)=withContext(Dispatchers.IO){lock.withLock{val all=readMotions(petId);val target=all.firstOrNull{it.id==id};require(target==null||all.count{it.action==target.action}>1){"每种互动至少保留一个动作"};val remaining=all.filter{it.id!=id};require(target==null||remaining.any{it.action==target.action&&it.triggered}){"请先开启另一个动作，再删除当前动作"};writeMotions(petId,remaining)}}
    suspend fun clearMotions(petId:String)=withContext(Dispatchers.IO){lock.withLock{writeMotions(petId,emptyList())}}
    suspend fun ensureMotions(art:Artwork):List<MotionClip> = withContext(Dispatchers.IO){lock.withLock{
        val original=readMotions(art.id);val all=mutableListOf<MotionClip>();val ids=mutableSetOf<String>()
        original.forEach{c->val id=if(ids.add(c.id))c.id else UUID.randomUUID().toString().also{ids.add(it)};all+=c.copy(id=id,name=Names.unique(c.name,all.filter{it.action==c.action}.map{it.name}))}
        MotionDefaults.actions.keys.filter{all.isEmpty() || original.isEmpty()}.forEach{a->all+=MotionDefaults.clip(art,a).copy(id=UUID.randomUUID().toString(),name="${MotionDefaults.actions[a]} 01")}
        all.map{it.action}.distinct().forEach{a->if(all.none{it.action==a&&it.triggered}){val i=all.indexOfFirst{it.action==a};if(i>=0)all[i]=all[i].copy(triggered=true)}}
        if(all!=original)writeMotions(art.id,all);all.toList()
    }}
    suspend fun importMotions(art:Artwork,clips:List<MotionClip>)=withContext(Dispatchers.IO){lock.withLock{
        require(clips.map{it.id}.distinct().size==clips.size);val all=mutableListOf<MotionClip>()
        clips.forEach{c->all+=c.copy(name=Names.unique(c.name,all.filter{it.action==c.action}.map{it.name}))}
        if(all.isEmpty())MotionDefaults.actions.keys.forEach{a->all+=MotionDefaults.clip(art,a).copy(id=UUID.randomUUID().toString())}
        all.map{it.action}.distinct().forEach{a->if(all.none{it.action==a&&it.triggered}){val i=all.indexOfFirst{it.action==a};all[i]=all[i].copy(triggered=true)}}
        require(all.size<=32&&all.sumOf{it.frames.size*it.width*it.height}<=4_194_304){"动作总量过大"};writeMotions(art.id,all)
    }}
    suspend fun createAction(art:Artwork,name:String):String {
        val all=ensureMotions(art);val id="custom-"+UUID.randomUUID()
        val label=Names.unique(name.trim().ifBlank{"新动作"}.take(20),all.map{MotionDefaults.label(it.action,all)}.distinct())
        saveMotion(art.id,MotionDefaults.clip(art,id).copy(id=UUID.randomUUID().toString(),name="序列 01",actionName=label,generated=false))
        return id
    }
    suspend fun renameAction(petId:String,action:String,name:String)=withContext(Dispatchers.IO){lock.withLock{
        val all=readMotions(petId);require(all.any{it.action==action}){"动作不存在"}
        val label=Names.unique(name.trim().ifBlank{"新动作"}.take(20),all.filter{it.action!=action}.map{MotionDefaults.label(it.action,all)}.distinct())
        writeMotions(petId,all.map{if(it.action==action)it.copy(actionName=label)else it});label
    }}
    suspend fun deleteAction(petId:String,action:String)=withContext(Dispatchers.IO){lock.withLock{
        val all=readMotions(petId);require(all.map{it.action}.distinct().size>1){"至少保留一个动作"}
        // Drafts are intentionally retained: deleting a live action never deletes authored work.
        writeMotions(petId,all.filter{it.action!=action})
    }}
    suspend fun copyMotion(art:Artwork,id:String):MotionClip{val c=ensureMotions(art).firstOrNull{it.id==id}?:error("动作不存在");return saveMotion(art.id,c.copy(id=UUID.randomUUID().toString(),name=c.name+" 副本"))}
    /** Rebuild procedural clips, preserving authored clips and unsaved action drafts. */
    suspend fun rebaseMotions(before:Artwork,after:Artwork)=withContext(Dispatchers.IO){lock.withLock{
        val old=readMotions(before.id)
        val rebuilt=old.map{c->val original=MotionDefaults.clip(before,c.action)
            if(c.generated||(c.action in MotionDefaults.actions&&c.width==original.width&&c.height==original.height&&c.frames==original.frames)){
                val fresh=MotionDefaults.clip(after,c.action).copy(id=c.id,name=c.name,triggered=c.triggered,random=c.random,actionName=c.actionName)
                readProjects().filter{it.artworkId==before.id&&it.motionId==c.id&&it.frames==c.frames}.forEach{draft->write(AtomicFile(File(root,"${key(draft.id)}.json")),encodeProject(draft.copy(width=fresh.width,height=fresh.height,frames=fresh.frames,name=fresh.name,updated=System.currentTimeMillis())).toString())}
                fresh
            }else c
        }
        if(rebuilt!=old)writeMotions(before.id,rebuilt);loadSummaries()
    }}
    private fun writeMotions(petId:String,all:List<MotionClip>){val a=JSONArray();all.forEach{a.put(encodeClip(it))};write(AtomicFile(File(motionRoot,"${key(petId)}.json")),JSONObject().put("schema",1).put("clips",a).toString());motionCache[petId]=Triple(File(motionRoot,"${key(petId)}.json").lastModified(),File(motionRoot,"${key(petId)}.json").length(),all);motionRevision.update{it+1}}
    private fun write(f:AtomicFile,text:String){val out=f.startWrite();try{out.write(text.toByteArray());f.finishWrite(out)}catch(e:Exception){f.failWrite(out);throw e}}
    companion object{
        private fun ints(a:JSONArray)=IntPixels(IntArray(a.length()){a.getInt(it)})
        private fun frames(a:JSONArray)=(0 until a.length()).map{a.getJSONObject(it).let{j->PixelFrame(ints(j.getJSONArray("pixels")),j.getInt("ms"))}}
        private fun framesJson(frames:List<PixelFrame>)=JSONArray().apply{frames.forEach{put(JSONObject().put("ms",it.durationMs).put("pixels",JSONArray(it.pixels)))}}
        fun encodeProject(p:PixelProject)=JSONObject().put("schema",1).put("id",p.id).put("name",p.name).put("width",p.width).put("height",p.height).put("palette",JSONArray(p.palette)).put("frames",framesJson(p.frames)).put("updated",p.updated).put("artwork",p.artworkId).put("referenceOpacity",p.referenceOpacity.toDouble()).put("motionAction",p.motionAction).put("motionId",p.motionId).put("triggered",p.triggered).put("random",p.random).put("brand",p.brandId).put("codes",JSONArray(p.colorCodes)).put("paletteId",p.paletteId)
        fun decodeProject(j:JSONObject):PixelProject{require(j.getInt("schema")==1){"不支持的动画版本，原文件已保留"};return PixelProject(j.getString("id"),if(j.optString("motionAction")=="feed")j.getString("name").replace("喂食","玩耍").replace("投喂","玩耍")else j.getString("name"),j.getInt("width"),j.getInt("height"),ints(j.getJSONArray("palette")),frames(j.getJSONArray("frames")),j.getLong("updated"),j.optString("artwork").takeIf{it.isNotBlank()&&it!="null"},j.optDouble("referenceOpacity",.35).toFloat(),j.optString("motionAction").let{if(it=="feed")"play"else it}.takeIf{MotionDefaults.validAction(it)},j.optString("motionId").takeIf{it.isNotBlank()&&it!="null"},j.optBoolean("triggered",true),j.optBoolean("random",false),j.optString("brand").takeIf{it.isNotBlank()&&it!="null"},j.optJSONArray("codes")?.let{a->(0 until a.length()).map{a.getString(it)}}?:emptyList(),j.optString("paletteId").takeIf{it.isNotBlank()&&it!="null"}?:j.optString("brand").takeIf{it.isNotBlank()&&it!="null"})}
        fun encodeClip(c:MotionClip)=JSONObject().put("id",c.id).put("name",c.name).put("action",c.action).put("width",c.width).put("height",c.height).put("frames",framesJson(c.frames)).put("triggered",c.triggered).put("random",c.random).put("generated",c.generated).put("actionName",c.actionName)
        fun decodeClip(j:JSONObject)=MotionClip(j.getString("id"),if(j.getString("action")=="feed")j.getString("name").replace("喂食","玩耍").replace("投喂","玩耍")else j.getString("name"),j.getString("action").let{if(it=="feed")"play"else it},j.getInt("width"),j.getInt("height"),frames(j.getJSONArray("frames")),j.optBoolean("triggered",true),j.optBoolean("random",false),generated=j.optBoolean("generated",false),actionName=j.optString("actionName").takeIf{it.isNotBlank()&&it!="null"})
    }
}

