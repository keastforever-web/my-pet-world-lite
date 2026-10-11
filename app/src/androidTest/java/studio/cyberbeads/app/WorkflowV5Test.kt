package studio.cyberbeads.app

import androidx.compose.ui.test.*
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.json.JSONObject
import studio.cyberbeads.core.data.*
import studio.cyberbeads.core.model.*
import studio.cyberbeads.feature.pixel.PixelViewModel
import java.util.UUID

@RunWith(AndroidJUnit4::class) class WorkflowV5Test{
 @get:Rule val compose=createAndroidComposeRule<MainActivity>()
 private val app get()=compose.activity.application as CyberApplication
 @Before fun waitForCurrentHome(){prepareTestDevice(compose.activity);
  compose.waitUntil(60000){(compose.activity.application as CyberApplication).artworkStore.items.value.any{it.id=="bear16-blue"}}
  compose.waitUntil(30000){compose.onAllNodesWithText("自由绘画").fetchSemanticsNodes().isNotEmpty()&&compose.onAllNodesWithText("设置",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()}
 }
 @Test fun exitPromptDiscardsUnsavedBlankWithoutAddingDraft(){
  val count=runBlocking{app.creative.refresh();app.creative.projects.value.size}
  compose.onNodeWithText("自由绘画").performClick();compose.onNodeWithText("新建").performClick();compose.waitUntil(10000){compose.onAllNodesWithContentDescription("画笔").fetchSemanticsNodes().isNotEmpty()}
  compose.onNode(hasContentDescription("自由创作画布",substring=true)).performTouchInput{click(center)};compose.onNodeWithContentDescription("返回").performClick();compose.onNodeWithText("把灵感留在这里 ♡").assertExists();compose.onNodeWithText("放弃本次修改").performClick()
  compose.waitUntil(10000){compose.onAllNodesWithText("新建").fetchSemanticsNodes().isNotEmpty()};assertEquals(count,app.creative.projects.value.size)
 }
 @Test fun sharedApiIsAbsentFromPetFormAndBackDoesNotOpenDetail(){
  compose.onNodeWithText("宠物",useUnmergedTree=true).performClick();compose.waitUntil(10000){compose.onAllNodesWithText("聊天设置").fetchSemanticsNodes().isNotEmpty()};compose.onAllNodesWithText("聊天设置").onFirst().performClick();compose.onNodeWithText("性格与记忆").assertExists();compose.onNode(hasText("API Key",substring=true)).assertDoesNotExist();compose.onNodeWithText("返回").performClick();compose.onNodeWithText("关闭详情").assertDoesNotExist()
 }
 @Test fun stableDraftAndRenameAcrossReopen()=runBlocking{
  val art=app.artworkStore.create("before",BeadPattern(2,2,listOf(-1),listOf(0,-1,-1,0)))
  suspend fun model():PixelViewModel{lateinit var vm:PixelViewModel;withContext(Dispatchers.Main){vm=PixelViewModel(app,app.creative,app.artworkStore,null,art.id,null,null,false)};withTimeout(10000){while(vm.project.value==null)delay(50)};return vm}
  try{val a=model();withContext(Dispatchers.Main){a.rename("统一名称")};a.save();val b=model();assertEquals(a.project.value!!.id,b.project.value!!.id);assertEquals("统一名称",app.artworkStore.get(art.id)!!.name);assertEquals("统一名称",b.project.value!!.name);b.save();app.creative.refresh();assertEquals(1,app.creative.projects.value.count{it.id==a.project.value!!.id})}finally{app.creative.linked(art.id)?.let{app.creative.delete(it.id)};app.artworkStore.delete(art.id)}
 }
 @Test fun pagedArchiveAndCompactionPreserveIsolation()=runBlocking{
  val s=app.companions;val id="test-memory-v5";val other="test-private-v5";s.clearHistory(id);s.clearHistory(other)
  try{repeat(125){s.append(id,ChatLine(if(it%2==0)"user" else "assistant","history-$it"))};s.append(other,ChatLine("user","OTHER PRIVATE"));val last=s.page(id,limit=40);val older=s.page(id,last.first().sequence,40);assertEquals(40,last.size);assertEquals("history-124",last.last().text);assertTrue(last.map{it.sequence}.intersect(older.map{it.sequence}.toSet()).isEmpty());assertEquals(125,s.history(id).size)
   s.saveApi(PetProfile(CompanionStore.SHARED_API,endpoint="https://example.com/v1",model="fixture"));s.saveSecret(CompanionStore.SHARED_API,"inert-v5-token");s.saveProfile(PetProfile(id,persona="独立性格A"))
   var requests=0;val client=PetChatClient(s,ChatTransport{_,token,body->requests++;assertEquals("inert-v5-token",token);assertFalse(body.contains("OTHER PRIVATE"));if(requests==1)assertTrue(body.contains("独立性格A"));"""{"choices":[{"message":{"content":"整理后的记忆"}}]}"""})
   client.send(id,"hello");assertEquals(2,requests);assertTrue(s.memory(id).through>0);assertEquals("整理后的记忆",s.memory(id).text);assertEquals("",s.memory(other).text)
   val payload=JSONObject(PetChatClient.payload(s.effectiveProfile(id),List(100){ChatLine("user","x".repeat(6000))},"hello",s.memory(id).text));assertTrue(payload.toString().length<17000);assertTrue(payload.toString().contains("整理后的记忆"))
  }finally{s.clearHistory(id);s.clearHistory(other);s.saveSecret(CompanionStore.SHARED_API,"")}
 }
 @Test fun petFileRoundTripKeepsAllActionsAndNoSecrets()=runBlocking{
  val art=app.artworkStore.create("分享小宠物",BuiltinPets.pattern("builtin-cat"));app.artworkStore.save(art.complete());val clip=MotionClip("special-wave","特别挥手","wave",2,2,listOf(PixelFrame(listOf(-1,0,0,-1),400)),true,true);app.creative.saveMotion(art.id,clip);app.companions.saveProfile(PetProfile(art.id,persona="独立温柔性格"));app.companions.append(art.id,ChatLine("user","private history forbidden"));var imported:String?=null
  try{val bytes=PetPackage.export(art.id,app.artworkStore,app.creative,app.companions);assertFalse(String(bytes).contains("private history forbidden"));assertFalse(String(bytes).contains("secret"));val copy=PetPackage.import(bytes,app.artworkStore,app.creative,app.companions);imported=copy.id;assertNotEquals(art.id,copy.id);assertTrue(copy.isComplete);assertEquals(art.pattern,copy.pattern);assertEquals(clip,app.creative.motions(copy.id).first{it.id==clip.id});assertEquals(app.creative.motions(art.id).map{it.action}.toSet(),app.creative.motions(copy.id).map{it.action}.toSet());assertEquals("独立温柔性格",app.companions.profile(copy.id).persona);assertTrue(app.companions.history(copy.id).isEmpty())
   val count=app.artworkStore.items.value.size;assertTrue(runCatching{PetPackage.import("{}".toByteArray(),app.artworkStore,app.creative,app.companions)}.isFailure);assertEquals(count,app.artworkStore.items.value.size)
  }finally{for(id in listOfNotNull(art.id,imported)){app.creative.clearMotions(id);app.artworkStore.delete(id);app.companions.clearHistory(id)}}
 }
 @Test fun brandSeriesAreValidAndCustomPaletteDeletionKeepsProjects()=runBlocking{
  val store=app.creative;assertTrue(store.brands.size>=8);store.brands.forEach{assertEquals(it.colors.size,it.codes.size);assertTrue(it.codes.none{c->c.startsWith("UNKNOWN")});assertTrue(it.colors.all{c->c ushr 24==255})};val bank=store.savePalette("删除测试",listOf(-1,0xffaaaaaa.toInt()));val p=PixelProject("palette-delete-v5","test",1,1,bank.colors,listOf(PixelFrame(listOf(-1))));try{store.save(p);store.deletePalette(bank.id);assertFalse(store.palettes.value.any{it.id==bank.id});assertEquals(bank.colors,store.get(p.id)!!.palette)}finally{store.delete(p.id)}
 }
 @Test fun photoHonorsRectangularCanvasAndBrandThenClearsPreview()=runBlocking{
  val bitmap=android.graphics.Bitmap.createBitmap(48,32,android.graphics.Bitmap.Config.ARGB_8888);val canvas=android.graphics.Canvas(bitmap);canvas.drawColor(android.graphics.Color.WHITE);val ink=android.graphics.Paint();ink.color=0xffe95689.toInt();canvas.drawRect(12f,4f,36f,28f,ink)
  val file=java.io.File(app.cacheDir,"canvas-photo-v5.png");file.outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
  lateinit var vm:studio.cyberbeads.feature.studio.StudioViewModel
  try{withContext(Dispatchers.Main){vm=studio.cyberbeads.feature.studio.StudioViewModel(app,app.subjectSegmenter,app.artworkStore);vm.import(android.net.Uri.fromFile(file))};withTimeout(30000){while(vm.state.value.busy)delay(100)}
   val bank=app.creative.brands.first{it.id=="brand-mard-221-alfonse-doudou"}
   withContext(Dispatchers.Main){vm.setParameters(grid=40,rows=24,subject=false,brandId=bank.id)};withTimeout(15000){while(vm.state.value.busy)delay(50)};val pattern=vm.state.value.pattern!!;assertEquals(40,pattern.width);assertEquals(24,pattern.height);assertEquals(bank.id,pattern.brandId);assertTrue(pattern.palette.all{it in bank.colors});assertEquals(pattern.palette.size,pattern.colorCodes.size)
   withContext(Dispatchers.Main){vm.consumed()};assertNull(vm.state.value.pattern);assertNull(vm.state.value.bitmap);assertNull(vm.state.value.savedId)
  }finally{file.delete()}
 }

 @Test fun delayedNameLoadUpdatesFormWithoutReplacingUserEdits(){
  val display=mutableStateOf("我的伙伴")
  compose.runOnUiThread{compose.activity.setContent{studio.cyberbeads.core.ui.CyberTheme{studio.cyberbeads.feature.pet.ChatScreen("test-late-name",display.value,app.companions,app.chatClient){}}}}
  compose.waitForIdle();compose.runOnIdle{display.value="加载后的真实名称"};compose.onNodeWithText("性格与记忆").performClick()
  val field=compose.onNode(hasSetTextAction() and hasText("名称 · 作品和草稿同步"));field.assertTextContains("加载后的真实名称");field.performTextReplacement("用户正在编辑的名称");compose.runOnIdle{display.value="外部名称更新"};field.assertTextContains("用户正在编辑的名称")
 }

}
