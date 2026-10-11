package studio.cyberbeads.core.model

import kotlin.math.*

/** World scale is based on the short edge, never the orientation's width. */
object ParkMetrics { fun scale(width:Int,height:Int)=min(width,height).coerceAtLeast(1)/560f*1.5f; const val petWidth=26 }
data class SwimmingFish(val id:Int,val x:Float,val y:Float,val direction:Int)
class FishingRound {
 var time=0f; private set
 var length=.04f; private set
 var angle=0f; private set
 var phase=0; private set // 0 swing, 1 cast, 2 reel
 var caught:Int?=null; private set
 var landed:Int?=null; private set
 fun fishes()=(0..6).map{i->val speed=.027f+i*.003f;val x=((i*.137f+time*speed)%1.18f)-.09f;SwimmingFish(i,if(i%2==0)x else 1-x,.36f+i*.078f,if(i%2==0)1 else -1)}
 fun hook()=Pair(.5f+sin(angle)*length,.17f+cos(angle)*length)
 fun cast(){if(phase==0){phase=1;caught=null;landed=null}else if(phase==1)phase=2}
 fun advance(dt:Float){
  time+=dt.coerceIn(0f,.1f)
  if(phase==0){angle=sin(time*1.4f)*.95f;return}
  length+=(if(phase==1).39f else -.52f)*dt.coerceIn(0f,.1f)
  val (x,y)=hook()
  if(phase==1){caught=fishes().firstOrNull{hypot(it.x-x,it.y-y)<.045f}?.id;if(caught!=null||y>.93f||x<.03f||x>.97f)phase=2}
  if(phase==2&&length<=.04f){length=.04f;landed=caught;phase=0}
 }
 fun collect():Int?{val value=landed?:return null;landed=null;caught=null;return value}
}
