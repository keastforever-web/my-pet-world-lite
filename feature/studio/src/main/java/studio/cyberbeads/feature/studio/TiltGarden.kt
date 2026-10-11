package studio.cyberbeads.feature.studio

import android.hardware.*
import android.view.Surface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.*
import kotlin.math.*

@Composable fun TiltGarden(modifier:Modifier=Modifier){
 val context=LocalContext.current;val life=LocalLifecycleOwner.current;val prefs=remember{context.getSharedPreferences("tilt-garden-v9",0)}
 val display=LocalView.current.display
 val scene=remember{prefs.getInt("next",0)%3};var tilt by remember{mutableStateOf(Offset.Zero)};var enabled by remember{mutableStateOf(true)};var supported by remember{mutableStateOf(false)}
 var time by remember{mutableFloatStateOf(0f)};var pos by remember{mutableStateOf(Offset(.5f,.55f))};var goal by remember{mutableStateOf(Offset(.5f,.55f))};var velocity by remember{mutableStateOf(Offset.Zero)};var touches by remember{mutableIntStateOf(0)};var pulse by remember{mutableFloatStateOf(0f)};var blooms by remember{mutableIntStateOf(0)}
 val titles=listOf("掌心潮汐瓶","风经过小花园","月光里的纸船")
 LaunchedEffect(Unit){prefs.edit().putInt("next",scene+1).apply()}
 DisposableEffect(life,enabled){
  val manager=context.getSystemService(SensorManager::class.java);val sensor=manager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)?:manager.getDefaultSensor(Sensor.TYPE_GRAVITY)?:manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);supported=sensor!=null
  var baseline:Offset?=null
  val listener=object:SensorEventListener{override fun onAccuracyChanged(s:Sensor?,accuracy:Int){};override fun onSensorChanged(e:SensorEvent){
   val raw=if(e.sensor.type==Sensor.TYPE_GAME_ROTATION_VECTOR){val r=FloatArray(9);val o=FloatArray(3);SensorManager.getRotationMatrixFromVector(r,e.values);SensorManager.getOrientation(r,o);Offset(o[2],-o[1])}else Offset(-e.values[0]/8f,e.values[1]/8f)
   val b=baseline?:raw.also{baseline=it};val d=raw-b;val rotated=when(display?.rotation){Surface.ROTATION_90->Offset(-d.y,d.x);Surface.ROTATION_180->Offset(-d.x,-d.y);Surface.ROTATION_270->Offset(d.y,-d.x);else->d}
   tilt=tilt*.85f+Offset(rotated.x.coerceIn(-1f,1f),rotated.y.coerceIn(-1f,1f))*.15f
  }}
  fun register(){if(enabled&&sensor!=null){baseline=null;manager.registerListener(listener,sensor,SensorManager.SENSOR_DELAY_GAME)}}
  val observer=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_RESUME)register()else if(event==Lifecycle.Event.ON_PAUSE){manager.unregisterListener(listener);tilt=Offset.Zero}}
  life.lifecycle.addObserver(observer);if(life.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))register()
  onDispose{manager.unregisterListener(listener);life.lifecycle.removeObserver(observer)}
 }
 LaunchedEffect(life){life.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED){while(isActive){delay(33);time+=.033f;pulse=(pulse-.02f).coerceAtLeast(0f);velocity=velocity*.93f+(goal-pos)*.002f+(if(enabled)tilt else Offset.Zero)*.0015f;pos=Offset((pos.x+velocity.x).coerceIn(.12f,.88f),(pos.y+velocity.y).coerceIn(.25f,.78f));if((pos-Offset(.78f,.68f)).getDistance()<.055f){blooms=(blooms+1).coerceAtMost(12);goal=Offset(.25f,.4f)}}}}
 Column(modifier.padding(top=8.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){
  Row(verticalAlignment=Alignment.CenterVertically){Text(titles[scene],Modifier.weight(1f),fontSize=13.sp,color=studio.cyberbeads.core.ui.Mint);TextButton({enabled=!enabled},contentPadding=PaddingValues(4.dp)){Text(if(enabled&&supported)"感应开 · 轻轻倾斜" else "触摸模式",fontSize=10.sp)}}
  Canvas(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(24.dp)).semantics{contentDescription="互动场景：${titles[scene]}";stateDescription="已互动 $touches 次"}.pointerInput(scene){detectTapGestures{goal=Offset(it.x/size.width,it.y/size.height);touches++;pulse=1f}}){
   val w=size.width;val h=size.height;val night=scene==2
   drawRect(Brush.verticalGradient(if(night)listOf(Color(0xff283c59),Color(0xff747197))else listOf(Color(0xffe4f1eb),Color(0xffabcfc3))))
   // Layered parallax hills, glints, curved water bands and foreground reeds.
   repeat(3){i->val y=h*(.32f+i*.13f);val path=Path().apply{moveTo(-w*.2f,h);lineTo(-w*.2f,y);cubicTo(w*.2f,y-h*.22f,w*.5f,y+h*.15f,w*1.2f,y-h*.08f);lineTo(w*1.2f,h);close()};translate(tilt.x*(i+1)*6,tilt.y*(i+1)*3){drawPath(path,(if(night)Color(0xff779898)else Color(0xff6baf9c)).copy(alpha=.12f+i*.12f))}}
   drawCircle(if(night)Color(0xffffedbd)else Color(0xfffff0bc),w*.067f,Offset(w*.79f+tilt.x*12,h*.17f))
   repeat(12){i->val y=h*(.48f+i*.04f);val p=Path().apply{moveTo(-15f,y);cubicTo(w*.3f,y+sin(time+i)*6,w*.6f,y-sin(time+i)*6,w+15,y)};drawPath(p,Color.White.copy(alpha=.1f),style=Stroke(1.4f))}
   repeat(28){i->val x=(i*73%100)/100f*w+sin(time*.3f+i)*7+tilt.x*8;val y=(i*47%95)/100f*h;drawCircle((if(night)Color(0xffffedac)else Color.White).copy(alpha=.15f+abs(sin(time+i))*.3f),1.5f+i%3,Offset(x,y))}
   val center=Offset(pos.x*w,pos.y*h)
   drawOval(Color(0xff344d61).copy(alpha=.13f),center+Offset(-24f,15f),Size(48f,9f))
   if(scene==0){
    repeat(5){i->val p=center+Offset(sin(time+i)*w*.08f-i*12,cos(time*.7f+i)*h*.07f);val color=listOf(Color(0xffe49f83),Color(0xfff5d59b),Color(0xff88b6bb))[i%3]
     rotate(sin(time+i)*12,p){drawOval(color,p-Offset(13f,7f),Size(26f,14f));drawPath(Path().apply{moveTo(p.x-9,p.y);lineTo(p.x-23,p.y-10);lineTo(p.x-23,p.y+10);close()},color.copy(alpha=.8f));drawCircle(Color(0xff4c5261),1.5f,p+Offset(7f,-2f))}}
    repeat(8){i->val y=h-(time*22+i*43)%(h*.65f);drawCircle(Color.White.copy(alpha=.5f),3f+i%3,Offset(w*(.18f+(i%4)*.2f)+tilt.x*y*.1f,y),style=Stroke(1f))}
   }else if(scene==1){
    drawCircle(Color.White.copy(alpha=.3f),18f,center);drawCircle(Color(0xffe5f7f3),9f,center);drawCircle(Color.White,3f,center-Offset(3f,3f))
   }else{val p=Path().apply{moveTo(center.x-25,center.y);lineTo(center.x+25,center.y);lineTo(center.x+14,center.y+15);lineTo(center.x-12,center.y+15);close()};drawPath(p,Color(0xffeac9a8));drawPath(Path().apply{moveTo(center.x,center.y-34);lineTo(center.x,center.y);lineTo(center.x+24,center.y);close()},Color(0xffffebca));drawLine(Color(0xffd3b799),center,center-Offset(0f,36f),2f)}
   repeat(12){i->val x=w*(.04f+i*.087f);val base=h*.96f;val bend=sin(time*.7f+i)*5+tilt.x*15;drawLine(Color(0xff4d8571),Offset(x,base),Offset(x+bend,base-h*(.1f+(i%3)*.025f)),2f)
    if(i<touches+2+blooms){val p=Offset(x+bend,base-h*(.1f+(i%3)*.025f));repeat(5){j->val a=j*2*PI/5;drawCircle(if(i%2==0)Color(0xffefbaca)else Color(0xffffe4b4),4.5f,p+Offset(cos(a).toFloat()*5,sin(a).toFloat()*5))};drawCircle(Color(0xffffefbd),3f,p)}}
   if(pulse>0)drawCircle(Color.White.copy(alpha=pulse*.65f),10+(1-pulse)*55,Offset(goal.x*w,goal.y*h),style=Stroke(2f))
   drawRoundRect(Color.White.copy(alpha=.14f),Offset(8f,8f),Size(w-16,h-16),CornerRadius(24f),style=Stroke(2f))
  }
  Text(if(scene==1)"把露珠送向右边，让花慢慢开 · 也可以轻点引导"else"轻轻倾斜，让风景流动 · 点一点，伙伴跟过来",fontSize=10.sp,color=studio.cyberbeads.core.ui.Muted)
 }
}
