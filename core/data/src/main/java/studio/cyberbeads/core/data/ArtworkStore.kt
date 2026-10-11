package studio.cyberbeads.core.data

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import studio.cyberbeads.core.model.*
import java.io.File
import java.util.UUID

interface ArtworkStoreOwner { val artworkStore: ArtworkStore }

/** Independent versioned documents: one damaged record cannot destroy the entire library. */
class ArtworkStore(context: Context, private val excludedIds: Set<String> = emptySet()) {
    private val root = File(context.filesDir, "artworks").apply { mkdirs() }
    private val prefs = context.getSharedPreferences("pet-state-v1", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val decoded=mutableMapOf<String,Triple<Long,Long,Artwork>>()
    private val _items = MutableStateFlow<List<Artwork>>(emptyList())
    val items: StateFlow<List<Artwork>> = _items
    private val _warning = MutableStateFlow<String?>(null)
    val warning: StateFlow<String?> = _warning

    fun wasDeleted(id:String)=prefs.getBoolean("deleted-$id",false)
    suspend fun refresh() = withContext(Dispatchers.IO) { mutex.withLock { loadAll() } }
    private fun loadAll() {
        var failed = 0
        _items.value = root.listFiles()?.filter { it.extension == "json" && it.nameWithoutExtension !in excludedIds && !wasDeleted(it.nameWithoutExtension) }?.mapNotNull { file ->
            runCatching { val old=decoded[file.name];if(old!=null&&old.first==file.lastModified()&&old.second==file.length())old.third else decode(AtomicFile(file).readFully().toString(Charsets.UTF_8)).also{decoded[file.name]=Triple(file.lastModified(),file.length(),it)} }.getOrElse { failed++; null }
        }?.sortedByDescending { it.createdAt } ?: emptyList()
        _warning.value = if (failed > 0) "$failed 件作品暂时无法读取，原文件已保留" else null
    }
    suspend fun create(name: String, pattern: BeadPattern): Artwork {
        require(pattern.beadCount > 0) { "至少保留一颗豆子" }
        val item = Artwork(UUID.randomUUID().toString(), Names.unique(name,items.value.map{it.name}), System.currentTimeMillis(), pattern)
        save(item); return item
    }
    suspend fun save(item: Artwork) = withContext(Dispatchers.IO) { mutex.withLock {
        require(item.id.matches(Regex("[a-zA-Z0-9-]{1,80}")))
        require(item.id !in excludedIds){"这位旧版内置伙伴已停用，可使用新 ID 导入"}
        require(!wasDeleted(item.id)){"这件作品已删除，请另存为新作品"}
        val file = AtomicFile(File(root, item.id + ".json"))
        val stream = file.startWrite()
        try { stream.write(encode(item).toByteArray()); file.finishWrite(stream);decoded.remove(file.baseFile.name) }
        catch (error: Exception) { file.failWrite(stream); throw error }
        loadAll()
    } }
    suspend fun get(id: String): Artwork? = withContext(Dispatchers.IO) { mutex.withLock {
        require(id.matches(Regex("[a-zA-Z0-9-]{1,80}")))
        val file = File(root, "$id.json")
        if (id in excludedIds||wasDeleted(id)||!file.exists()) null else decode(AtomicFile(file).readFully().toString(Charsets.UTF_8))
    } }
    suspend fun delete(id: String) = withContext(Dispatchers.IO) { mutex.withLock {
        require(id.matches(Regex("[a-zA-Z0-9-]{1,80}")))
        check(prefs.edit().putBoolean("deleted-$id",true).commit()){ "未能保存删除记录，请重试" };AtomicFile(File(root, "$id.json")).delete();decoded.remove("$id.json")
        prefs.edit().remove(id).apply(); loadAll()
    } }
    suspend fun update(id: String, transform: (Artwork) -> Artwork) = withContext(Dispatchers.IO) { mutex.withLock {
        require(id.matches(Regex("[a-zA-Z0-9-]{1,80}")))
        val file = AtomicFile(File(root, "$id.json"))
        val current = decode(file.readFully().toString(Charsets.UTF_8))
        val next = transform(current)
        require(next.id == id)
        val stream = file.startWrite()
        try { stream.write(encode(next).toByteArray()); file.finishWrite(stream);decoded.remove(file.baseFile.name) }
        catch (error: Exception) { file.failWrite(stream); throw error }
        loadAll()
    } }
    fun petState(id: String): PetState {
        val now = System.currentTimeMillis()
        return runCatching {
            val obj = JSONObject(prefs.getString(id, null) ?: return@runCatching PetState(lastUpdated = now))
            PetState(obj.optInt("affection",50), obj.optBoolean("sleeping"), obj.optLong("updated",now)).tick(now)
        }.getOrElse { PetState(lastUpdated = now) }
    }
    fun savePetState(id: String, state: PetState) {
        prefs.edit().putString(id, JSONObject().put("affection", state.affection)
            .put("sleeping", state.sleeping).put("updated", state.lastUpdated).toString()).apply()
    }
    fun observePetState(onChange: (String) -> Unit): java.io.Closeable {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key -> if (key != null) onChange(key) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        return java.io.Closeable { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    private fun encode(item: Artwork): String = JSONObject().put("schema", 1).put("id", item.id).put("name", item.name)
        .put("created", item.createdAt).put("width", item.pattern.width).put("height", item.pattern.height)
        .put("palette", JSONArray(item.pattern.palette)).put("cells", JSONArray(item.pattern.cells))
        .put("paletteId",item.pattern.paletteId).put("brand",item.pattern.brandId).put("codes",JSONArray(item.pattern.colorCodes)).put("placed", JSONArray(item.placed.toList())).toString()
    private fun decode(text: String): Artwork {
        val obj = JSONObject(text)
        require(obj.getInt("schema") == 1) { "暂不支持此作品版本，请保留原文件" }
        fun ints(name: String): List<Int> = obj.getJSONArray(name).let { a -> (0 until a.length()).map { a.getInt(it) } }
        val pattern = BeadPattern(obj.getInt("width"), obj.getInt("height"), ints("palette"), ints("cells"),obj.optString("brand").takeIf{it.isNotBlank()&&it!="null"},obj.optJSONArray("codes")?.let{a->(0 until a.length()).map{a.getString(it)}}?:emptyList(),obj.optString("paletteId").takeIf{it.isNotBlank()&&it!="null"}?:obj.optString("brand").takeIf{it.isNotBlank()&&it!="null"})
        return Artwork(obj.getString("id"), obj.getString("name"), obj.getLong("created"), pattern,
            ints("placed").filter { it in pattern.cells.indices && pattern.cells[it] >= 0 }.toSet())
    }
}
