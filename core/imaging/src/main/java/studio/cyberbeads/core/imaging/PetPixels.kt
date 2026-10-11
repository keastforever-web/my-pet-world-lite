package studio.cyberbeads.core.imaging
import android.graphics.Bitmap
import studio.cyberbeads.core.model.*
object PetPixels {
    fun frame(id:String,pattern:BeadPattern,frame:Int=0,action:String="idle"):Bitmap {
        val p=if(id in BuiltinPets.names)BuiltinPets.pattern(id,frame,action)else pattern
        return Bitmap.createBitmap(IntArray(p.cells.size){i->if(p.cells[i]<0)0 else p.palette[p.cells[i]] or 0xff000000.toInt()},p.width,p.height,Bitmap.Config.ARGB_8888)
    }
}
