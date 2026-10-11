package studio.cyberbeads.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import studio.cyberbeads.core.data.CompanionStore
import studio.cyberbeads.core.imaging.OptionalModel
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.*
import studio.cyberbeads.core.ui.*

@Composable fun SettingsScreen(store:CompanionStore) {
    val context=LocalContext.current;val scope=rememberCoroutineScope();val model=remember{OptionalModel(context)}
    var available by remember{mutableStateOf(model.available())};var progress by remember{mutableIntStateOf(0)};var loading by remember{mutableStateOf(false)};var modelError by remember{mutableStateOf<String?>(null)};var job by remember{mutableStateOf<Job?>(null)}
    fun prepare(uri:android.net.Uri?){job=scope.launch{loading=true;progress=0;modelError=null;try{val callback:(Int)->Unit={n->scope.launch{progress=n}};if(uri!=null)model.import(uri,callback)else model.download(callback);available=model.available()}catch(e:CancellationException){modelError="已取消，未改变现有模型";throw e}catch(e:Exception){modelError=e.message}finally{loading=false}}}
    val importModel=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){it?.let{uri->prepare(uri)}}
    val s by store.settings.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=22.dp).padding(top=12.dp,bottom=22.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
        SectionLabel("MAKE IT YOURS","我的偏好","让这片小宇宙，更像你。")
        CyberCard(Modifier.fillMaxWidth()) {
            Text("应用风格",style=MaterialTheme.typography.titleMedium)
            listOf("pink" to "草莓奶霜 · 可爱赛博", "mint" to "薄荷苏打 · 清新", "night" to "月光莓果 · 夜间").forEach{(id,label)->
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){RadioButton(s.style==id,onClick={store.saveSettings(s.copy(style=id))});Text(label)}
            }
        }
        CyberCard(Modifier.fillMaxWidth()) {
            Text("桌面伙伴",style=MaterialTheme.typography.titleMedium)
            PreferenceHeading("外观")
            PreferenceSlider("宠物大小",s.petSize.toFloat(),32f..200f,"迷你 32 dp","200 dp",{ "${it.toInt()} dp" },"pet-size"){store.saveSettings(store.settings.value.copy(petSize=it.toInt()))}
            PreferenceSlider("伙伴与气泡不透明度",s.opacity*100,20f..100f,"更通透","完全不透明",{ "${it.toInt()}%" },"pet-opacity"){store.saveSettings(store.settings.value.copy(opacity=it/100))}
            HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
            PreferenceHeading("活动与躲藏")
            PreferenceSwitch("动态互动","播放伙伴的待机与互动动作",s.motion){store.saveSettings(s.copy(motion=it))}
            PreferenceSwitch("自由活动","闲下来向左或向右散步，互动时停下",s.freeRoam){store.saveSettings(s.copy(freeRoam=it))}
            PreferenceSwitch("永不躲藏","开启后留在桌面；关闭后可靠边休息",!s.edgeHide){store.saveSettings(s.copy(edgeHide=!it))}
            if(s.edgeHide)CompactChoice("空闲多久后靠边休息",s.edgeSeconds,listOf(10,20,30,60,120).map{it to "$it 秒"},{store.saveSettings(s.copy(edgeSeconds=it))},Modifier.fillMaxWidth())
            HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
            PreferenceHeading("语音与搭话")
            PreferenceSwitch("朗读宠物说的话","使用系统语音，需已安装中文语音",s.voice){store.saveSettings(s.copy(voice=it))}
            PreferenceSwitch("夜间安静","22:00–08:00 不主动搭话",s.quietNight){store.saveSettings(s.copy(quietNight=it))}
            PreferenceSlider("主动搭话间隔",s.idleMinutes.toFloat(),5f..60f,"5 分钟","60 分钟",{ "${it.toInt()} 分钟" },"pet-idle"){store.saveSettings(store.settings.value.copy(idleMinutes=it.toInt()))}
            Text("还需在每只宠物的聊天设置中单独开启。仅在宠物运行、屏幕解锁时触发；每只每天最多 12 次。不会自动点亮手机屏幕。",color=Muted,fontSize=12.sp)
        }
        GlobalApiPanel(store)
        CyberCard(Modifier.fillMaxWidth()) {
            Text("精细提取模型",style=MaterialTheme.typography.titleMedium)
            Text(if(available)"精细模型已就绪，可在创作页选择。" else "快速模型已内置。精细模型约 176 MB，按需下载或从本地导入，不占基础安装包。",color=Muted,fontSize=13.sp)
            if(loading){LinearProgressIndicator(progress={progress/100f},modifier=Modifier.fillMaxWidth());Text("正在准备 $progress%");OutlinedButton(onClick={job?.cancel()}){Text("取消")}}
            else if(!available){Button(onClick={prepare(null)}){Text("下载精细模型 · 176 MB")};OutlinedButton(onClick={importModel.launch(arrayOf("*/*"))}){Text("从文件导入 u2net.onnx")}}
            modelError?.let{Text(it,color=MaterialTheme.colorScheme.error)}
            Text("下载源为 rembg 官方模型发布页；网络失败可使用交付文件中的离线模型。校验通过后才启用。",fontSize=11.sp,color=Muted)
        }
        CyberCard(Modifier.fillMaxWidth()) {
            Text("数据与连接",style=MaterialTheme.typography.titleMedium)
            Text("照片提取与拼豆制作在本机完成。只有你配置并使用对话 API 后，该宠物的性格及最近对话才会发送到你填写的服务。图片和其他宠物的记录不会附带发送。",color=Muted,fontSize=13.sp)
            Text("API Key 使用 Android Keystore 加密保存。服务商可能计费；主动搭话可单独关闭。每只宠物的聊天页可清除记录与记忆；共享密钥在此页管理。",color=Muted,fontSize=13.sp)
            Text("版本 0.16.3 · 本地作品请在卸载前导出。",color=Muted,fontSize=12.sp)
        }
        Spacer(Modifier.height(24.dp))
    }
}
