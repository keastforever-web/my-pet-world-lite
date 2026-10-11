package studio.cyberbeads.feature.pixel

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import studio.cyberbeads.core.model.*
import studio.cyberbeads.core.ui.*
import kotlin.math.roundToInt

private val EraserIcon=ImageVector.Builder("Eraser",24.dp,24.dp,24f,24f).apply{
    path(fill=null,stroke=SolidColor(Color.Black),strokeLineWidth=1.8f,strokeLineJoin=StrokeJoin.Round){moveTo(3f,14f);lineTo(13f,4f);lineTo(21f,12f);lineTo(13f,20f);lineTo(9f,20f);close();moveTo(8f,9f);lineTo(16f,17f);moveTo(11f,20f);lineTo(22f,20f)}
}.build()

@Composable internal fun ToolKey(icon:ImageVector,label:String,selected:Boolean=false,enabled:Boolean=true,rotation:Float=0f,onClick:()->Unit){
    val focus=LocalFocusManager.current
    OutlinedIconButton({focus.clearFocus();onClick()},enabled=enabled,modifier=Modifier.size(42.dp),shape=RoundedCornerShape(12.dp),colors=IconButtonDefaults.outlinedIconButtonColors(containerColor=if(selected)MaterialTheme.colorScheme.primaryContainer else Panel)){
        Icon(icon,label,Modifier.size(20.dp).graphicsLayer{rotationZ=rotation})
    }
}
@Composable internal fun EditorTools(tool:String,playing:Boolean,undo:Int,redo:Int,onTool:(String)->Unit,onUndo:()->Unit,onRedo:()->Unit,onLayers:()->Unit,vertical:Boolean,onMirror:()->Unit={},onShift:()->Unit={}){
    val content:@Composable ()->Unit={
        listOf(Triple("draw",Icons.Outlined.Edit,"画笔"),Triple("erase",EraserIcon,"橡皮"),Triple("fill",Icons.Outlined.FormatColorFill,"填充"),Triple("pick",Icons.Outlined.Colorize,"吸色"),Triple("select",Icons.Outlined.CropFree,"框选区域"),Triple("pan",Icons.Outlined.PanTool,"移动缩放视野")).forEach{(id,icon,label)->ToolKey(icon,label,tool==id,!playing){onTool(id)}}
        ToolKey(Icons.Outlined.GridOn,"画板显示方式",enabled=!playing){onTool("board")}
        ToolKey(Icons.Outlined.Undo,"撤销",enabled=undo>0&&!playing,onClick=onUndo);ToolKey(Icons.Outlined.Redo,"重做",enabled=redo>0&&!playing,onClick=onRedo);ToolKey(Icons.Outlined.Layers,"描色底图",onClick=onLayers)
    }
    if(vertical)Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(4.dp)){content()}
    else Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(4.dp)){content()}
}

@Composable internal fun ColorStrip(label:String,colors:List<Int>,selected:Int,onColor:(Int)->Unit,onPicker:(()->Unit)?=null,codes:List<String> = emptyList()){
    Column { Canvas(Modifier.fillMaxWidth().height(10.dp)){if(colors.isNotEmpty())colors.forEachIndexed{i,c->drawRect(Color(c),Offset(i*size.width/colors.size,0f),Size(size.width/colors.size+1,size.height))}}
    Row(verticalAlignment=Alignment.CenterVertically){Text(label,fontSize=11.sp,color=Muted,modifier=Modifier.width(48.dp))
        LazyRow(Modifier.weight(1f).height(48.dp).border(1.dp,Border,RoundedCornerShape(10.dp)).padding(4.dp),horizontalArrangement=Arrangement.spacedBy(4.dp)){
            if(colors.isEmpty())item{Text("还没有落笔",fontSize=11.sp,modifier=Modifier.padding(6.dp))}
            itemsIndexed(colors){index,c->Box(Modifier.size(38.dp).background(Color(c),RoundedCornerShape(7.dp)).border(if(c==selected)3.dp else 1.dp,if(c==selected)Mint else Border,RoundedCornerShape(7.dp)).semantics{contentDescription=(codes.getOrNull(index)?.plus(" ")?:"")+"颜色 #"+String.format("%06X",c and 0xffffff);this.selected=c==selected}.clickable{onColor(c)},contentAlignment=Alignment.Center){if(codes.isNotEmpty()||c==selected)Text(codes.getOrNull(index)?:"✓",fontSize=9.sp,color=if(android.graphics.Color.luminance(c)>.5f)Color.Black else Color.White)}}
        };if(onPicker!=null)ToolKey(Icons.Outlined.Palette,"色卡工具箱",onClick=onPicker)
    }}
}

