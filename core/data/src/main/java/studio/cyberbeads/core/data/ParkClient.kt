package studio.cyberbeads.core.data

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.*
import java.net.HttpURLConnection
import java.net.URI
import studio.cyberbeads.core.model.*

class ParkClient(context:Context){
 private val prefs=context.getSharedPreferences("park-connection-v9",0)
 private val secrets=(context.applicationContext as CompanionOwner).companions
 private val lock=Mutex()
 var endpoint:String=prefs.getString("endpoint","").orEmpty();private set
 var nickname:String=prefs.getString("name","林间旅人").orEmpty();private set
 var identity:String=prefs.getString("id","").orEmpty();private set
 private val secretId="park-session-v9"
 fun configured()=endpoint.isNotBlank()&&identity.isNotBlank()&&secrets.hasSecret(secretId)
 suspend fun connect(url:String,name:String):JSONObject=lock.withLock{
  val next=validateEndpoint(url);require(name.trim().isNotEmpty()){"先给自己起个昵称吧"}
  if(next==endpoint&&configured())return@withLock call("/lobby")
  val result=request(next,"/register",JSONObject().put("name",name.trim().take(20)),"")
  endpoint=next;nickname=result.getString("name");identity=result.getString("id")
  secrets.saveSecret(secretId,result.getString("token"));prefs.edit().putString("endpoint",endpoint).putString("name",nickname).putString("id",identity).apply();result
 }
 suspend fun call(path:String,data:JSONObject=JSONObject()):JSONObject=request(endpoint,path,data,secrets.secret(secretId))
 suspend fun deleteAccount(){call("/account/delete");secrets.saveSecret(secretId,"");identity="";prefs.edit().remove("id").apply()}
 fun avatar(art:Artwork):JSONObject=JSONObject().put("width",art.pattern.width).put("height",art.pattern.height).put("pixels",JSONArray(PixelOps.fromPattern(art.pattern)))
 private suspend fun request(base:String,path:String,data:JSONObject,token:String):JSONObject=withContext(Dispatchers.IO){
  val conn=URI.create(base+path).toURL().openConnection() as HttpURLConnection
  try{conn.connectTimeout=6000;conn.readTimeout=7000;conn.instanceFollowRedirects=false;conn.requestMethod="POST";conn.doOutput=true;conn.setRequestProperty("Content-Type","application/json");if(token.isNotBlank())conn.setRequestProperty("Authorization","Bearer $token")
   conn.outputStream.use{it.write(data.toString().toByteArray())};val status=conn.responseCode
   val bytes=(if(status in 200..299)conn.inputStream else conn.errorStream)?.use{input->val out=java.io.ByteArrayOutputStream();val b=ByteArray(8192);while(true){val n=input.read(b);if(n<0)break;require(out.size()+n<=1_200_000){"房间数据过大"};out.write(b,0,n)};out.toByteArray()}?:error("服务器没有回应")
   val j=JSONObject(String(bytes,Charsets.UTF_8));require(status in 200..299){j.optString("error","连接失败：$status")};j
  }finally{conn.disconnect()}
 }
 companion object {
  fun validateEndpoint(input:String):String{val u=URI(input.trim().trimEnd('/'));val host=u.host.orEmpty();require(host.isNotBlank()&&u.userInfo==null&&u.query==null&&u.fragment==null&&(u.path.isNullOrEmpty()||u.path=="/")){"请填写服务地址，例如 https://park.example.com"}
   val octets=host.split('.').mapNotNull{it.toIntOrNull()};val ipv4=octets.size==4&&octets.all{it in 0..255}&&Regex("[0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+").matches(host)
   val local=host=="localhost"||(ipv4&&(octets[0]==127||octets[0]==10||(octets[0]==192&&octets[1]==168)||(octets[0]==172&&octets[1] in 16..31)))
   require(u.scheme=="https"||(u.scheme=="http"&&local)){"公网服务请使用 HTTPS；HTTP 仅用于本地测试"};return u.toString().trimEnd('/')}
 }
}
