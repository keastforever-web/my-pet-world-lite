package studio.cyberbeads.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.Alignment
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.*
import studio.cyberbeads.core.data.*
import studio.cyberbeads.core.model.*
import studio.cyberbeads.core.ui.*

class ApiForm:ViewModel(){var endpoint by mutableStateOf<String?>(null);var model by mutableStateOf("");var secret by mutableStateOf("")}
@Composable fun GlobalApiPanel(store:CompanionStore){
    val f:ApiForm=viewModel(key="shared-api-form");if(f.endpoint==null){f.endpoint=store.apiProfile().endpoint;f.model=store.apiProfile().model}
    var reveal by remember{mutableStateOf(false)};var status by remember{mutableStateOf<String?>(null)};var busy by remember{mutableStateOf(false)};val scope=rememberCoroutineScope();val clipboard=LocalClipboardManager.current;val rev by store.revision.collectAsState()
    fun save(){val p=PetProfile(CompanionStore.SHARED_API,endpoint=f.endpoint.orEmpty(),model=f.model);PetChatClient.endpoint(p.endpoint);require(p.model.isNotBlank()){ "请填写模型名称" };if(f.secret.isNotBlank())store.saveSecret(CompanionStore.SHARED_API,f.secret.trim());store.saveApi(p);f.secret=""}
    CompactPanel("共享聊天接口"){
        Text("所有宠物共用一个接口和密钥；性格、聊天与记忆分别保存。",color=Muted,fontSize=12.sp)
        OutlinedButton(onClick={f.endpoint="https://api.deepseek.com";f.model="deepseek-flash"},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(10.dp),contentPadding=PaddingValues(8.dp)){Text("使用 DeepSeek 官方接口")}
        OutlinedTextField(f.endpoint.orEmpty(),{f.endpoint=it},label={Text("HTTPS 接口地址")},singleLine=true,modifier=Modifier.fillMaxWidth());OutlinedTextField(f.model,{f.model=it},label={Text("模型名称")},singleLine=true,modifier=Modifier.fillMaxWidth())
        OutlinedTextField(f.secret,{f.secret=it},label={Text(if(store.hasSecret(CompanionStore.SHARED_API))"API Key（已保存；留空保留）" else "API Key")},singleLine=true,visualTransformation=if(reveal)VisualTransformation.None else PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password,autoCorrectEnabled=false),trailingIcon={Row{IconButton(onClick={f.secret=clipboard.getText()?.text.orEmpty().trim()}){Icon(Icons.Outlined.ContentPaste,"粘贴密钥",Modifier.size(18.dp))};IconButton(onClick={reveal=!reveal}){Icon(if(reveal)Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,if(reveal)"隐藏密钥" else "显示密钥",Modifier.size(18.dp))}}},modifier=Modifier.fillMaxWidth())
        Text(if(store.hasSecret(CompanionStore.SHARED_API))"✓ 密钥已加密保存在本机" else "尚未保存密钥",fontSize=12.sp,color=Mint)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
        Button(onClick={runCatching{save();status="共享接口已保存"}.onFailure{status=it.message}},enabled=!busy,modifier=Modifier.weight(1f),shape=RoundedCornerShape(10.dp),contentPadding=PaddingValues(8.dp)){Text("保存共享接口",fontSize=12.sp)}
        OutlinedButton(onClick={scope.launch{busy=true;try{save();val p=store.apiProfile();val reply=PetChatClient.response(HttpsChatTransport.request(PetChatClient.endpoint(p.endpoint),store.secret(CompanionStore.SHARED_API),PetChatClient.payload(p.copy(persona="只回复连接成功"),emptyList(),"测试连接")));status="连接成功：$reply"}catch(e:Exception){status=e.message}finally{busy=false}}},enabled=!busy&&(f.secret.isNotBlank()||store.hasSecret(CompanionStore.SHARED_API)),modifier=Modifier.weight(1f),shape=RoundedCornerShape(10.dp),contentPadding=PaddingValues(8.dp)){Text("保存并测试连接",fontSize=12.sp)}
        }
        OutlinedButton(onClick={store.saveSecret(CompanionStore.SHARED_API,"");f.secret="";status="共享密钥已移除，所有宠物暂停联网"},enabled=!busy,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(10.dp),contentPadding=PaddingValues(8.dp)){Icon(Icons.Outlined.KeyOff,null,Modifier.size(16.dp));Spacer(Modifier.width(6.dp));Text("移除共享密钥",fontSize=12.sp)}
        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth());status?.let{Text(it,fontSize=12.sp,color=Mint)}
    }
}
