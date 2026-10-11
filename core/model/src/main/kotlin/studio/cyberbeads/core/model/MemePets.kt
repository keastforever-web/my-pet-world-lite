package studio.cyberbeads.core.model
import kotlin.math.*
/** Video-derived 96 px frames are registered by the data layer; old drawings remain for migration detection. */
object MemePets {
    val names=linkedMapOf("builtin-nailong" to "奶龙 · 像素版","builtin-maodie" to "耄耋猫 · 像素版")
    private val videos=mutableMapOf<String,List<BeadPattern>>()
    fun register(id:String,frames:List<BeadPattern>){require(id in names&&frames.size==16&&frames.all{it.width==96&&it.height==96});videos[id]=frames}
    fun pattern(id:String,frame:Int=0,action:String="idle"):BeadPattern{
        videos[id]?.let{frames->val i=Math.floorMod(frame,16);val index=when(action){"idle","sleep"->if(i<8)i else 15-i;"pet","wave"->(i+3)%16;"play"->i;"walk"->15-i;else->i};return frames[index]}
        return legacyPattern(id,frame,action)
    }
    fun legacyPattern(id:String,frame:Int=0,action:String="idle"):BeadPattern{
        val n=48;val p=MutableList(n*n){-1};val dragon=id=="builtin-nailong"
        val colors=(if(dragon)listOf(0xffffce40,0xffefa82d,0xffffe883,0xff573b32,0xfffff7d7,0xffeb867d,0xffffe5a4,0xffa8d2a5)else listOf(0xffc89054,0xff946139,0xfff4e6d3,0xff493b32,0xffd5b589,0xffdf938e,0xfffff8e8,0xffddbc88)).map{it.toInt()}
        fun dot(x:Int,y:Int,c:Int){if(x in 0 until n&&y in 0 until n)p[y*n+x]=c}
        fun rect(x:Int,y:Int,w:Int,h:Int,c:Int){for(yy in y until y+h)for(xx in x until x+w)dot(xx,yy,c)}
        fun oval(cx:Int,cy:Int,rx:Int,ry:Int,c:Int){for(y in max(0,cy-ry)..min(47,cy+ry))for(x in max(0,cx-rx)..min(47,cx+rx))if((x-cx).toDouble().pow(2)/max(1,rx*rx)+(y-cy).toDouble().pow(2)/max(1,ry*ry)<=1)dot(x,y,c)}
        val phase=frame%24;val sway=sin(phase*PI/12).roundToInt();val bounce=if(action=="walk")abs(sin(phase*PI/6)*2).roundToInt()else 0
        val blink=action=="sleep"||phase in 19..20;val chew=false&&phase%6 in 1..3
        if(dragon){
            // Tiny limbs, oversized soft belly and a broad curious face.
            oval(35,34-bounce,8,4,1);rect(10,40-bounce,10,4,1);rect(28,40+bounce,10,4,1)
            oval(24,30-bounce,15+if(action=="play")sway else 0,13,0);oval(25,31-bounce,10,10,2)
            oval(23,15-bounce,14,11,0);oval(30,20-bounce,12,7,0);oval(31,23-bounce,9,3,2)
            rect(12,3-bounce,4,4,1);rect(24,2-bounce,4,4,1)
            val ey=13-bounce
            oval(18,ey,3,if(blink)1 else 4,3);oval(29,ey,3,if(blink)1 else 4,3)
            if(!blink){rect(17,ey-2,2,2,4);rect(28,ey-2,2,2,4)}
            dot(34,18-bounce,1);dot(38,18-bounce,1)
            if(chew||action=="play"){oval(27,23-bounce,4,if(chew)3 else 2+abs(sway),3);oval(27,25-bounce,3,1,5)}else{rect(22,23-bounce,9,1,3);dot(21,22-bounce,3);dot(31,22-bounce,3)}
            val arm=if(action in listOf("wave","pet","play"))((sin(phase*PI/6)+1)*4).roundToInt()else 0
            oval(9,29-arm-bounce,4,5,0);oval(39,29+if(false)-4 else -arm,4,5,0)
            if(false){oval(28,33-bounce,5,4,1);rect(24,31-bounce,8,3,6);rect(25,35-bounce,6,1,3)}
        }else{
            val hiss=action in listOf("pet","play")&&phase in 4..16
            oval(24,34-bounce,13,10,0);oval(24,36-bounce,8,8,2)
            rect(12,40-bounce,8,5,2);rect(29,40+bounce,8,5,2);oval(37+sway,35,6,3,1)
            if(!hiss){rect(8,8,8,10,0);rect(31,8,8,10,0);rect(10,10,4,5,5);rect(33,10,4,5,5)}else{oval(8,17,5,3,1);oval(39,17,5,3,1)}
            oval(24,22-bounce,18,15,0);oval(24,28-bounce,14,9,2)
            // M-shaped forehead stripes and narrow amber eyes.
            rect(21,8-bounce,3,7,1);rect(27,9-bounce,3,6,1);rect(15,11-bounce,3,5,1);rect(32,12-bounce,3,5,1)
            rect(7,21-bounce,5,2,1);rect(36,21-bounce,5,2,1)
            oval(16,21-bounce,5,if(blink)1 else 3,3);oval(31,21-bounce,5,if(blink)1 else 3,3)
            if(!blink){rect(13,20-bounce,6,2,7);rect(28,20-bounce,6,2,7);rect(16,19-bounce,1,4,3);rect(31,19-bounce,1,4,3)}
            rect(22,25-bounce,5,2,5);dot(24,27-bounce,3)
            if(hiss){oval(24,32-bounce,7,6,3);rect(20,28-bounce,2,4,6);rect(27,28-bounce,2,4,6);oval(24,36-bounce,4,2,5);if(phase%4<2){rect(1,26,3,1,4);rect(43,26,3,1,4)}}else if(chew){oval(24,30-bounce,3,2,3);rect(23,31-bounce,3,1,5)}else{rect(22,29-bounce,5,1,3);dot(21,28-bounce,3);dot(27,28-bounce,3)}
            rect(4,28-bounce,8,1,4);rect(35,28-bounce,9,1,4);rect(5,31-bounce,6,1,4);rect(37,31-bounce,7,1,4)
            if(action=="wave"){oval(7,30-(phase%8/2),4,5,2);rect(5,26-(phase%8/2),1,2,1);rect(8,26-(phase%8/2),1,2,1)}
            if(false){oval(24,40,6,2,1);rect(21,37,6,3,4)}
        }
        return BeadPattern(n,n,colors,p)
    }
}
