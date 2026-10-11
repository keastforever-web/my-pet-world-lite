package studio.cyberbeads.core.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.AtomicFile
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.*
import studio.cyberbeads.core.model.*
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class PetChatEvent(val id:String,val answer:String?=null,val proactive:Boolean=false)
interface CompanionOwner { val companions:CompanionStore; val chatClient:PetChatClient }
class CompanionStore(context:Context) {
    private val prefs=context.getSharedPreferences("companion-settings-v2",Context.MODE_PRIVATE)
    private val root=File(context.filesDir,"pet-chats-v2").apply{mkdirs()}
    private val lock=Mutex()
    private val archive=ChatArchive(context)
    companion object{const val SHARED_API="shared-api"}
    init{migrateSharedApi()}
    private fun migrateSharedApi(){if(prefs.getBoolean("shared-api-migrated",false))return;val id=prefs.all.keys.filter{it.startsWith("profile-")}.map{it.removePrefix("profile-")}.sorted().firstOrNull{hasSecret(it)&&profile(it).endpoint.isNotBlank()};val e=prefs.edit();if(id!=null){val p=profile(id);e.putString("api-endpoint",p.endpoint).putString("api-model",p.model);prefs.getString("secret-$id",null)?.let{e.putString("secret-$SHARED_API",it)}};e.putBoolean("shared-api-migrated",true).apply()}
    fun apiProfile()=PetProfile(SHARED_API,endpoint=prefs.getString("api-endpoint","").orEmpty(),model=prefs.getString("api-model","").orEmpty())
    fun saveApi(p:PetProfile){PetChatClient.endpoint(p.endpoint);require(p.model.isNotBlank()){ "请填写模型名称" };prefs.edit().putString("api-endpoint",p.endpoint.trim()).putString("api-model",p.model.trim()).apply();revision.value++}
    fun connected()=hasSecret(SHARED_API)&&apiProfile().endpoint.isNotBlank()
    fun effectiveProfile(id:String):PetProfile{val api=apiProfile();return profile(id).copy(endpoint=api.endpoint,model=api.model)}
    val chatEvents=kotlinx.coroutines.flow.MutableSharedFlow<PetChatEvent>(extraBufferCapacity=32)
    val revision=MutableStateFlow(0)
    val settings=MutableStateFlow(readSettings())
    private fun key(id:String):String { require(id.matches(Regex("[a-zA-Z0-9-]{1,80}"))); return id }
    private fun readSettings()=AppPreferences(prefs.getString("style","pink")!!,prefs.getBoolean("motion",true),prefs.getInt("size",120),prefs.getBoolean("voice",false),prefs.getInt("idle",15),prefs.getBoolean("quiet",true),prefs.getBoolean("edgeHide",true),prefs.getInt("edgeSeconds",20),prefs.getBoolean("freeRoam",false),prefs.getFloat("opacity",.78f).coerceIn(.2f,1f))
    fun saveSettings(s:AppPreferences) { prefs.edit().putString("style",s.style).putBoolean("motion",s.motion).putBoolean("freeRoam",s.freeRoam).putFloat("opacity",s.opacity.coerceIn(.2f,1f)).putInt("size",s.petSize.coerceIn(32,200)).putBoolean("voice",s.voice).putInt("idle",s.idleMinutes.coerceIn(5,60)).putBoolean("quiet",s.quietNight).putBoolean("edgeHide",s.edgeHide).putInt("edgeSeconds",s.edgeSeconds.coerceIn(10,120)).apply(); settings.value=readSettings() }
    fun profile(id:String):PetProfile = runCatching {
        val j=JSONObject(prefs.getString("profile-${key(id)}", "{}")!!)
        PetProfile(id,j.optString("name"),j.optString("persona",PetProfile(id).persona),j.optString("endpoint"),j.optString("model"),j.optBoolean("proactive"))
    }.getOrElse { PetProfile(id) }
    fun saveProfile(p:PetProfile) { require(p.persona.length<=4000); prefs.edit().putString("profile-${key(p.id)}",JSONObject().put("name",p.nickname.take(24)).put("persona",p.persona).put("endpoint",p.endpoint.trim()).put("model",p.model.trim()).put("proactive",p.proactive).toString()).apply(); revision.value++ }
    private fun encryptionKey():SecretKey {
        val ks=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
        return ks.getKey("cyberbeads-api-v2",null) as? SecretKey ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("cyberbeads-api-v2",KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun hasSecret(id:String)=prefs.contains("secret-${key(id)}")
    fun saveSecret(id:String,value:String) {
        val editor=prefs.edit(); val name="secret-${key(id)}"
        if(value.isEmpty()) editor.remove(name) else {
            val cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE,encryptionKey())
            editor.putString(name,Base64.encodeToString(cipher.iv,Base64.NO_WRAP)+":"+Base64.encodeToString(cipher.doFinal(value.toByteArray()),Base64.NO_WRAP))
        }; editor.apply();revision.value++
    }
    fun secret(id:String):String {
        val encoded=prefs.getString("secret-${key(id)}",null)?:return ""
        return try { val parts=encoded.split(':'); val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,encryptionKey(),GCMParameterSpec(128,Base64.decode(parts[0],Base64.NO_WRAP)));String(cipher.doFinal(Base64.decode(parts[1],Base64.NO_WRAP))) }
        catch(_:Exception){error("本机密钥无法解密，请重新填写 API Key")}
    }
    suspend fun history(id:String):List<ChatLine> = page(id,limit=200)
    suspend fun page(id:String,before:Long=Long.MAX_VALUE,limit:Int=40):List<ChatLine> = withContext(Dispatchers.IO){lock.withLock{archive.page(key(id),before,limit)}}
    suspend fun append(id:String,line:ChatLine)=withContext(Dispatchers.IO){lock.withLock{archive.append(key(id),line);revision.value++}}
    suspend fun memory(id:String):PetMemory=withContext(Dispatchers.IO){lock.withLock{archive.memory(key(id))}}
    suspend fun remember(id:String,m:PetMemory)=withContext(Dispatchers.IO){lock.withLock{archive.remember(key(id),m);revision.value++}}
    suspend fun pendingMemory(id:String,after:Long)=withContext(Dispatchers.IO){lock.withLock{archive.pending(key(id),after)}}
    suspend fun clearHistory(id:String)=withContext(Dispatchers.IO){lock.withLock{archive.clear(key(id));revision.value++}}
}
