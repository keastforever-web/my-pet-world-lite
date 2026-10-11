package studio.cyberbeads.app

import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import studio.cyberbeads.core.data.*
import studio.cyberbeads.core.model.*
import studio.cyberbeads.core.ui.*
import studio.cyberbeads.feature.pet.PetPortrait
import studio.cyberbeads.feature.pixel.*
import java.io.File

@RunWith(AndroidJUnit4::class)
class LiteRegressionTest {
 @get:Rule val compose=createAndroidComposeRule<MainActivity>()
 private val app get()=compose.activity.application as CyberApplication
 private val auto get()=InstrumentationRegistry.getInstrumentation().uiAutomation
 private fun shell(command:String)=auto.executeShellCommand(command).use{ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().readText()}
 private fun ready(){compose.waitUntil(60000){app.artworkStore.items.value.any{it.id=="bear16-honey"}};compose.waitUntil(30000){compose.onAllNodesWithText("自由绘画").fetchSemanticsNodes().isNotEmpty()&&compose.onAllNodesWithText("设置",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()}}
 private fun capture(name:String){auto.takeScreenshot()?.let{bitmap->File(app.getExternalFilesDir(null),"lite17-$name.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()}}
 private fun nameField()=compose.onNodeWithContentDescription("作品名称")
 private fun imeBottom()=ViewCompat.getRootWindowInsets(compose.activity.window.decorView)?.getInsets(WindowInsetsCompat.Type.ime())?.bottom?:0
 private fun assertNameVisible(){
  compose.waitUntil(15000){imeBottom()>0};SystemClock.sleep(500);compose.waitForIdle()
  capture("name-keyboard-inspection")
  val field=nameField().fetchSemanticsNode().boundsInWindow
  val keyboardTop=compose.activity.window.decorView.height-imeBottom()
  assertTrue("Name field $field must remain above keyboard at $keyboardTop",field.top>=0&&field.bottom<=keyboardTop)
  nameField().assertIsDisplayed()
  val preview=compose.onNodeWithTag("studio-preview")
  preview.assertIsDisplayed()
  val imageBounds=preview.fetchSemanticsNode().boundsInWindow
  assertTrue("Current image $imageBounds must fit below input $field and above keyboard $keyboardTop",imageBounds.top>=field.bottom&&imageBounds.bottom<=keyboardTop&&imageBounds.width>40&&imageBounds.height>40)
  assertEquals(imageBounds.width,imageBounds.height,2f)
  val bitmap=preview.captureToImage().asAndroidBitmap()
  val pixels=IntArray(bitmap.width*bitmap.height);bitmap.getPixels(pixels,0,bitmap.width,0,0,bitmap.width,bitmap.height)
  assertTrue("The preview must render the actual multicolor pattern",pixels.distinct().size>10)
 }
 @Before fun prepareOwnedEmulator(){prepareTestDevice(compose.activity);shell("input keyevent KEYCODE_WAKEUP");shell("wm dismiss-keyguard");shell("settings put secure show_ime_with_hard_keyboard 1");if(imeBottom()>0){shell("input keyevent KEYCODE_BACK");compose.waitUntil(10000){imeBottom()==0}}}
 @After fun restoreDevice(){compose.mainClock.autoAdvance=true;if(imeBottom()>0)shell("input keyevent KEYCODE_BACK");compose.activity.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;shell("settings put system font_scale 1.0")}

 @Test fun onnxTelemetryDisabledBeforeNativeInitialization(){
  ready();assertEquals("1",android.system.Os.getenv("ORT_DISABLE_TELEMETRY"))
  assertNull(app.packageManager.resolveContentProvider(app.packageName+".onnxruntime_telemetry_initializer",0))
 }

 @Test fun photoNameRemainsVisibleWithKeyboardAndLandscape(){
  val title="小熊画像-${SystemClock.uptimeMillis()}"
  try{
  ready();compose.onNodeWithText("先用像素猫体验一下  →").performClick()
  compose.waitUntil(30000){compose.onAllNodesWithContentDescription("作品名称").fetchSemanticsNodes().isNotEmpty()}
  compose.waitUntil(60000){compose.onAllNodes(hasText("进入画板 →") and isEnabled()).fetchSemanticsNodes().isNotEmpty()}
  nameField().performClick().performTextReplacement(title)
  assertNameVisible();capture("name-keyboard-portrait")
  compose.activity.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
  compose.waitUntil(15000){app.resources.configuration.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE&&compose.activity.window.decorView.width>compose.activity.window.decorView.height}
  nameField().performClick();assertNameVisible();nameField().assertTextContains(title);capture("name-keyboard-landscape")
  shell("input keyevent KEYCODE_BACK");compose.waitUntil(10000){imeBottom()==0}
  compose.activity.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
  compose.waitUntil(15000){app.resources.configuration.orientation==android.content.res.Configuration.ORIENTATION_PORTRAIT&&compose.activity.window.decorView.height>compose.activity.window.decorView.width}
  compose.onNodeWithText("进入画板 →").performClick()
  compose.waitUntil(30000){compose.onAllNodesWithContentDescription("保存草稿").fetchSemanticsNodes().isNotEmpty()}
  compose.waitUntil(30000){compose.onAllNodes(hasSetTextAction() and hasText(title)).fetchSemanticsNodes().isNotEmpty()}
  compose.onNode(hasSetTextAction() and hasText(title)).assertExists()
  }finally{runBlocking{app.artworkStore.items.value.filter{it.name==title}.forEach{art->app.creative.linked(art.id)?.let{app.creative.delete(it.id)};app.creative.clearMotions(art.id);app.artworkStore.delete(art.id)}}}
 }

 @Test fun photoNameRemainsVisibleWithLargeFont(){
  ready();shell("settings put system font_scale 1.3")
  compose.waitUntil(15000){app.resources.configuration.fontScale>1.2f}
  compose.onNodeWithText("先用像素猫体验一下  →").performClick()
  compose.waitUntil(30000){compose.onAllNodesWithContentDescription("作品名称").fetchSemanticsNodes().isNotEmpty()}
  compose.waitUntil(60000){compose.onAllNodes(hasText("进入画板 →") and isEnabled()).fetchSemanticsNodes().isNotEmpty()}
  nameField().performClick().performTextReplacement("大字模式的小熊");assertNameVisible();capture("name-keyboard-large-font")
 }

 @Test fun allTenNewPortraitsActuallyAnimateAndRespectMotionOff()=runBlocking<Unit>{
  ready();val ids=(1..5).map{"pack16-$it"}+listOf("honey","rose","lilac","mint","blue").map{"bear16-$it"}
  val arts=ids.map{app.artworkStore.get(it)!!};val clips=arts.map{app.creative.portraitMotion(it)!!}
  clips.forEach{assertTrue(it.frames.map{frame->frame.pixels}.distinct().size>1)}
  var animate by mutableStateOf(true)
  compose.runOnUiThread{compose.activity.setContent{CyberTheme{Column{arts.forEach{PetPortrait(it,animate,Modifier.size(56.dp))}}}}}
  compose.waitUntil(30000){ids.all{id->compose.onNodeWithTag("portrait-$id").fetchSemanticsNode().config[SemanticsProperties.StateDescription].startsWith("动画帧")}}
  compose.mainClock.autoAdvance=false
  fun pixels(id:String):IntArray{val image=compose.onNodeWithTag("portrait-$id").captureToImage().asAndroidBitmap();return IntArray(image.width*image.height).also{image.getPixels(it,0,image.width,0,0,image.width,image.height)}}
  val before=ids.associateWith{pixels(it)};val changed=mutableSetOf<String>()
  repeat(12){compose.mainClock.advanceTimeBy(100);ids.filter{it !in changed}.forEach{if(!before.getValue(it).contentEquals(pixels(it)))changed+=it}}
  assertEquals("Every new pet must change actual rendered pixels",ids.toSet(),changed)
  compose.mainClock.autoAdvance=true;capture("ten-animated-portraits")
  compose.runOnUiThread{animate=false};compose.waitForIdle()
  ids.forEach{assertEquals("静态预览",compose.onNodeWithTag("portrait-$it").fetchSemanticsNode().config[SemanticsProperties.StateDescription])}
  val stopped=ids.associateWith{pixels(it)};compose.mainClock.advanceTimeBy(500)
  ids.forEach{assertArrayEquals(stopped.getValue(it),pixels(it))}
 }

 @Test fun portraitUsesEditedIdleAndCacheLoadsOnlyIdle()=runBlocking<Unit>{
  ready();val art=app.artworkStore.get("bear16-honey")!!;val start=SystemClock.elapsedRealtime()
  val first=SpriteCatalog.portrait(app,art.id)!!;assertSame(first,SpriteCatalog.portrait(app,art.id));assertEquals("idle",first.action)
  assertTrue(SystemClock.elapsedRealtime()-start<3000)
  val test=app.artworkStore.create("预览序列测试",demoPattern()).complete();app.artworkStore.save(test)
  try{val frames=listOf(PixelFrame(PixelOps.fromPattern(test.pattern),100),PixelFrame(PixelOps.mirror(PixelOps.fromPattern(test.pattern),test.pattern.width),180))
   val clip=MotionClip("lite17-idle","自定义待机","idle",test.pattern.width,test.pattern.height,frames,actionName="待机")
   app.creative.importMotions(test,listOf(clip));assertEquals(clip,app.creative.portraitMotion(test))
  }finally{app.creative.clearMotions(test.id);app.artworkStore.delete(test.id)}
 }

 @Test fun settingsRoundSlidersPersistAndThreeThemesRender(){
  ready();val old=app.companions.settings.value
  try{compose.onNodeWithText("设置",useUnmergedTree=true).performClick()
   compose.onNodeWithTag("pet-size").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress){it(64f)}
   compose.onNodeWithTag("pet-opacity").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress){it(40f)}
   compose.onNodeWithTag("pet-idle").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress){it(30f)}
   val saved=CompanionStore(app).settings.value;assertEquals(64,saved.petSize);assertEquals(.4f,saved.opacity,.001f);assertEquals(30,saved.idleMinutes)
   compose.onNodeWithText("伙伴与气泡不透明度").performScrollTo().assertIsDisplayed();capture("settings-appearance")
   mapOf("pink" to 0xfffff5fa.toInt(),"mint" to 0xfff0fbf6.toInt(),"night" to 0xff1c1926.toInt()).forEach{(style,color)->
    app.companions.saveSettings(app.companions.settings.value.copy(style=style));compose.waitForIdle()
    // StateFlow may be committed before the new theme reaches the screen buffer.
    compose.waitUntil(15000){auto.takeScreenshot()?.let{b->val drawn=b.getPixel(0,b.height/2)==color;b.recycle();drawn}==true}
    capture("settings-$style")
   }
  }finally{app.companions.saveSettings(old)}
 }

 @Test fun liteFourTabsSwitchWithoutParkAndReturnKeepsData(){
  ready();val before=app.artworkStore.items.value.map{it.id}
  repeat(2){listOf("作品" to "作品收藏室","宠物" to "伙伴收藏室","设置" to "我的偏好","创作" to "今天，创造哪位伙伴？").forEach{(tab,title)->
   compose.onNodeWithText(tab,useUnmergedTree=true).performClick();compose.onNodeWithText(title).assertIsDisplayed()
   compose.onAllNodesWithTag("home-park-entry").assertCountEquals(0);compose.onAllNodesWithText("食物背包").assertCountEquals(0)
  }};assertEquals(before,app.artworkStore.items.value.map{it.id});capture("lite-home")
 }

 @Test fun petDetailsAnimateWithoutActionButtonsAndEditorShowsSymmetry(){
  ready();compose.onNodeWithText("宠物",useUnmergedTree=true).performClick();compose.onNodeWithText("草莓小狗").performClick()
  compose.onNodeWithText("设计互动动作").assertExists();compose.onAllNodesWithText("喂食").assertCountEquals(0);compose.onAllNodesWithText("玩耍").assertCountEquals(0)
  compose.onNodeWithText("设计互动动作").performClick();compose.waitUntil(20000){compose.onAllNodesWithText("动作工坊").fetchSemanticsNodes().isNotEmpty()}
  compose.waitUntil(30000){compose.onAllNodesWithText("编辑",substring=false).fetchSemanticsNodes().isNotEmpty()}
  compose.onAllNodesWithText("编辑",substring=false).onFirst().performScrollTo().performClick();compose.waitUntil(20000){compose.onAllNodesWithText("正常绘制").fetchSemanticsNodes().isNotEmpty()}
  compose.onNodeWithText("左右对称").assertExists();compose.onNodeWithText("四角对称").assertExists();capture("motion-editor")
 }

 @Test fun builtinDeletionSurvivesReseedAndStoreReload()=runBlocking<Unit>{
  ready();val token="lite17-delete-"+java.util.UUID.randomUUID();val folder=File(app.cacheDir,token).apply{mkdirs()}
  val context=object:android.content.ContextWrapper(app){override fun getFilesDir()=folder;override fun getSharedPreferences(name:String,mode:Int)=app.getSharedPreferences("$token-$name",mode)}
  val store=ArtworkStore(context)
  try{SpriteCatalog.seed(context,store);val art=store.get("bear16-rose")!!;store.delete(art.id);SpriteCatalog.seed(context,store)
   assertNull(store.get(art.id));assertNull(ArtworkStore(context).get(art.id));assertTrue(store.wasDeleted(art.id))
  }finally{folder.deleteRecursively();app.deleteSharedPreferences("$token-pet-state-v1")}

 }

 @Test fun blank128DraftRenameSurvivesReload()=runBlocking<Unit>{
  ready();val id="lite17-draft";val project=PixelProject(id,"小熊的新画像",128,128,PixelOps.starterPalette,listOf(PixelFrame(List(128*128){0},180)))
  try{app.creative.save(project);assertEquals(project.name,CreativeStore(app).get(id)!!.name)
   compose.runOnUiThread{compose.activity.setContent{CyberTheme{PixelScreen(app.creative,app.artworkStore,id,null,null,null,false,onBack={},onPublished={})}}}
   compose.waitUntil(30000){compose.onAllNodesWithText("正常绘制").fetchSemanticsNodes().isNotEmpty()}
   compose.onNode(hasSetTextAction() and hasText(project.name)).performTextReplacement("改名后的小熊")
   compose.onNodeWithContentDescription("保存草稿").performClick()
   compose.waitUntil(15000){runBlocking{app.creative.get(id)?.name=="改名后的小熊"}}
   assertEquals(128,CreativeStore(app).get(id)!!.width);capture("static-editor")
  }finally{app.creative.delete(id)}
 }
}
