package studio.cyberbeads.app
import android.graphics.Rect
import android.os.*
import android.view.*
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
import studio.cyberbeads.feature.pet.*
import java.io.File
@RunWith(AndroidJUnit4::class) class DesktopV16Test {
 @get:Rule val compose=createAndroidComposeRule<MainActivity>()
 private val app get()=compose.activity.application as CyberApplication
 private val auto get()=InstrumentationRegistry.getInstrumentation().uiAutomation
 private fun shell(s:String)=auto.executeShellCommand(s).use{ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().readText()}
 private fun nodes():List<AccessibilityNodeInfo>{auto.serviceInfo=auto.serviceInfo.apply{flags=flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS};val all=mutableListOf<AccessibilityNodeInfo>();fun walk(n:AccessibilityNodeInfo?){if(n==null)return;all+=n;repeat(n.childCount){walk(n.getChild(it))}};auto.windows.forEach{walk(it.root)};return all}
 private fun await(match:(AccessibilityNodeInfo)->Boolean):AccessibilityNodeInfo{val end=SystemClock.elapsedRealtime()+20000;while(SystemClock.elapsedRealtime()<end){nodes().firstOrNull(match)?.let{return it};SystemClock.sleep(120)};File(app.getExternalFilesDir(null),"v16-desktop-nodes.txt").writeText(nodes().joinToString("\n"){"${it.text} | ${it.contentDescription} | ${rect(it)}"});capture("desktop-failure");error("Desktop node missing; running=${PetController.runningIds.value}, visible=${PetController.visibleIds.value}")}
 private fun rect(n:AccessibilityNodeInfo)=Rect().also{n.getBoundsInScreen(it)}
 private fun capture(name:String){auto.takeScreenshot()?.let{b->File(app.getExternalFilesDir(null),"v16-$name.png").outputStream().use{b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};b.recycle()}}
 @Test fun radialUsesVisibleCentreRoamingMovesAndKeyboardDeleteRepeats()=runBlocking<Unit>{compose.waitUntil(60000){app.artworkStore.items.value.any{it.id=="bear16-blue"}};shell("input keyevent KEYCODE_WAKEUP");shell("wm dismiss-keyguard");prepareTestDevice(compose.activity);shell("appops set ${compose.activity.packageName} SYSTEM_ALERT_WINDOW allow");compose.mainClock.advanceTimeBy(500);compose.waitForIdle();val old=app.companions.settings.value
  app.companions.saveSettings(old.copy(petSize=96,edgeHide=false,motion=false,voice=false,freeRoam=false,opacity=.65f))
  val px=List(256){if(it%16 in 2..7&&it/16 in 3..10)0xffb7d49a.toInt() else 0};val art=app.artworkStore.create("菜单偏心验证",PixelOps.toPattern(px,16,16));app.artworkStore.save(art.complete())
  try{app.creative.importMotions(art,listOf(MotionClip("test-idle","安静","idle",16,16,listOf(PixelFrame(px,180)),actionName="安静"),MotionClip("test-wave","招手","wave",16,16,listOf(PixelFrame(px,180)),actionName="招手")))
   compose.runOnUiThread{PetController.start(compose.activity,art.id)};val pet=await{it.contentDescription?.startsWith(art.name+"，点击互动")==true};val before=rect(pet);SystemClock.sleep(600);assertTrue(pet.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK));val a=await{it.contentDescription=="桌面动作：安静"};val b=await{it.contentDescription=="桌面动作：招手"};SystemClock.sleep(800);val ar=rect(a);val br=rect(b);val central=rect(await{it.contentDescription=="圆环中央的${art.name}"})
   assertEquals(before.width(),central.width());assertEquals(before.height(),central.height());assertEquals(central.left+central.width()*5/16f,(ar.centerX()+br.centerX())/2f,3f);assertEquals(central.top+central.height()*7/16f,(ar.centerY()+br.centerY())/2f,3f);capture("radial-centre")
   b.performAction(AccessibilityNodeInfo.ACTION_CLICK);SystemClock.sleep(400);val after=rect(await{it.contentDescription?.startsWith(art.name+"，点击互动")==true});assertEquals(central,after)
   await{it.contentDescription?.startsWith(art.name+"，点击互动")==true}.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK);await{it.text?.toString()=="聊天"}.performAction(AccessibilityNodeInfo.ACTION_CLICK);val erase=await{it.text?.toString()=="⌫"};val zed=await{it.text?.toString()=="z"};assertEquals(rect(zed).centerY(),rect(erase).centerY());val keyboardBefore=rect(await{it.contentDescription=="宠物配色迷你键盘"});await{it.text?.toString()=="123"}.performAction(AccessibilityNodeInfo.ACTION_CLICK);SystemClock.sleep(200);assertEquals(keyboardBefore.height(),rect(await{it.contentDescription=="宠物配色迷你键盘"}).height());await{it.text?.toString()=="ABC"}.performAction(AccessibilityNodeInfo.ACTION_CLICK)
   val input=await{it.contentDescription=="桌面聊天输入"};input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,Bundle().apply{putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,"abcdefghijkl")});val er=rect(await{it.text?.toString()=="⌫"});val t=SystemClock.uptimeMillis();auto.injectInputEvent(MotionEvent.obtain(t,t,MotionEvent.ACTION_DOWN,er.centerX().toFloat(),er.centerY().toFloat(),0).apply{source=InputDevice.SOURCE_TOUCHSCREEN},true);SystemClock.sleep(850);auto.injectInputEvent(MotionEvent.obtain(t,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,er.centerX().toFloat(),er.centerY().toFloat(),0).apply{source=InputDevice.SOURCE_TOUCHSCREEN},true);assertTrue(await{it.contentDescription=="桌面聊天输入"}.text.length<9);capture("keyboard");await{it.text?.toString()=="关闭"}.performAction(AccessibilityNodeInfo.ACTION_CLICK)
   app.companions.saveSettings(old.copy(petSize=96,edgeHide=false,motion=true,voice=false,freeRoam=true,opacity=.65f));val start=rect(await{it.contentDescription?.startsWith(art.name+"，点击互动")==true});val deadline=SystemClock.elapsedRealtime()+15000;var moved=false;while(SystemClock.elapsedRealtime()<deadline&&!moved){SystemClock.sleep(300);moved=rect(await{it.contentDescription?.startsWith(art.name+"，点击互动")==true}).left!=start.left};assertTrue("Free roaming must move the window",moved);capture("roaming")
  }finally{compose.runOnUiThread{PetController.remove(compose.activity,art.id)};SystemClock.sleep(300);app.companions.saveSettings(old);app.creative.clearMotions(art.id);app.artworkStore.delete(art.id)}
 }
}
