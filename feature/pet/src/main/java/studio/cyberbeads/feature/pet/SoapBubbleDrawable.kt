package studio.cyberbeads.feature.pet

import android.content.res.Resources
import android.graphics.*
import android.graphics.drawable.Drawable

/** Generated pearlescent film, native contour mask and pet colour wash. No text baked in. */
internal class SoapBubbleDrawable(resources:Resources,private val accent:Int):Drawable(){
 private val image=shared?:BitmapFactory.decodeResource(resources,R.drawable.soap_bubble_v15).also{shared=it}
 private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
 private val density=resources.displayMetrics.density
 private var opacity=255
 override fun draw(canvas:Canvas){
  val r=RectF(bounds);val tail=9*density;val radius=minOf(20*density,r.height()/2)
  val body=RectF(r.left,r.top,r.right,r.bottom-tail)
  val path=Path().apply{addRoundRect(body,radius,radius,Path.Direction.CW);moveTo(r.centerX()-tail,body.bottom-1);quadTo(r.centerX()-tail*.35f,body.bottom+tail*.25f,r.centerX(),r.bottom);quadTo(r.centerX()+tail*.35f,body.bottom+tail*.25f,r.centerX()+tail,body.bottom-1);close()}
  canvas.save();canvas.clipPath(path)
  paint.color=Color.argb(opacity*175/255,255,255,255);canvas.drawPath(path,paint)
  paint.color=Color.argb(opacity*25/255,Color.red(accent),Color.green(accent),Color.blue(accent));canvas.drawPath(path,paint)
  paint.color=Color.WHITE;paint.alpha=opacity*200/255
  // Three horizontal slices keep round ends and central tail readable on short text.
  val inset=minOf(r.width()*.25f,22*density)
  val xs=intArrayOf(0,image.width/4,image.width*3/4,image.width)
  val dx=floatArrayOf(r.left,r.left+inset,r.right-inset,r.right)
  for(i in 0..2)canvas.drawBitmap(image,Rect(xs[i],0,xs[i+1],image.height),RectF(dx[i],r.top,dx[i+1],r.bottom),paint)
  canvas.restore()
 }
 override fun setAlpha(alpha:Int){opacity=alpha;invalidateSelf()}
 override fun setColorFilter(colorFilter:ColorFilter?){paint.colorFilter=colorFilter}
 @Deprecated("Deprecated in Java") override fun getOpacity()=PixelFormat.TRANSLUCENT
 companion object{private var shared:Bitmap?=null}
}
