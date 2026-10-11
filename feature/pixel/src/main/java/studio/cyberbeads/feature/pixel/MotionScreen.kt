package studio.cyberbeads.feature.pixel

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.unit.*
import kotlinx.coroutines.*
import studio.cyberbeads.core.data.*
import studio.cyberbeads.core.model.*
import studio.cyberbeads.core.ui.*
import java.util.UUID

@Composable fun MotionScreen(petId:String,artworks:ArtworkStore,store:CreativeStore,onBack:()->Unit,onEdit:(String,String,Boolean)->Unit){
    val scope=rememberCoroutineScope();var art by remember{mutableStateOf<Artwork?>(null)};var action by rememberSaveable(petId){mutableStateOf("")};var clips by remember{mutableStateOf(emptyList<MotionClip>())};var error by remember{mutableStateOf<String?>(null)};var removing by remember{mutableStateOf<MotionClip?>(null)};val revision by store.motionRevision.collectAsState()
    var busy by remember{mutableStateOf(false)};var adding by remember{mutableStateOf(false)};var newName by remember{mutableStateOf("")};var deleting by remember{mutableStateOf(false)}
    BackHandler{onBack()}
    LaunchedEffect(petId,revision){try{art=artworks.get(petId);art?.let{clips=store.ensureMotions(it);if(clips.none{c->c.action==action})action=clips.firstOrNull()?.action.orEmpty()}}catch(e:Exception){error=e.message}}
    fun task(block:suspend ()->Unit){scope.launch{busy=true;try{block()}catch(e:Exception){error=e.message}finally{busy=false}}}
    val group=clips.filter{it.action==action}
    val actions=MotionDefaults.groups(clips);val label=MotionDefaults.label(action,clips)
    var editName by remember(action,label){mutableStateOf(label)}
    Column(Modifier.fillMaxSize().padding(horizontal=12.dp)){
      Row(verticalAlignment=Alignment.CenterVertically){OutlinedButton(onBack){Text("← 返回")};Column(Modifier.padding(start=10.dp)){Text("动作工坊",style=MaterialTheme.typography.titleMedium);Text(art?.name.orEmpty(),fontSize=11.sp,color=Muted)}}
      Row(Modifier.weight(1f),horizontalArrangement=Arrangement.spacedBy(10.dp)){
       LazyColumn(Modifier.width(96.dp),verticalArrangement=Arrangement.spacedBy(6.dp),contentPadding=PaddingValues(bottom=20.dp)){
        items(actions,key={it}){id->val first=clips.first{it.action==id};OutlinedCard(onClick={action=id},colors=CardDefaults.outlinedCardColors(containerColor=if(id==action)MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface),modifier=Modifier.fillMaxWidth()){
         Column(Modifier.fillMaxWidth().padding(8.dp),horizontalAlignment=Alignment.CenterHorizontally){PixelThumbnail(first.frames.first().pixels,first.width,first.height,Modifier.size(40.dp));Text(MotionDefaults.label(id,clips),fontSize=12.sp,maxLines=2);Text("${clips.count{it.action==id}}组",fontSize=10.sp,color=Muted)}
        }}
        item{OutlinedButton({newName="";adding=true},Modifier.fillMaxWidth(),enabled=!busy&&clips.size<32,contentPadding=PaddingValues(6.dp)){Text("＋ 动作",fontSize=11.sp)}}
       }
    LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(bottom=24.dp)){
        item{
          OutlinedTextField(editName,{editName=it.take(20)},singleLine=true,label={Text("动作名称",fontSize=11.sp)},modifier=Modifier.fillMaxWidth())
          Row{TextButton({task{editName=store.renameAction(petId,action,editName)}},enabled=!busy&&editName!=label){Text("保存名称",fontSize=11.sp)};TextButton({deleting=true},enabled=!busy&&actions.size>1){Text("删除动作",fontSize=11.sp)}}
          Text("${group.size}组序列 · 从启用的序列中随机播放",fontSize=11.sp,color=Muted)
        }
        items(group,key={it.id}){clip->CompactPanel(clip.name){
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){MotionPreview(clip,Modifier.size(48.dp));Column(Modifier.weight(1f)){Text("${clip.frames.size} 帧 · ${clip.durationMs}ms",fontSize=12.sp);Text("序列动画",fontSize=10.sp,color=Muted)}}
            Row(verticalAlignment=Alignment.CenterVertically){Text("互动播放",Modifier.weight(1f),fontSize=12.sp);Switch(clip.triggered,{task{store.saveMotion(petId,clip.copy(triggered=it))}},enabled=!busy&&(!clip.triggered||group.count{it.triggered}>1),modifier=Modifier.height(36.dp))}
            Row(verticalAlignment=Alignment.CenterVertically){Text("空闲表演",Modifier.weight(1f),fontSize=12.sp);Switch(clip.random,{task{store.saveMotion(petId,clip.copy(random=it))}},enabled=!busy&&action!="sleep",modifier=Modifier.height(36.dp))}
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){
                Button(onClick={onEdit(action,clip.id,false)},modifier=Modifier.weight(1f),shape=RoundedCornerShape(10.dp),contentPadding=PaddingValues(3.dp)){Text("编辑",fontSize=11.sp)}
                OutlinedButton(onClick={art?.let{a->task{store.copyMotion(a,clip.id)}}},enabled=!busy,modifier=Modifier.weight(1f),shape=RoundedCornerShape(10.dp),contentPadding=PaddingValues(3.dp)){Text("复制",fontSize=11.sp)}
                OutlinedButton(onClick={removing=clip},enabled=group.size>1&&!busy&&(!clip.triggered||group.count{it.triggered}>1),modifier=Modifier.weight(1f),shape=RoundedCornerShape(10.dp),contentPadding=PaddingValues(3.dp)){Text("删除",fontSize=11.sp)}
            }

        }}
        item{Button(onClick={onEdit(action,UUID.randomUUID().toString(),true)},enabled=!busy,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp)){Text("＋ 新建序列")};Text("每个动作至少保留一组已启用的序列。",fontSize=11.sp,color=Muted)}
        item{error?.let{Text(it,color=MaterialTheme.colorScheme.error)}}
    }
    }}
    if(adding)AlertDialog(onDismissRequest={adding=false},title={Text("给动作起个名字")},text={OutlinedTextField(newName,{newName=it.take(20)},singleLine=true,label={Text("例如：转个圈")})},confirmButton={Button({art?.let{a->task{action=store.createAction(a,newName);adding=false}}},enabled=!busy){Text("创建")}},dismissButton={OutlinedButton({adding=false}){Text("取消")}})
    if(deleting)AlertDialog(onDismissRequest={deleting=false},title={Text("删除「$label」？")},text={Text("桌面圆环会移除这个动作，已保存的序列草稿继续保留。")},confirmButton={Button({deleting=false;task{store.deleteAction(petId,action);action=""}}){Text("删除动作")}},dismissButton={OutlinedButton({deleting=false}){Text("取消")}})
    removing?.let{clip->AlertDialog(onDismissRequest={removing=null},title={Text("删除 ${clip.name}？")},text={Text("每种互动至少保留一个动作。其他组不受影响。")},confirmButton={OutlinedButton(onClick={removing=null;task{store.deleteMotion(petId,clip.id)}}){Text("删除")}},dismissButton={OutlinedButton(onClick={removing=null}){Text("保留")}})}
}
@Composable private fun MotionPreview(clip:MotionClip,modifier:Modifier){var elapsed by remember(clip){mutableLongStateOf(0)};LaunchedEffect(clip){while(isActive){delay(80);elapsed+=80}};PixelThumbnail(clip.frameAt(elapsed,true).pixels,clip.width,clip.height,modifier)}
