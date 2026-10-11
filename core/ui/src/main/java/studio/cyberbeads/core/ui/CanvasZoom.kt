package studio.cyberbeads.core.ui
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
@Composable fun CanvasZoom(zoom:Float,onMinus:()->Unit,onReset:()->Unit,onPlus:()->Unit,modifier:Modifier=Modifier){
    Row(modifier.clip(RoundedCornerShape(24.dp)).background(Panel.copy(alpha=.96f)).padding(horizontal=2.dp),verticalAlignment=Alignment.CenterVertically){
        IconButton(onMinus,Modifier.size(40.dp).semantics{contentDescription="缩小画布"}){Text("−",fontSize=20.sp)}
        OutlinedButton(onReset,Modifier.height(34.dp).widthIn(min=66.dp),contentPadding=PaddingValues(horizontal=8.dp,vertical=0.dp)){Text("${(zoom*100).roundToInt()}%",fontSize=11.sp)}
        IconButton(onPlus,Modifier.size(40.dp).semantics{contentDescription="放大画布"}){Text("＋",fontSize=18.sp)}
    }
}
