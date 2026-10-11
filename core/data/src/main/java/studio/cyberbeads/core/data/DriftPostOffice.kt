package studio.cyberbeads.core.data
import org.json.*
import studio.cyberbeads.core.model.*

class DriftPostOffice(val client:ParkClient,val garden:GardenStore){
 private fun decode(j:JSONObject)=TravelPostcard(j.getString("id"),j.getString("pet"),j.getString("name"),j.getString("region"),j.getString("location"),j.getString("caption"),j.getLong("time"),j.getInt("w"),j.getInt("h"),j.getJSONArray("pixels").let{a->(0 until a.length()).map{a.getInt(it)}},j.optString("place"),j.getJSONArray("notes").let{a->(0 until a.length()).map{a.getJSONObject(it).let{n->NoteJson.decode(n)}}},remote=true,hop=j.optInt("hop"))
 private fun encode(c:TravelPostcard)=JSONObject().put("id",c.id).put("pet",c.petId).put("name",c.petName).put("region",c.region).put("location",c.location).put("caption",c.caption).put("place",c.placeId).put("w",c.width).put("h",c.height).put("pixels",JSONArray(c.pixels)).put("notes",JSONArray().apply{c.notes.forEach{n->put(NoteJson.encode(n))}})
 suspend fun receive():Int{require(client.configured()){"先到好友乐园连接你的服务器，再开启漂流收信"};val a=client.call("/postcards/receive").getJSONArray("cards");for(i in 0 until a.length()){val c=decode(a.getJSONObject(i));garden.importCard(c);client.call("/postcards/ack",JSONObject().put("id",c.id))};return a.length()}
 suspend fun sync(c:TravelPostcard){if(c.remote&&c.status=="owned"){val j=client.call("/postcards/card",JSONObject().put("id",c.id));garden.importCard(decode(j.getJSONObject("card")))}}
 suspend fun append(c:TravelPostcard,text:String,ink:List<List<InkPoint>> = emptyList()){require(text.isNotBlank()||ink.isNotEmpty());if(c.remote){val j=client.call("/postcards/append",JSONObject().put("id",c.id).put("revision",c.notes.size).put("text",text).put("ink",NoteJson.ink(ink)));garden.importCard(decode(j.getJSONObject("card")))}else garden.appendCard(c.id,CardNote(client.nickname,text,System.currentTimeMillis(),ink))}
 suspend fun send(c:TravelPostcard){require(client.configured()){"先到好友乐园连接服务器；未连接不会假装寄出"};require(c.status in listOf("owned","sending"));garden.cardStatus(c.id,"sending");client.call("/postcards/send",JSONObject().put("id",c.id).put("nonce",c.id+"-"+c.hop+"-"+c.notes.size).put("revision",c.notes.size).put("card",encode(c)));garden.cardStatus(c.id,"sent")}
}
