package studio.cyberbeads.app

import android.app.Application
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import kotlin.math.abs
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import studio.cyberbeads.core.data.*
import studio.cyberbeads.core.imaging.*
import studio.cyberbeads.core.model.*
import studio.cyberbeads.core.ui.*
import studio.cyberbeads.feature.studio.StudioScreen
import studio.cyberbeads.feature.library.LibraryScreen
import studio.cyberbeads.feature.pet.*
import studio.cyberbeads.feature.settings.SettingsScreen
import studio.cyberbeads.feature.pixel.*


class CyberApplication:Application(),ArtworkStoreOwner,CompanionOwner,CreativeOwner,FoodOwner,GardenOwner {
    override fun attachBaseContext(base:android.content.Context){
        // Set before any ContentProvider or ONNX native library can initialize telemetry.
        android.system.Os.setenv("ORT_DISABLE_TELEMETRY","1",true)
        super.attachBaseContext(base)
    }
    override fun onCreate(){super.onCreate()}
    override val artworkStore by lazy{ArtworkStore(this,if(BuildConfig.HAS_PARK)emptySet()else LiteCompanions.retiredIds)}
    override val foods by lazy{FoodStore(this)}
    override val garden by lazy{GardenStore(this)}
    override val creative by lazy{CreativeStore(this)}
    override val companions by lazy{CompanionStore(this)}
    override val chatClient by lazy{PetChatClient(companions)}
    val subjectSegmenter:SubjectSegmenter by lazy{OnnxSubjectSegmenter(this)}
    suspend fun syncNames(){val prefs=getSharedPreferences("identity-v5",MODE_PRIVATE);if(!prefs.getBoolean("migrated",false)){artworkStore.items.value.forEach{art->val profile=companions.profile(art.id);if(profile.nickname.isNotBlank()){artworkStore.update(art.id){it.copy(name=profile.nickname)};creative.renameArtwork(art.id,profile.nickname);companions.saveProfile(profile.copy(nickname=""))}};prefs.edit().putBoolean("migrated",true).apply()}}
    suspend fun seedCompanions(){val prefs=getSharedPreferences("builtin-seeds",MODE_PRIVATE)
        if(!prefs.getBoolean("v2",false)){(if(BuildConfig.HAS_PARK)BuiltinPets.names else LiteCompanions.animalNames).forEach{(id,name)->if(artworkStore.get(id)==null&&!artworkStore.wasDeleted(id))artworkStore.save(Artwork(id,name,System.currentTimeMillis(),BuiltinPets.pattern(id)).complete())};prefs.edit().putBoolean("v2",true).apply()}
        if(BuildConfig.HAS_PARK&&!prefs.getBoolean("v8",false)){MemePets.names.forEach{(id,name)->if(artworkStore.get(id)==null&&!artworkStore.wasDeleted(id))artworkStore.save(Artwork(id,name,System.currentTimeMillis(),BuiltinPets.pattern(id)).complete())};prefs.edit().putBoolean("v8",true).apply()}
        if(!BuildConfig.HAS_PARK)artworkStore.get("pack16-5")?.takeIf{it.name=="鼠袋师"}?.let{artworkStore.update(it.id){it.copy(name="鼠大师")};creative.renameArtwork(it.id,"鼠大师")}}

}
class MainActivity:ComponentActivity(){
    private var incoming by mutableStateOf<String?>(null)
    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);incoming=intent.getStringExtra("chatPet")
        val graph=application as CyberApplication
        if(android.os.Build.VERSION.SDK_INT>=29)window.isNavigationBarContrastEnforced=false
        setContent{val prefs by graph.companions.settings.collectAsState()
            SideEffect{val bar=if(prefs.style=="night")SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)else SystemBarStyle.light(android.graphics.Color.TRANSPARENT,android.graphics.Color.TRANSPARENT);enableEdgeToEdge(statusBarStyle=bar,navigationBarStyle=bar)}
            CyberTheme(prefs.style){CyberApp(graph,incoming){incoming=null}}}
    }
    override fun onNewIntent(intent:Intent){super.onNewIntent(intent);setIntent(intent);incoming=intent.getStringExtra("chatPet")}
}
data class FeatureEntry(val id:String,val title:String,val icon:ImageVector)
val features=listOf(FeatureEntry("studio","创作",Icons.Outlined.AutoAwesome),FeatureEntry("library","作品",Icons.Outlined.GridView),FeatureEntry("pet","宠物",Icons.Outlined.Pets),FeatureEntry("settings","设置",Icons.Outlined.Settings))