@Composable internal fun ColorPicker(initial:Int,onPick:(Int)->Unit,onDismiss:()->Unit){
    val start=remember{FloatArray(3).also{android.graphics.Color.colorToHSV(initial,it)}}
    var hue by remember{mutableFloatStateOf(start[0])};var sat by remember{mutableFloatStateOf(start[1])};var value by remember{mutableFloatStateOf(start[2])}
    val chosen=android.graphics.Color.HSVToColor(floatArrayOf(hue,sat,value));var hex by remember{mutableStateOf(String.format("%06X",initial and 0xffffff))}
    LaunchedEffect(chosen){hex=String.format("%06X",chosen and 0xffffff)}
    AlertDialog(onDismissRequest=onDismiss,title={Text("自由选色",style=MaterialTheme.typography.titleMedium)},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
        Canvas(Modifier.fillMaxWidth().height(148.dp).semantics{contentDescription="饱和度与明度色盘"}.pointerInput(hue){detectDragGestures(onDragStart={o->sat=(o.x/size.width).coerceIn(0f,1f);value=(1-o.y/size.height).coerceIn(0f,1f)}){change,_->change.consume();sat=(change.position.x/size.width).coerceIn(0f,1f);value=(1-change.position.y/size.height).coerceIn(0f,1f)}}.pointerInput(hue){detectTapGestures{o->sat=(o.x/size.width).coerceIn(0f,1f);value=(1-o.y/size.height).coerceIn(0f,1f)}}){
            drawRect(Brush.horizontalGradient(listOf(Color.White,Color.hsv(hue,1f,1f))));drawRect(Brush.verticalGradient(listOf(Color.Transparent,Color.Black)));drawCircle(Color.White,7.dp.toPx(),Offset(sat*size.width,(1-value)*size.height),style=androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
        }
        Text("色相",fontSize=12.sp);Box(Modifier.background(Brush.horizontalGradient((0..6).map{Color.hsv((it*60f).coerceAtMost(359.9f),1f,1f)}),RoundedCornerShape(24.dp))){Slider(hue,{hue=it},valueRange=0f..359.9f,colors=SliderDefaults.colors(activeTrackColor=Color.Transparent,inactiveTrackColor=Color.Transparent,thumbColor=Color.White))}
        Row(verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(48.dp).background(Color(chosen),RoundedCornerShape(10.dp)).border(1.dp,Border,RoundedCornerShape(10.dp)));Spacer(Modifier.width(10.dp));OutlinedTextField(hex,{s->hex=s.filter{it in "0123456789aAbBcCdDeEfF"}.take(6);if(hex.length==6){val hsv=FloatArray(3);android.graphics.Color.colorToHSV((0xff000000L or hex.toLong(16)).toInt(),hsv);hue=hsv[0];sat=hsv[1];value=hsv[2]}},label={Text("HEX 色值")},singleLine=true)}
    }},confirmButton={Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton(onClick=onDismiss,modifier=Modifier.weight(1f)){Text("取消")};Button(onClick={onPick(chosen);onDismiss()},modifier=Modifier.weight(1f)){Text("使用颜色")}}})
}

@Composable internal fun FrameTimeline(p:PixelProject,selected:Int,playing:Boolean,onSelect:(Int)->Unit,onMove:(Int,Int)->Unit){
    val step=with(LocalDensity.current){72.dp.toPx()};var dragging by remember{mutableIntStateOf(-1)};var delta by remember{mutableFloatStateOf(0f)}
    val latestMove by rememberUpdatedState(onMove)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical=4.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)){
        p.frames.forEachIndexed{i,f->Column(Modifier.width(66.dp).graphicsLayer{translationX=if(dragging==i)delta else 0f;alpha=if(dragging==i).7f else 1f}.border(if(i==selected)2.dp else 1.dp,if(i==selected)Mint else Border,RoundedCornerShape(8.dp)).semantics{contentDescription="第 ${i+1} 帧，长按拖动排序"}.clickable(enabled=!playing){onSelect(i)}.pointerInput(i,p.frames.size,playing){if(!playing)detectDragGesturesAfterLongPress(onDragStart={dragging=i;delta=0f},onDragCancel={dragging=-1;delta=0f},onDragEnd={val to=(i+(delta/step).roundToInt()).coerceIn(p.frames.indices);latestMove(i,to);dragging=-1;delta=0f}){c,d->c.consume();delta+=d.x}}.padding(4.dp),horizontalAlignment=Alignment.CenterHorizontally){PixelThumbnail(f.pixels,p.width,p.height,Modifier.size(54.dp));Text("${i+1} · ${f.durationMs}ms",fontSize=10.sp)}}
    }
}
