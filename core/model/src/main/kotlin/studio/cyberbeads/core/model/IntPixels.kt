package studio.cyberbeads.core.model

/** Immutable primitive-backed pixels: 128² frames need 64KB, not 16K boxed integers. */
class IntPixels(private val data:IntArray):AbstractList<Int>(),java.util.RandomAccess {
 override val size:Int get()=data.size
 override fun get(index:Int)=data[index]
}
