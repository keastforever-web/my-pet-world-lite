package studio.cyberbeads.core.data
import org.json.*
import studio.cyberbeads.core.model.*
object NoteJson{
 fun ink(lines:List<List<InkPoint>>)=JSONArray().apply{lines.forEach{line->put(JSONArray().apply{line.forEach{p->put(JSONArray(listOf(p.x,p.y)))}})}}
 fun encode(n:CardNote)=JSONObject().put("author",n.author).put("text",n.text).put("time",n.time).put("ink",ink(n.ink))
 fun decode(j:JSONObject):CardNote{val a=j.optJSONArray("ink");val lines=if(a==null)emptyList()else (0 until a.length()).map{i->val line=a.getJSONArray(i);(0 until line.length()).map{k->val p=line.getJSONArray(k);InkPoint(p.getInt(0),p.getInt(1))}};return CardNote(j.optString("author"),j.optString("text"),j.optLong("time"),lines)}
}
