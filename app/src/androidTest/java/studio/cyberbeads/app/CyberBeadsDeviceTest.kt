package studio.cyberbeads.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import studio.cyberbeads.core.data.ArtworkStore
import studio.cyberbeads.core.imaging.*
import studio.cyberbeads.core.model.*
import studio.cyberbeads.core.ui.demoPattern
import studio.cyberbeads.feature.pet.PetController
import java.io.File

@RunWith(AndroidJUnit4::class)
class CyberBeadsDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

 @Before fun waitForCurrentHome(){prepareTestDevice(compose.activity);
  compose.waitUntil(60000){(compose.activity.application as CyberApplication).artworkStore.items.value.any{it.id=="bear16-blue"}}
  compose.waitUntil(30000){compose.onAllNodesWithText("自由绘画").fetchSemanticsNodes().isNotEmpty()&&compose.onAllNodesWithText("设置",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()}
 }
    @Test fun savedPatternPersistsAndConcurrentPlacementDoesNotLoseUpdates() = runBlocking {
        val context = compose.activity.applicationContext
        val store = (context as CyberApplication).artworkStore
        val art = store.create("设备测试-持久化", demoPattern())
        val indices = art.pattern.cells.indices.filter { art.pattern.cells[it] >= 0 }.take(20)
        coroutineScope { indices.map { index -> async(Dispatchers.Default) { store.update(art.id) { it.place(index) } } }.awaitAll() }
        val reopened = ArtworkStore(context).get(art.id)!!
        assertEquals(indices.toSet(), reopened.placed)
        assertEquals(art.pattern, reopened.pattern)
        val sprite = BeadRenderer.sprite(reopened.pattern)
        assertEquals(0, sprite.getPixel(0, 0) ushr 24)
        val chart = BeadRenderer.chart(reopened.pattern, reopened.name)
        assertTrue(chart.width >= 720)
        val folder = context.getExternalFilesDir("verification")!!
        folder.mkdirs()
        File(folder, "export-chart.png").outputStream().use { chart.compress(Bitmap.CompressFormat.PNG, 100, it) }
        File(folder, "export-sticker.png").outputStream().use { sprite.compress(Bitmap.CompressFormat.PNG, 100, it) }
        sprite.recycle(); chart.recycle(); store.delete(art.id)
    }

    @Test fun bundledModelRunsOfflineAndReturnsAUsableMask() = runBlocking {
        val context = compose.activity.applicationContext as CyberApplication
        assertEquals(android.content.pm.PackageManager.PERMISSION_GRANTED,
            context.checkSelfPermission(android.Manifest.permission.INTERNET))
        // Original deterministic fixture; no personal photo is accessed or uploaded.
        val bitmap = Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap); canvas.drawColor(Color.rgb(244, 235, 217))
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = Color.rgb(30, 90, 65); canvas.drawRoundRect(144f, 145f, 160f, 278f, 8f, 8f, p)
        canvas.drawOval(154f, 195f, 226f, 231f, p)
        p.color = Color.rgb(211, 55, 97)
        for (i in 0..5) {
            canvas.save(); canvas.rotate(i * 60f, 150f, 130f)
            canvas.drawOval(122f, 48f, 178f, 139f, p); canvas.restore()
        }
        p.color = Color.rgb(252, 191, 58); canvas.drawCircle(150f, 130f, 33f, p)
        val cutout = withTimeout(120_000) { context.subjectSegmenter.isolate(bitmap,false) }
        assertEquals(bitmap.width, cutout.width)
        val pixels = IntArray(320 * 320); cutout.getPixels(pixels, 0, 320, 0, 0, 320, 320)
        assertTrue("Foreground mask must contain visible pixels", pixels.count { it ushr 24 > 180 } > 500)
        assertTrue("Background must actually be removed", pixels.count { it ushr 24 < 50 } > 500)
        val pattern = BeadQuantizer.convert(pixels, 320, 320, 32, 12)
        assertTrue(pattern.beadCount > 0); assertTrue(pattern.palette.size <= 12)
        val folder = context.getExternalFilesDir("verification")!!; folder.mkdirs()
        File(folder, "model-input.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        File(folder, "model-cutout.png").outputStream().use { cutout.compress(Bitmap.CompressFormat.PNG, 100, it) }
        File(folder, "model-beads.png").outputStream().use { BeadRenderer.sprite(pattern).compress(Bitmap.CompressFormat.PNG, 100, it) }
        context.artworkStore.create("离线提取 · 小花", pattern)
        bitmap.recycle(); cutout.recycle()
    }

    @Test fun demoFlowsThroughSavingCraftingAndPetPage() {
        compose.onNodeWithText("先用像素猫体验一下  →").performScrollTo().performClick()
        compose.waitUntil(30000){compose.onAllNodesWithContentDescription("放大画布").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("进入画板 →").assertIsDisplayed().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("画笔").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("画笔").assertExists()
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("完成图案，去拼装") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("完成图案，去拼装").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("保存为待拼装图纸").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("保存为待拼装图纸").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("一键拼好整件作品").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("一键拼好整件作品").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("拼装完成！").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("让它成为桌面宠物").performScrollTo().performClick()
        compose.onNodeWithText("伙伴收藏室").assertExists()
        compose.onNodeWithText("草莓小狗").assertExists()
    }
    @Test fun zoomShowsMinimapAndResetHidesIt() {
        compose.onNodeWithText("先用像素猫体验一下  →").performScrollTo().performClick()
        compose.waitUntil(30000){compose.onAllNodesWithContentDescription("放大画布").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithContentDescription("放大画布").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("整体预览小窗，点击定位").assertExists()
        compose.onNodeWithText("150%").performClick()
        compose.onNodeWithContentDescription("整体预览小窗，点击定位").assertDoesNotExist()
    }
    @Test fun multipleWindowsCanCoexistAndCloseIndependently() {
        val context=compose.activity
        val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.executeShellCommand("appops set ${compose.activity.packageName} SYSTEM_ALERT_WINDOW allow").use{
            android.os.ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().readText()
        }
        assertTrue(android.provider.Settings.canDrawOverlays(context))
        compose.waitUntil(10_000){(context.application as CyberApplication).artworkStore.items.value.any{it.id=="builtin-dog"}}
        compose.runOnUiThread{PetController.stop(context)}
        compose.waitUntil(10_000){PetController.runningIds.value.isEmpty()}
        compose.runOnUiThread{PetController.start(context,"builtin-dog");PetController.start(context,"builtin-snake")}
        try {
            compose.waitUntil(60_000){PetController.runningIds.value==setOf("builtin-dog","builtin-snake")}
            assertEquals(setOf("builtin-dog","builtin-snake"),PetController.runningIds.value)
            compose.runOnUiThread{PetController.interact(context,"builtin-dog","play");PetController.remove(context,"builtin-dog")}
            compose.waitUntil(10_000){PetController.runningIds.value==setOf("builtin-snake")}
        }finally{compose.runOnUiThread{PetController.stop(context)};compose.waitUntil(10_000){PetController.runningIds.value.isEmpty()}}
    }
}

