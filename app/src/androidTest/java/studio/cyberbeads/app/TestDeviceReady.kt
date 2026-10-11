package studio.cyberbeads.app

import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry

/** Recover only the emulator System UI ANR; never dismiss an App ANR or permission request. */
internal fun prepareTestDevice(activity:android.app.Activity){
 val auto=InstrumentationRegistry.getInstrumentation().uiAutomation
 fun shell(s:String)=auto.executeShellCommand(s).use{ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().readText()}
 shell("input keyevent KEYCODE_WAKEUP");shell("wm dismiss-keyguard")
 auto.serviceInfo=auto.serviceInfo.apply{flags=flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS}
 val nodes=mutableListOf<AccessibilityNodeInfo>()
 fun walk(n:AccessibilityNodeInfo?){if(n==null)return;nodes+=n;repeat(n.childCount){walk(n.getChild(it))}}
 auto.windows.forEach{walk(it.root)}
 if(nodes.any{it.text?.toString()?.startsWith("System UI") == true && it.text.toString().contains("responding")}){
  nodes.firstOrNull{it.text?.toString()=="Close app"&&it.packageName?.toString()=="android"}?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
  SystemClock.sleep(1000)
 }
 val ime=androidx.core.view.ViewCompat.getRootWindowInsets(activity.window.decorView)?.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime())?.bottom?:0
 if(ime>0){shell("input keyevent KEYCODE_BACK");SystemClock.sleep(500)}
}
