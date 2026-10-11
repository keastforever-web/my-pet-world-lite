package studio.cyberbeads.feature.pixel

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import studio.cyberbeads.core.data.CreativeStore
import studio.cyberbeads.core.model.*
import studio.cyberbeads.core.ui.*
import java.util.UUID

@Composable fun PixelHome(store:CreativeStore,onBack:()->Unit,onOpen:(String)->Unit){
    BackHandler{onBack()}
    val projects by store.projects.collectAsState();val scope=rememberCoroutineScope()
    var width by rememberSaveable{mutableStateOf("32")};var height by rememberSaveable{mutableStateOf("32")};var tab by rememberSaveable{mutableStateOf("shape")}
    var error by remember{mutableStateOf<String?>(null)};var deleting by remember{mutableStateOf<String?>(null)}
    LaunchedEffect(Unit){runCatching{store.refresh()}.onFailure{error="草稿读取失败，原文件已保留"}}
    val visibleProjects=projects.filter{!it.id.startsWith("food-")&&!it.id.startsWith("furniture-")};val shown=visibleProjects.filter{when(tab){"shape"->it.motionAction==null&&!it.id.startsWith("food-");else->it.motionAction!=null}}
    LazyVerticalGrid(columns=GridCells.Adaptive(140.dp),modifier=Modifier.fillMaxSize().padding(horizontal=16.dp),horizontalArrangement=Arrangement.spacedBy(10.dp),verticalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(bottom=24.dp)){
        item(span={GridItemSpan(maxLineSpan)}){Row(verticalAlignment=Alignment.CenterVertically){OutlinedButton(onClick=onBack,shape=RoundedCornerShape(12.dp)){Text("← 返回创作")};Text("自由创作",Modifier.padding(start=12.dp),style=MaterialTheme.typography.titleLarge)}}
        item(span={GridItemSpan(maxLineSpan)}){CompactPanel("新建形象 · 单帧画布"){
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically){
                CompactInput(width,{width=it.filter(Char::isDigit).take(3)},"宽 1–128",Modifier.weight(1f))
                CompactInput(height,{height=it.filter(Char::isDigit).take(3)},"高 1–128",Modifier.weight(1f))
            Button(onClick={val w=width.toIntOrNull()?:0;val h=height.toIntOrNull()?:0;if(w in 1..128&&h in 1..128)onOpen("blank-$w-$h-${UUID.randomUUID()}")else error="宽高均需为1–128"},modifier=Modifier.height(48.dp),contentPadding=PaddingValues(horizontal=12.dp),shape=RoundedCornerShape(12.dp)){Text("新建",fontSize=12.sp)}
            }
        }}
        item(span={GridItemSpan(maxLineSpan)}){Column{
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){FilterChip(tab=="shape",{tab="shape"},label={Text("形象 · ${visibleProjects.count{it.motionAction==null&&!it.id.startsWith("food-")}}",fontSize=11.sp)});FilterChip(tab=="motion",{tab="motion"},label={Text("动作 · ${visibleProjects.count{it.motionAction!=null}}",fontSize=11.sp)})}
            error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
            if(shown.isEmpty())Text(if(tab=="shape")"第一颗像素，等你落笔。" else "在宠物站选择互动，设计它的小动作。",Modifier.padding(vertical=16.dp),color=Muted,fontSize=12.sp)
        }}
        items(shown,key={it.id}){draft->OutlinedCard(onClick={onOpen(draft.id)},shape=RoundedCornerShape(16.dp),colors=CardDefaults.outlinedCardColors(containerColor=Panel)){
            Column(Modifier.padding(8.dp),verticalArrangement=Arrangement.spacedBy(3.dp)){
                PixelThumbnail(draft.preview,draft.width,draft.height,Modifier.fillMaxWidth().height(62.dp))
                Text(draft.name,maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.titleSmall)
                Text("${draft.width}×${draft.height} · ${draft.motionAction?.let{MotionDefaults.label(it)}?:"静态形象"} · ${draft.frames}帧",fontSize=10.sp,color=Muted,maxLines=1)
                Row(verticalAlignment=Alignment.CenterVertically){Text(java.text.SimpleDateFormat("MM-dd HH:mm",androidx.compose.ui.platform.LocalConfiguration.current.locales[0]).format(java.util.Date(draft.updated)),Modifier.weight(1f),fontSize=10.sp,color=Muted);IconButton(onClick={deleting=draft.id},modifier=Modifier.size(40.dp)){Icon(Icons.Outlined.DeleteOutline,"删除草稿 ${draft.name}",Modifier.size(18.dp))}}
            }
        }}
    }
    deleting?.let{id->AlertDialog(onDismissRequest={deleting=null},title={Text("删除这份草稿？")},text={Text("已生成的作品与宠物动作不会被删除。")},confirmButton={OutlinedButton(onClick={scope.launch{runCatching{store.delete(id)}.onFailure{error=it.message};deleting=null}}){Text("删除")}},dismissButton={OutlinedButton(onClick={deleting=null}){Text("保留")}})}
}
