package studio.cyberbeads.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject
import studio.cyberbeads.core.data.*
import studio.cyberbeads.core.model.*
import studio.cyberbeads.core.imaging.PetPixels

@RunWith(AndroidJUnit4::class)
class CompanionDeviceTest {
    @Test fun encryptedSecretsAndHistoriesAreIndependentAndSurviveReopening()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val store=CompanionStore(context);val a="test-profile-a";val b="test-profile-b"
        store.clearHistory(a);store.clearHistory(b)
        store.saveProfile(PetProfile(a,nickname="A",persona="pink dog persona"));store.saveProfile(PetProfile(b,nickname="B",persona="purple snake persona"))
        store.saveSecret(a,"fixture-secret-a");store.saveSecret(b,"fixture-secret-b")
        store.append(a,ChatLine("user","Only A knows this"));store.append(b,ChatLine("assistant","Only B knows this"))
        val reopened=CompanionStore(context)
        assertEquals("fixture-secret-a",reopened.secret(a));assertEquals("fixture-secret-b",reopened.secret(b))
        assertEquals("A",reopened.profile(a).nickname);assertEquals("B",reopened.profile(b).nickname)
        assertEquals("Only A knows this",reopened.history(a).single().text);assertEquals("Only B knows this",reopened.history(b).single().text)
        val xml=java.io.File(context.applicationInfo.dataDir,"shared_prefs/companion-settings-v2.xml")
        // commit is a synchronization barrier for earlier apply writes.
        context.getSharedPreferences("companion-settings-v2",0).edit().putBoolean("test-barrier",true).commit()
        assertFalse(xml.readText().contains("fixture-secret"))
        store.clearHistory(a);assertEquals(1,store.history(b).size);store.saveSecret(a,"");assertFalse(store.hasSecret(a));assertTrue(store.hasSecret(b))
        store.clearHistory(b);store.saveSecret(b,"")
    }
    @Test fun chatProtocolValidatesHttpsAndUsesOnlySuppliedPetHistory(){
        assertEquals("https://example.com/v1/chat/completions",PetChatClient.endpoint("https://example.com/v1/").toString())
        listOf("http://example.com/v1","https://key@example.com/v1","https://example.com/v1?key=secret").forEach{assertTrue(runCatching{PetChatClient.endpoint(it)}.isFailure)}
        val payload=JSONObject(PetChatClient.payload(PetProfile("test",persona="my persona",model="my-model"),listOf(ChatLine("user","my history")),"hello"))
        assertEquals("my-model",payload.getString("model"));assertFalse(payload.getBoolean("stream"));assertEquals(3,payload.getJSONArray("messages").length())
        assertEquals("my persona",payload.getJSONArray("messages").getJSONObject(0).getString("content"))
        assertEquals("hello",PetChatClient.response("""{"choices":[{"message":{"content":"hello"}}]}"""))
        assertTrue(runCatching{PetChatClient.response("""{"choices":[]}""")}.isFailure)
    }
    @Test fun petSpriteHasNoSemiTransparentPixels(){
        BuiltinPets.names.keys.forEach{id->val b=PetPixels.frame(id,BuiltinPets.pattern(id),3,"play");val pixels=IntArray(b.width*b.height);b.getPixels(pixels,0,b.width,0,0,b.width,b.height)
            assertTrue(pixels.all{it ushr 24 in listOf(0,255)});b.recycle()}
    }
    @Test fun themeSettingsSurviveReopening(){val context=InstrumentationRegistry.getInstrumentation().targetContext;val store=CompanionStore(context);val original=store.settings.value
        try{store.saveSettings(original.copy(style="mint",petSize=150,motion=false));val s=CompanionStore(context).settings.value;assertEquals("mint",s.style);assertEquals(150,s.petSize);assertFalse(s.motion)}finally{store.saveSettings(original)}}
    @Test fun sendPersistsOnlyItsOwnConversationAndFailuresDoNotPolluteHistory()=runBlocking {
        val store=CompanionStore(InstrumentationRegistry.getInstrumentation().targetContext)
        val a="test-client-a";val b="test-client-b";store.clearHistory(a);store.clearHistory(b)
        try {
            store.saveProfile(PetProfile(a,persona="A persona",endpoint="https://example.com/v1",model="fixture"));store.saveApi(PetProfile(CompanionStore.SHARED_API,endpoint="https://example.com/v1",model="fixture"));store.saveSecret(CompanionStore.SHARED_API,"test-a-token")
            store.append(b,ChatLine("user","B private history"))
            val client=PetChatClient(store,ChatTransport { uri,token,body ->
                assertEquals("https://example.com/v1/chat/completions",uri.toString());assertEquals("test-a-token",token)
                assertFalse(body.contains("B private history"));assertTrue(body.contains("A persona"))
                """{"choices":[{"message":{"content":"A reply"}}]}"""
            })
            assertEquals("A reply",client.send(a,"hello"));assertEquals(listOf("user","assistant"),store.history(a).map{it.role});assertEquals(1,store.history(b).size)
            client.send(a,"idle prompt",true);assertEquals(listOf("user","assistant","assistant"),store.history(a).map{it.role})
            val broken=PetChatClient(store,ChatTransport{_,_,_->error("fixture network failure")})
            assertTrue(runCatching{broken.send(a,"do not save failed request")}.isFailure);assertEquals(3,store.history(a).size)
        }finally{store.clearHistory(a);store.clearHistory(b);store.saveSecret(CompanionStore.SHARED_API,"")}
    }
}
