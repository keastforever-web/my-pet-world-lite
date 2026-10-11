package studio.cyberbeads.core.model

import kotlin.math.*

data class PixelRegion(val left:Int,val top:Int,val right:Int,val bottom:Int){fun contains(x:Int,y:Int)=x in left..right&&y in top..bottom}
object CanvasOps {
    fun resize(pixels:List<Int>,w:Int,h:Int,nw:Int,nh:Int):List<Int>{
        require(nw in 1..128&&nh in 1..128)
        val dx=(nw-w)/2;val dy=(nh-h)/2
        return List(nw*nh){i->val x=i%nw-dx;val y=i/nw-dy;if(x in 0 until w&&y in 0 until h)pixels[y*w+x]else 0}
    }
    fun move(pixels:List<Int>,w:Int,region:PixelRegion?,dx:Int,dy:Int):List<Int>{
        if(region==null)return PixelOps.shift(pixels,w,dx,dy)
        val h=pixels.size/w;val out=pixels.toMutableList()
        for(y in region.top..region.bottom)for(x in region.left..region.right)if(x in 0 until w&&y in 0 until h)out[y*w+x]=0
        for(y in region.top..region.bottom)for(x in region.left..region.right){val nx=x+dx;val ny=y+dy;if(x in 0 until w&&y in 0 until h&&nx in 0 until w&&ny in 0 until h)out[ny*w+nx]=pixels[y*w+x]}
        return out
    }
    fun fit(pattern:BeadPattern,w:Int,h:Int,occupancy:Double=1.0):BeadPattern{
        require(w in 1..128&&h in 1..128)
        val scale=min(max(1,floor(w*occupancy).toInt()).toDouble()/pattern.width,max(1,floor(h*occupancy).toInt()).toDouble()/pattern.height)
        val fw=(pattern.width*scale).roundToInt().coerceIn(1,w);val fh=(pattern.height*scale).roundToInt().coerceIn(1,h)
        val ox=(w-fw)/2;val oy=(h-fh)/2
        return pattern.copy(width=w,height=h,cells=List(w*h){i->val x=i%w-ox;val y=i/w-oy;if(x in 0 until fw&&y in 0 until fh)pattern.cells[(y*pattern.height/fh)*pattern.width+x*pattern.width/fw]else -1})
    }
    private fun lab(c:Int):DoubleArray{
        fun linear(v:Int):Double{val x=v/255.0;return if(x<=.04045)x/12.92 else ((x+.055)/1.055).pow(2.4)}
        val r=linear(c ushr 16 and 255);val g=linear(c ushr 8 and 255);val b=linear(c and 255)
        fun f(v:Double)=if(v>.008856)v.pow(1.0/3)else 7.787*v+16.0/116
        val x=f((.4124564*r+.3575761*g+.1804375*b)/.95047);val y=f(.2126729*r+.7151522*g+.0721750*b);val z=f((.0193339*r+.1191920*g+.9503041*b)/1.08883)
        return doubleArrayOf(116*y-16,500*(x-y),200*(y-z))
    }
    fun match(colors:List<Int>,bank:ColorBank):Map<Int,Int>{
        val targets=bank.colors.map(::lab)
        return colors.distinct().associateWith{c->val a=lab(c);bank.colors[targets.indices.minBy{i->(0..2).sumOf{(a[it]-targets[i][it]).pow(2)}}]}
    }
    fun brand(pattern:BeadPattern,bank:ColorBank):BeadPattern{
        val matches=match(pattern.palette,bank);val colors=pattern.palette.map{matches.getValue(it)}.distinct()
        return pattern.copy(palette=colors,cells=pattern.cells.map{if(it<0)-1 else colors.indexOf(matches.getValue(pattern.palette[it]))},brandId=if(bank.brand)bank.id else null,paletteId=bank.id,colorCodes=if(bank.codes.isEmpty())emptyList()else colors.map{bank.codes.getOrNull(bank.colors.indexOf(it))?:""})
    }
    fun pattern(p:PixelProject):BeadPattern{
        val colors=p.frames.first().pixels.filter{it ushr 24>0}.distinct();require(colors.isNotEmpty()){"画布还是空的"};require(colors.size<=512){"作品最多512种颜色"}
        val index=colors.withIndex().associate{it.value to it.index}
        return BeadPattern(p.width,p.height,colors,p.frames.first().pixels.map{if(it ushr 24==0)-1 else index.getValue(it)},p.brandId,if(p.colorCodes.isEmpty())emptyList()else colors.map{p.colorCodes.getOrNull(p.palette.indexOf(it))?:"?"},p.paletteId)
    }
}
