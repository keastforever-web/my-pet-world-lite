package studio.cyberbeads.feature.pixel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import studio.cyberbeads.core.model.*
import studio.cyberbeads.core.ui.*
import kotlin.math.*

@Composable fun PixelThumbnail(pixels:List<Int>,width:Int,height:Int,modifier:Modifier=Modifier){
 val image=remember(pixels,width,height){android.graphics.Bitmap.createBitmap(pixels.toIntArray(),width,height,android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()}
 Canvas(modifier){val c=min(size.width/width,size.height/height);val w=(c*width).roundToInt();val h=(c*height).roundToInt();drawImage(image,dstOffset=IntOffset(((size.width-w)/2).roundToInt(),((size.height-h)/2).roundToInt()),dstSize=IntSize(w,h),filterQuality=FilterQuality.None)}
}

@Composable fun PixelCanvas(p:PixelProject,frame:Int,reference:ImageBitmap?,onion:Boolean,tool:String,playing:Boolean,onBegin:()->Unit,onStroke:(Int,Int,Boolean)->Unit,onEnd:()->Unit,onTap:(Int,String)->Unit,modifier:Modifier=Modifier,region:PixelRegion?=null,onRegion:(Int,Int)->Unit={_,_->},onMove:(Int,Int)->Unit={_,_->},checker:Boolean=true,brushSize:Int=1,onBrush:(Int)->Unit={}){
    var zoom by remember(p.id){mutableFloatStateOf(1f)};var pan by remember(p.id){mutableStateOf(Offset.Zero)};var board by remember{mutableStateOf(IntSize.Zero)}
    var moveDelta by remember(p.id){mutableStateOf<IntOffset?>(null)}
    val latestSelection by rememberUpdatedState(region)
    val latestRegion by rememberUpdatedState(onRegion);val latestMove by rememberUpdatedState(onMove)
    val latestStroke by rememberUpdatedState(onStroke);val latestTap by rememberUpdatedState(onTap);val latestBegin by rememberUpdatedState(onBegin);val latestEnd by rememberUpdatedState(onEnd)
    val brushTrackHeight=with(LocalDensity.current){(board.height.toDp()-96.dp).coerceIn(24.dp,100.dp)}
    val codes=remember(p.palette,p.frames[frame].pixels){(p.palette+p.frames[frame].pixels.filter{it ushr 24>0}).distinct().withIndex().associate{it.value to it.index+1}}
    val ink=MaterialTheme.colorScheme.onSurface;val surface=MaterialTheme.colorScheme.surfaceVariant;val accent=Mint
    val frameImage=remember(p.frames[frame].pixels,p.width,p.height){android.graphics.Bitmap.createBitmap(p.frames[frame].pixels.toIntArray(),p.width,p.height,android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()}
    val checkerImage=remember(p.width,p.height,ink){val light=Color.White.copy(alpha=.5f).toArgb();val dark=ink.copy(alpha=.07f).toArgb();android.graphics.Bitmap.createBitmap(IntArray(p.width*p.height){i->if((i%p.width+i/p.width)%2==0)light else dark},p.width,p.height,android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()}
    val onionImage=remember(p.frames,frame,onion,playing){if(onion&&!playing&&frame>0)android.graphics.Bitmap.createBitmap(p.frames[frame-1].pixels.map{v->if(v ushr 24>0)lerp(Color(v),Color(0xffdf5e9e),.25f).copy(alpha=.38f).toArgb()else 0}.toIntArray(),p.width,p.height,android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()else null}
    fun limit(v:Offset,z:Float)=Offset(v.x.coerceIn(-board.width*(z-1)/2,board.width*(z-1)/2),v.y.coerceIn(-board.height*(z-1)/2,board.height*(z-1)/2))
    fun indexAt(point:Offset,size:IntSize):Int?{val c=min(size.width.toFloat()/p.width,size.height.toFloat()/p.height)*.92f;val center=Offset(size.width/2f,size.height/2f);val q=(point-center-pan)/zoom+center;val x=floor((q.x-(size.width-c*p.width)/2)/c).toInt();val y=floor((q.y-(size.height-c*p.height)/2)/c).toInt();return if(x in 0 until p.width&&y in 0 until p.height)y*p.width+x else null}
    Box(modifier){
        Canvas(Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)).background(surface).onSizeChanged{board=it}.semantics{contentDescription="自由创作画布，${p.width}乘${p.height}，第${frame+1}帧";stateDescription=moveDelta?.let{"半透明移动预览 ${it.x},${it.y}"}?:"画布就绪"}
            .pointerInput(tool,playing,p.id,p.width,p.height){
                if(playing)return@pointerInput
                if(tool=="pan")detectTransformGestures{_,delta,factor,_->zoom=(zoom*factor).coerceIn(1f,12f);pan=limit(pan+delta,zoom)}
                else if(tool in listOf("select","move"))awaitEachGesture{
                    val down=awaitFirstDown(requireUnconsumed=false);val start=indexAt(down.position,size);var end=start;var released=false
                    if(start!=null){down.consume();if(tool=="move")moveDelta=IntOffset.Zero
                        try{do{val e=awaitPointerEvent();val c=e.changes.firstOrNull{it.id==down.id}?:break
                            indexAt(c.position,size)?.let{end=it;if(tool=="select")latestRegion(start,it)else moveDelta=IntOffset(it%p.width-start%p.width,it/p.width-start/p.width)}
                            if(!c.pressed){released=true;break};c.consume()
                        }while(true)
                        if(released){if(tool=="move"&&end!=start)latestMove(start,end?:start)else if(tool=="select")latestRegion(start,end?:start)}
                        }finally{moveDelta=null}
                    }
                }
                else if(tool in listOf("fill","pick"))detectTapGestures{point->indexAt(point,size)?.let{latestTap(it,tool)}}
                else awaitEachGesture{val first=awaitFirstDown(requireUnconsumed=false);var last=indexAt(first.position,size);var started=false
                    if(last!=null){latestBegin();started=true;latestStroke(last!!,last!!,tool=="erase");first.consume()}
                    try{do{val event=awaitPointerEvent();val change=event.changes.firstOrNull{it.id==first.id}?:break;if(!change.pressed)break
                        val cell=indexAt(change.position,size);if(cell!=null&&event.changes.count{it.pressed}==1){if(!started){latestBegin();started=true};latestStroke(last?:cell,cell,tool=="erase");last=cell;change.consume()}else last=null
                    }while(true)}finally{if(started)latestEnd()}
                }
            }){
            val c=min(size.width/p.width,size.height/p.height)*.92f;val ox=(size.width-c*p.width)/2;val oy=(size.height-c*p.height)/2
            withTransform({translate(pan.x,pan.y);scale(zoom,zoom,center)}){
                if(checker)drawImage(checkerImage,dstOffset=IntOffset(ox.roundToInt(),oy.roundToInt()),dstSize=IntSize((p.width*c).roundToInt(),(p.height*c).roundToInt()),filterQuality=FilterQuality.None)
                if(reference!=null){val fit=min(p.width*c/reference.width,p.height*c/reference.height);val w=(reference.width*fit).roundToInt();val h=(reference.height*fit).roundToInt();drawImage(reference,dstOffset=IntOffset((ox+(p.width*c-w)/2).roundToInt(),(oy+(p.height*c-h)/2).roundToInt()),dstSize=IntSize(w,h),alpha=p.referenceOpacity)}
                fun layer(pixels:List<Int>,tint:Color?=null){pixels.forEachIndexed{i,v->if(v ushr 24>0)drawRect(tint?:Color(v),Offset(ox+i%p.width*c,oy+i/p.width*c),Size(c,c))}}
                onionImage?.let{drawImage(it,dstOffset=IntOffset(ox.roundToInt(),oy.roundToInt()),dstSize=IntSize((p.width*c).roundToInt(),(p.height*c).roundToInt()),filterQuality=FilterQuality.None)}
                val selection=region?:PixelRegion(0,0,p.width-1,p.height-1)
                if(moveDelta==null)drawImage(frameImage,dstOffset=IntOffset(ox.roundToInt(),oy.roundToInt()),dstSize=IntSize((p.width*c).roundToInt(),(p.height*c).roundToInt()),filterQuality=FilterQuality.None)else{
                    layer(p.frames[frame].pixels.mapIndexed{i,v->if(selection.contains(i%p.width,i/p.width))0 else v})
                    val d=moveDelta!!
                    clipRect(ox,oy,ox+p.width*c,oy+p.height*c){p.frames[frame].pixels.forEachIndexed{i,v->if(v ushr 24>0&&selection.contains(i%p.width,i/p.width))drawRect(Color(v).copy(alpha=.55f),Offset(ox+(i%p.width+d.x)*c,oy+(i/p.width+d.y)*c),Size(c,c))}}
                }
                if(region!=null||moveDelta!=null){val r=selection;val d=moveDelta?:IntOffset.Zero;drawRect(accent,Offset(ox+(r.left+d.x)*c,oy+(r.top+d.y)*c),Size((r.right-r.left+1)*c,(r.bottom-r.top+1)*c),style=Stroke(2.dp.toPx()/zoom))}
                if(c*zoom>=32f&&!playing&&moveDelta==null){val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply{textAlign=android.graphics.Paint.Align.CENTER;textSize=c*.23f}
                    drawIntoCanvas{canvas->p.frames[frame].pixels.forEachIndexed{i,v->if(v ushr 24>0){paint.color=if(android.graphics.Color.luminance(v)>.45f)android.graphics.Color.BLACK else android.graphics.Color.WHITE;canvas.nativeCanvas.drawText((p.colorCodes.getOrNull(p.palette.indexOf(v))?:codes[v].toString()),ox+(i%p.width+.5f)*c,oy+(i/p.width+.62f)*c,paint)}}}
                }
                if(checker&&c*zoom>=16f&&!playing){for(x in 0..p.width)drawLine(ink.copy(alpha=.14f),Offset(ox+x*c,oy),Offset(ox+x*c,oy+p.height*c),.6f/zoom);for(y in 0..p.height)drawLine(ink.copy(alpha=.14f),Offset(ox,oy+y*c),Offset(ox+p.width*c,oy+y*c),.6f/zoom)}
            }
        }
        CanvasZoom(zoom,{zoom=(zoom/1.5f).coerceAtLeast(1f);pan=limit(pan,zoom)},{zoom=1f;pan=Offset.Zero},{zoom=(zoom*1.5f).coerceAtMost(12f)},Modifier.align(Alignment.BottomStart).padding(8.dp))
        if(tool in listOf("draw","erase")&&!playing)Column(Modifier.align(Alignment.TopStart).padding(6.dp).width(36.dp).clip(RoundedCornerShape(18.dp)).background(Panel.copy(alpha=.95f)).padding(vertical=8.dp),horizontalAlignment=Alignment.CenterHorizontally){
            Text("${brushSize}px",fontSize=10.sp,color=Mint)
            Canvas(Modifier.width(32.dp).height(brushTrackHeight).semantics{contentDescription="笔触宽度滑条";stateDescription="$brushSize 像素";setProgress{onBrush(it.toInt().coerceIn(1,24));true}}.pointerInput(Unit){detectTapGestures{onBrush((1+(1-it.y/size.height)*23).roundToInt().coerceIn(1,24))}}.pointerInput(Unit){detectDragGestures(onDragStart={onBrush((1+(1-it.y/size.height)*23).roundToInt().coerceIn(1,24))}){c,_->c.consume();onBrush((1+(1-c.position.y/size.height)*23).roundToInt().coerceIn(1,24))}}){
                val x=size.width/2;val top=8.dp.toPx();val bottom=size.height-top;val y=bottom-(brushSize-1)/23f*(bottom-top)
                drawLine(Color(0xffead5e2),Offset(x,top),Offset(x,bottom),5.dp.toPx(),StrokeCap.Round)
                drawLine(accent,Offset(x,y),Offset(x,bottom),5.dp.toPx(),StrokeCap.Round)
                drawCircle(accent,7.dp.toPx(),Offset(x,y));drawCircle(Color.White,3.dp.toPx(),Offset(x,y))
            }
        }
        if(zoom>1.05f){val bc=min(board.width.toFloat()/p.width,board.height.toFloat()/p.height)*.92f
            CanvasMiniMap(p.frames[frame].pixels,p.width,p.height,visibleCanvasRect(p.width,p.height,board,bc,zoom,pan),{x,y->pan=limit(Offset((.5f-x)*p.width*bc*zoom,(.5f-y)*p.height*bc*zoom),zoom)},Modifier.align(Alignment.TopEnd).padding(8.dp))
        }
    }
}
