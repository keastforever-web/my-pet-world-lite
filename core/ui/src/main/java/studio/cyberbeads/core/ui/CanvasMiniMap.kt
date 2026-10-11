package studio.cyberbeads.core.ui
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import kotlin.math.min
@Composable fun CanvasMiniMap(pixels:List<Int>,width:Int,height:Int,viewport:Rect,onLocate:(Float,Float)->Unit,modifier:Modifier=Modifier){
    val locate by rememberUpdatedState(onLocate)
    Column(modifier.width(92.dp).clip(RoundedCornerShape(12.dp)).background(Panel).border(1.dp,Mint,RoundedCornerShape(12.dp)).padding(6.dp)){
        Text("整体预览",fontSize=10.sp,color=Mint)
        Canvas(Modifier.fillMaxWidth().height(72.dp).semantics{contentDescription="整体预览小窗，点击定位"}.pointerInput(width,height){detectTapGestures{p->val c=min(size.width.toFloat()/width,size.height.toFloat()/height);locate(((p.x-(size.width-c*width)/2)/(c*width)).coerceIn(0f,1f),((p.y-(size.height-c*height)/2)/(c*height)).coerceIn(0f,1f))}}){
            val c=min(size.width/width,size.height/height);val ox=(size.width-c*width)/2;val oy=(size.height-c*height)/2
            pixels.forEachIndexed{i,v->if(v ushr 24>0)drawRect(Color(v),Offset(ox+i%width*c,oy+i/width*c),Size(c,c))}
            val l=viewport.left.coerceIn(0f,1f);val t=viewport.top.coerceIn(0f,1f);val rr=viewport.right.coerceIn(l,1f);val b=viewport.bottom.coerceIn(t,1f)
            drawRect(Color(0xffed5c73),Offset(ox+l*width*c,oy+t*height*c),Size((rr-l)*width*c,(b-t)*height*c),style=Stroke(1.5.dp.toPx()))
        }
    }
}
fun visibleCanvasRect(width:Int,height:Int,board:IntSize,cell:Float,zoom:Float,pan:Offset):Rect{
    if(cell<=0)return Rect(0f,0f,1f,1f)
    val cx=.5f-pan.x/(zoom*cell*width);val cy=.5f-pan.y/(zoom*cell*height);val hw=board.width/(2*zoom*cell*width);val hh=board.height/(2*zoom*cell*height)
    return Rect(cx-hw,cy-hh,cx+hw,cy+hh)
}
