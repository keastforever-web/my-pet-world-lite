package studio.cyberbeads.core.ui
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable fun <T> CompactChoice(label:String,value:T,options:List<Pair<T,String>>,onSelect:(T)->Unit,modifier:Modifier=Modifier,enabled:Boolean=true){
    var open by remember{mutableStateOf(false)}
    Box(modifier){OutlinedButton(onClick={open=true},enabled=enabled,modifier=Modifier.fillMaxWidth(),contentPadding=PaddingValues(horizontal=10.dp,vertical=8.dp)){
        Text("$label${options.firstOrNull{it.first==value}?.second?:value} ▾",fontSize=12.sp,maxLines=1)
    };DropdownMenu(expanded=open,onDismissRequest={open=false}){options.forEach{(id,name)->DropdownMenuItem(text={Text(name)},onClick={onSelect(id);open=false})}}}
}
