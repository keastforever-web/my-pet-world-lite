package studio.cyberbeads.feature.pet
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.delay
import studio.cyberbeads.core.model.*
import kotlin.math.*
@Composable internal fun ActionPortrait(art:Artwork,clip:MotionClip?,trigger:Int,modifier:Modifier){
    val idle=remember(art){MotionDefaults.clip(art,"idle")};var elapsed by remember{mutableLongStateOf(0)}
    LaunchedEffect(clip,trigger){elapsed=0;while(true){delay(40);elapsed+=40}}
    val current=if(clip!=null&&elapsed<clip.durationMs)clip else idle;val f=current.frameAt(elapsed)
    val image=remember(f){android.graphics.Bitmap.createBitmap(f.pixels.toIntArray(),current.width,current.height,android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()}
    Canvas(modifier){val c=min(size.width/current.width,size.height/current.height);val w=(current.width*c).toInt().coerceAtLeast(1);val h=(current.height*c).toInt().coerceAtLeast(1);drawImage(image,dstOffset=IntOffset(((size.width-w)/2).toInt(),((size.height-h)/2).toInt()),dstSize=IntSize(w,h),filterQuality=FilterQuality.None)}
}
