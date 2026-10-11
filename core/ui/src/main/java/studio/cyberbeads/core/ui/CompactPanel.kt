package studio.cyberbeads.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable fun CompactPanel(title:String,modifier:Modifier=Modifier,content:@Composable ColumnScope.()->Unit){
    OutlinedCard(modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp),colors=CardDefaults.outlinedCardColors(containerColor=Panel)){
        Column(Modifier.padding(10.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){Text(title,fontSize=12.sp,color=Mint);content()}
    }
}
@Composable fun PaletteRibbon(colors:List<Int>){Canvas(Modifier.fillMaxWidth().height(12.dp)){colors.forEachIndexed{i,c->drawRect(Color(c),Offset(i*size.width/colors.size,0f),Size(size.width/colors.size+1,size.height))}}}
@Composable fun <T> ChoiceField(label:String,value:T,options:List<Pair<T,String>>,onSelect:(T)->Unit,modifier:Modifier=Modifier,enabled:Boolean=true){
    var open by remember{mutableStateOf(false)}
    Box(modifier){OutlinedButton(onClick={open=true},enabled=enabled,shape=RoundedCornerShape(12.dp),modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),contentPadding=PaddingValues(horizontal=10.dp,vertical=5.dp)){
        Column(Modifier.weight(1f)){Text(label,fontSize=10.sp,color=Muted);Text(options.firstOrNull{it.first==value}?.second?:value.toString(),fontSize=12.sp,maxLines=1,overflow=TextOverflow.Ellipsis)};Text("⌄",Modifier.padding(start=5.dp))
    };DropdownMenu(open,{open=false}){options.forEach{(id,name)->DropdownMenuItem(text={Text(name)},onClick={onSelect(id);open=false})}}}
}

@Composable fun CompactInput(value:String,onValue:(String)->Unit,label:String,modifier:Modifier=Modifier,onFocus:(Boolean)->Unit={}){
    OutlinedCard(modifier.height(48.dp),shape=RoundedCornerShape(10.dp),colors=CardDefaults.outlinedCardColors(containerColor=Panel)){
        Column(Modifier.fillMaxSize().padding(horizontal=10.dp,vertical=4.dp),verticalArrangement=Arrangement.Center){
            Text(label,fontSize=9.sp,lineHeight=12.sp,color=Muted,maxLines=1)
            androidx.compose.foundation.text.BasicTextField(value,onValue,singleLine=true,textStyle=MaterialTheme.typography.bodyMedium.copy(color=MaterialTheme.colorScheme.onSurface,lineHeight=20.sp),modifier=Modifier.fillMaxWidth().onFocusChanged{onFocus(it.isFocused)}.semantics{contentDescription=label})
        }
    }
}

