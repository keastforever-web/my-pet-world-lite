package studio.cyberbeads.app

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import studio.cyberbeads.core.data.*
import studio.cyberbeads.core.model.*
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class LiteRosterReleaseTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = compose.activity.application as CyberApplication
    private fun ready() {
        prepareTestDevice(compose.activity)
        compose.waitUntil(60000) { app.artworkStore.items.value.any { it.id == "bear16-blue" } }
        compose.waitUntil(30000) { compose.onAllNodesWithText("自由绘画").fetchSemanticsNodes().isNotEmpty() }
        // The home content exists underneath the launch artwork before navigation appears.
        compose.waitUntil(30000) { compose.onAllNodesWithText("宠物", useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty() }
    }
    @Test fun upgradeShowsOnlyFourteenDefaultsAndNoRetiredPet() = runBlocking<Unit> {
        ready(); app.seedCompanions(); SpriteCatalog.seed(app, app.artworkStore); app.artworkStore.refresh()
        val ids = app.artworkStore.items.value.map { it.id }.toSet()
        assertEquals(14, LiteCompanions.builtinIds.size)
        assertEquals(LiteCompanions.builtinIds, ids.intersect(LiteCompanions.builtinIds))
        assertTrue(ids.intersect(LiteCompanions.retiredIds).isEmpty())
        LiteCompanions.retiredIds.forEach { assertNull(app.artworkStore.get(it)) }
        compose.onNodeWithText("宠物", useUnmergedTree=true).performClick()
        compose.onAllNodesWithText("耄耋猫 · 像素版").assertCountEquals(0)
        compose.onAllNodesWithText("奶龙 · 像素版").assertCountEquals(0)
    }
    @Test fun retiredRecordsRemainOnDiskAndUserArtworkSurvives() = runBlocking<Unit> {
        ready()
        val token="roster-${UUID.randomUUID()}"; val folder=File(app.cacheDir,token).apply { mkdirs() }
        val context=object:ContextWrapper(app) {
            override fun getFilesDir()=folder
            override fun getSharedPreferences(name:String,mode:Int)=app.getSharedPreferences("$token-$name",mode)
        }
        try {
            val old=ArtworkStore(context)
            old.save(Artwork("builtin-maodie","曾修改的旧伙伴",1,BuiltinPets.pattern("builtin-cat")).complete())
            val custom=old.create("自己的小猫",BuiltinPets.pattern("builtin-cat")).complete(); old.save(custom)
            val current=ArtworkStore(context,LiteCompanions.retiredIds); current.refresh()
            assertNull(current.get("builtin-maodie")); assertEquals(listOf(custom.id),current.items.value.map { it.id })
            assertTrue(File(folder,"artworks/builtin-maodie.json").isFile)
            assertEquals("曾修改的旧伙伴",ArtworkStore(context).get("builtin-maodie")!!.name)
            SpriteCatalog.seed(context,current); current.delete("bear16-blue"); SpriteCatalog.seed(context,current)
            assertNull(ArtworkStore(context,LiteCompanions.retiredIds).get("bear16-blue"))
            assertNotNull(current.get(custom.id))
        } finally { folder.deleteRecursively(); app.deleteSharedPreferences("$token-pet-state-v1") }
    }
    @Test fun allTenSpritePetsKeepTheirEightCompleteAnimations() = runBlocking<Unit> {
        ready()
        LiteCompanions.spriteIds.forEach { id ->
            val clips=SpriteCatalog.motions(app,id)
            assertEquals(id,8,clips.size)
            assertTrue(clips.any { it.action=="idle" }); assertTrue(clips.any { it.action=="walk" })
            clips.forEach { clip ->
                assertEquals(128,clip.width); assertEquals(128,clip.height)
                assertTrue(clip.frames.size>=8)
                assertTrue(clip.frames.all { it.pixels.size==128*128 && it.durationMs>0 })
            }
        }
    }
}
