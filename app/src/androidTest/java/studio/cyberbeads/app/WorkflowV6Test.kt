package studio.cyberbeads.app

import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import studio.cyberbeads.core.data.*
import studio.cyberbeads.core.model.*
import studio.cyberbeads.feature.pixel.PixelViewModel
import studio.cyberbeads.feature.pet.PetController
import studio.cyberbeads.feature.studio.StudioViewModel
import java.util.UUID

@RunWith(AndroidJUnit4::class) class WorkflowV6Test {
 @get:Rule val compose=createAndroidComposeRule<MainActivity>()
 private val app get()=compose.activity.application as CyberApplication
 private val auto get()=InstrumentationRegistry.getInstrumentation().uiAutomation
 private fun shell(s:String)=auto.executeShellCommand(s).use{android.os.ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().readText()}
 private fun node(match:(AccessibilityNodeInfo)->Boolean):AccessibilityNodeInfo?{
  fun find(n:AccessibilityNodeInfo?):AccessibilityNodeInfo?{if(n==null)return null;if(match(n))return n;for(i in 0 until n.childCount)find(n.getChild(i))?.let{return it};return null}
  val info=auto.serviceInfo;val flag=android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
  if(info.flags and flag==0){info.flags=info.flags or flag;auto.serviceInfo=info}
  return auto.windows.firstNotNullOfOrNull{find(it.root)}
 }
 private fun waitNode(timeout:Long=12000,match:(AccessibilityNodeInfo)->Boolean):AccessibilityNodeInfo{val end=SystemClock.elapsedRealtime()+timeout;while(SystemClock.elapsedRealtime()<end){node(match)?.let{return it};SystemClock.sleep(150)}
  val observed=mutableListOf<String>();node{n->if(n.text!=null||n.contentDescription!=null)observed+="${n.text}|${n.contentDescription}";false}
  auto.takeScreenshot()?.let{b->java.io.File(app.getExternalFilesDir(null),"wake-failure.png").outputStream().use{b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};b.recycle()}
  throw AssertionError("Expected overlay node not found; settings=${app.companions.settings.value}, running=${PetController.runningIds.value}, visible=${PetController.visibleIds.value}, interactive=${app.getSystemService(android.os.PowerManager::class.java).isInteractive}, locked=${app.getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked}; nodes=$observed")}
 private fun assertBubble(text:String){
  // Inspect the app-owned non-touchable window through the public API29 inspector.
  // Interactive-window accessibility queries intentionally exclude this kind of bubble.
  val deadline=SystemClock.elapsedRealtime()+12000
  while(SystemClock.elapsedRealtime()<deadline){var visible=false
   InstrumentationRegistry.getInstrumentation().runOnMainSync{
    val roots=android.view.inspector.WindowInspector.getGlobalWindowViews()
    fun awake(v:android.view.View):Boolean{if(!v.isShown)return false;if(v.contentDescription?.contains("点击互动，长按菜单")==true)return true;return v is android.view.ViewGroup&&(0 until v.childCount).any{awake(v.getChildAt(it))}}
    val fullSize=(app.companions.settings.value.petSize*app.resources.displayMetrics.density).toInt()
    val restored=roots.any{v->awake(v)&&kotlin.math.abs(v.width-fullSize)<=1&&kotlin.math.abs(v.height-fullSize)<=1}
    visible=restored&&roots.filterIsInstance<android.widget.TextView>().any{v->
     val lp=v.layoutParams as? android.view.WindowManager.LayoutParams
     v.text.contains(text)&&v.isShown&&v.getGlobalVisibleRect(android.graphics.Rect())&&v.width>0&&v.height>0&&lp!=null&&lp.flags and android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE!=0
    }
   }
   if(visible)return
   SystemClock.sleep(100)
  }
  throw AssertionError("Visible touch-through message bubble missing: $text")
 }
 @Before fun waitForCurrentHome(){prepareTestDevice(compose.activity);
  compose.waitUntil(60000){app.artworkStore.items.value.any{it.id=="bear16-blue"}}
  compose.waitUntil(30000){compose.onAllNodesWithText("自由绘画").fetchSemanticsNodes().isNotEmpty()&&compose.onAllNodesWithText("设置",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()}
 }
 @Test fun duplicateDraftSavesRemainDistinctAndUpdateSameIdentity()=runBlocking<Unit>{
  val a=PixelProject(UUID.randomUUID().toString(),"同名草稿-v6",2,2,listOf(-1),listOf(PixelFrame(listOf(-1,0,0,0))))
  val b=a.copy(id=UUID.randomUUID().toString())
  try{val savedA=app.creative.save(a);val savedB=app.creative.save(b);assertNotEquals(savedA.name,savedB.name);val updated=app.creative.save(savedB.copy(frames=listOf(PixelFrame(listOf(0,-1,0,0)))));assertEquals(savedB.name,updated.name);assertEquals(savedA.frames,app.creative.get(a.id)!!.frames);assertEquals(2,app.creative.projects.value.count{it.id in listOf(a.id,b.id)})}finally{app.creative.delete(a.id);app.creative.delete(b.id)}
 }
 @Test fun motionCopiesNamesFlagsAndDeletionStayIndependent()=runBlocking<Unit>{
  val art=app.artworkStore.create("动作隔离-v6",BuiltinPets.pattern("builtin-cat"))
  try{val all=app.creative.ensureMotions(art);assertEquals(MotionDefaults.actions.keys,all.map{it.action}.toSet());val first=all.first{it.action=="pet"};assertTrue(runCatching{app.creative.deleteMotion(art.id,first.id)}.isFailure)
   val copy=app.creative.copyMotion(art,first.id);val duplicate=app.creative.saveMotion(art.id,copy.copy(id=UUID.randomUUID().toString(),name=first.name));assertNotEquals(first.name,duplicate.name)
   app.creative.saveMotion(art.id,copy.copy(triggered=false,random=true));val loaded=CreativeStore(app).motions(art.id);assertTrue(loaded.first{it.id==first.id}.triggered);assertFalse(loaded.first{it.id==copy.id}.triggered);assertTrue(loaded.first{it.id==copy.id}.random)
   assertTrue(runCatching{app.creative.saveMotion(art.id,first.copy(action="play"))}.isFailure);app.creative.deleteMotion(art.id,copy.id);assertTrue(app.creative.motions(art.id).any{it.id==first.id});assertEquals(first.frames,duplicate.frames)
  }finally{app.creative.clearMotions(art.id);app.artworkStore.delete(art.id)}
 }
 @Test fun photoAndEditorUseSameCustomPaletteAndOutline()=runBlocking<Unit>{
  val bank=app.creative.savePalette("自定义导图-v6",listOf(0xff243b47.toInt(),0xff9af5ce.toInt(),0xffbaa5ff.toInt(),0xfffbdeed.toInt()))
  lateinit var vm:StudioViewModel;var artId:String?=null
  try{withContext(Dispatchers.Main){vm=StudioViewModel(app,app.subjectSegmenter,app.artworkStore);vm.demo()};withTimeout(40000){while(vm.state.value.busy)delay(100)};assertNotNull(vm.state.value.bitmap)
   withContext(Dispatchers.Main){vm.setParameters(grid=40,rows=30,brandId=bank.id,outlineWidth=1)};withTimeout(15000){while(vm.state.value.busy)delay(50)};val pattern=vm.state.value.pattern!!;assertEquals(bank.id,pattern.paletteId);assertNull(pattern.brandId);assertTrue(pattern.palette.all{it in bank.colors})
   val art=app.artworkStore.create("配色交接-v6",pattern);artId=art.id;lateinit var editor:PixelViewModel;withContext(Dispatchers.Main){editor=PixelViewModel(app,app.creative,app.artworkStore,null,art.id,null,null,false)};withTimeout(10000){while(editor.project.value==null)delay(50)};assertEquals(bank.id,editor.project.value!!.paletteId);assertEquals(bank.colors,editor.project.value!!.palette);editor.save();assertEquals(bank.id,app.creative.get(editor.project.value!!.id)!!.paletteId)
  }finally{artId?.let{id->app.creative.linked(id)?.let{app.creative.delete(it.id)};app.artworkStore.delete(id)};app.creative.deletePalette(bank.id)}
 }
 @Test fun catUsesPhotoWorkbenchAndCanBeRemoved(){
  compose.onNodeWithText("照片转换").assertDoesNotExist();compose.onNodeWithText("先用像素猫体验一下  →").performClick();compose.waitUntil(40000){compose.onAllNodesWithContentDescription("放大画布").fetchSemanticsNodes().isNotEmpty()}
  compose.onNodeWithText("移除图片").assertIsDisplayed().performClick();compose.onNodeWithText("先用像素猫体验一下  →").assertExists();compose.onNodeWithContentDescription("放大画布").assertDoesNotExist()
 }
 @Test fun draftTabsSeparateStaticAndMotionAndShowNames()=runBlocking<Unit>{
  val stem=UUID.randomUUID().toString();val a=PixelProject("a-$stem","静态分类测试",2,2,listOf(-1),listOf(PixelFrame(listOf(-1,0,0,0))));val b=a.copy(id="b-$stem",name="动作分类测试",motionAction="wave",motionId="c-$stem")
  try{app.creative.save(a);app.creative.save(b);compose.onNodeWithText("自由绘画").performClick();compose.onNodeWithText("静态分类测试").performScrollTo().assertExists();compose.onNodeWithText("动作分类测试").assertDoesNotExist();compose.onNode(hasText("动作 ·",substring=true)).performScrollTo().performClick();compose.onNodeWithText("动作分类测试").performScrollTo().assertExists();compose.onNodeWithText("静态分类测试").assertDoesNotExist()}finally{app.creative.delete(a.id);app.creative.delete(b.id)}
 }
 @Test fun incomingChatUnparksPetAndUnlockRestoresVisibility()=runBlocking<Unit>{
  val old=app.companions.settings.value;app.companions.saveSettings(old.copy(edgeHide=true,edgeSeconds=10,voice=false));shell("appops set ${compose.activity.packageName} SYSTEM_ALERT_WINDOW allow")
  compose.waitUntil(10000){app.artworkStore.items.value.any{it.id=="builtin-dog"}};compose.runOnUiThread{PetController.start(compose.activity,"builtin-dog")};compose.waitUntil(10000){"builtin-dog" in PetController.runningIds.value};shell("input keyevent 3")
  try{waitNode(18000){it.contentDescription?.contains("躲在屏幕边缘")==true};app.companions.saveApi(PetProfile(CompanionStore.SHARED_API,endpoint="https://example.com",model="fixture"));app.companions.saveSecret(CompanionStore.SHARED_API,"inert-v6-fixture")
   val client=PetChatClient(app.companions,ChatTransport{_,_,_->"""{"choices":[{"message":{"content":"收到测试消息啦"}}]}"""});client.send("builtin-dog","测试唤回")
   // Capture the transient reply before polling other windows; bubbles expire after 4.5s.
   assertBubble("收到测试消息啦")
   shell("input keyevent 223");withTimeout(10000){while(app.getSystemService(android.os.PowerManager::class.java).isInteractive||"builtin-dog" in PetController.visibleIds.value)delay(150)};shell("input keyevent 224");shell("wm dismiss-keyguard");assertBubble("你回来啦")
   compose.runOnUiThread{compose.activity.stopService(android.content.Intent(compose.activity,studio.cyberbeads.feature.pet.PetOverlayService::class.java))};withTimeout(5000){while(PetController.runningIds.value.isNotEmpty())delay(50)}
   assertTrue("Desktop selection must survive service destruction",app.getSharedPreferences("desktop-session-v6",0).getStringSet("ids",emptySet()).orEmpty().contains("builtin-dog"));compose.runOnUiThread{androidx.core.content.ContextCompat.startForegroundService(compose.activity,android.content.Intent(compose.activity,studio.cyberbeads.feature.pet.PetOverlayService::class.java))};withTimeout(20000){while("builtin-dog" !in PetController.runningIds.value)delay(100)};waitNode{it.contentDescription?.contains("草莓小狗")==true}
  }finally{shell("input keyevent 224");shell("wm dismiss-keyguard");app.companions.saveSecret(CompanionStore.SHARED_API,"");app.companions.clearHistory("builtin-dog");compose.runOnUiThread{PetController.stop(compose.activity)};app.companions.saveSettings(old)}
 }
}