@OptIn(ExperimentalLayoutApi::class)
@Composable fun CyberApp(graph:CyberApplication,incoming:String?=null,consume:()->Unit={}){
    var route by rememberSaveable{mutableStateOf("studio")};var artworkId by rememberSaveable{mutableStateOf<String?>(null)}
    var projectId by rememberSaveable{mutableStateOf<String?>(null)};var sourceId by rememberSaveable{mutableStateOf<String?>(null)};var motionAction by rememberSaveable{mutableStateOf<String?>(null)};var clipId by rememberSaveable{mutableStateOf<String?>(null)};var blank by rememberSaveable{mutableStateOf(false)};var pixelReturn by rememberSaveable{mutableStateOf("creative")}
    var swipe by remember{mutableFloatStateOf(0f)};val haptic=LocalHapticFeedback.current
    var editorSession by rememberSaveable{mutableIntStateOf(0)}
    fun editArtwork(id:String){editorSession++;projectId=null;sourceId=id;motionAction=null;clipId=null;blank=false;pixelReturn="library";route="pixel"}
    val items by graph.artworkStore.items.collectAsState()
    var loadError by remember{mutableStateOf<String?>(null)};var launching by rememberSaveable{mutableStateOf(true)}
    val keyboardVisible=WindowInsets.isImeVisible
    LaunchedEffect(Unit){runCatching{graph.artworkStore.refresh();graph.seedCompanions();SpriteCatalog.seed(graph,graph.artworkStore);graph.syncNames()}.onFailure{loadError="伙伴读取失败，请重启重试；原作品已保留"};kotlinx.coroutines.delay(900);launching=false}
    LaunchedEffect(incoming){if(incoming!=null){artworkId=incoming;route="chat";consume()}}
    Scaffold(contentWindowInsets=if(route=="park"||launching)WindowInsets(0,0,0,0)else ScaffoldDefaults.contentWindowInsets,containerColor=Ink,topBar={if(!launching&&route!="park"&&route!="pixel"&&!keyboardVisible)Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal=22.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically){
        Text("✦",color=Mint,fontSize=23.sp);Spacer(Modifier.width(9.dp));Text("PET WORLD",fontFamily=FontFamily.Monospace,fontWeight=FontWeight.Bold,fontSize=15.sp,letterSpacing=2.sp);Spacer(Modifier.weight(1f));Text("我的宠物世界",color=Muted,fontSize=11.sp)
    }},bottomBar={if(!launching&&route!="pixel"&&route!="park"&&!keyboardVisible)NavigationBar(containerColor=Ink,tonalElevation=0.dp){features.forEach{f->NavigationBarItem(selected=route==f.id||(route=="chat"&&f.id=="pet"),onClick={route=f.id;artworkId=null},icon={Icon(f.icon,null)},label={Text(f.title)},colors=NavigationBarItemDefaults.colors(indicatorColor=Panel,selectedIconColor=Mint,selectedTextColor=Mint,unselectedIconColor=Muted,unselectedTextColor=Muted))}}}){padding->
        Box(Modifier.fillMaxSize().then(if(route=="park"||launching)Modifier else Modifier.padding(padding).consumeWindowInsets(padding).imePadding()),contentAlignment=Alignment.TopCenter){Box(Modifier.widthIn(max=1200.dp).fillMaxSize().graphicsLayer{translationX=swipe*.25f;alpha=1f-(abs(swipe)/1500f).coerceAtMost(.15f)}.pointerInput(route){
            if(route in features.map{it.id})awaitEachGesture{
                val down=awaitFirstDown(requireUnconsumed=false);var total=androidx.compose.ui.geometry.Offset.Zero;var claimed=false
                try{do{val event=awaitPointerEvent();val change=event.changes.firstOrNull{it.id==down.id}?:break
                    if(!change.pressed){if(claimed&&abs(total.x)>size.width*.22f){val next=features.indexOfFirst{it.id==route}+(if(total.x<0)1 else -1);if(next in features.indices){route=features[next].id;artworkId=null;haptic.performHapticFeedback(HapticFeedbackType.LongPress)}};break}
                    if(change.isConsumed&&!claimed)break
                    total+=change.position-change.previousPosition
                    if(!claimed&&abs(total.y)>viewConfiguration.touchSlop)break
                    if(abs(total.x)>viewConfiguration.touchSlop*2&&abs(total.x)>abs(total.y)*2)claimed=true
                    if(claimed){change.consume();swipe=total.x.coerceIn(-size.width*.4f,size.width*.4f)}
                }while(true)}finally{swipe=0f}
            }
        }){
            when(route){
                "studio"->StudioScreen(graph.artworkStore,graph.subjectSegmenter,onCreative={route="creative"},onEdit={editArtwork(it)},onSettings={route="settings"},parkEntry=editionEntry{route="park"},onLibrary={id->artworkId=id;route="library"})
                "library"->LibraryScreen(graph.artworkStore,artworkId,onCreate={route="studio"},onPet={id->artworkId=id;route="pet"},onAnimate={editArtwork(it)})
                "pet"->PetScreen(graph.artworkStore,graph.companions,artworkId,onLibrary={route="library";artworkId=null},onChat={id->artworkId=id;route="chat"},onMotions={id->artworkId=id;route="motions"})
                "creative"->PixelHome(graph.creative,onBack={route="studio"},onOpen={id->editorSession++;projectId=id;sourceId=null;motionAction=null;clipId=null;blank=false;pixelReturn="creative";route="pixel"})
                "pixel"->PixelScreen(graph.creative,graph.artworkStore,projectId,sourceId,motionAction,clipId,blank,session=editorSession,onBack={route=pixelReturn},onPublished={id->if(id.startsWith("furniture-")){route="park"}else{artworkId=id;route=if(graph.artworkStore.items.value.any{it.id==id&&it.isComplete})"pet" else "library"}})
                "park"->EditionPark(graph.artworkStore,onBack={route="studio"},onDrawFurniture={id->editorSession++;projectId=id;sourceId=null;motionAction=null;clipId=null;blank=false;pixelReturn="park";route="pixel"})
                "motions"->artworkId?.let{id->MotionScreen(id,graph.artworkStore,graph.creative,onBack={route="pet"},onEdit={action,clip,isBlank->editorSession++;projectId=null;sourceId=id;motionAction=action;clipId=clip;blank=isBlank;pixelReturn="motions";route="pixel"})}
                "settings"->SettingsScreen(graph.companions)
                "chat"->artworkId?.let{id->ChatScreen(id,items.firstOrNull{it.id==id}?.name?:"我的伙伴",graph.companions,graph.chatClient){artworkId=null;route="pet"}}
            }
            loadError?.let{Text(it,color=MaterialTheme.colorScheme.error)}
            LaunchGarden(launching){launching=false}
        }}
    }
}
