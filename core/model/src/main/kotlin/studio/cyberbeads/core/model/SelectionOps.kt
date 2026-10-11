package studio.cyberbeads.core.model

/** Clipboard stores a detached rectangle; transparent pixels are copied too. */
data class PixelClipboard(val width:Int,val height:Int,val pixels:List<Int>)
object SelectionOps {
    fun copy(pixels:List<Int>,w:Int,r:PixelRegion):PixelClipboard {
        val h=pixels.size/w
        val width=r.right-r.left+1;val height=r.bottom-r.top+1
        return PixelClipboard(width,height,List(width*height){i->val x=r.left+i%width;val y=r.top+i/width;if(x in 0 until w&&y in 0 until h)pixels[y*w+x]else 0})
    }
    fun paste(pixels:List<Int>,w:Int,clip:PixelClipboard,x:Int,y:Int):List<Int> {
        val out=pixels.toMutableList();val h=pixels.size/w
        clip.pixels.forEachIndexed{i,c->val px=x+i%clip.width;val py=y+i/clip.width;if(px in 0 until w&&py in 0 until h)out[py*w+px]=c}
        return out
    }
    fun transform(clip:PixelClipboard,mode:String):PixelClipboard {
        val nw=if(mode=="rotate")clip.height else clip.width;val nh=if(mode=="rotate")clip.width else clip.height
        val out=MutableList(nw*nh){0}
        clip.pixels.forEachIndexed{i,c->val x=i%clip.width;val y=i/clip.width;val nx=when(mode){"horizontal"->clip.width-1-x;"rotate"->clip.height-1-y;else->x};val ny=when(mode){"vertical"->clip.height-1-y;"rotate"->x;else->y};out[ny*nw+nx]=c}
        return PixelClipboard(nw,nh,out)
    }
    fun stroke(pixels:List<Int>,w:Int,from:Int,to:Int,color:Int,size:Int,symmetry:String):List<Int> {
        val h=pixels.size/w
        fun reflect(i:Int,horizontal:Boolean,vertical:Boolean)=(if(vertical)h-1-i/w else i/w)*w+(if(horizontal)w-1-i%w else i%w)
        val mask=PixelOps.stroke(List(pixels.size){0},w,from,to,1,size)
        val out=pixels.toMutableList()
        mask.forEachIndexed{i,v->if(v!=0){
            out[i]=color
            if(symmetry in listOf("horizontal","four"))out[reflect(i,true,false)]=color
            if(symmetry in listOf("vertical","four"))out[reflect(i,false,true)]=color
            if(symmetry=="four")out[reflect(i,true,true)]=color
        }}
        return out
    }
    fun nextName(existing:Collection<String>,base:String="我的宠物"):String {
        val used=existing.map{it.trim().lowercase()}.toSet();var n=1
        while("$base-$n".lowercase() in used)n++
        return "$base-$n"
    }
}
