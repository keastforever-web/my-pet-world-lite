package studio.cyberbeads.feature.studio

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import studio.cyberbeads.core.data.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import studio.cyberbeads.core.imaging.*
import studio.cyberbeads.core.model.*
import studio.cyberbeads.core.ui.*
import java.io.File

data class StudioState(val bitmap: Bitmap? = null, val pattern: BeadPattern? = null,
    val grid: Int = 32, val rows:Int=32,val brandId:String?=null, val colors: Int = 12, val subjectOnly: Boolean = true, val detailed:Boolean=false, val cutoff:Int=145,
    val busy: Boolean = false, val message: String? = null, val error: String? = null,
    val outlineWidth:Int=0,val outlineColor:Int=0xff493246.toInt(),val outlineHalf:Boolean=false,
    val brush: Int = -2, val canUndo: Boolean = false, val savedId: String? = null)

class StudioViewModel(private val context: Application, private val segmenter: SubjectSegmenter, private val store: ArtworkStore) : ViewModel() {
    private val _state = MutableStateFlow(StudioState())
    val state = _state.asStateFlow()
    private var original: Bitmap? = null
    private var cutout: Bitmap? = null
    private var task: Job? = null;private var generation=0
    private val history = ArrayDeque<BeadPattern>()
    fun import(uri: Uri) {
        task?.cancel();val request=++generation
        task = viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, error = null, message = "正在读取图片…", savedId = null)
            try {
                original = ImageInput.decode(context, uri); cutout = null
                _state.value = _state.value.copy(bitmap = original, pattern = null)
                _state.value = _state.value.copy(message = "正在本机提取主体…")
                cutout = segmenter.isolate(original!!, _state.value.detailed)
                _state.value = _state.value.copy(subjectOnly = true)
                generate()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                _state.value = _state.value.copy(error = "主体提取未完成：${e.message ?: "请重试"}。可以改用保留整图。", message = null)
            } finally { if(request==generation)_state.value = _state.value.copy(busy = false) }
        }
    }
    fun consumed(){generation++;task?.cancel();original=null;cutout=null;history.clear();_state.value=StudioState()}
    fun demo() {
        val file=File(context.cacheDir,"pixel-cat-demo.png")
        context.assets.open("pixel-cat-demo.png").use{input->file.outputStream().use{input.copyTo(it)}}
        import(Uri.fromFile(file))
    }
    fun quality(detailed:Boolean) {
        if(_state.value.busy)return
        _state.value=_state.value.copy(detailed=detailed);cutout=null;if(original!=null)setParameters(subject=true)
    }
    fun setParameters(grid: Int = _state.value.grid, colors: Int = _state.value.colors, subject: Boolean = _state.value.subjectOnly, cutoff:Int=_state.value.cutoff,rows:Int=_state.value.rows,brandId:String?=_state.value.brandId,outlineWidth:Int=_state.value.outlineWidth,outlineColor:Int=_state.value.outlineColor,outlineHalf:Boolean=_state.value.outlineHalf) {
        if (original == null || _state.value.busy) return
        val request=++generation
        val bank=(context as CreativeOwner).creative.palettes.value.firstOrNull{it.id==brandId}
        val mapped=if(bank!=null)CanvasOps.match(listOf(outlineColor),bank).getValue(outlineColor)else outlineColor
        task = viewModelScope.launch {
            _state.value = _state.value.copy(grid = grid, rows=rows,brandId=brandId,outlineWidth=outlineWidth,outlineColor=mapped,outlineHalf=outlineHalf,colors = colors, subjectOnly = subject, cutoff=cutoff, busy = true, error = null, savedId = null)
            try {
                if (subject && cutout == null) cutout = segmenter.isolate(original!!, _state.value.detailed)
                generate()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _state.value = _state.value.copy(error = e.message ?: "转换失败，请重试") }
            finally { if(request==generation)_state.value = _state.value.copy(busy = false) }
        }
    }
    private suspend fun generate() {
        val s = _state.value
        val bitmap = if (s.subjectOnly) cutout ?: error("主体尚未提取，请选择整图") else original ?: error("请先导入图片")
        val pattern = withContext(Dispatchers.Default) {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            val unlined=CanvasOps.fit(BeadQuantizer.convert(pixels, bitmap.width, bitmap.height, maxOf(s.grid,s.rows,8), s.colors,s.cutoff),s.grid,s.rows,if(s.subjectOnly).75 else 1.0)
            val raw=OutlineOps.apply(unlined,s.outlineWidth,s.outlineColor,s.outlineHalf)
            val bank=(context as CreativeOwner).creative.palettes.value.firstOrNull{it.id==s.brandId}
            if(bank!=null)CanvasOps.brand(raw,bank)else raw
        }
        kotlin.coroutines.coroutineContext.ensureActive()
        history.clear()
        _state.value = _state.value.copy(pattern = pattern, brush = -2, canUndo = false,
            message = "${if (s.subjectOnly) "主体" else "整图"}已转为拼豆 · 可手动补豆或擦除", error = null)
    }
    fun brush(color: Int) { _state.value = _state.value.copy(brush = color) }
    fun paint(index: Int) {
        val state = _state.value; val pattern = state.pattern ?: return
        if (state.brush < -1 || state.busy || pattern.cells[index] == state.brush) return
        history.addLast(pattern); if (history.size > 25) history.removeFirst()
        _state.value = state.copy(pattern = pattern.paint(index, state.brush), canUndo = true, savedId = null)
    }
    fun undo() {
        if (history.isNotEmpty()) _state.value = _state.value.copy(pattern = history.removeLast(), canUndo = history.isNotEmpty(), savedId = null)
    }
    fun save(name: String) {
        val pattern = _state.value.pattern ?: return
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, error = null)
            try {
                val item = store.create(name, pattern)
                _state.value = _state.value.copy(savedId = item.id, message = "作品已保存，去作品库开始拼装")
            } catch (e: Exception) { _state.value = _state.value.copy(error = e.message ?: "保存失败，请重试") }
            finally { _state.value = _state.value.copy(busy = false) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable fun StudioScreen(store: ArtworkStore, segmenter: SubjectSegmenter, onCreative:()->Unit={},onEdit:(String)->Unit={},onSettings:()->Unit={},parkEntry:(@Composable ()->Unit)?=null,onLibrary: (String?) -> Unit) {
    val context=LocalContext.current;val creative=(context.applicationContext as CreativeOwner).creative
    val factory=remember { object:ViewModelProvider.Factory { @Suppress("UNCHECKED_CAST") override fun <T:ViewModel> create(modelClass:Class<T>):T=StudioViewModel(context.applicationContext as Application,segmenter,store) as T } }
    val vm:StudioViewModel=viewModel(factory=factory);val state by vm.state.collectAsStateWithLifecycle();val banks by creative.palettes.collectAsStateWithLifecycle()
    LaunchedEffect(Unit){creative.refresh()}
    var canvasW by rememberSaveable{mutableStateOf("32")};var canvasH by rememberSaveable{mutableStateOf("32")};var name by rememberSaveable{mutableStateOf("我的拼豆伙伴")}
    var cameraUri by rememberSaveable{mutableStateOf<String?>(null)};var cameraError by remember{mutableStateOf<String?>(null)};var openCanvas by remember{mutableStateOf(false)}
    var settingsTab by rememberSaveable{mutableIntStateOf(0)};var outlinePicker by remember{mutableStateOf(false)}
    var paletteDialog by remember{mutableStateOf(false)};var outlineHex by rememberSaveable{mutableStateOf("493246")}
    LaunchedEffect(state.savedId){if(openCanvas&&state.savedId!=null){val id=state.savedId!!;openCanvas=false;vm.consumed();onEdit(id)}}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()){uri->if(uri!=null)vm.import(uri)}
    val camera=rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()){ok->if(ok)cameraUri?.let{vm.import(Uri.parse(it))}}
    var nameFocused by remember{mutableStateOf(false)}
    val keyboardVisible=WindowInsets.isImeVisible
    val editingName=keyboardVisible&&nameFocused
    BoxWithConstraints(Modifier.fillMaxSize()){val wide=!keyboardVisible&&maxWidth>maxHeight&&maxWidth>=600.dp
    val previewSize=if(keyboardVisible)minOf(96.dp,maxOf(0.dp,maxHeight-200.dp))else if(wide)maxOf(80.dp,maxHeight-124.dp)else minOf(maxWidth-56.dp,maxOf(80.dp,minOf(maxHeight*.43f,maxHeight-220.dp)))
    Column(Modifier.fillMaxSize().then(if(state.bitmap==null)Modifier.verticalScroll(rememberScrollState())else Modifier).padding(horizontal=22.dp).padding(top=12.dp,bottom=8.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
        if(state.bitmap==null)SectionLabel("PIXEL PLAYGROUND","今天，创造哪位伙伴？","一张照片，或从第一颗像素开始。")
        if(state.bitmap==null)Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
            CreationEntry("导入图片",Icons.Outlined.AddPhotoAlternate,Modifier.weight(1f),!state.busy){picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))}
            CreationEntry("拍一张",Icons.Outlined.PhotoCamera,Modifier.weight(1f),!state.busy){try{val folder=File(context.cacheDir,"captures").apply{mkdirs()};folder.listFiles()?.sortedByDescending{it.lastModified()}?.drop(4)?.forEach{it.delete()};val uri=FileProvider.getUriForFile(context,context.packageName+".files",File.createTempFile("capture-",".jpg",folder));cameraUri=uri.toString();cameraError=null;camera.launch(uri)}catch(_:Exception){cameraError="没有可用相机，请导入图片"}}
            CreationEntry("自由绘画",Icons.Outlined.Brush,Modifier.weight(1f),!state.busy,onCreative)
        }
        if(state.bitmap==null&&!state.busy)OutlinedButton(onClick={name="薄荷像素猫";vm.demo()},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp)){Text("先用像素猫体验一下  →")}
        if(state.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        (state.error?:cameraError)?.let{Text(it,color=MaterialTheme.colorScheme.error,fontSize=12.sp)}
        if(state.bitmap!=null){
            if(!editingName)Row(verticalAlignment=Alignment.CenterVertically){Text("图片工作台",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium);OutlinedButton(onClick={vm.consumed();cameraError=null},modifier=Modifier.height(36.dp),contentPadding=PaddingValues(horizontal=10.dp),shape=RoundedCornerShape(12.dp)){Icon(Icons.Outlined.Close,null,Modifier.size(16.dp));Text("移除图片")}}
            // Keep the focused input in one composition slot across rotation and IME changes.
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
                CompactInput(name,{name=it.take(40)},"作品名称",Modifier.weight(1f),onFocus={nameFocused=it})
                Button(onClick={openCanvas=true;vm.save(name)},enabled=!state.busy&&(state.pattern?.beadCount?:0)>0,modifier=Modifier.width(100.dp).height(48.dp),contentPadding=PaddingValues(8.dp),shape=RoundedCornerShape(14.dp)){Text("进入画板 →",fontSize=12.sp)}
            }
            val preview:@Composable (Modifier,androidx.compose.ui.unit.Dp)->Unit={mod,side->state.pattern?.let{pattern->Surface(modifier=mod,shape=RoundedCornerShape(18.dp),color=Panel,tonalElevation=1.dp){Column(Modifier.padding(4.dp),verticalArrangement=Arrangement.spacedBy(2.dp)){
                Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center){BeadBoard(pattern,Modifier.size(side).testTag("studio-preview"),onCell=if(editingName)null else ({vm.paint(it)}),zoomable=!editingName)
                if(side>=110.dp)Text("${pattern.width} × ${pattern.height} · ${pattern.palette.size} 色",fontSize=10.sp,lineHeight=12.sp,color=Muted,modifier=Modifier.align(Alignment.TopStart).padding(8.dp))}
            }}}
            }
            val controls:@Composable ColumnScope.()->Unit={
            if(!editingName){
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){
                listOf("主体 · 描边","画布 · 配色").forEachIndexed{i,label->OutlinedButton(onClick={settingsTab=i},modifier=Modifier.weight(1f),contentPadding=PaddingValues(4.dp),shape=RoundedCornerShape(10.dp),colors=ButtonDefaults.outlinedButtonColors(containerColor=if(settingsTab==i)MaterialTheme.colorScheme.primaryContainer else Panel)){Text(label,fontSize=12.sp)}}
            }
            Box(Modifier.fillMaxWidth().then(if(wide)Modifier else Modifier.weight(1f))){
            Column(Modifier.fillMaxWidth().then(if(wide)Modifier else Modifier.fillMaxSize().verticalScroll(rememberScrollState())),verticalArrangement=Arrangement.spacedBy(6.dp)){
            if(settingsTab==0)OutlinedCard(shape=RoundedCornerShape(14.dp),colors=CardDefaults.outlinedCardColors(containerColor=Panel)){Column(Modifier.padding(8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    CompactChoice("",state.subjectOnly,listOf(true to "只保留主体",false to "保留整图"),{vm.setParameters(subject=it)},Modifier.weight(1f),!state.busy)
                    CompactChoice("",state.detailed,listOf(false to "快速提取",true to "精细提取"),{fine->if(fine&&!OptionalModel(context).available())cameraError="精细提取需要到设置准备模型" else vm.quality(fine)},Modifier.weight(1f),!state.busy)
                }
                Row(horizontalArrangement=Arrangement.spacedBy(4.dp)){
                    CompactChoice("",state.cutoff,listOf(90 to "细节边缘",145 to "自然边缘",200 to "收紧边缘"),{vm.setParameters(cutoff=it)},Modifier.weight(1f),!state.busy)
                    CompactChoice("",state.outlineWidth,(0..4).map{it to if(it==0)"不描边" else "描边 $it 格"},{vm.setParameters(outlineWidth=it)},Modifier.weight(1f),!state.busy)
                    CompactChoice("",state.outlineHalf,listOf(false to "全描边",true to "半描边"),{vm.setParameters(outlineHalf=it)},Modifier.weight(1f),!state.busy)
                }
                if(state.outlineWidth>0)OutlinedButton(onClick={outlinePicker=true},enabled=!state.busy,modifier=Modifier.fillMaxWidth().height(36.dp),contentPadding=PaddingValues(4.dp),shape=RoundedCornerShape(10.dp)){
                    Box(Modifier.size(18.dp).background(Color(state.outlineColor),RoundedCornerShape(4.dp)));Spacer(Modifier.width(8.dp));Text("描边颜色 · "+(banks.firstOrNull{it.id==state.brandId}?.name?:"自由 RGB"),fontSize=11.sp)
                }
                if(cameraError?.contains("模型")==true)OutlinedButton(onClick=onSettings){Text("去设置准备精细模型")}
            }}
            if(settingsTab==1)OutlinedCard(shape=RoundedCornerShape(14.dp),colors=CardDefaults.outlinedCardColors(containerColor=Panel)){Column(Modifier.padding(8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically){
                    CompactInput(canvasW,{canvasW=it.filter(Char::isDigit).take(3)},"宽 1–128",Modifier.weight(1f))
                    CompactInput(canvasH,{canvasH=it.filter(Char::isDigit).take(3)},"高 1–128",Modifier.weight(1f))
                OutlinedButton(onClick={val w=canvasW.toIntOrNull()?:0;val h=canvasH.toIntOrNull()?:0;if(w in 1..128&&h in 1..128)vm.setParameters(grid=w,rows=h)else cameraError="宽高需为1–128"},enabled=!state.busy,modifier=Modifier.height(48.dp),contentPadding=PaddingValues(horizontal=8.dp),shape=RoundedCornerShape(12.dp)){Icon(Icons.Outlined.CenterFocusStrong,null,Modifier.size(16.dp));Spacer(Modifier.width(6.dp));Text("应用",fontSize=12.sp)}
                }
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    OutlinedButton(onClick={paletteDialog=true},enabled=!state.busy,shape=RoundedCornerShape(12.dp),modifier=Modifier.weight(1f).height(40.dp),contentPadding=PaddingValues(horizontal=8.dp,vertical=2.dp)){Column{Text("色卡",fontSize=10.sp,lineHeight=12.sp,color=Muted);Text(banks.firstOrNull{it.id==state.brandId}?.name?:"自由 RGB",fontSize=12.sp,lineHeight=16.sp,maxLines=1,overflow=TextOverflow.Ellipsis)}}
                    CompactChoice("",state.colors,listOf(8,12,20,32).map{it to "$it 色"},{vm.setParameters(colors=it)},Modifier.weight(1f),!state.busy)
                }
                banks.firstOrNull{it.id==state.brandId}?.let{PaletteRibbon(it.colors)}
            }
            }
            }}
            }else{Spacer(Modifier.weight(1f))}
            }
            if(editingName)BoxWithConstraints(Modifier.fillMaxWidth().weight(1f),contentAlignment=Alignment.Center){
                // Measure the space actually left by the input and IME, including large fonts.
                val side=(minOf(maxWidth,maxHeight)-8.dp).coerceAtLeast(1.dp)
                preview(Modifier.size(side+8.dp),side)
            }
            else if(wide)Row(Modifier.fillMaxWidth().weight(1f),horizontalArrangement=Arrangement.spacedBy(12.dp)){preview(Modifier.weight(1f),previewSize);Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(6.dp)){controls()}}
            else{if(previewSize>0.dp)preview(Modifier.width(previewSize+8.dp).align(Alignment.CenterHorizontally),previewSize);controls()}
        }
        if(state.bitmap==null){Text("图片仅在本机处理 · 与画板共用色卡 · 品牌色号随图纸保存",color=Muted,fontSize=11.sp);Spacer(Modifier.height(12.dp));parkEntry?.invoke()}
    }}
    if(outlinePicker){val bank=banks.firstOrNull{it.id==state.brandId};val colors=bank?.colors?:PixelOps.starterPalette
        AlertDialog(onDismissRequest={outlinePicker=false},title={Text(bank?.name?:"描边颜色")},text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
            LazyVerticalGrid(columns=GridCells.Adaptive(42.dp),modifier=Modifier.fillMaxWidth().height(230.dp),horizontalArrangement=Arrangement.spacedBy(5.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){
                items(colors.size){i->Column(horizontalAlignment=Alignment.CenterHorizontally){Box(Modifier.size(40.dp).background(Color(colors[i]),RoundedCornerShape(8.dp)).clickable{vm.setParameters(outlineColor=colors[i]);outlinePicker=false});Text(bank?.codes?.getOrNull(i)?:"${i+1}",fontSize=9.sp)}}
            }
            if(bank==null){OutlinedTextField(outlineHex,{v->outlineHex=v.filter{it in "0123456789abcdefABCDEF"}.take(6)},label={Text("自由色 #RRGGBB")},singleLine=true);OutlinedButton(onClick={vm.setParameters(outlineColor=PixelOps.hex(outlineHex));outlinePicker=false},enabled=outlineHex.length==6){Text("使用自由色")}}
        }},confirmButton={OutlinedButton(onClick={outlinePicker=false}){Text("关闭")}})
    }
    if(paletteDialog)AlertDialog(onDismissRequest={paletteDialog=false},title={Text("选择配色板")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
        OutlinedButton(onClick={vm.setParameters(brandId=null);paletteDialog=false},modifier=Modifier.fillMaxWidth()){Text("自由 RGB")}
        banks.forEach{bank->OutlinedCard(onClick={vm.setParameters(brandId=bank.id);paletteDialog=false},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp)){Column(Modifier.padding(10.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){Text(bank.name,fontSize=13.sp);PaletteRibbon(bank.colors);Text("${bank.colors.size}色 · ${if(bank.brand)"品牌色号" else "自选配色"}",fontSize=10.sp,color=Muted)}}}
    }},confirmButton={OutlinedButton(onClick={paletteDialog=false}){Text("关闭")}})
}
@Composable private fun CreationEntry(label:String,icon:androidx.compose.ui.graphics.vector.ImageVector,modifier:Modifier,enabled:Boolean,onClick:()->Unit){
    OutlinedCard(onClick=onClick,enabled=enabled,modifier=modifier,shape=RoundedCornerShape(16.dp),colors=CardDefaults.outlinedCardColors(containerColor=Panel)){
        Column(Modifier.fillMaxWidth().padding(vertical=14.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)){Icon(icon,null,Modifier.size(24.dp),tint=Mint);Text(label,fontSize=12.sp,fontWeight=FontWeight.Medium)}
    }
}
