package studio.cyberbeads.core.ui

import androidx.compose.foundation.*
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import studio.cyberbeads.core.model.BeadPattern
import kotlin.math.*

@Composable fun BeadBoard(pattern:BeadPattern,modifier:Modifier=Modifier,placed:Set<Int>?=null,onCell:((Int)->Unit)?=null,zoomable:Boolean=false,verticalZoom:Boolean=false) {
    var zoom by remember(pattern.width,pattern.height){mutableFloatStateOf(1f)}
    var offset by remember(pattern.width,pattern.height){mutableStateOf(Offset.Zero)}
    var board by remember {mutableStateOf(IntSize.Zero)}
    val surface=MaterialTheme.colorScheme.surfaceVariant
    val ink=MaterialTheme.colorScheme.onSurface
    val accent=Mint
    fun limit(v:Offset,z:Float)=Offset(v.x.coerceIn(-board.width*(z-1)/2,board.width*(z-1)/2),v.y.coerceIn(-board.height*(z-1)/2,board.height*(z-1)/2))
    val transform=rememberTransformableState{change,pan,_->zoom=(zoom*change).coerceIn(1f,8f);offset=limit(offset+pan,zoom)}
    fun reset(){zoom=1f;offset=Offset.Zero}
    val textPaint=remember{android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply{textAlign=android.graphics.Paint.Align.CENTER;typeface=android.graphics.Typeface.DEFAULT_BOLD}}
    BoxWithConstraints(modifier) {
        val canvasModifier=if(verticalZoom)Modifier.align(Alignment.CenterEnd).size(minOf(maxHeight,maxWidth-56.dp))else Modifier.fillMaxSize()
        Canvas(canvasModifier.clip(RoundedCornerShape(20.dp)).background(surface).onSizeChanged{board=it}
            .then(if(zoomable)Modifier.transformable(transform,canPan={zoom>1f})else Modifier)
            .pointerInput(pattern,zoom,offset,onCell){detectTapGestures(onDoubleTap={reset()},onTap={p->
                val cell=min(size.width.toFloat()/(pattern.width+2),size.height.toFloat()/(pattern.height+2))
                val center=Offset(size.width/2f,size.height/2f);val origin=Offset((size.width-cell*pattern.width)/2,(size.height-cell*pattern.height)/2)
                val q=(p-center-offset)/zoom+center-origin;val x=floor(q.x/cell).toInt();val y=floor(q.y/cell).toInt()
                if(x in 0 until pattern.width&&y in 0 until pattern.height)onCell?.invoke(y*pattern.width+x)
            })}.semantics{contentDescription="拼豆画布，${pattern.width}乘${pattern.height}格，共${pattern.beadCount}颗豆子"}) {
            val cell=min(size.width/(pattern.width+2),size.height/(pattern.height+2));val ox=(size.width-cell*pattern.width)/2;val oy=(size.height-cell*pattern.height)/2
            withTransform({translate(offset.x,offset.y);scale(zoom,zoom,center)}) {
                pattern.cells.forEachIndexed{i,value->
                    val x=ox+(i%pattern.width)*cell;val y=oy+(i/pattern.width)*cell
                    if(value>=0){
                        val c=Color(pattern.palette[value]);val done=placed==null||i in placed
                        drawRoundRect(if(done)c else c.copy(alpha=.25f),Offset(x+.5f,y+.5f),Size(cell-1,cell-1),CornerRadius(cell*.12f))
                        if(zoomable&&zoom>=1.4f&&cell*zoom>=22){
                            val rgb=pattern.palette[value];val lum=(rgb shr 16 and 255)*.2126+(rgb shr 8 and 255)*.7152+(rgb and 255)*.0722
                            textPaint.color=if(lum>145)android.graphics.Color.rgb(55,35,48)else android.graphics.Color.WHITE
                            textPaint.textSize=cell*.25f
                            drawContext.canvas.nativeCanvas.drawText(pattern.code(value),x+cell/2,y+cell/2-(textPaint.ascent()+textPaint.descent())/2,textPaint)
                        }else if(done){drawCircle(ink.copy(alpha=.22f),cell*.13f,Offset(x+cell/2,y+cell/2))}
                    }else drawCircle(ink.copy(alpha=.16f),cell*.045f,Offset(x+cell/2,y+cell/2))
                }
            }
        }
        if(zoomable) {
            CanvasZoom(zoom,{zoom=(zoom/1.5f).coerceAtLeast(1f);offset=limit(offset,zoom)},{reset()},{zoom=(zoom*1.5f).coerceAtMost(8f)},Modifier.align(Alignment.BottomStart).padding(8.dp))
            if(zoom>1.05f){val c=min(board.width.toFloat()/(pattern.width+2),board.height.toFloat()/(pattern.height+2))
                CanvasMiniMap(pattern.cells.map{if(it<0)0 else pattern.palette[it]},pattern.width,pattern.height,visibleCanvasRect(pattern.width,pattern.height,board,c,zoom,offset),{x,y->offset=limit(Offset((.5f-x)*pattern.width*c*zoom,(.5f-y)*pattern.height*c*zoom),zoom)},Modifier.align(Alignment.TopEnd).padding(8.dp))
            }
        }
    }
}
