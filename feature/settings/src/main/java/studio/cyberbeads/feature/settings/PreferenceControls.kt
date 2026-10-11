package studio.cyberbeads.feature.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import studio.cyberbeads.core.ui.Muted
import kotlin.math.roundToInt

@Composable internal fun PreferenceHeading(text:String){
    Text(text,color=MaterialTheme.colorScheme.primary,fontSize=12.sp,fontWeight=FontWeight.SemiBold)
}

@Composable internal fun PreferenceSwitch(title:String,description:String,value:Boolean,onChange:(Boolean)->Unit){
    Row(Modifier.fillMaxWidth().heightIn(min=52.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)){
            Text(title,fontSize=14.sp)
            Text(description,fontSize=11.sp,lineHeight=16.sp,color=Muted)
        }
        Switch(value,onChange)
    }
}

/** Continuous rounded track, no tick marks or oversized vertical Material handle. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun PreferenceSlider(title:String,value:Float,range:ClosedFloatingPointRange<Float>,low:String,high:String,format:(Float)->String,tag:String,onCommit:(Float)->Unit){
    var pending by remember(value){mutableFloatStateOf(value)}
    val active=MaterialTheme.colorScheme.primary;val inactive=MaterialTheme.colorScheme.secondaryContainer;val surface=MaterialTheme.colorScheme.surface
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(0.dp)){
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically){
            Text(title,Modifier.weight(1f),fontSize=13.sp)
            Text(format(pending),fontSize=13.sp,fontWeight=FontWeight.SemiBold,color=active)
        }
        Slider(value=pending,onValueChange={pending=it.roundToInt().toFloat().coerceIn(range)},onValueChangeFinished={onCommit(pending)},valueRange=range,
            modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag(tag),
            thumb={Box(Modifier.size(22.dp).shadow(2.dp,CircleShape).background(active,CircleShape).border(3.dp,surface,CircleShape))},
            track={Canvas(Modifier.fillMaxWidth().height(6.dp)){
                val inset=3.dp.toPx();val y=size.height/2;val start=Offset(inset,y);val end=Offset(size.width-inset,y)
                drawLine(inactive,start,end,6.dp.toPx(),StrokeCap.Round)
                val fraction=((pending-range.start)/(range.endInclusive-range.start)).coerceIn(0f,1f)
                if(fraction>0)drawLine(active,start,Offset(inset+(size.width-2*inset)*fraction,y),6.dp.toPx(),StrokeCap.Round)
            }})
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(low,fontSize=10.sp,color=Muted);Text(high,fontSize=10.sp,color=Muted)}
    }
}
