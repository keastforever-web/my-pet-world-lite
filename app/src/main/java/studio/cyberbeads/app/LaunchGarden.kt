package studio.cyberbeads.app
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.*
@Composable fun LaunchGarden(visible:Boolean,onSkip:()->Unit){
 AnimatedVisibility(visible,enter=fadeIn(tween(200)),exit=fadeOut(tween(450))){
  val t=rememberInfiniteTransition(label="welcome")
  val scale by t.animateFloat(1f,1.045f,infiniteRepeatable(tween(2400),RepeatMode.Reverse),label="garden breath")
  Box(Modifier.fillMaxSize().clickable(onClick=onSkip).background(Color(0xfffff6ed))){
   Image(painterResource(R.drawable.launch_garden_v11),null,Modifier.fillMaxSize().graphicsLayer{scaleX=scale;scaleY=scale},contentScale=ContentScale.Crop)
   Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xff243828).copy(alpha=.1f),Color.Transparent,Color(0xff203322).copy(alpha=.78f)))))
   Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom=72.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(10.dp)){
    Text("我的宠物世界",color=Color(0xfffff4d7),fontFamily=FontFamily.Serif,fontWeight=FontWeight.Bold,fontSize=34.sp,letterSpacing=4.sp)
    Text("给每一颗像素，一个想回来的家",color=Color(0xfffff4d7),fontSize=13.sp)
    Text("轻触进入",color=Color(0xfffff4d7).copy(alpha=.8f),fontSize=10.sp)
   }
  }
 }
}
