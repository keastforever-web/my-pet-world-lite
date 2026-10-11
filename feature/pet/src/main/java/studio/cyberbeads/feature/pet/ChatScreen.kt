package studio.cyberbeads.feature.pet

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*
import studio.cyberbeads.core.data.*
import studio.cyberbeads.core.model.*
import studio.cyberbeads.core.ui.*

@Composable fun ChatScreen(id:String,name:String,store:CompanionStore,client:PetChatClient,onBack:()->Unit){
    BackHandler{onBack()};val app=LocalContext.current.applicationContext;val artworks=(app as ArtworkStoreOwner).artworkStore;val creative=(app as CreativeOwner).creative
    val scope=rememberCoroutineScope();val rev by store.revision.collectAsState();var profile by remember(id){mutableStateOf(store.profile(id))};var rename by rememberSaveable(id){mutableStateOf(name)};var nameEdited by rememberSaveable(id){mutableStateOf(false)}
    LaunchedEffect(name){if(!nameEdited)rename=name}
    var revealMemory by rememberSaveable(id){mutableStateOf(false)}
    var configure by rememberSaveable(id){mutableStateOf(false)};var history by remember(id){mutableStateOf(emptyList<ChatLine>())};var hasMore by remember{mutableStateOf(true)};var loading by remember{mutableStateOf(false)};var memory by remember{mutableStateOf("")}
    var message by rememberSaveable(id){mutableStateOf("")};var error by remember{mutableStateOf<String?>(null)};var busy by remember{mutableStateOf(false)};var clear by remember{mutableStateOf(false)};val list=rememberLazyListState()
    LaunchedEffect(id,rev){val recent=store.page(id);history=(history+recent).distinctBy{it.sequence}.sortedBy{it.sequence};memory=store.memory(id).text;if(history.size<=40)hasMore=recent.size==40}
    suspend fun older(){if(loading||!hasMore)return;loading=true;try{val older=store.page(id,history.firstOrNull()?.sequence?:Long.MAX_VALUE);history=(older+history).distinctBy{it.sequence};hasMore=older.size==40}finally{loading=false}}
    LaunchedEffect(list.firstVisibleItemIndex,history.size){if(history.isNotEmpty()&&list.firstVisibleItemIndex>=history.size-8&&hasMore)older()}
    Column(Modifier.fillMaxSize().imePadding().padding(horizontal=14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        Row(verticalAlignment=Alignment.CenterVertically){OutlinedButton(onClick=onBack){Text("返回")};Text(name,Modifier.weight(1f).padding(horizontal=10.dp),style=MaterialTheme.typography.titleMedium);OutlinedButton(onClick={configure=true}){Text("性格与记忆")}}
        if(!store.connected())Text("请先到设置页配置共享聊天接口与密钥。",color=Mint,fontSize=12.sp)
        LazyColumn(Modifier.weight(1f).fillMaxWidth(),state=list,reverseLayout=true,verticalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(vertical=8.dp)){
            items(history.asReversed(),key={it.sequence}){line->Row(Modifier.fillMaxWidth(),horizontalArrangement=if(line.role=="user")Arrangement.End else Arrangement.Start){Surface(shape=androidx.compose.foundation.shape.RoundedCornerShape(16.dp),color=if(line.role=="user")MaterialTheme.colorScheme.primaryContainer else Panel,modifier=Modifier.widthIn(max=300.dp)){Column(Modifier.padding(12.dp)){Text(if(line.role=="user")"我" else name,fontSize=11.sp,color=Mint);Text(line.text)}}}}
            item{if(hasMore)OutlinedButton(onClick={scope.launch{older()}},enabled=!loading){Text(if(loading)"正在加载…" else "加载更早消息")}else Text("已经到最早的记录",fontSize=11.sp,color=Muted)}
        }
        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth());error?.let{Text(it,fontSize=12.sp,color=Mint,maxLines=3)}
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedTextField(message,{message=it.take(4000)},label={Text("和它说点什么")},modifier=Modifier.weight(1f),maxLines=4);Button(onClick={val text=message;scope.launch{busy=true;error=null;try{client.send(id,text);message="";list.animateScrollToItem(0)}catch(e:Exception){error=e.message}finally{busy=false}}},enabled=!busy&&message.isNotBlank()&&store.connected()){Text("发送")}}
    }
    if(configure)AlertDialog(onDismissRequest={configure=false},title={Text("性格与记忆")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)){
        OutlinedTextField(rename,{nameEdited=true;rename=it.take(40)},label={Text("名称 · 作品和草稿同步")},singleLine=true,modifier=Modifier.fillMaxWidth());OutlinedTextField(profile.persona,{profile=profile.copy(persona=it.take(4000))},label={Text("性格与说话方式")},minLines=3,maxLines=6,modifier=Modifier.fillMaxWidth())
        Row(verticalAlignment=Alignment.CenterVertically){Text("允许主动问候",Modifier.weight(1f));Switch(profile.proactive,{profile=profile.copy(proactive=it)})};Text("问候会参考这只宠物的性格和记忆，开启后会调用共享接口。",fontSize=12.sp)
        CompactPanel("小小记忆盒"){
            Text("嘘～不要偷偷翻小宠物的记忆哦！不过，你可以光明正大地看看 ♡",fontSize=12.sp)
            Text("积累约24条消息后，自动把较早的对话整理成摘要，记住你的偏好与约定。每只宠物独立保存，也会用于主动问候。",fontSize=11.sp,color=Muted)
            OutlinedButton(onClick={revealMemory=!revealMemory},modifier=Modifier.fillMaxWidth(),shape=androidx.compose.foundation.shape.RoundedCornerShape(10.dp)){Text(if(revealMemory)"合上记忆盒" else "打开记忆盒")}
            if(revealMemory)Text(memory.ifBlank{"小本本还空着，等我们聊得更多，就会有第一段记忆啦。"},fontSize=12.sp)
        }
        OutlinedButton(onClick={clear=true},modifier=Modifier.fillMaxWidth(),shape=androidx.compose.foundation.shape.RoundedCornerShape(10.dp)){Text("清除聊天和记忆")}
    }},confirmButton={Button(onClick={scope.launch{try{val draft=creative.linked(id);val art=artworks.get(id)?:error("伙伴不存在");val form=draft?.copy(name=rename)?:PixelProject("static-$id",rename,art.pattern.width,art.pattern.height,art.pattern.palette,listOf(PixelFrame(PixelOps.fromPattern(art.pattern))),artworkId=id);val clean=creative.availableName(form);artworks.update(id){it.copy(name=clean)};creative.renameArtwork(id,clean);store.saveProfile(profile.copy(nickname=""));nameEdited=false;configure=false}catch(e:Exception){error=e.message}}}){Text("保存设定")}},dismissButton={OutlinedButton(onClick={configure=false}){Text("取消")}})
    if(clear)AlertDialog(onDismissRequest={clear=false},title={Text("清除这只宠物的聊天和记忆？")},text={Text("其他宠物、性格与共享接口不受影响。")},confirmButton={Button(onClick={scope.launch{store.clearHistory(id);history=emptyList();memory="";hasMore=false;clear=false}}){Text("确认清除")}},dismissButton={OutlinedButton(onClick={clear=false}){Text("取消")}})
}
