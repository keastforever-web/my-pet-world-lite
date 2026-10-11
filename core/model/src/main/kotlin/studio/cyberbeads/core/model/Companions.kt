package studio.cyberbeads.core.model

data class PetProfile(val id: String, val nickname: String = "", val persona: String = "你是一只温柔俏皮的像素宠物。用中文简短自然地回应，不要假装看得到用户的屏幕。", val endpoint: String = "", val model: String = "", val proactive: Boolean = false)
data class ChatLine(val role: String, val text: String, val at: Long = System.currentTimeMillis(),val sequence:Long=0)
data class AppPreferences(val style: String = "pink", val motion: Boolean = true, val petSize: Int = 120, val voice: Boolean = false, val idleMinutes: Int = 15, val quietNight: Boolean = true, val edgeHide:Boolean=true, val edgeSeconds:Int=20, val freeRoam:Boolean=false, val opacity:Float=.78f)

/** Original pixel sprites. Each frame changes eyes, ears, feet or tail rather than translating a static image. */
object BuiltinPets {
    val names = linkedMapOf("builtin-dog" to "草莓小狗", "builtin-snake" to "紫芋小蛇", "builtin-bunny" to "奶油兔兔", "builtin-cat" to "薄荷小猫").apply{putAll(MemePets.names)}
    fun pattern(id: String, frame: Int = 0, action: String = "idle"): BeadPattern {
        if(id in MemePets.names)return MemePets.pattern(id,frame,action)
        val pixels = MutableList(24 * 24) { -1 }
        val palette = when (id) {
            "builtin-dog" -> listOf(0xffff9fc4,0xffd9699b,0xff4c344b,0xffffe5ef,0xffffcfdf)
            "builtin-snake" -> listOf(0xffb59aed,0xff8062bd,0xff433652,0xffffe7f5,0xffe3cff8)
            "builtin-bunny" -> listOf(0xffffe8cf,0xffecb2bb,0xff523c46,0xffffffff,0xffffd7e5)
            else -> listOf(0xffa2dece,0xff6eafa6,0xff344b49,0xfffff4ef,0xfff6b9cd)
        }.map { it.toInt() }
        fun dot(x:Int,y:Int,c:Int) { if(x in 0..23 && y in 0..23) pixels[y*24+x]=c }
        fun rect(x:Int,y:Int,w:Int,h:Int,c:Int) { for(yy in y until y+h) for(xx in x until x+w) dot(xx,yy,c) }
        fun oval(cx:Int,cy:Int,rx:Int,ry:Int,c:Int) { for(y in 0..23) for(x in 0..23) if((x-cx)*(x-cx).toFloat()/(rx*rx)+(y-cy)*(y-cy).toFloat()/(ry*ry)<=1f) dot(x,y,c) }
        val blink = action=="sleep" || frame%12==10
        val move = if(frame%2==0) 0 else 1
        if(id=="builtin-snake") {
            oval(11,19,9,3,1); oval(12,18,7,2,0); rect(7,10,6,9,0)
            oval(12,8,7,5,0); rect(6,11,12,2,4); rect(17,7,3,2,0)
            rect(18,9,3+move,1,1)
            rect(8,7,2,if(blink) 1 else 3,2); rect(14,7,2,if(blink) 1 else 3,2)
            if(!blink) { dot(8,7,3);dot(14,7,3) }; rect(11,11,2,1,2)
            if(action=="play" || frame%4==0) {rect(11,12,1,2,1);dot(10,14,1);dot(12,14,1)}
            rect(18+move,18,4-move,2,0)
        } else {
            oval(12,16,6,6,0); rect(7,20-move,4,3,1);rect(14,20+move,4,3-move,1)
            if(id=="builtin-dog") {rect(3,5+move,4,9,1);rect(17,5-move,4,9,1)}
            else if(id=="builtin-bunny") {rect(7,0+move,3,8,0);rect(14,0,3,8,0);rect(8,1+move,1,5,1);rect(15,1,1,5,1)}
            else {rect(5,2,4,7,0);rect(15,2,4,7,0);rect(6,3,2,3,1);rect(16,3,2,3,1)}
            oval(12,9,8,6,0); oval(12,12,4,2,3)
            rect(7,8,2,if(blink) 1 else 3,2);rect(15,8,2,if(blink) 1 else 3,2)
            if(!blink) {dot(7,8,3);dot(15,8,3)}
            rect(11,11,2,1,2);dot(12,12,2);rect(6,12,2,1,4);rect(17,12,2,1,4)
            if(false) rect(11,13,2,1+move,1)
            if(action=="wave" || action=="play") {rect(3,14-move*2,4,3,0);rect(18,14+move,3,3,0)}
            else {rect(5,16,3,3,0);rect(17,16,3,3,0)}
            rect(18,16-move*2,4,2,0);rect(21,14-move*2,2,3,1)
        }
        return BeadPattern(24,24,palette,pixels)
    }
}
