package studio.cyberbeads.feature.studio

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.*
import kotlin.math.*

@Composable private fun LegacyHomeScene(modifier:Modifier=Modifier){
    val prefs=LocalContext.current.getSharedPreferences("home-scenes-v8",0);val lifecycle=LocalLifecycleOwner.current
    var scene by remember{mutableIntStateOf(prefs.getInt("next",0)%6)}
    var time by remember{mutableFloatStateOf(0f)};var tap by remember{mutableStateOf(Offset(.5f,.5f))};var fish by remember{mutableStateOf(Offset(.5f,.5f))};var touchAt by remember{mutableFloatStateOf(-10f)};var touches by remember{mutableIntStateOf(0)}
    LaunchedEffect(Unit){prefs.edit().putInt("next",(scene+1)%6).apply()}
    LaunchedEffect(lifecycle){lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED){while(isActive){delay(33);time+=.033f;fish+= (tap-fish)*.035f}}}
    val titles=listOf("一尾小小的海","雪落在你的指尖","给春天一点回应","夜色里的萤火","雨后的涟漪","星星邮递员")
    val hints=listOf("点一点，小鱼来找你","点一下，让雪下得更快","轻触土地，花就会开","点亮一盏萤火灯","落下指尖，听见雨的形状","点一下，寄出一颗流星")
    Column(modifier.padding(top=10.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(titles[scene],fontSize=13.sp,color=studio.cyberbeads.core.ui.Mint);Text("小小互动 · ${scene+1}/6",fontSize=10.sp,color=studio.cyberbeads.core.ui.Muted)}
        Canvas(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(24.dp)).semantics{contentDescription="互动场景：${titles[scene]}";stateDescription="已互动 $touches 次"}.pointerInput(scene){detectTapGestures{tap=Offset((it.x/size.width).coerceIn(.08f,.92f),(it.y/size.height).coerceIn(.12f,.8f));touchAt=time;touches++}}){
            val sx=size.width/360f;val sy=size.height/230f
            withTransform({scale(sx,sy,Offset.Zero)}){
                val palettes=listOf(listOf(Color(0xffdcefeb),Color(0xffb7dcd9)),listOf(Color(0xffd6dcef),Color(0xfff3e4ef)),listOf(Color(0xffe4efd8),Color(0xffd7e8d1)),listOf(Color(0xff343e67),Color(0xff526578)),listOf(Color(0xffdce5f1),Color(0xffb8d2d8)),listOf(Color(0xff454064),Color(0xff8173a1)))
                drawRect(Brush.verticalGradient(palettes[scene],endY=230f),size=Size(360f,230f))
                fun ellipse(c:Color,x:Float,y:Float,w:Float,h:Float){drawOval(c,Offset(x,y),Size(w,h))}
                fun star(x:Float,y:Float,r:Float,c:Color){drawLine(c,Offset(x-r,y),Offset(x+r,y),2f);drawLine(c,Offset(x,y-r),Offset(x,y+r),2f)}
                fun flower(x:Float,y:Float,g:Float,c:Color){drawLine(Color(0xff689b75),Offset(x,y),Offset(x,y-18*g),2f);repeat(5){k->val a=k*PI*2/5;drawCircle(c,4*g,Offset(x+cos(a).toFloat()*5*g,y-20*g+sin(a).toFloat()*5*g))};drawCircle(Color(0xffffda8b),3*g,Offset(x,y-20*g))}
                val power=(1-(time-touchAt)/3).coerceIn(0f,1f)
                when(scene){
                    0->{
                        ellipse(Color(0xffe8ddc6),-10f,199f,400f,66f)
                        repeat(12){i->val x=15f+i*29;val sway=sin(time+i).toFloat()*4;val h=18f+i%4*9;drawLine(Color(0xff74aaa0),Offset(x,215f),Offset(x+sway,215-h),4f,StrokeCap.Round);ellipse(Color(0xff91bdb0),x+sway-8,210-h,11f,6f)}
                        repeat(7){i->val px=if(i<3)fish.x*300+20+i*15 else (40+i*43+sin(time*.5+i)*25).toFloat();val py=if(i<3)fish.y*155+20+i*13 else (70+i*10+sin(time+i)*15).toFloat();val c=listOf(Color(0xffeb9ba8),Color(0xfff0c783),Color(0xff8caccd))[i%3]
                            val path=Path().apply{moveTo(px-11,py);lineTo(px-23,py-8);lineTo(px-23,py+8);close()};drawPath(path,c);ellipse(c,px-13,py-8,27f,17f);drawCircle(Color(0xff4a5367),1.8f,Offset(px+6,py-2))}
                        repeat(12){i->val x=25f+i*28;val y=220-((time*12+i*27)%200);drawCircle(Color.White.copy(alpha=.45f),2f+i%3,Offset(x,y),style=Stroke(1f))}
                    }
                    1->{
                        ellipse(Color.White.copy(alpha=.7f),-40f,185f,460f,80f)
                        repeat(5){i->val x=25f+i*76;val h=35f+i%3*16;val path=Path().apply{moveTo(x,192-h);lineTo(x-19,194f);lineTo(x+19,194f);close()};drawPath(path,Color(0xff9bafb9));drawLine(Color(0xff77939f),Offset(x,192f),Offset(x,204f),4f)}
                        repeat(55){i->val x=((i*71f+sin(time*.6f+i)*8)%360+360)%360;val y=((time*(11+i%7+power*35)+i*31)%225);drawCircle(Color.White.copy(alpha=.85f),1f+i%3*.6f,Offset(x,y))}
                        ellipse(Color(0xfffbf5f0),252f,174f,25f,25f);ellipse(Color(0xfffbf5f0),257f,159f,17f,19f);drawCircle(Color(0xff617184),1f,Offset(263f,165f));drawLine(Color(0xffe8ae82),Offset(263f,170f),Offset(271f,170f),2f)
                    }
                    2->{
                        ellipse(Color(0xffa7cb9e),-20f,169f,400f,100f);val grow=(touches.coerceAtMost(8)/8f)
                        drawLine(Color(0xff9c8b73),Offset(270f,192f),Offset(270f,140f-grow*55),8f);drawCircle(Color(0xff95bd89),24f+grow*25,Offset(270f,125f-grow*30));drawCircle(Color(0xffb4d5a0),18f+grow*15,Offset(251f,110f-grow*30))
                        repeat(14){i->val g=if(i<touches+3)1f else .2f;flower(23f+i*23,205f+(i%3)*5,g,if(i%2==0)Color(0xffe7a1ba)else Color(0xfff2eee0))}
                        repeat(4){i->val x=(65+i*66+sin(time*.6f+i)*25).toFloat();val y=(75+i%2*30+cos(time+i)*10).toFloat();val w=4f+abs(sin(time*6+i)).toFloat()*4;ellipse(Color(0xffc19bcb),x-w,y-4,w,9f);ellipse(Color(0xffebc0d8),x+1,y-4,w,9f);drawLine(Color(0xff8b778c),Offset(x,y-3),Offset(x,y+6),1f)}
                        ellipse(Color(0xfff5eddc),60f,179f,18f,13f);ellipse(Color(0xfff5eddc),63f,165f,4f,16f);ellipse(Color(0xfff5eddc),70f,164f,4f,16f)
                    }
                    3->{
                        drawCircle(Color(0xfff9e9b9),18f,Offset(294f,40f));ellipse(Color(0xff3b665e),-20f,185f,430f,80f)
                        repeat(22){i->val x=(i*37f+sin(time*.6f+i)*17)%355;val y=(90+i%5*21+cos(time+i)*12).toFloat();val a=(.3+abs(sin(time*1.2+i))*.65).toFloat();drawCircle(Color(0xffe9ea9b).copy(alpha=a*.2f),8f,Offset(x,y));drawCircle(Color(0xffffefa6).copy(alpha=a),2f,Offset(x,y))}
                        if(power>0)repeat(8){i->val a=i*PI/4;val d=(1-power)*65;drawCircle(Color(0xffffe9aa).copy(alpha=power),3f,Offset(tap.x*360+cos(a).toFloat()*d,tap.y*230+sin(a).toFloat()*d))}
                    }
                    4->{
                        ellipse(Color(0xffa3c8ce),-20f,122f,400f,130f);repeat(30){i->val x=(i*57f)%360;val y=(time*70+i*37)%230;drawLine(Color.White.copy(alpha=.4f),Offset(x,y),Offset(x-3,y+8),1f)}
                        repeat(5){i->val progress=(time*.35f+i*.2f)%1;ellipse(Color.White.copy(alpha=(1-progress)*.5f),i*71f-18*progress,150f+i%3*20-4*progress,36*progress,8*progress)}
                        if(power>0)repeat(3){i->val d=(1-power)*90+i*18;drawOval(Color.White.copy(alpha=power*.7f),Offset(tap.x*360-d/2,tap.y*230-d/6),Size(d,d/3),style=Stroke(1.5f))}
                        ellipse(Color(0xff81ae99),252f,185f,44f,12f);flower(272f,186f,1f,Color(0xfff4c0d3))
                    }
                    else->{
                        repeat(35){i->star((i*71%350).toFloat()+5,(i*43%190).toFloat()+10,1f+abs(sin(time+i)).toFloat()*2,Color(0xfff7e0bd).copy(alpha=.7f))}
                        drawCircle(Color(0xfff7d8b7),23f,Offset(285f,55f));drawCircle(Color(0xff635883),22f,Offset(275f,48f));ellipse(Color(0xff635c89),-30f,195f,420f,80f)
                        if(power>0){val x=tap.x*360+(1-power)*100;val y=tap.y*230+(1-power)*50;drawLine(Brush.linearGradient(listOf(Color.Transparent,Color(0xffffe7b6)),Offset(x-40,y-20),Offset(x,y)),Offset(x-40,y-20),Offset(x,y),3f);star(x,y,5f,Color(0xffffe7b6))}
                    }
                }
                if(power>0&&scene in 0..2)drawCircle(Color.White.copy(alpha=power*.6f),8+(1-power)*25,Offset(tap.x*360,tap.y*230),style=Stroke(1.5f))
            }
        }
        Text(hints[scene]+" · 切回首页还有新风景",fontSize=10.sp,color=studio.cyberbeads.core.ui.Muted)
    }
}

@Composable fun HomeScene(modifier:Modifier=Modifier)=TiltGarden(modifier)
