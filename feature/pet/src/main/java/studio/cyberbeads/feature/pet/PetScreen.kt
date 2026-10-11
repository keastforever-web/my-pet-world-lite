package studio.cyberbeads.feature.pet

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.*
import studio.cyberbeads.core.data.*
import studio.cyberbeads.core.model.*
import studio.cyberbeads.core.ui.*
import kotlin.math.floor
import kotlin.math.min

@Composable fun PetPortrait(art:Artwork,motion:Boolean,modifier:Modifier=Modifier) {
    val context=LocalContext.current;val creative=(context.applicationContext as CreativeOwner).creative
    val revision by creative.motionRevision.collectAsStateWithLifecycle();val lifecycle=LocalLifecycleOwner.current.lifecycle
    val clip by produceState<MotionClip?>(null,art.id,art.pattern,revision,motion){value=null;if(motion)value=creative.portraitMotion(art)}
    var frame by remember(art.id,clip){mutableIntStateOf(0)}
    LaunchedEffect(clip,motion,lifecycle){if(motion)clip?.let{animation->lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED){while(isActive){delay(animation.frames[frame].durationMs.toLong());frame=(frame+1)%animation.frames.size}}}}
    val animation=clip.takeIf{motion};val staticPixels=remember(art.pattern){PixelOps.fromPattern(art.pattern)}
    val width=animation?.width?:art.pattern.width;val height=animation?.height?:art.pattern.height
    val pixels=animation?.frames?.get(frame)?.pixels?:staticPixels
    val bitmap=remember(pixels,width,height){android.graphics.Bitmap.createBitmap(pixels.toIntArray(),width,height,android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()}
    Canvas(modifier.testTag("portrait-${art.id}").semantics{stateDescription=if(motion&&clip!=null)"动画帧 ${frame+1}/${clip!!.frames.size}" else "静态预览"}){val cell=min(size.width/width,size.height/height);val w=(width*cell).toInt();val h=(height*cell).toInt()
        drawImage(bitmap,dstOffset=IntOffset(((size.width-w)/2).toInt(),((size.height-h)/2).toInt()),dstSize=IntSize(w.coerceAtLeast(1),h.coerceAtLeast(1)),filterQuality=FilterQuality.None)}
}

