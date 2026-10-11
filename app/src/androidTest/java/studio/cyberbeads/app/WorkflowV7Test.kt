package studio.cyberbeads.app

import android.os.SystemClock
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import studio.cyberbeads.core.model.*
import studio.cyberbeads.core.data.*
import studio.cyberbeads.feature.pixel.PixelViewModel
import studio.cyberbeads.feature.pet.PetController
import java.util.UUID

@RunWith(AndroidJUnit4::class) class WorkflowV7Test {
 @get:Rule val compose=createAndroidComposeRule<MainActivity>()
 private val app get()=compose.activity.application as CyberApplication
 private val auto get()=InstrumentationRegistry.getInstrumentation().uiAutomation
 private fun shell(s:String)=auto.executeShellCommand(s).use{android.os.ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().readText()}
 private fun node(match:(AccessibilityNodeInfo)->Boolean):AccessibilityNodeInfo?{
  fun find(n:AccessibilityNodeInfo?):AccessibilityNodeInfo?{if(n==null)return null;if(match(n))return n;for(i in 0 until n.childCount)find(n.getChild(i))?.let{return it};return null}
  auto.serviceInfo=auto.serviceInfo.apply{flags=flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS}
  return auto.windows.firstNotNullOfOrNull{find(it.root)}
 }
 private fun waitNode(match:(AccessibilityNodeInfo)->Boolean):AccessibilityNodeInfo{val end=SystemClock.elapsedRealtime()+12000;while(SystemClock.elapsedRealtime()<end){node(match)?.let{return it};SystemClock.sleep(120)};error("Overlay node missing")}
 private fun click(s:String){waitNode{it.text?.toString()==s}.performAction(AccessibilityNodeInfo.ACTION_CLICK);SystemClock.sleep(120)}
 private fun bounds(n:AccessibilityNodeInfo)=Rect().also{n.getBoundsInScreen(it)}
 @Before fun waitForCurrentHome(){prepareTestDevice(compose.activity);
  compose.waitUntil(60000){app.artworkStore.items.value.any{it.id=="bear16-blue"}}
  compose.waitUntil(30000){compose.onAllNodesWithText("自由绘画").fetchSemanticsNodes().isNotEmpty()&&compose.onAllNodesWithText("设置",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()}
 }
 @Test fun editedPetDraftRebasesDefaultMotionsPreservesCustomAndDrafts()=runBlocking<Unit>{
  var art=app.artworkStore.create("形象联动-v7",BeadPattern(4,4,listOf(-1),List(16){if(it in 5..10)0 else -1})).complete();app.artworkStore.save(art)
  val clips=app.creative.ensureMotions(art);val custom=clips.first().copy(id=UUID.randomUUID().toString(),name="手绘保留",frames=listOf(PixelFrame(List(16){if(it==0)-1 else 0})),generated=false)
  app.creative.saveMotion(art.id,custom)
  lateinit var vm:PixelViewModel
  try{
   withContext(Dispatchers.Main){vm=PixelViewModel(app,app.creative,app.artworkStore,null,art.id,null,null,false)}
   withTimeout(10000){while(vm.project.value==null)delay(50)}
   withContext(Dispatchers.Main){vm.chooseColor(0xffaa5588.toInt());vm.beginStroke();vm.stroke(0,3,false);vm.endStroke()};vm.save()
   val after=app.artworkStore.get(art.id)!!;assertTrue(after.isComplete);assertNotEquals(art.pattern,after.pattern)
   val rebuilt=app.creative.motions(art.id);assertEquals(custom.frames,rebuilt.first{it.id==custom.id}.frames)
   clips.forEach{old->assertEquals(MotionDefaults.clip(after,old.action).frames,rebuilt.first{it.id==old.id}.frames)}
   val draft=app.creative.get(vm.project.value!!.id)!!;assertEquals(after.name,draft.name)
   vm.publish(true);assertNotNull(app.creative.get(draft.id))
   app.creative.delete(draft.id);assertNotNull(app.artworkStore.get(art.id))
  }finally{app.creative.projects.value.filter{it.name.startsWith("形象联动-v7")}.forEach{app.creative.delete(it.id)};app.creative.clearMotions(art.id);app.artworkStore.delete(art.id)}
 }
 @Test fun contextualToolsInlineNameAndSymmetryWork(){
  val title="工具验证-v7-${SystemClock.uptimeMillis()}"
  try{
  compose.onNodeWithText("自由绘画").performClick();compose.onNodeWithText("新建").performClick()
  compose.waitUntil(10000){compose.onAllNodesWithContentDescription("画笔").fetchSemanticsNodes().isNotEmpty()}
  val name=compose.onNode(hasSetTextAction() and hasText("作品名称"));name.performTextReplacement(title)
  compose.onNodeWithContentDescription("框选区域").performScrollTo().performClick()
  compose.onNodeWithContentDescription("手动移动").assertExists();compose.onNodeWithContentDescription("复制选区").assertExists()
  compose.onNodeWithContentDescription("画笔").performScrollTo().performClick();compose.onNodeWithContentDescription("手动移动").assertDoesNotExist()
  compose.onNodeWithText("四角对称").performClick()
  compose.onNodeWithContentDescription("填充").performScrollTo().performClick();compose.onNodeWithText("四角对称").assertDoesNotExist();compose.onNodeWithContentDescription("画笔").performScrollTo().performClick()
  fun imeVisible()=androidx.core.view.ViewCompat.getRootWindowInsets(compose.activity.window.decorView)?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime())==true
  println("SYMMETRY keyboard visible before stroke: ${imeVisible()}")
  if(imeVisible())shell("input keyevent KEYCODE_BACK")
  compose.waitUntil(15000){!imeVisible()};SystemClock.sleep(500);compose.waitForIdle()
  val canvas=compose.onNode(hasContentDescription("自由创作画布",substring=true))
  val size=canvas.fetchSemanticsNode().size
  assertTrue("Drawing area must be usable after keyboard closes: $size",size.width>100&&size.height>100)
  auto.takeScreenshot()?.let{b->java.io.File(app.getExternalFilesDir(null),"v17-symmetry-before-stroke.png").outputStream().use{b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};b.recycle()}
  canvas.performTouchInput{click(center.copy(x=center.x+35,y=center.y+25))}
  compose.onNodeWithContentDescription("保存草稿").performClick()
  compose.waitUntil(15000){compose.onAllNodesWithText("草稿已保存").fetchSemanticsNodes().isNotEmpty()&&app.creative.projects.value.any{it.name==title}}
  runBlocking{val summary=app.creative.projects.value.first{it.name==title};val p=app.creative.get(summary.id)!!;assertEquals(4,p.frames[0].pixels.count{it ushr 24>0})}
  }finally{runBlocking{app.creative.projects.value.filter{it.name==title}.forEach{app.creative.delete(it.id)}}}
 }
 @Test fun selectedPixelsPreviewBeforeFingerRelease()=runBlocking<Unit>{
  val id=UUID.randomUUID().toString();val p=PixelProject(id,"实时拖动-v7",16,16,listOf(-1),listOf(PixelFrame(List(256){if(it%16 in 5..8&&it/16 in 5..8)-1 else 0})))
  app.creative.save(p)
  try{
   compose.onNodeWithText("自由绘画").performClick();compose.onNodeWithText(p.name).performScrollTo().performClick()
   compose.waitUntil(10000){compose.onAllNodesWithContentDescription("画笔").fetchSemanticsNodes().isNotEmpty()}
   compose.onNodeWithContentDescription("框选区域").performScrollTo().performClick()
   val canvas=compose.onNode(hasContentDescription("自由创作画布",substring=true))
   canvas.performTouchInput{swipe(center.copy(x=center.x-45,y=center.y-45),center.copy(x=center.x+25,y=center.y+25),250)}
   compose.onNodeWithContentDescription("手动移动").performClick()
   canvas.performTouchInput{down(center);moveTo(center.copy(x=center.x+65),250)}
   compose.waitUntil(3000){canvas.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.StateDescription].startsWith("半透明移动预览")}
   canvas.performTouchInput{up()}
   canvas.assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription,"画布就绪"))
   compose.onNodeWithContentDescription("保存草稿").performClick()
   withTimeout(10000){while(app.creative.get(id)!!.frames==p.frames)delay(50)}
  }finally{app.creative.delete(id)}
 }
 @Test fun keyboardRowsStayStableAcrossCaseNumbersAndPunctuation()=runBlocking<Unit>{
  val prefs=app.companions.settings.value;app.companions.saveSettings(prefs.copy(voice=false));shell("appops set ${compose.activity.packageName} SYSTEM_ALERT_WINDOW allow")
  compose.waitUntil(10000){app.artworkStore.items.value.any{it.id=="builtin-dog"}}
  compose.runOnUiThread{PetController.chat(compose.activity,"builtin-dog")}
  try{
   val board=bounds(waitNode{it.contentDescription?.toString()=="宠物配色迷你键盘"})
   val q=bounds(waitNode{it.text?.toString()=="q"});val z=bounds(waitNode{it.text?.toString()=="z"});assertTrue(kotlin.math.abs(q.width()-z.width())<=2)
   assertNull(node{it.text?.toString()=="标点"});assertNull(node{it.text?.toString()=="ABC"})
   val space=bounds(waitNode{it.text?.toString()=="空格"});assertTrue(space.width()>bounds(waitNode{it.text?.toString()=="123"}).width()*1.8f)
   click("123");assertEquals(board,bounds(waitNode{it.contentDescription?.toString()=="宠物配色迷你键盘"}));click("1");click("，")
   assertNull(node{it.text?.toString()=="123"});assertNull(node{it.text?.toString()=="标点"})
   click("ABC");click("中/英");assertNull(node{it.text?.toString()=="ABC"});click("↑");click("A")
   click("123");click("?");click("ABC");click("⇧");click("a")
   assertEquals(board,bounds(waitNode{it.contentDescription?.toString()=="宠物配色迷你键盘"}))
   assertEquals(space,bounds(waitNode{it.text?.toString()=="空格"}))
   assertTrue(waitNode{it.contentDescription?.toString()=="桌面聊天输入"}.text.toString().contains("1，A?a"))
  }finally{compose.runOnUiThread{PetController.stop(compose.activity)};app.companions.saveSettings(prefs)}
 }
}
