package studio.cyberbeads.app
import android.os.SystemClock
import android.graphics.Rect
import android.view.MotionEvent
import android.view.InputDevice
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import studio.cyberbeads.core.data.*
import studio.cyberbeads.core.model.*
import studio.cyberbeads.feature.pixel.PixelViewModel
import studio.cyberbeads.feature.pet.PetController
import java.util.UUID

@RunWith(AndroidJUnit4::class) class WorkflowV8Test{
 @get:Rule val compose=createAndroidComposeRule<MainActivity>()
 private val app get()=compose.activity.application as CyberApplication
 private val auto get()=InstrumentationRegistry.getInstrumentation().uiAutomation
 private fun shell(s:String)=auto.executeShellCommand(s).use{android.os.ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().readText()}
 private fun node(match:(AccessibilityNodeInfo)->Boolean):AccessibilityNodeInfo?{fun find(n:AccessibilityNodeInfo?):AccessibilityNodeInfo?{if(n==null)return null;if(match(n))return n;for(i in 0 until n.childCount)find(n.getChild(i))?.let{return it};return null};auto.serviceInfo=auto.serviceInfo.apply{flags=flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS};return auto.windows.firstNotNullOfOrNull{find(it.root)}}
 private fun waitNode(match:(AccessibilityNodeInfo)->Boolean):AccessibilityNodeInfo{val end=SystemClock.elapsedRealtime()+12000;while(SystemClock.elapsedRealtime()<end){node(match)?.let{return it};SystemClock.sleep(100)};error("Missing expected overlay control")}
 @Before fun waitForCurrentHome(){prepareTestDevice(compose.activity);
  compose.waitUntil(60000){app.artworkStore.items.value.any{it.id=="bear16-blue"}}
  compose.waitUntil(30000){compose.onAllNodesWithText("自由绘画").fetchSemanticsNodes().isNotEmpty()&&compose.onAllNodesWithText("设置",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()}
 }
 @Test fun copyMovePasteRestoresSourceAndKeepsMovedCopy()=runBlocking<Unit>{
  val id="v8-copy-"+UUID.randomUUID();val pixels=List(64){if(it%8 in 1..2&&it/8 in 1..2)0xffff4488.toInt() else 0};app.creative.save(PixelProject(id,"复制测试",8,8,listOf(0xffff4488.toInt()),listOf(PixelFrame(pixels))))
  try{lateinit var vm:PixelViewModel;withContext(Dispatchers.Main){vm=PixelViewModel(app,app.creative,app.artworkStore,id,null,null,null,false)};withTimeout(10000){while(vm.project.value==null)delay(20)}
   withContext(Dispatchers.Main){vm.selectRegion(9,18);vm.selectionCommand("copy");vm.movePixels(9,13);vm.selectionCommand("paste")}
   val out=vm.project.value!!.frames[0].pixels;assertEquals(8,out.count{it ushr 24>0});for(y in 1..2)for(x in listOf(1,2,5,6))assertEquals(0xffff4488.toInt(),out[y*8+x]);vm.save();assertEquals(out,CreativeStore(app).get(id)!!.frames[0].pixels)
  }finally{app.creative.delete(id)}
 }
 @Test fun motionOrderStableAndFinalEnabledCannotBeRemoved()=runBlocking<Unit>{
  val art=app.artworkStore.create("v8动作约束",BuiltinPets.pattern("builtin-cat"));val list=app.creative.ensureMotions(art);val first=list.first{it.action=="idle"};val second=app.creative.copyMotion(art,first.id)
  try{val order=app.creative.motions(art.id).map{it.id};app.creative.saveMotion(art.id,first.copy(triggered=false));assertEquals(order,app.creative.motions(art.id).map{it.id});assertTrue(runCatching{app.creative.saveMotion(art.id,second.copy(triggered=false))}.isFailure);assertTrue(runCatching{app.creative.deleteMotion(art.id,second.id)}.isFailure);assertTrue(CreativeStore(app).motions(art.id).first{it.id==second.id}.triggered)}finally{app.creative.clearMotions(art.id);app.artworkStore.delete(art.id)}
 }
 @Test fun packageImportPreservesDisabledFirstClipAndOrder()=runBlocking<Unit>{
  val art=app.artworkStore.create("v8分享顺序",BuiltinPets.pattern("builtin-cat")).complete();app.artworkStore.save(art);var imported:String?=null
  try{val first=app.creative.ensureMotions(art).first{it.action=="idle"};val second=app.creative.copyMotion(art,first.id);app.creative.saveMotion(art.id,first.copy(triggered=false));val order=app.creative.motions(art.id).map{it.id};val copy=PetPackage.import(PetPackage.export(art.id,app.artworkStore,app.creative,app.companions),app.artworkStore,app.creative,app.companions);imported=copy.id;val actual=app.creative.motions(copy.id);assertEquals(order,actual.map{it.id});assertFalse(actual.first{it.id==first.id}.triggered);assertTrue(actual.first{it.id==second.id}.triggered)}finally{app.creative.clearMotions(art.id);app.artworkStore.delete(art.id);imported?.let{app.creative.clearMotions(it);app.artworkStore.delete(it)}}
 }


 @Test fun brushToolsAndBoardToggleShareZoomAndMiniMap(){
  compose.onNodeWithText("自由绘画").performClick();compose.onNodeWithText("新建").performClick();compose.waitUntil(10000){compose.onAllNodesWithContentDescription("画笔").fetchSemanticsNodes().isNotEmpty()}
  compose.onNodeWithText("左右对称").assertIsDisplayed();compose.onNodeWithContentDescription("笔触宽度滑条").assertIsDisplayed()
  compose.onNodeWithContentDescription("画板显示方式").performScrollTo().performClick();compose.onNodeWithText("左右对称").assertDoesNotExist();compose.onNodeWithContentDescription("放大画布").performClick();compose.onNodeWithContentDescription("整体预览小窗，点击定位").assertIsDisplayed()
 }
 @Test fun desktopDeleteKeyRepeats(){
  val original=app.companions.settings.value;app.companions.saveSettings(original.copy(edgeHide=false,voice=false));shell("appops set ${compose.activity.packageName} SYSTEM_ALERT_WINDOW allow");compose.waitUntil(10000){app.artworkStore.items.value.any{it.id=="builtin-dog"}}
  try{compose.runOnUiThread{PetController.start(compose.activity,"builtin-dog")};compose.waitUntil(10000){"builtin-dog" in PetController.runningIds.value}
   compose.runOnUiThread{PetController.chat(compose.activity,"builtin-dog")};val input=waitNode{it.contentDescription?.toString()=="桌面聊天输入"};input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,android.os.Bundle().apply{putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,"abcdefghijklmnop")});val n=waitNode{it.text?.toString()=="⌫"};val b=Rect().also{n.getBoundsInScreen(it)};val t=SystemClock.uptimeMillis();val down=MotionEvent.obtain(t,t,MotionEvent.ACTION_DOWN,b.centerX().toFloat(),b.centerY().toFloat(),0);down.source=InputDevice.SOURCE_TOUCHSCREEN;auto.injectInputEvent(down,true);down.recycle();SystemClock.sleep(950);val up=MotionEvent.obtain(t,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,b.centerX().toFloat(),b.centerY().toFloat(),0);up.source=InputDevice.SOURCE_TOUCHSCREEN;auto.injectInputEvent(up,true);up.recycle();val length=waitNode{it.contentDescription?.toString()=="桌面聊天输入"}.text.length;assertTrue("Holding delete must remove multiple characters",length<14);SystemClock.sleep(200);assertEquals(length,waitNode{it.contentDescription?.toString()=="桌面聊天输入"}.text.length)
  }finally{compose.runOnUiThread{PetController.stop(compose.activity)};app.companions.saveSettings(original)}
 }
}
