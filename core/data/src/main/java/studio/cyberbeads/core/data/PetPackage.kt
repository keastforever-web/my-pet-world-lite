package studio.cyberbeads.core.data

import kotlinx.coroutines.*
import org.json.*
import studio.cyberbeads.core.model.*
import java.util.UUID

/** Portable data only. Never contains credentials, endpoints, conversation history or personal memory. */
object PetPackage {
    const val MAX_BYTES=48*1024*1024
    suspend fun export(id:String,artworks:ArtworkStore,creative:CreativeStore,companions:CompanionStore):ByteArray=withContext(Dispatchers.Default){
        val art=artworks.get(id)?:error("伙伴不存在");val own=creative.ensureMotions(art)
        val clips=own
        require(clips.size<=32&&clips.sumOf{it.width*it.height*it.frames.size}<=4_194_304){"动作数量过大，请精简后分享"}
        val p=art.pattern;val j=JSONObject().put("format","cyberbeads-pet").put("version",1).put("name",art.name).put("width",p.width).put("height",p.height).put("palette",JSONArray(p.palette)).put("cells",JSONArray(p.cells)).put("paletteId",p.paletteId).put("brand",p.brandId).put("codes",JSONArray(p.colorCodes)).put("persona",companions.profile(id).persona).put("clips",JSONArray().apply{clips.forEach{put(CreativeStore.encodeClip(it))}})
        j.toString().toByteArray(Charsets.UTF_8).also{require(it.size<=MAX_BYTES){"宠物文件超过48MB"}}
    }
    suspend fun import(bytes:ByteArray,artworks:ArtworkStore,creative:CreativeStore,companions:CompanionStore):Artwork=withContext(Dispatchers.Default){
        require(bytes.size<=MAX_BYTES){"宠物文件超过48MB"};val j=JSONObject(String(bytes,Charsets.UTF_8));require(j.getString("format")=="cyberbeads-pet"&&j.getInt("version")==1){"不是支持的宠物文件"}
        fun ints(key:String)=j.getJSONArray(key).let{a->(0 until a.length()).map{a.getInt(it)}}
        val codes=j.optJSONArray("codes")?.let{a->(0 until a.length()).map{a.getString(it).take(30)}}?:emptyList()
        val pattern=BeadPattern(j.getInt("width"),j.getInt("height"),ints("palette"),ints("cells"),j.optString("brand").takeIf{it.isNotBlank()&&it!="null"},codes,j.optString("paletteId").takeIf{it.isNotBlank()&&it!="null"});require(pattern.beadCount>0)
        val a=j.getJSONArray("clips");require(a.length()<=32);val clips=(0 until a.length()).map{CreativeStore.decodeClip(a.getJSONObject(it))};require(clips.map{it.id}.distinct().size==clips.size);require(clips.sumOf{it.width*it.height*it.frames.size}<=4_194_304)
        val art=Artwork(UUID.randomUUID().toString(),Names.unique(j.getString("name"),artworks.items.value.map{it.name}),System.currentTimeMillis(),pattern).complete()
        // Validate whole document before writes; publish artwork last so half imports never appear in collection.
        try{creative.importMotions(art,clips);companions.saveProfile(PetProfile(art.id,persona=j.optString("persona",PetProfile(art.id).persona).take(4000),proactive=false));artworks.save(art)}catch(e:Exception){runCatching{creative.clearMotions(art.id)};throw e}
        art
    }
}
