package studio.cyberbeads.feature.pixel

import android.app.Application
import androidx.activity.compose.*
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.*
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.*
import studio.cyberbeads.core.data.*
import studio.cyberbeads.core.model.*
import studio.cyberbeads.core.ui.*

@Composable fun PixelScreen(store:CreativeStore,artworks:ArtworkStore,projectId:String?,sourceId:String?,action:String?,clipId:String?,blank:Boolean,session:Int=0,onBack:()->Unit,onPublished:(String)->Unit){
    val context=LocalContext.current;val scope=rememberCoroutineScope();val focus=LocalFocusManager.current
    val key=listOf(projectId,sourceId,action,clipId,blank,session).joinToString(":")
    val vm:PixelViewModel=viewModel(key=key,factory=remember(key){object:ViewModelProvider.Factory{@Suppress("UNCHECKED_CAST") override fun <T:ViewModel> create(c:Class<T>):T=PixelViewModel(context.applicationContext as Application,store,artworks,projectId,sourceId,action,clipId,blank) as T}})
    val dirty by vm.dirty.collectAsState();val brushSize by vm.brushSize.collectAsState();val region by vm.region.collectAsState();var exitPrompt by remember{mutableStateOf(false)};var resizeW by remember{mutableStateOf("")};var resizeH by remember{mutableStateOf("")};var deleteBank by remember{mutableStateOf<ColorBank?>(null)}
    val project by vm.project.collectAsState();val selected by vm.selected.collectAsState();val color by vm.color.collectAsState();val ref by vm.reference.collectAsState();val error by vm.error.collectAsState();val busy by vm.busy.collectAsState();val status by vm.status.collectAsState();val undo by vm.undoCount.collectAsState();val redo by vm.redoCount.collectAsState();val banks by store.palettes.collectAsState()
    val symmetry by vm.symmetry.collectAsState();val clipboard by vm.clipboard.collectAsState();var checker by remember{mutableStateOf(true)};var paletteTab by remember{mutableIntStateOf(0)};var subTools by remember{mutableStateOf("symmetry")};var nameFocused by remember{mutableStateOf(false)}
    var tool by remember{mutableStateOf("draw")};var onion by remember{mutableStateOf(true)};var playing by remember{mutableStateOf(false)};var preview by remember{mutableIntStateOf(0)};var dialog by remember{mutableStateOf<String?>(null)};var more by remember{mutableStateOf(false)};var paletteName by remember{mutableStateOf("我的色卡")};var paletteText by remember{mutableStateOf("#FF9FC4 #FFFFFF #4C344B")};var flags by remember{mutableIntStateOf(0)}
    fun task(block:suspend ()->Unit){scope.launch{vm.busy.value=true;vm.error.value=null;try{block()}catch(e:CancellationException){throw e}catch(e:Exception){vm.error.value=e.message?:"操作未完成"}finally{vm.busy.value=false}}}
    fun leave(){playing=false;if(dirty)exitPrompt=true else onBack()}
    BackHandler{leave()}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()){it?.let(vm::importReference)}
    val palettePicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null)task{val text=withContext(Dispatchers.IO){context.contentResolver.openInputStream(uri)?.use{stream->val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(4096);while(true){val n=stream.read(buffer);if(n<0)break;out.write(buffer,0,n);require(out.size()<=262144){"色卡文件不能超过 256 KB"}};val bytes=out.toByteArray();require(bytes.size<=262144){"色卡文件不能超过 256 KB"};String(bytes,Charsets.UTF_8)}?:kotlin.error("无法读取色卡")};val colors=PixelOps.importPalette(text);store.savePalette(paletteName,colors);vm.palette(colors);dialog=null}}
    LaunchedEffect(playing,project?.frames){if(playing){preview=selected;while(isActive){val p=project?:break;delay(p.frames[preview.coerceIn(p.frames.indices)].durationMs.toLong());preview=(preview+1)%p.frames.size}}}
    val p=project
    val animated=vm.action!=null
    val furnitureMode=p?.id?.startsWith("furniture-")==true
    var petName by remember{mutableStateOf("")};var existingPet by remember{mutableStateOf(false)}
    LaunchedEffect(p?.artworkId){val art=p?.artworkId?.let{artworks.get(it)};petName=art?.name.orEmpty();existingPet=art?.isComplete==true}
    var picking by remember{mutableStateOf(false)}
    BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal=10.dp)){
        val wide=maxWidth>maxHeight && maxWidth>=600.dp
        val panelHeight=minOf(if(animated)244.dp else 176.dp,maxHeight*.44f)
        Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(4.dp)){
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)){
                ToolKey(Icons.Outlined.ArrowBack,"返回",enabled=!busy){leave()}
                Column(Modifier.weight(1f)){if(animated)Text("$petName · ${vm.actionLabel}",fontSize=10.sp,color=Muted,maxLines=1);OutlinedTextField(p?.name.orEmpty(),vm::rename,singleLine=true,label={Text(if(animated)"动作名称" else "作品名称")},textStyle=MaterialTheme.typography.bodyMedium,modifier=Modifier.fillMaxWidth().onFocusChanged{state->if(nameFocused&&!state.isFocused)scope.launch{runCatching{vm.commitName()}.onFailure{vm.error.value=it.message}};nameFocused=state.isFocused})}
                ToolKey(Icons.Outlined.Save,"保存草稿",enabled=!busy){task{vm.save()}}
                Box{ToolKey(Icons.Outlined.MoreHoriz,"更多选项"){more=true};DropdownMenu(more,{more=false}){
                    (listOf("size" to "画布宽高","palette" to "色卡管理","layers" to "描色底图")).forEach{(id,label)->DropdownMenuItem(text={Text(label)},onClick={if(id=="layers")subTools=if(subTools=="layers")"" else "layers" else dialog=id;more=false})}
                }}
            }
            if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            if(p!=null){
                val tools:@Composable (Boolean)->Unit={_->
                    EditorTools(if(tool=="move")"select" else tool,playing,undo,redo,{id->
                        focus.clearFocus()
                        if(id=="board"){checker=!checker;subTools=""}else{subTools=when(id){"select"->"select";"draw"->"symmetry";else->""};tool=id}
                    },{focus.clearFocus();vm.undo()},{focus.clearFocus();vm.redo()},{focus.clearFocus();subTools=if(subTools=="layers")"" else "layers"},false)
                    if(subTools in listOf("select","symmetry"))Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(4.dp)){
                        if(subTools=="select"){
                            ToolKey(Icons.Outlined.CropFree,"框选区域",tool=="select",!playing){tool="select"}
                            ToolKey(Icons.Outlined.OpenWith,"手动移动",tool=="move",!playing){tool="move"}
                            listOf(Triple("horizontal",Icons.Outlined.Flip,"左右翻转"),Triple("vertical",Icons.Outlined.Flip,"上下翻转"),Triple("rotate",Icons.Outlined.RotateRight,"旋转选区"),Triple("copy",Icons.Outlined.ContentCopy,"复制选区"),Triple("paste",Icons.Outlined.ContentPaste,"粘贴选区"),Triple("left",Icons.Outlined.ArrowBack,"选区向左"),Triple("up",Icons.Outlined.ArrowUpward,"选区向上"),Triple("down",Icons.Outlined.ArrowDownward,"选区向下"),Triple("right",Icons.Outlined.ArrowForward,"选区向右")).forEach{(id,icon,label)->ToolKey(icon,label,enabled=!playing&&(id!="paste"||clipboard!=null),rotation=if(id=="vertical")90f else 0f){vm.selectionCommand(id)}}
                        }else listOf("normal" to "正常绘制","horizontal" to "左右对称","vertical" to "上下对称","four" to "四角对称").forEach{(id,label)->FilterChip(symmetry==id,{vm.symmetry.value=id},label={Text(label)})}
                    }
                }
                val canvas:@Composable (Modifier)->Unit={mod->PixelCanvas(p,if(playing)preview.coerceIn(p.frames.indices)else selected,ref?.asImageBitmap(),animated&&onion,tool,playing,vm::beginStroke,vm::stroke,vm::endStroke,vm::tap,mod,region,vm::selectRegion,vm::movePixels,checker,brushSize,{vm.brushSize.value=it})}
                val panel:@Composable ()->Unit={
                    Row(verticalAlignment=Alignment.CenterVertically){OutlinedButton(onClick={picking=true},modifier=Modifier.weight(1f)){Text("自由选色")};if(region!=null)OutlinedButton(onClick={vm.region.value=null}){Text("取消选区")}}
                    ColorStrip("色卡",p.palette,color,{focus.clearFocus();vm.chooseColor(it)},{dialog="palette"},p.colorCodes)
                    ColorStrip("已用色",p.frames[if(animated)selected else 0].pixels.filter{it ushr 24>0}.distinct(),color,{focus.clearFocus();vm.chooseColor(it)})
                    if(animated){
                        Row(verticalAlignment=Alignment.CenterVertically){ToolKey(if(playing)Icons.Outlined.Stop else Icons.Outlined.PlayArrow,if(playing)"停止播放" else "播放动画"){playing=!playing};Text("${if(playing)preview+1 else selected+1}/${p.frames.size} 帧",fontSize=12.sp,modifier=Modifier.weight(1f));ToolKey(Icons.Outlined.Add,"新增空帧",enabled=!playing){vm.addFrame(false)};ToolKey(Icons.Outlined.ContentCopy,"复制帧",enabled=!playing){vm.addFrame(true)};ToolKey(Icons.Outlined.DeleteOutline,"删除当前帧",enabled=!playing&&p.frames.size>1){dialog="deleteFrame"}}
                        FrameTimeline(p,selected,playing,{playing=false;vm.select(it)},vm::reorderFrame)
                        Text("长按帧缩略图，左右拖动调整顺序",fontSize=10.sp,color=Muted)
                        Row(verticalAlignment=Alignment.CenterVertically){ToolKey(Icons.Outlined.ChevronLeft,"当前帧前移",enabled=selected>0&&!playing){vm.moveFrame(-1)};ToolKey(Icons.Outlined.ChevronRight,"当前帧后移",enabled=selected<p.frames.lastIndex&&!playing){vm.moveFrame(1)};CompactChoice("时长 ",p.frames[selected].durationMs,(listOf(80,120,160,180,240,400,800)+p.frames[selected].durationMs).distinct().sorted().map{it to "${it}ms"},vm::duration,Modifier.weight(1f),!playing)}
                    }
                }
                val footer:@Composable ()->Unit={
                    Button(onClick={playing=false;if(furnitureMode)task{vm.save();val f=(context.applicationContext as GardenOwner).garden.saveDrawing(vm.project.value!!);onPublished(f.id)}else if(animated)task{vm.applyMotion()}else dialog="publish"},enabled=!busy,modifier=Modifier.fillMaxWidth()){Text(if(furnitureMode)"完成家饰，加入小园背包" else if(animated)"应用到${vm.actionLabel}动作" else if(existingPet)"更新伙伴形象" else "完成图案，去拼装")}
                    Text(status.ifBlank{if(animated)"当前编辑：${p.name} · 保存草稿后仍需应用动作" else if(existingPet)"保存草稿会同步伙伴形象与默认动作，手绘动作保留。" else "只创作一张静态图案，拼好后可在宠物站设计动作。"},fontSize=10.sp,color=Muted,maxLines=2)
                }
                tools(false)
                if(subTools=="layers")Surface(shape=RoundedCornerShape(12.dp),color=Panel){Column(Modifier.padding(horizontal=10.dp,vertical=4.dp)){
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
                        OutlinedButton(onClick={picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))},contentPadding=PaddingValues(horizontal=10.dp)){Text(if(ref==null)"导入底图" else "更换底图",fontSize=12.sp)}
                        if(ref!=null)IconButton(vm::clearReference){Icon(Icons.Outlined.DeleteOutline,"移除底图")}
                        if(animated){Text("洋葱皮",fontSize=12.sp);Switch(onion,{onion=it})}
                    }
                    if(ref!=null)Row(verticalAlignment=Alignment.CenterVertically){Text("底图 ${(p.referenceOpacity*100).toInt()}%",fontSize=11.sp);Slider(p.referenceOpacity,vm::opacity,Modifier.weight(1f))}
                }}
                if(wide)Row(Modifier.weight(1f),horizontalArrangement=Arrangement.spacedBy(8.dp)){canvas(Modifier.weight(1f).fillMaxHeight());Column(Modifier.width(286.dp).fillMaxHeight(),verticalArrangement=Arrangement.spacedBy(4.dp)){Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(4.dp)){panel()};footer()}}
                else {canvas(Modifier.fillMaxWidth().weight(1f));Column(Modifier.fillMaxWidth().heightIn(max=panelHeight).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(3.dp)){panel()};footer()}
            }
        }
    }
    if(picking)ColorPicker(color,{focus.clearFocus();vm.chooseColor(it)},{picking=false})
    if(exitPrompt)AlertDialog(onDismissRequest={exitPrompt=false},icon={Icon(Icons.Outlined.Save,null)},title={Text("把灵感留在这里 ♡")},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
        Text("保存会更新同一份草稿。放弃只撤销本次未保存的画面修改。")
        Button(onClick={task{vm.save();exitPrompt=false;onBack()}},enabled=!busy,modifier=Modifier.fillMaxWidth()){Text("保存草稿并退出")}
        OutlinedButton(onClick={exitPrompt=false},enabled=!busy,modifier=Modifier.fillMaxWidth()){Text("继续创作")}
        OutlinedButton(onClick={task{vm.discard();exitPrompt=false;onBack()}},enabled=!busy,modifier=Modifier.fillMaxWidth()){Text("放弃本次修改")}
    }},confirmButton={})
    deleteBank?.let{bank->AlertDialog(onDismissRequest={deleteBank=null},title={Text("删除 ${bank.name}？")},text={Text("已有图案的颜色不会改变。")},confirmButton={Button(onClick={task{store.deletePalette(bank.id);deleteBank=null}}){Text("删除色卡")}},dismissButton={OutlinedButton(onClick={deleteBank=null}){Text("取消")}})}
    if(error!=null)AlertDialog(onDismissRequest={vm.error.value=null},title={Text("还差一步")},text={Text(error!!)},confirmButton={OutlinedButton(onClick={vm.error.value=null}){Text("知道了")}})
    if(dialog=="deleteFrame"&&p!=null)AlertDialog(onDismissRequest={dialog=null},title={Text("删除第 ${selected+1} 帧？",style=MaterialTheme.typography.titleMedium)},text={Text("删除后可撤销，至少保留一帧。",fontSize=13.sp)},confirmButton={Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
        OutlinedButton(onClick={dialog=null},modifier=Modifier.weight(1f)){Text("取消")}
        Button(onClick={vm.removeFrame();dialog=null},modifier=Modifier.weight(1f)){Text("删除帧")}
    }})
    if(dialog=="size"&&p!=null){LaunchedEffect(Unit){resizeW=p.width.toString();resizeH=p.height.toString()}
        AlertDialog(onDismissRequest={dialog=null},title={Text("画布尺寸",style=MaterialTheme.typography.titleMedium)},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
                OutlinedTextField(resizeW,{resizeW=it.filter(Char::isDigit).take(3)},label={Text("宽 1–128")},singleLine=true,modifier=Modifier.weight(1f))
                Text("×");OutlinedTextField(resizeH,{resizeH=it.filter(Char::isDigit).take(3)},label={Text("高 1–128")},singleLine=true,modifier=Modifier.weight(1f))
            };Text("图案居中；缩小会裁边，可撤销。",fontSize=12.sp,color=Muted)
        }},confirmButton={Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
            OutlinedButton(onClick={dialog=null},modifier=Modifier.weight(1f)){Text("取消")}
            Button(onClick={val w=resizeW.toIntOrNull()?:0;val h=resizeH.toIntOrNull()?:0;if(w in 1..128&&h in 1..128){vm.resize(w,h);dialog=null}else vm.error.value="宽高需为1–128"},modifier=Modifier.weight(1f)){Text("应用尺寸")}
        }})
    }
    if(dialog!=null&&dialog !in listOf("size","deleteFrame")&&p!=null)AlertDialog(onDismissRequest={dialog=null},title={Text(when(dialog){"deleteFrame"->"删除第 ${selected+1} 帧？";"palette"->"色卡工具箱";"layers"->"底图与洋葱皮";"frame"->"第 ${selected+1} 帧";"publish"->"发布作品";"brush"->"笔触大小";"size"->"画布宽高";else->"名称设置"})},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)){
        when(dialog){
            "palette"->{
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){
                    listOf("选择色卡","新建 / 导入").forEachIndexed{i,t->FilterChip(paletteTab==i,{paletteTab=i},label={Text(t,fontSize=12.sp)},modifier=Modifier.weight(1f))}
                }
                if(paletteTab==0){Text("当前："+(banks.firstOrNull{it.id==p.paletteId}?.name?:"自由配色"),color=Mint,fontSize=12.sp)
                    banks.forEach{bank->OutlinedCard(shape=RoundedCornerShape(12.dp),colors=CardDefaults.outlinedCardColors(containerColor=Panel)){
                        Column(Modifier.padding(horizontal=10.dp,vertical=6.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
                            Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(bank.name,fontSize=13.sp);Text("${bank.colors.size} 色",fontSize=10.sp,color=Muted)}
                                if(bank.id !in store.builtinBanks.map{it.id})IconButton(onClick={deleteBank=bank},modifier=Modifier.size(40.dp)){Icon(Icons.Outlined.DeleteOutline,"删除色卡 ${bank.name}",Modifier.size(18.dp),tint=Muted)}
                                OutlinedButton(onClick={vm.useBank(bank);dialog=null},contentPadding=PaddingValues(horizontal=10.dp)){Text(if(bank.id==p.paletteId)"已选" else "使用",fontSize=12.sp)}
                            }
                            PaletteRibbon(bank.colors)
                        }
                    }}
                }else{
                    OutlinedTextField(paletteName,{paletteName=it.take(30)},label={Text("色卡名称")},singleLine=true,modifier=Modifier.fillMaxWidth())
                    OutlinedTextField(paletteText,{paletteText=it.take(8000)},label={Text("HEX 颜色，用空格分隔")},minLines=2,maxLines=3,modifier=Modifier.fillMaxWidth())
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                        OutlinedButton(onClick={palettePicker.launch(arrayOf("*/*"))},modifier=Modifier.weight(1f)){Text("导入文件",fontSize=12.sp)}
                        Button(onClick={task{val bank=store.savePalette(paletteName,PixelOps.importPalette(paletteText));vm.useBank(bank);dialog=null}},modifier=Modifier.weight(1f)){Text("创建并使用",fontSize=12.sp)}
                    }
                    Text("支持 HEX / TXT / JSON / GPL。品牌色卡会匹配现有画面颜色，自选色卡保留原画。",fontSize=11.sp,color=Muted)
                }
                Text("品牌数据 HansBug/pindou-color-data · MIT",fontSize=10.sp,color=Muted)
            }
            "layers"->{OutlinedButton(onClick={picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))}){Text("导入描色底图")};Text("底图透明度 ${(p.referenceOpacity*100).toInt()}%");Slider(p.referenceOpacity,vm::opacity);if(ref!=null)OutlinedButton(onClick=vm::clearReference){Text("移除底图")};if(animated)Row(verticalAlignment=Alignment.CenterVertically){Text("显示前一帧洋葱皮",Modifier.weight(1f));Switch(onion,{onion=it})};Text("前一帧保留原色并略染玫红，透明显示；当前帧保持实色。选择「移动/缩放」工具可双指缩放；画笔模式按像素描色。",fontSize=12.sp)}
            "publish"->{error?.let{Text(it,color=MaterialTheme.colorScheme.error)};if(busy)LinearProgressIndicator(Modifier.fillMaxWidth());Text(if(p.artworkId!=null)"更新形象：已有宠物保留拼装完成状态，默认动作随新形象重算；手绘动作保留。保存草稿也会同步已有宠物。" else "生成静态拼豆图纸。拼装完成后，进入宠物站为每种互动设计动画。");if(existingPet)Button(enabled=!busy,onClick={task{val id=vm.publish(true);dialog=null;onPublished(id)}}){Text("更新形象与默认动作")}else{Button(enabled=!busy,onClick={task{val id=vm.publish(false);dialog=null;onPublished(id)}}){Text("保存为待拼装图纸")};Button(enabled=!busy,onClick={task{val id=vm.publish(true);dialog=null;onPublished(id)}}){Text("直接拼好，进入宠物站")}}}
            else->{OutlinedTextField(p.name,vm::rename,label={Text("名称")});if(vm.action!=null){Text("所属互动：${vm.actionLabel}");key(flags){Row(verticalAlignment=Alignment.CenterVertically){Text("互动触发后执行",Modifier.weight(1f));Switch(vm.triggered,{vm.flags(it,vm.random);flags++})};Row(verticalAlignment=Alignment.CenterVertically){Text("空闲时随机执行",Modifier.weight(1f));Switch(vm.random,{vm.flags(vm.triggered,it);flags++})}};Text("每次从符合条件的动作组中选一组播放。休息动作不会在空闲时随机触发。",fontSize=12.sp)}}
        }
    }},confirmButton={OutlinedButton(onClick={dialog=null}){Text("完成")}})
}
