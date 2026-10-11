package studio.cyberbeads.core.model

object Names {
    fun unique(value:String,existing:Collection<String>):String {
        val base=value.trim().ifBlank{"我的像素作品"}.take(40)
        val used=existing.map{it.trim().lowercase(java.util.Locale.ROOT)}.toSet()
        if(base.lowercase(java.util.Locale.ROOT) !in used)return base
        var n=2
        while(true){val suffix=" ($n)";val name=base.take(40-suffix.length)+suffix;if(name.lowercase(java.util.Locale.ROOT) !in used)return name;n++}
    }
}

/** Full outer silhouette, or selective lower/right outer silhouette. Never overwrites the subject. */
object OutlineOps {
    fun apply(p:BeadPattern,width:Int,color:Int,half:Boolean):BeadPattern {
        require(width in 0..4);if(width==0)return p
        val palette=if(color in p.palette)p.palette else p.palette+color
        require(palette.size<=512)
        val index=palette.indexOf(color);val cells=p.cells.toMutableList()
        for(y in 0 until p.height)for(x in 0 until p.width){
            if(p.cells[y*p.width+x]<0)continue
            for(dy in -width..width)for(dx in -width..width){
                if(half&&(dx<0||dy<0))continue
                val nx=x+dx;val ny=y+dy
                if(nx in 0 until p.width&&ny in 0 until p.height&&p.cells[ny*p.width+nx]<0)cells[ny*p.width+nx]=index
            }
        }
        return p.copy(palette=palette,cells=cells,colorCodes=emptyList(),brandId=null,paletteId=null)
    }
}
