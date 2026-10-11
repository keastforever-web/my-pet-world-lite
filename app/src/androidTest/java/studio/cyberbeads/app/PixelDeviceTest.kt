package studio.cyberbeads.app

import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import studio.cyberbeads.core.data.*
import studio.cyberbeads.core.imaging.OptionalModel
import studio.cyberbeads.core.model.*
import studio.cyberbeads.feature.pet.PetController
import java.io.File

@RunWith(AndroidJUnit4::class)
class PixelDeviceTest {
 @get:Rule val compose=createAndroidComposeRule<MainActivity>()
 private val app get()=compose.activity.application as CyberApplication
 private val auto get()=InstrumentationRegistry.getInstrumentation().uiAutomation
 private fun shell(command:String):String=auto.executeShellCommand(command).use{android.os.ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().readText()}
 private fun node(match:(AccessibilityNodeInfo)->Boolean):AccessibilityNodeInfo?{
   fun find(n:AccessibilityNodeInfo?):AccessibilityNodeInfo?{if(n==null)return null;if(match(n))return n;for(i in 0 until n.childCount)find(n.getChild(i))?.let{return it};return null}
   auto.serviceInfo=auto.serviceInfo.apply{flags=flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS}
   return auto.windows.firstNotNullOfOrNull{find(it.root)}
 }
 private fun waitNode(timeout:Long=10000,match:(AccessibilityNodeInfo)->Boolean):AccessibilityNodeInfo{val end=SystemClock.elapsedRealtime()+timeout;while(SystemClock.elapsedRealtime()<end){node(match)?.let{return it};SystemClock.sleep(200)};throw AssertionError("Expected desktop node not found; settings=${app.companions.settings.value}, running=${PetController.runningIds.value}, visible=${PetController.visibleIds.value}, interactive=${app.getSystemService(android.os.PowerManager::class.java).isInteractive}, locked=${app.getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked}; windows=${auto.windows.map{it.root?.contentDescription}}")}
 private fun tap(text:String){waitNode{it.text?.toString()==text}.performAction(AccessibilityNodeInfo.ACTION_CLICK)}
 private fun startPet(){shell("appops set ${compose.activity.packageName} SYSTEM_ALERT_WINDOW allow");compose.waitUntil(10000){app.artworkStore.items.value.any{it.id=="builtin-dog"}};compose.runOnUiThread{PetController.start(compose.activity,"builtin-dog")};compose.waitUntil(10000){"builtin-dog" in PetController.runningIds.value}}
 private fun stopPet(){compose.runOnUiThread{PetController.stop(compose.activity)};compose.waitUntil(10000){PetController.runningIds.value.isEmpty()}}
 @Before fun waitForCurrentHome(){prepareTestDevice(compose.activity);
  compose.waitUntil(60000){(compose.activity.application as CyberApplication).artworkStore.items.value.any{it.id=="bear16-blue"}}
  compose.waitUntil(30000){compose.onAllNodesWithText("自由绘画").fetchSemanticsNodes().isNotEmpty()&&compose.onAllNodesWithText("设置",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()}
 }
 @Test fun projectsPalettesAndPerPetMotionsSurviveReopening()=runBlocking{
   val store=app.creative;val p=PixelProject("test-pixel-project","draft",2,2,listOf(-1),listOf(PixelFrame(listOf(-1,0,0,0),80),PixelFrame(listOf(0,-1,0,0),240)),artworkId="test-pet-a",motionAction="wave",motionId="test-clip",triggered=false,random=true)
   val clip=MotionClip("test-clip","wave","wave",2,2,p.frames,true,true);var bank:String?=null
   try{store.save(p);store.saveMotion("test-pet-a",clip);store.saveMotion("test-pet-b",clip.copy(name="other",action="play"));bank=store.savePalette("test colors",listOf(-1,0xffffaabb.toInt())).id
     val bmp=Bitmap.createBitmap(2,2,Bitmap.Config.ARGB_8888);store.referenceFile(p.id).outputStream().use{bmp.compress(Bitmap.CompressFormat.PNG,100,it)};bmp.recycle()
     val reopened=CreativeStore(app);reopened.refresh();val loaded=reopened.get(p.id)!!;assertEquals(p.frames,loaded.frames);assertEquals("wave",loaded.motionAction);assertTrue(loaded.triggered);assertTrue(loaded.random);assertTrue(reopened.referenceFile(p.id).exists());assertEquals("wave",reopened.motions("test-pet-a").single().action);assertEquals("play",reopened.motions("test-pet-b").single().action);assertTrue(reopened.palettes.value.any{it.id==bank})
     store.clearMotions("test-pet-a");assertEquals(1,store.motions("test-pet-b").size)
   }finally{store.delete(p.id);store.clearMotions("test-pet-a");store.clearMotions("test-pet-b");bank?.let{store.deletePalette(it)}}
 }
 @Test fun staticCanvasThenPetActionEditorHasClearStages(){
   compose.onNodeWithText("自由绘画").performClick();compose.onNodeWithText("新建").performClick()
   compose.waitUntil(10000){compose.onAllNodes(hasContentDescription("自由创作画布",substring=true)).fetchSemanticsNodes().isNotEmpty()}
   compose.onNodeWithContentDescription("复制帧").assertDoesNotExist()
   compose.onNodeWithContentDescription("画笔").assertExists();compose.onNodeWithText("自由选色").performClick()
   compose.onNodeWithText("使用颜色").assertExists();compose.onNodeWithText("使用颜色").performClick()
   compose.onNode(hasContentDescription("自由创作画布",substring=true)).performTouchInput{swipe(center.copy(x=center.x-25),center.copy(x=center.x+25),300)}
   compose.onNodeWithText("完成图案，去拼装").performClick();compose.onNodeWithText("直接拼好，进入宠物站").performClick()
   compose.waitUntil(10000){compose.onAllNodesWithText("伙伴收藏室").fetchSemanticsNodes().isNotEmpty()}
   compose.onNodeWithText("设计互动动作").performClick()
   compose.waitUntil(15000){compose.onAllNodesWithText("编辑").fetchSemanticsNodes().isNotEmpty()};compose.onAllNodesWithText("编辑").onFirst().performScrollTo().performClick()
   compose.waitUntil(10000){compose.onAllNodesWithContentDescription("复制帧").fetchSemanticsNodes().isNotEmpty()}
   compose.onNode(hasText(" · 日常待机",substring=true)).assertExists()
   compose.onNodeWithContentDescription("复制帧").performClick()
   compose.onNodeWithContentDescription("删除当前帧").performClick();compose.onNodeWithText("删除帧").performClick()
   compose.onNodeWithContentDescription("第 1 帧，长按拖动排序").performTouchInput{down(center);advanceEventTime(650);moveBy(androidx.compose.ui.geometry.Offset(200f,0f));up()}
   compose.onNodeWithText("应用到日常待机动作").performClick()
   compose.waitUntil(10000){compose.onAllNodes(hasText("已应用到日常待机",substring=true)).fetchSemanticsNodes().isNotEmpty()}
   runBlocking{val art=app.artworkStore.items.value.first{it.name.startsWith("我的宠物-")&&it.isComplete};assertTrue(art.isComplete);assertTrue(app.creative.motions(art.id).any{it.action=="idle"})}
 }
 @Test fun apiKeyCanBeTypedSavedAndReopenedWithoutPlaintext(){
   compose.onNodeWithText("设置",useUnmergedTree=true).performClick()
   compose.onNodeWithText("使用 DeepSeek 官方接口").performScrollTo().performClick()
   compose.onNode(hasSetTextAction() and hasText("API Key",substring=true)).performScrollTo().performTextInput("safe-fixture-key-v4")
   prepareTestDevice(compose.activity)
   compose.activityRule.scenario.recreate()
   compose.waitUntil(30000){compose.onAllNodesWithText("PET WORLD").fetchSemanticsNodes().isNotEmpty()}
   compose.onNodeWithText("保存共享接口").performScrollTo().performClick()
   compose.waitUntil(10000){app.companions.hasSecret(CompanionStore.SHARED_API)}
   assertEquals("safe-fixture-key-v4",app.companions.secret(CompanionStore.SHARED_API));assertEquals("https://api.deepseek.com",app.companions.apiProfile().endpoint)
   val prefs=java.io.File(app.applicationInfo.dataDir,"shared_prefs/companion-settings-v2.xml");android.os.SystemClock.sleep(400);assertFalse(prefs.readText().contains("safe-fixture-key-v4"))
   app.companions.saveSecret(CompanionStore.SHARED_API,"");app.companions.saveProfile(PetProfile("builtin-dog"))
 }
 @Test fun canvasSurvivesLandscapeAndBackWithVisibleTools(){
   compose.onNodeWithText("自由绘画").performClick();compose.onNodeWithText("新建").performClick()
   compose.waitUntil(10000){compose.onAllNodesWithContentDescription("画笔").fetchSemanticsNodes().isNotEmpty()}
   compose.onNode(hasContentDescription("自由创作画布",substring=true)).performTouchInput{click(center)}
   try{
     compose.runOnUiThread{compose.activity.requestedOrientation=android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE}
     compose.waitUntil(10000){compose.activity.resources.configuration.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE}
     compose.onNodeWithContentDescription("画笔").assertIsDisplayed();compose.onNodeWithText("完成图案，去拼装").assertIsDisplayed()
     compose.onNodeWithText("自由选色").performScrollTo().performClick();compose.onNodeWithText("使用颜色").performClick()
     compose.onNodeWithContentDescription("保存草稿").performClick()
   }finally{compose.runOnUiThread{compose.activity.requestedOrientation=android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT}}
   compose.waitUntil(10000){compose.activity.resources.configuration.orientation==android.content.res.Configuration.ORIENTATION_PORTRAIT}
   compose.onNodeWithContentDescription("画笔").assertIsDisplayed()
 }
 @Test fun invalidOptionalModelImportDoesNotReplaceExistingModel()=runBlocking{
   val model=OptionalModel(app);val old=model.file.length();val fixture=File(app.cacheDir,"invalid-model-test.onnx");fixture.writeText("not a model")
   try{assertTrue(runCatching{model.import(android.net.Uri.fromFile(fixture))}.isFailure);assertEquals(old,model.file.length());assertFalse(File(app.filesDir,"u2net-install.tmp").exists());assertFalse(app.assets.list("")!!.contains("u2net.onnx"));assertTrue(app.assets.list("")!!.contains("u2netp.onnx"))}finally{fixture.delete()}
 }
 @Test fun desktopChatKeepsHomeVisibleAndMiniKeyboardInsertsText(){
   val original=app.companions.settings.value;app.companions.saveSettings(original.copy(edgeHide=false));startPet()
   try{shell("input keyevent 3");compose.runOnUiThread{PetController.interact(compose.activity,"builtin-dog","chat")}
     tap("n");tap("i");tap("h");tap("a");tap("o");tap("你好");val input=waitNode{it.contentDescription?.toString()=="桌面聊天输入"};assertEquals("你好",input.text.toString());assertFalse(compose.activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED));tap("使用手机键盘");tap("迷你键盘");tap("关闭")
     assertNull(node{it.contentDescription?.toString()=="桌面聊天输入"})
   }finally{stopPet();app.companions.saveSettings(original)}
 }
 @Test fun cardDetailOpensMiniChatThenWorksOnHome(){
   val old=app.companions.settings.value;app.companions.saveSettings(old.copy(voice=false,edgeHide=false));shell("appops set ${compose.activity.packageName} SYSTEM_ALERT_WINDOW allow")
   try{
     compose.onNodeWithText("宠物",useUnmergedTree=true).performClick();compose.waitUntil(10000){compose.onAllNodesWithText("草莓小狗").fetchSemanticsNodes().isNotEmpty()}
     compose.onNodeWithText("草莓小狗").performClick();compose.onNodeWithText("打开迷你聊天").performClick()
     waitNode{it.contentDescription?.toString()=="桌面聊天输入"};shell("input keyevent 3");tap("你好呀")
     assertEquals("你好呀",waitNode{it.contentDescription?.toString()=="桌面聊天输入"}.text.toString());assertFalse(compose.activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED));tap("关闭")
   }finally{stopPet();app.companions.saveSettings(old)}
 }
 @Test fun idlePetParksAndTapRestoresFullPet(){
   val original=app.companions.settings.value;app.companions.saveSettings(original.copy(edgeHide=true,edgeSeconds=10));startPet()
   try{println("EDGE_TEST waiting for parking");val n=waitNode(18000){it.contentDescription?.contains("躲在屏幕边缘")==true};println("EDGE_TEST parked, tapping to restore");assertTrue(n.performAction(AccessibilityNodeInfo.ACTION_CLICK));waitNode{it.contentDescription?.contains("点击互动，长按菜单")==true};assertNull(node{it.contentDescription?.contains("躲在屏幕边缘")==true})}finally{stopPet();app.companions.saveSettings(original)}
 }
 @Test fun miniCandidatesKeepLetterKeysStationary(){startPet();try{shell("input keyevent 3");compose.runOnUiThread{PetController.interact(compose.activity,"builtin-dog","chat")}
   val before=android.graphics.Rect();waitNode{it.text?.toString()=="n"}.getBoundsInScreen(before)
   assertNull(node{it.text?.toString()=="语音"});tap("n");tap("i");tap("h");tap("a");tap("o");val after=android.graphics.Rect();waitNode{it.text?.toString()=="n"}.getBoundsInScreen(after);assertEquals(before,after);tap("你好");val cleared=android.graphics.Rect();waitNode{it.text?.toString()=="n"}.getBoundsInScreen(cleared);assertEquals(before,cleared);tap("关闭")
 }finally{stopPet()}}
 @Test fun customInteractionActuallyRendersItsOwnFramesOnDesktop()=runBlocking{
   val id="test-render-clip";val colorA=0xff12c7ae.toInt();val colorB=0xffa347cf.toInt();val original=app.companions.settings.value
   app.companions.saveSettings(original.copy(edgeHide=false,opacity=1f));val clip=MotionClip(id,"render test","play",24,24,listOf(PixelFrame(List(576){colorA},2000),PixelFrame(List(576){colorB},2000)))
   val art=app.artworkStore.get("builtin-dog")!!;val existing=app.creative.ensureMotions(art).filter{it.action=="play"};app.creative.saveMotion("builtin-dog",clip);existing.forEach{app.creative.saveMotion(art.id,it.copy(triggered=false))};startPet()
   try{compose.runOnUiThread{PetController.interact(compose.activity,"builtin-dog","play")};SystemClock.sleep(500)
     val bitmap=auto.takeScreenshot()!!;val pixels=IntArray(bitmap.width*bitmap.height);bitmap.getPixels(pixels,0,bitmap.width,0,0,bitmap.width,bitmap.height);bitmap.recycle();assertTrue("Custom frame colors must appear in the actual overlay",pixels.count{it==colorA||it==colorB}>1000)
   }finally{stopPet();existing.forEach{app.creative.saveMotion("builtin-dog",it)};app.creative.deleteMotion("builtin-dog",id);app.companions.saveSettings(original)}
 }

}
