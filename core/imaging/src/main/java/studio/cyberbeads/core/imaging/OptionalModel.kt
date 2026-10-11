package studio.cyberbeads.core.imaging

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** The large model is optional, checksum checked, and never downloaded by merely opening a screen. */
class OptionalModel(context:Context){
    private val app=context.applicationContext
    private val lock=Mutex()
    val file get()=File(app.filesDir,"u2net.onnx")
    fun available()=file.isFile&&file.length()==BYTES
    suspend fun import(uri:Uri,onProgress:(Int)->Unit={})=lock.withLock{withContext(Dispatchers.IO){app.contentResolver.openInputStream(uri)?.use{install(it,onProgress)}?:error("无法读取模型文件")}}
    suspend fun download(onProgress:(Int)->Unit={})=lock.withLock{withContext(Dispatchers.IO){
        var url=URL(SOURCE);var conn:HttpURLConnection?=null
        try{repeat(6){
            conn=url.openConnection() as HttpURLConnection;conn!!.instanceFollowRedirects=false;conn!!.connectTimeout=20000;conn!!.readTimeout=20000
            val code=conn!!.responseCode
            if(code in 300..399){val next=URL(url,conn!!.getHeaderField("Location")?:error("下载地址失效"));require(next.protocol=="https");conn!!.disconnect();url=next}
            else {require(code==200){"模型下载失败（HTTP $code），可改用离线导入"};conn!!.inputStream.use{install(it,onProgress)};return@withContext}
        };error("下载重定向过多") }finally{conn?.disconnect()}
    }}
    private suspend fun install(input:InputStream,progress:(Int)->Unit){val temp=File(app.filesDir,"u2net-install.tmp");val digest=MessageDigest.getInstance("SHA-256");var size=0L
        try{temp.outputStream().use{out->val buffer=ByteArray(65536);while(true){currentCoroutineContext().ensureActive();val n=input.read(buffer);if(n<0)break;size+=n;require(size<=BYTES){"不是匹配的精细模型"};digest.update(buffer,0,n);out.write(buffer,0,n);progress((size*100/BYTES).toInt())}}
            require(size==BYTES&&digest.digest().joinToString(""){"%02x".format(it)}==SHA256){"模型校验未通过，原有模型未改动"}
            currentCoroutineContext().ensureActive();java.nio.file.Files.move(temp.toPath(),file.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);progress(100)
        }finally{temp.delete()}
    }
    companion object{const val BYTES=175997641L;const val SHA256="8d10d2f3bb75ae3b6d527c77944fc5e7dcd94b29809d47a739a7a728a912b491";const val SOURCE="https://github.com/danielgatis/rembg/releases/download/v0.0.0/u2net.onnx"}
}
