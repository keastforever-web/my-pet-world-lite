package studio.cyberbeads.core.model

import kotlin.math.*

data class PixelFood(val id:String,val name:String,val width:Int,val height:Int,val pixels:List<Int>,val quantity:Int=5,val builtin:Boolean=false){
    init{require(id.matches(Regex("[a-zA-Z0-9-]{1,80}")));require(name.isNotBlank()&&name.length<=40);require(width in 1..128&&height in 1..128&&pixels.size==width*height);require(pixels.any{it ushr 24>0});require(quantity in -1..9999);require((quantity==-1)==builtin)}
    val glow get()=0xff6aae9d.toInt()
}
/** Normalize the visible drawing before scoring; names and transparent RGB never affect recipes. */
object FoodRecipe{
    fun normalized(pixels:List<Int>,width:Int,height:Int):List<Int>{
        require(width in 1..128&&height in 1..128&&pixels.size==width*height)
        val visible=pixels.indices.filter{pixels[it] ushr 24>0};require(visible.isNotEmpty()){"先画一点食物吧"}
        val l=visible.minOf{it%width};val t=visible.minOf{it/width};val w=visible.maxOf{it%width}-l+1;val h=visible.maxOf{it/width}-t+1
        return List(1024){i->val x=l+(i%32*w/32).coerceAtMost(w-1);val y=t+(i/32*h/32).coerceAtMost(h-1);pixels[y*width+x].let{if(it ushr 24==0)0 else it or 0xff000000.toInt()}}
    }
    fun builtins():List<PixelFood>{return listOf("berry" to "草莓奶糖","cookie" to "星星饼干","apple" to "青苹果").mapIndexed{index,(id,name)->
        val p=MutableList(24*24){0};fun rect(x:Int,y:Int,w:Int,h:Int,c:Int){for(yy in y until y+h)for(xx in x until x+w)if(xx in 0..23&&yy in 0..23)p[yy*24+xx]=c}
        fun oval(cx:Int,cy:Int,rx:Int,ry:Int,c:Int){for(y in 0..23)for(x in 0..23)if((x-cx).toDouble().pow(2)/rx.pow(2)+(y-cy).toDouble().pow(2)/ry.pow(2)<=1)p[y*24+x]=c}
        when(index){0->{oval(12,12,7,7,0xffe76b98.toInt());rect(5,6,14,4,0xfff695b6.toInt());rect(10,3,4,4,0xff75ae80.toInt());for(y in listOf(9,13,17))for(x in listOf(9,14))rect(x,y,1,2,0xffffe6ac.toInt())}
            1->{oval(12,12,9,9,0xffd99b52.toInt());oval(12,11,8,8,0xffffd591.toInt());for((x,y)in listOf(8 to 8,14 to 7,11 to 12,16 to 14,7 to 15))rect(x,y,2,2,0xff986344.toInt())}
            else->{oval(9,13,6,7,0xff85bc63.toInt());oval(15,13,6,7,0xffa9d880.toInt());rect(11,3,2,5,0xff775448.toInt());rect(13,3,5,3,0xff5d9763.toInt());rect(6,9,2,4,0xffe4f3bd.toInt())}}
        PixelFood("food-$id",name,24,24,p,-1,true)
    }}
    private fun Int.pow(n:Int)=toDouble().pow(n)
}