@Composable fun PetScreen(store:ArtworkStore,companions:CompanionStore,initialId:String?,onLibrary:()->Unit,onChat:(String)->Unit,onMotions:(String)->Unit = {}) {
    val context=LocalContext.current;val scope=rememberCoroutineScope();val creative=(context.applicationContext as CreativeOwner).creative;val lifecycle=LocalLifecycleOwner.current
    val items by store.items.collectAsStateWithLifecycle();val running by PetController.runningIds.collectAsStateWithLifecycle()
    val prefs by companions.settings.collectAsStateWithLifecycle();val revision by companions.revision.collectAsStateWithLifecycle()
    
    val completed=items.filter{it.isComplete}.sortedWith(compareBy<Artwork>{BuiltinPets.names.keys.indexOf(it.id).let{n->if(n<0)Int.MAX_VALUE else n}}.thenByDescending{it.createdAt});var selected by rememberSaveable{mutableStateOf<String?>(null)}
    var allowed by remember{mutableStateOf(Settings.canDrawOverlays(context))};var message by remember{mutableStateOf<String?>(null)}
    var pending by remember{mutableStateOf<String?>(null)};var stateRevision by remember{mutableIntStateOf(0)}
    LaunchedEffect(initialId){if(initialId!=null)selected=initialId}
    DisposableEffect(lifecycle){val observer=LifecycleEventObserver{_,e->if(e==Lifecycle.Event.ON_RESUME)allowed=Settings.canDrawOverlays(context)};lifecycle.lifecycle.addObserver(observer);onDispose{lifecycle.lifecycle.removeObserver(observer)}}
    DisposableEffect(store){val listener=store.observePetState{stateRevision++};onDispose{listener.close()}}
    fun launch(id:String){try{PetController.start(context,id);message="伙伴正在来到桌面。长按它，可以展开互动菜单。"}catch(e:Exception){message=e.message}}
        val notification=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){pending?.let{launch(it)};pending=null}
    fun start(id:String){if(!allowed){message="请先在下面开启悬浮窗权限";return};if(running.size>=5){message="最多同时陪伴 5 只，请先收回一只";return}
        if(Build.VERSION.SDK_INT>=33&&ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){pending=id;notification.launch(Manifest.permission.POST_NOTIFICATIONS)}else launch(id)}
    var shareId by rememberSaveable{mutableStateOf<String?>(null)};var fileBusy by remember{mutableStateOf(false)}
    val exporter=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")){uri->if(uri!=null)scope.launch{fileBusy=true;try{val bytes=PetPackage.export(shareId?:error("未选择伙伴"),store,creative,companions);withContext(Dispatchers.IO){context.contentResolver.openOutputStream(uri)?.use{it.write(bytes)}?:error("无法写入文件")};message="宠物文件已保存，包含图案、动作与性格；不包含密钥、聊天和记忆"}catch(e:Exception){message=e.message}finally{fileBusy=false}}}
    val importer=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null)scope.launch{fileBusy=true;try{val bytes=withContext(Dispatchers.IO){context.contentResolver.openInputStream(uri)?.use{input->val out=java.io.ByteArrayOutputStream();val buf=ByteArray(8192);while(true){val n=input.read(buf);if(n<0)break;require(out.size()+n<=PetPackage.MAX_BYTES){"文件超过48MB"};out.write(buf,0,n)};out.toByteArray()}?:error("无法读取文件")};val art=PetPackage.import(bytes,store,creative,companions);selected=art.id;message="已导入 ${art.name}，可直接互动"}catch(e:Exception){message="导入失败：${e.message}"}finally{fileBusy=false}}}
    val columns=if(LocalConfiguration.current.screenWidthDp>=840)4 else if(LocalConfiguration.current.screenWidthDp>=600)3 else 2
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(start=22.dp,end=22.dp,top=12.dp,bottom=28.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item { Column(verticalArrangement=Arrangement.spacedBy(14.dp)) {
        SectionLabel("LITTLE FRIENDS CLUB","伙伴收藏室","把喜欢的小小生命，收进每一天。")
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("${completed.size} 位伙伴",color=Muted);Text("桌面 ${running.size} / 5",color=Mint)}
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton(onClick={importer.launch(arrayOf("*/*"))},enabled=!fileBusy,modifier=Modifier.weight(1f),contentPadding=PaddingValues(4.dp)){Text("导入宠物",fontSize=12.sp)}};if(fileBusy)LinearProgressIndicator(Modifier.fillMaxWidth())
        } }
        items(completed.chunked(columns),key={it.first().id}){row->Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){
            row.forEach{art->val profile=remember(art.id,revision){companions.profile(art.id)}
                CyberCard(Modifier.weight(1f).clickable{selected=art.id},contentPadding=12.dp,spacing=6.dp){
                    Text(if(art.id in running)"● 正在陪伴" else "✦ 收藏伙伴",color=Mint,fontSize=11.sp)
                    PetPortrait(art,prefs.motion,Modifier.fillMaxWidth().height(76.dp))
                    Text(art.name,fontWeight=FontWeight.Bold,fontSize=16.sp)
                    Text("点击查看形象",fontSize=10.sp,color=Muted)
                    OutlinedButton(onClick={selected=null;onChat(art.id)},modifier=Modifier.fillMaxWidth(),contentPadding=PaddingValues(6.dp)){Text("聊天设置")}

                }
            };repeat(columns-row.size){Spacer(Modifier.weight(1f))}
        }}
        item { Column(verticalArrangement=Arrangement.spacedBy(14.dp)) {
        if(completed.isEmpty())CyberCard{Text("拼好第一件作品，让它成为伙伴。");Button(onClick=onLibrary){Text("去作品库")}}
        message?.let{Text(it,color=Mint)}
        CyberCard(Modifier.fillMaxWidth()){
            Text(if(allowed)"悬浮窗权限已开启" else "让它走出 App",fontWeight=FontWeight.Bold)
            Text("需要系统的“显示在其他应用上层”权限。拖动换位置，轻点摸摸，长按打开菜单。锁屏时隐藏，不读取屏幕内容。",color=Muted,fontSize=13.sp)
            if(!allowed)Button(onClick={runCatching{context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:${context.packageName}")))}.onFailure{message="请在系统设置中开启显示在其他应用上层"}},modifier=Modifier.fillMaxWidth()){Text("开启悬浮窗权限")}
            if(running.isNotEmpty())OutlinedButton(onClick={PetController.stop(context)},modifier=Modifier.fillMaxWidth()){Text("收回所有桌面伙伴")}
        }
        Text("照片转换在本机完成。聊天需自行配置接口；共享密钥在设置页管理，每位伙伴单独保存性格、记忆与记录，主动搭话默认关闭。",color=Muted,fontSize=12.sp)
    }
    } }
    selected?.let{id->completed.firstOrNull{it.id==id}?.let{art->val state=remember(id,stateRevision){store.petState(id)}
        AlertDialog(onDismissRequest={selected=null},title={Text(art.name)},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)){
            PetPortrait(art,prefs.motion,Modifier.fillMaxWidth().height(110.dp))
            Text("亲密度 ${state.affection} · 轻点送一个抱抱",color=Muted)
            Button(onClick={if(id in running)PetController.remove(context,id)else start(id)},modifier=Modifier.fillMaxWidth()){Text(if(id in running)"收回这只" else "带到桌面")}
                        Button(onClick={if(!allowed)message="请先开启悬浮窗权限" else if(id !in running&&running.size>=5)message="桌面已有5位伙伴，请先收回一只" else {PetController.chat(context,id);selected=null}},modifier=Modifier.fillMaxWidth()){Text("打开迷你聊天")}
            OutlinedButton(onClick={selected=null;onMotions(id)},modifier=Modifier.fillMaxWidth()){Text("设计互动动作")}
            OutlinedButton(onClick={shareId=id;exporter.launch("${art.name.replace(Regex("[^a-zA-Z0-9\u4e00-\u9fff-]"),"_")}.cyberpet")},enabled=!fileBusy,modifier=Modifier.fillMaxWidth()){Text("分享宠物文件")};Text("包含所有动作和性格，不包含私人聊天、记忆与密钥。",fontSize=11.sp,color=Muted)
            message?.let{Text(it,fontSize=12.sp,color=Mint)}
        }},confirmButton={OutlinedButton(onClick={selected=null}){Text("关闭详情")}})
    }}


}
