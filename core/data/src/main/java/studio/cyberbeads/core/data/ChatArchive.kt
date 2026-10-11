package studio.cyberbeads.core.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import studio.cyberbeads.core.model.ChatLine
import java.io.File

data class PetMemory(val text:String="",val through:Long=0)
/** Indexed append-only chat, paged by a stable sequence; migration is transactional and idempotent. */
internal class ChatArchive(context:Context):SQLiteOpenHelper(context,"pet-chat-v5.db",null,1){
    private val legacy=File(context.filesDir,"pet-chats-v2")
    override fun onCreate(db:SQLiteDatabase){db.execSQL("CREATE TABLE messages(seq INTEGER PRIMARY KEY AUTOINCREMENT, pet TEXT NOT NULL, role TEXT NOT NULL, text TEXT NOT NULL, at INTEGER NOT NULL)");db.execSQL("CREATE INDEX pet_seq ON messages(pet,seq)");db.execSQL("CREATE TABLE memory(pet TEXT PRIMARY KEY, text TEXT NOT NULL, through_seq INTEGER NOT NULL)");db.execSQL("CREATE TABLE migrated(pet TEXT PRIMARY KEY)")}
    override fun onUpgrade(db:SQLiteDatabase,old:Int,new:Int){error("Unsupported chat database upgrade")}
    private fun migrate(id:String){val db=writableDatabase;db.rawQuery("SELECT pet FROM migrated WHERE pet=?",arrayOf(id)).use{if(it.moveToFirst())return};db.beginTransaction();try{
        val f=File(legacy,"$id.json");if(f.exists()){val a=JSONArray(f.readText());for(i in 0 until a.length()){val j=a.getJSONObject(i);db.execSQL("INSERT INTO messages(pet,role,text,at) VALUES(?,?,?,?)",arrayOf<Any>(id,j.getString("role"),j.getString("text"),j.getLong("at")))}}
        db.execSQL("INSERT OR IGNORE INTO migrated(pet) VALUES(?)",arrayOf(id));db.setTransactionSuccessful()
    }finally{db.endTransaction()}}
    fun page(id:String,before:Long=Long.MAX_VALUE,limit:Int=40):List<ChatLine>{migrate(id);return readableDatabase.rawQuery("SELECT seq,role,text,at FROM messages WHERE pet=? AND seq<? ORDER BY seq DESC LIMIT ?",arrayOf(id,before.toString(),limit.coerceIn(1,200).toString())).use{c->buildList{while(c.moveToNext())add(ChatLine(c.getString(1),c.getString(2),c.getLong(3),c.getLong(0)))}}.reversed()}
    fun append(id:String,line:ChatLine){migrate(id);writableDatabase.execSQL("INSERT INTO messages(pet,role,text,at) VALUES(?,?,?,?)",arrayOf<Any>(id,line.role,line.text.take(6000),line.at))}
    fun memory(id:String):PetMemory=readableDatabase.rawQuery("SELECT text,through_seq FROM memory WHERE pet=?",arrayOf(id)).use{if(it.moveToFirst())PetMemory(it.getString(0),it.getLong(1))else PetMemory()}
    fun remember(id:String,m:PetMemory){writableDatabase.execSQL("INSERT OR REPLACE INTO memory(pet,text,through_seq) VALUES(?,?,?)",arrayOf<Any>(id,m.text.take(1600),m.through))}
    fun pending(id:String,after:Long,limit:Int=24):List<ChatLine>{migrate(id);return readableDatabase.rawQuery("SELECT seq,role,text,at FROM messages WHERE pet=? AND seq>? ORDER BY seq LIMIT ?",arrayOf(id,after.toString(),limit.coerceIn(1,40).toString())).use{c->buildList{while(c.moveToNext())add(ChatLine(c.getString(1),c.getString(2),c.getLong(3),c.getLong(0)))}}}
    fun clear(id:String){migrate(id);writableDatabase.beginTransaction();try{writableDatabase.delete("messages","pet=?",arrayOf(id));writableDatabase.delete("memory","pet=?",arrayOf(id));writableDatabase.setTransactionSuccessful()}finally{writableDatabase.endTransaction()};android.util.AtomicFile(File(legacy,"$id.json")).delete()}
}
