package studio.cyberbeads.core.model

import kotlin.math.*

data class PixelFrame(val pixels:List<Int>,val durationMs:Int=160) {init{require(durationMs in 40..2000)}}
data class PixelProject(val id:String,val name:String,val width:Int,val height:Int,val palette:List<Int>,val frames:List<PixelFrame>,val updated:Long=System.currentTimeMillis(),val artworkId:String?=null,val referenceOpacity:Float=.35f,val motionAction:String?=null,val motionId:String?=null,val triggered:Boolean=true,val random:Boolean=false,val brandId:String?=null,val colorCodes:List<String> = emptyList(),val paletteId:String?=brandId){
    init{require(id.matches(Regex("[a-zA-Z0-9-]{1,80}")));require(width in 1..128&&height in 1..128);require(palette.size in 1..512);require(frames.size in 1..32&&frames.all{it.pixels.size==width*height});require(referenceOpacity in 0f..1f)}
}
data class PixelSummary(val id:String,val name:String,val width:Int,val height:Int,val frames:Int,val updated:Long,val motionAction:String?=null,val preview:List<Int> = emptyList())
data class ColorBank(val id:String,val name:String,val colors:List<Int>,val codes:List<String> = emptyList(),val brand:Boolean=false){init{require(colors.size in 1..512);require(colors.all{it ushr 24==255})}}
data class MotionClip(val id:String,val name:String,val action:String,val width:Int,val height:Int,val frames:List<PixelFrame>,val triggered:Boolean=true,val random:Boolean=false,val brandId:String?=null,val colorCodes:List<String> = emptyList(),val generated:Boolean=false,val actionName:String?=null){
    init{require(id.matches(Regex("[a-zA-Z0-9-]{1,80}")));require(MotionDefaults.validAction(action));require(width in 1..128&&height in 1..128);require(frames.size in 1..32&&frames.all{it.pixels.size==width*height})}
    val durationMs get()=frames.sumOf{it.durationMs}
    fun frameAt(elapsedMs:Long,loop:Boolean=true):PixelFrame {var t=if(loop)Math.floorMod(elapsedMs,durationMs.toLong())else elapsedMs.coerceIn(0,durationMs.toLong()-1);for(f in frames){if(t<f.durationMs)return f;t-=f.durationMs};return frames.last()}
}
object PixelOps {
    val starterPalette=listOf(0xff493246,0xffffffff,0xffffa9cb,0xffd96399,0xffffe3ef,0xffb39be8,0xff7e62b5,0xffa3dfce,0xff5ca998,0xffffd480,0xfff49a78,0xff89bce6).map{it.toInt()}
    val banks=listOf(ColorBank("pastel","奶霜赛博",starterPalette),ColorBank("retro","复古像素",listOf(0xff222034,0xff45283c,0xff663931,0xff8f563b,0xffdf7126,0xffd9a066,0xffeec39a,0xfffbf236,0xff99e550,0xff6abe30,0xff37946e,0xff4b692f,0xff524b24,0xff323c39,0xff3f3f74,0xff306082,0xff5b6ee1,0xff639bff,0xff5fcde4,0xffcbdbfc,0xffffffff,0xff9badb7,0xff847e87,0xff696a6a,0xff595652,0xff76428a,0xffac3232,0xffd95763,0xffd77bba,0xff8f974a,0xff8a6f30,0xffe8d4b8).map{it.toInt()}),ColorBank("gray","明暗练习",(0..7).map{val c=it*255/7;0xff000000.toInt() or(c shl 16)or(c shl 8)or c}))
    fun fromPattern(p:BeadPattern)=p.cells.map{if(it<0)0 else p.palette[it] or 0xff000000.toInt()}
    fun toPattern(pixels:List<Int>,width:Int,height:Int):BeadPattern {
        val colors=pixels.filter{it ushr 24>0}.distinct()
        require(colors.isNotEmpty()){ "画布还是空的，先画几颗像素吧" }
        if(colors.size<=32){val indices=colors.withIndex().associate{it.value to it.index};return BeadPattern(width,height,colors,pixels.map{if(it ushr 24==0)-1 else indices.getValue(it)})}
        val palette=colors.sortedByDescending{c->pixels.count{it==c}}.take(32)
        fun distance(a:Int,b:Int):Int{val r=((a ushr 16)and 255)-((b ushr 16)and 255);val g=((a ushr 8)and 255)-((b ushr 8)and 255);val blue=(a and 255)-(b and 255);return r*r+g*g+blue*blue}
        val indices=colors.associateWith{c->palette.indices.minBy{distance(c,palette[it])}}
        return BeadPattern(width,height,palette,pixels.map{if(it ushr 24==0)-1 else indices.getValue(it)})
    }
    fun stroke(pixels:List<Int>,width:Int,from:Int,to:Int,color:Int,brushSize:Int=1):List<Int>{
        if(from !in pixels.indices||to !in pixels.indices)return pixels
        val out=pixels.toMutableList();var x=from%width;var y=from/width;val tx=to%width;val ty=to/width
        val dx=abs(tx-x);val sx=if(x<tx)1 else -1;val dy=-abs(ty-y);val sy=if(y<ty)1 else -1;var error=dx+dy
        while(true){val start=-(brushSize.coerceIn(1,24)-1)/2;for(dy0 in start until start+brushSize.coerceIn(1,24))for(dx0 in start until start+brushSize.coerceIn(1,24)){val xx=x+dx0;val yy=y+dy0;if(xx in 0 until width&&yy in 0 until pixels.size/width)out[yy*width+xx]=color};if(x==tx&&y==ty)break;val e=2*error;if(e>=dy){error+=dy;x+=sx};if(e<=dx){error+=dx;y+=sy}}
        return out
    }
    fun fill(pixels:List<Int>,width:Int,index:Int,color:Int):List<Int>{
        if(index !in pixels.indices||pixels[index]==color)return pixels
        val out=pixels.toMutableList();val original=out[index];val queue=ArrayDeque<Int>();queue.add(index);out[index]=color
        while(queue.isNotEmpty()){val i=queue.removeFirst();val x=i%width;for(n in intArrayOf(if(x>0)i-1 else -1,if(x<width-1)i+1 else -1,i-width,i+width))if(n in out.indices&&out[n]==original){out[n]=color;queue.add(n)}}
        return out
    }
    fun mirror(pixels:List<Int>,width:Int)=pixels.indices.map{i->pixels[i/width*width+width-1-i%width]}
    fun shift(pixels:List<Int>,width:Int,dx:Int,dy:Int)=pixels.indices.map{i->val x=i%width-dx;val y=i/width-dy;if(x in 0 until width&&y in 0 until pixels.size/width)pixels[y*width+x] else 0}
    fun hex(value:String):Int{val s=value.trim().removePrefix("#");require(s.matches(Regex("[0-9a-fA-F]{6}"))){"颜色格式为 #RRGGBB"};return 0xff000000.toInt() or s.toInt(16)}
    fun importPalette(text:String):List<Int>{
        require(text.length<=262144){"色卡文件过大"}
        val hex=Regex("#([0-9a-fA-F]{6})(?![0-9a-fA-F])").findAll(text).map{hex(it.value)}.toList()
        val colors=if(hex.isNotEmpty())hex else text.lineSequence().mapNotNull{line->val m=Regex("^\\s*(\\d{1,3})\\s+(\\d{1,3})\\s+(\\d{1,3})(?:\\s.*)?$").matchEntire(line)?:return@mapNotNull null;val (r,g,b)=m.destructured;if(listOf(r,g,b).any{it.toInt()>255})return@mapNotNull null;0xff000000.toInt() or(r.toInt() shl 16)or(g.toInt() shl 8)or b.toInt()}.toList()
        require(colors.isNotEmpty()){ "没找到颜色，请导入含 #RRGGBB 的 TXT/HEX/JSON 或 GIMP GPL 色卡" };require(colors.distinct().size<=512){"每张色卡最多 512 色，请分组导入"};return colors.distinct()
    }
}
object MotionDefaults {
    val actions=linkedMapOf("idle" to "日常待机","pet" to "摸摸","wave" to "挥手","play" to "玩耍","walk" to "散步","sleep" to "睡眠")
    fun validAction(id:String)=id in actions||id.matches(Regex("custom-[a-zA-Z0-9-]{1,60}"))
    fun label(id:String,clips:List<MotionClip> = emptyList())=clips.firstOrNull{it.action==id}?.actionName?.takeIf{it.isNotBlank()}?:actions[id]?:"自定义动作"
    fun groups(clips:List<MotionClip>)=clips.map{it.action}.distinct()
    fun clip(art:Artwork,action:String):MotionClip{
        require(validAction(action));val p=art.pattern;val builtin=art.id in BuiltinPets.names&&p==BuiltinPets.pattern(art.id);val frames=if(builtin&&action in actions)(0 until if(art.id in MemePets.names)16 else 12).map{PixelFrame(PixelOps.fromPattern(BuiltinPets.pattern(art.id,it,action)),if(art.id in MemePets.names)80 else 180)}else{
            val base=PixelOps.fromPattern(p)
            (0 until 6).map{f->val pixels=when(action){"play"->if(f in 2..3)PixelOps.mirror(base,p.width)else base;"pet","walk"->PixelOps.shift(base,p.width,if(action=="walk")f%3-1 else 0,if(f%3==1)-1 else 0);"wave"->base.indices.map{i->val x=i%p.width;val y=i/p.width;val sy=y+if(x>p.width/2&&f%2==1)1 else 0;if(sy<p.height)base[sy*p.width+x]else 0};"sleep"->base.indices.map{i->val sy=((i/p.width)*1.1f).toInt();if(sy<p.height)base[sy*p.width+i%p.width]else 0};else->base};PixelFrame(pixels,180)}
        }
        return MotionClip("default-$action","默认${label(action)}",action,p.width,p.height,frames,generated=true)
    }
    fun eligible(clips:List<MotionClip>,action:String?,random:Boolean)=clips.filter{if(random)it.random&&it.action!="sleep" else it.action==action&&it.triggered}
}
