package studio.cyberbeads.feature.pet

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.view.*
import android.widget.*
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.channels.Channel
import studio.cyberbeads.core.data.*
import studio.cyberbeads.core.imaging.PetPixels
import studio.cyberbeads.core.model.*
import java.util.*
import kotlin.math.*

object PetController {
    val runningIds=MutableStateFlow<Set<String>>(emptySet())
    val visibleIds=MutableStateFlow<Set<String>>(emptySet())
    fun start(context:Context,id:String){check(Settings.canDrawOverlays(context)){"请先开启悬浮窗权限"};ContextCompat.startForegroundService(context,Intent(context,PetOverlayService::class.java).putExtra("artwork",id))}
    fun chat(context:Context,id:String){check(Settings.canDrawOverlays(context)){"请先开启悬浮窗权限"};ContextCompat.startForegroundService(context,Intent(context,PetOverlayService::class.java).putExtra("artwork",id).putExtra("openChat",true))}
    fun stop(context:Context){context.getSharedPreferences("desktop-session-v6",Context.MODE_PRIVATE).edit().clear().apply();context.stopService(Intent(context,PetOverlayService::class.java))}
    fun remove(context:Context,id:String){if(id in runningIds.value)context.startService(Intent(context,PetOverlayService::class.java).setAction("remove").putExtra("artwork",id))}
    fun interact(context:Context,id:String,action:String){if(id in runningIds.value)context.startService(Intent(context,PetOverlayService::class.java).setAction(action).putExtra("artwork",id))}
}

class PetOverlayService:Service() {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private val pets=linkedMapOf<String,WindowPet>()
    private val commands=Channel<suspend ()->Unit>(Channel.UNLIMITED)
    private var pendingCommands=0
    private fun enqueue(block:suspend ()->Unit){pendingCommands++;commands.trySend(block)}
    private lateinit var wm:WindowManager
    private lateinit var store:ArtworkStore
    private lateinit var companions:CompanionStore
    private lateinit var client:PetChatClient
    private lateinit var creative:CreativeStore
    private var chat:DesktopChatWindow?=null;private var chatPet:String?=null
    private var tts:TextToSpeech?=null
    @Volatile private var speechReady=false
    @Volatile private var speechClosed=false
    private val speechWork=java.util.concurrent.Executors.newSingleThreadExecutor()
    private var paused=false
    private val session by lazy{getSharedPreferences("desktop-session-v6",MODE_PRIVATE)}
    private var screenCheck:Job?=null
    private fun refreshScreen(){val visible=getSystemService(PowerManager::class.java).isInteractive&&!getSystemService(KeyguardManager::class.java).isKeyguardLocked;val waking=paused&&visible;paused=!visible;pets.values.forEach{it.screenVisible(visible,waking)};if(paused)chat?.dismiss()}
    private val screen=object:BroadcastReceiver(){override fun onReceive(c:Context?,i:Intent?){
        if(i?.action==Intent.ACTION_SCREEN_OFF){paused=true;pets.values.forEach{it.screenVisible(false,false)};chat?.dismiss()}
        else {screenCheck?.cancel();screenCheck=scope.launch{repeat(4){refreshScreen();delay(350)}}}
    }}
    private fun dp(v:Int)=(v*resources.displayMetrics.density).roundToInt()
    override fun onCreate(){super.onCreate();wm=getSystemService(WindowManager::class.java);store=(application as ArtworkStoreOwner).artworkStore
        companions=(application as CompanionOwner).companions;client=(application as CompanionOwner).chatClient;creative=(application as CreativeOwner).creative
        scope.launch{for(command in commands){try{command()}catch(e:CancellationException){throw e}catch(e:Exception){android.util.Log.w("PetCommand","Command failed",e)}finally{pendingCommands--;if(pets.isEmpty()&&pendingCommands==0)stopSelf()}}}
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("pet","桌面伙伴",NotificationManager.IMPORTANCE_LOW))
        ContextCompat.registerReceiver(this,screen,IntentFilter().apply{addAction(Intent.ACTION_SCREEN_OFF);addAction(Intent.ACTION_SCREEN_ON);addAction(Intent.ACTION_USER_PRESENT)},ContextCompat.RECEIVER_NOT_EXPORTED)

        scope.launch{while(isActive){delay(2000);refreshScreen();if(!Settings.canDrawOverlays(this@PetOverlayService)){stopSelf();break};pets.values.toList().forEach{it.maybeTalk()}}}
        scope.launch{store.items.collect{items->pets.keys.filter{id->items.none{it.id==id&&it.isComplete}}.toList().forEach{remove(it)};pets.values.forEach{pet->items.firstOrNull{it.id==pet.art.id}?.let{pet.updateArtwork(it)}}}}
        scope.launch{companions.settings.collect{if(it.voice)prepareSpeech() else if(tts!=null)speechWork.execute{tts?.stop()};pets.values.forEach{it.resize()}}}
        scope.launch{companions.chatEvents.collect{event->if(!event.proactive)pets[event.id]?.chatActivity(event.answer)}}
        scope.launch{creative.motionRevision.collect{pets.values.toList().forEach{it.reloadMotions()}}}
    }
    private fun prepareSpeech(){
        if(tts!=null||speechClosed)return
        tts=TextToSpeech(this){code->
            val engine=tts
            if(code==TextToSpeech.SUCCESS&&engine!=null&&!speechClosed)speechWork.execute{
                val ready=runCatching{engine.setLanguage(Locale.SIMPLIFIED_CHINESE)>=0}.getOrDefault(false)
                speechReady=ready&&!speechClosed
            }
        }
    }
    private fun notification():Notification {
        val stop=PendingIntent.getService(this,1,Intent(this,PetOverlayService::class.java).setAction("stop"),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val open=PendingIntent.getActivity(this,2,packageManager.getLaunchIntentForPackage(packageName)!!,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this,"pet").setSmallIcon(R.drawable.ic_pet_notification).setContentTitle("${pets.size.coerceAtLeast(1)} 位宠物伙伴正在陪伴")
            .setContentText("点一下互动 · 长按菜单 · 可分别关闭").setOngoing(true).setContentIntent(open).addAction(0,"全部收回",stop).build()
    }
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        if(intent?.action=="stop"){session.edit().clear().apply();stopSelf();return START_NOT_STICKY}
        val id=intent?.getStringExtra("artwork")
        if(intent?.action=="remove"){enqueue{id?.let{remove(it)}};return START_STICKY}
        if(intent?.action!=null){enqueue{id?.let{pets[it]?.interact(intent.action!!)}};return START_STICKY}
        val desired=if(id!=null)setOf(id)else session.getStringSet("ids",emptySet()).orEmpty().toSet()
        if(desired.isEmpty()||!Settings.canDrawOverlays(this)){stopSelf();return START_NOT_STICKY}
        try{ServiceCompat.startForeground(this,701,notification(),if(Build.VERSION.SDK_INT>=34)ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0)}catch(e:Exception){android.util.Log.w("PetRecovery","Foreground restore rejected",e);stopSelf();return START_NOT_STICKY}
        enqueue{try{if(store.items.value.isEmpty())store.refresh();desired.filter{it !in pets}.forEach{petId->val art=store.get(petId);if(art!=null&&art.isComplete&&petId !in pets&&pets.size<5){val loaded=creative.ensureMotions(art);val pet=WindowPet(art,pets.size,loaded);pets[petId]=pet;pet.show()}};updateRunning();refreshScreen();if(id!=null&&intent?.getBooleanExtra("openChat",false)==true)pets[id]?.interact("chat");if(pets.isEmpty()&&pendingCommands==0)stopSelf()}catch(e:Exception){android.util.Log.w("PetRecovery","Desktop restore failed",e);if(pets.isEmpty()&&pendingCommands==0)stopSelf()}}
        return START_STICKY
    }
    private fun updateRunning(){PetController.runningIds.value=pets.keys.toSet();session.edit().putStringSet("ids",pets.keys.toSet()).apply();getSystemService(NotificationManager::class.java).notify(701,notification())}
    private fun remove(id:String){if(chatPet==id)chat?.dismiss();pets.remove(id)?.dispose();updateRunning();if(pets.isEmpty()&&pendingCommands==0)stopSelf()}
    override fun onConfigurationChanged(config:Configuration){super.onConfigurationChanged(config);pets.values.forEach{it.resize()};chat?.resize()}
    override fun onDestroy(){chat?.dismiss();scope.cancel();pets.values.forEach{it.dispose()};pets.clear();PetController.runningIds.value=emptySet();unregisterReceiver(screen);speechClosed=true;speechReady=false;speechWork.execute{tts?.stop();tts?.shutdown()};speechWork.shutdown();stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy()}
    override fun onBind(intent:Intent?)=null

    private inner class WindowPet(var art:Artwork,index:Int,initialClips:List<MotionClip>) {
        val root=FrameLayout(this@PetOverlayService)
        private val bubble=TextView(this@PetOverlayService).apply{
            textSize=11f;gravity=Gravity.CENTER;maxLines=4;maxWidth=dp(210);includeFontPadding=false
            setPadding(dp(15),dp(10),dp(15),dp(18));importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_YES
            accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        private var bubbleAttached=false;private var bubbleJob:Job?=null
        private val bubbleParams=WindowManager.LayoutParams(-2,-2,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT).apply{gravity=Gravity.TOP or Gravity.START;alpha=companions.settings.value.opacity}
        private val orbit=FrameLayout(this@PetOverlayService)
        private var accentArtwork:Artwork?=null;private var accentColor=0xff91b9a2.toInt()
        private fun accent():Int {
            if(accentArtwork===art)return accentColor
            val candidates=art.pattern.palette.filter{c->val hsv=FloatArray(3);Color.colorToHSV(c,hsv);hsv[1]>.2f&&hsv[2]>.35f}
            val counts=IntArray(art.pattern.palette.size);art.pattern.cells.forEach{if(it in counts.indices)counts[it]++}
            accentColor=candidates.maxByOrNull{c->counts[art.pattern.palette.indexOf(c)]}?:0xff91b9a2.toInt();accentArtwork=art;return accentColor
        }
        private fun pastel(alpha:Int=195):Int{val c=accent();return Color.argb(alpha,(Color.red(c)*.25f+255*.75f).toInt(),(Color.green(c)*.25f+255*.75f).toInt(),(Color.blue(c)*.25f+255*.75f).toInt())}
        private fun ink():Int{val c=accent();return Color.rgb((Color.red(c)*.30f).toInt(),(Color.green(c)*.30f).toInt(),(Color.blue(c)*.30f).toInt())}
        private fun styleBubble(){bubble.setTextColor(ink());bubble.background=SoapBubbleDrawable(resources,accent())}
        private val controls=FrameLayout(this@PetOverlayService).apply{visibility=View.GONE}
        private var menuPage=0;private var menuAnimator:android.animation.ValueAnimator?=null
        private var state=store.petState(art.id)
        private var action="idle";private var actionUntil=0L
        private var defaults=mutableMapOf<String,MotionClip>()
        private fun fallback(id:String)=defaults.getOrPut(id){MotionDefaults.clip(art,id)}
        private var clips=initialClips;private var playing:MotionClip?=null;private var startedAt=0L
        private var parked=false;private var parkedRight=false;private var parkedEdge="left";private var awakeX=0;private var awakeY=0
        private var nextRandom=SystemClock.elapsedRealtime()+30000L
        fun updateArtwork(value:Artwork){if(value!=art){art=value;defaults.clear();sprite.contentDescription=if(parked)"${art.name}躲在屏幕边缘，点击唤回"else "${art.name}，点击互动，长按菜单，拖动移动";sprite.invalidate()}}
        suspend fun reloadMotions(){try{closeMenu();clips=creative.motions(art.id);playing=null;action="idle";actionUntil=0;sprite.invalidate()}catch(_:Exception){say("动作读取失败，暂用默认模式")}}
        private var frame=0;private var lastInteraction=SystemClock.elapsedRealtime();private var lastAuto=lastInteraction
        private var talking:Job?=null;private var direction=1;private var draggingNow=false;private var roamingUntil=0L;private var nextRoam=SystemClock.elapsedRealtime()+4000L
        private val budget=getSharedPreferences("proactive-budget-v2",MODE_PRIVATE)
        private val params=WindowManager.LayoutParams(dp(companions.settings.value.petSize),dp(companions.settings.value.petSize),WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT).apply{gravity=Gravity.TOP or Gravity.START;x=dp(90+(index%2)*160);y=dp(230+(index/2)*165);alpha=companions.settings.value.opacity}
        private val sprite=Sprite()
        private var loop:Job?=null;private var pendingReply:String?=null
        init {
            root.addView(orbit,FrameLayout.LayoutParams(-1,-1))
            orbit.addView(sprite,FrameLayout.LayoutParams(-1,-1,Gravity.CENTER))
            styleBubble();attachGestures()
        }
        fun show(){params.title="pet-anchor-${art.id}";wm.addView(root,params);root.post{(sprite.layoutParams as? FrameLayout.LayoutParams)?.gravity=Gravity.CENTER;clamp()};paused=!getSystemService(PowerManager::class.java).isInteractive||getSystemService(KeyguardManager::class.java).isKeyguardLocked;screenVisible(!paused,false);say("${art.name}来啦 ♡")}
        fun screenVisible(visible:Boolean,waking:Boolean){
            if(!visible){closeMenu();hideBubble()}
            root.visibility=if(visible)View.VISIBLE else View.GONE
            PetController.visibleIds.value=if(visible)PetController.visibleIds.value+art.id else PetController.visibleIds.value-art.id
            if(!visible){loop?.cancel();loop=null;return}
            if(waking){restore();lastInteraction=SystemClock.elapsedRealtime();state=state.copy(sleeping=false);play(MotionDefaults.eligible(clips,"wave",false).randomOrNull()?:fallback("wave"));say(pendingReply?:"你回来啦，我一直把小脑袋留给你 ♡");pendingReply=null}
            if(loop?.isActive==true)return
            loop=scope.launch{while(isActive){delay(40);val now=SystemClock.elapsedRealtime();frame++
                if(now>actionUntil){action="idle";playing=null}
                val roaming=companions.settings.value.freeRoam&&companions.settings.value.motion&&!parked&&!state.sleeping&&!draggingNow&&controls.visibility!=View.VISIBLE&&chatPet!=art.id
                if(roaming&&now>=nextRoam&&action=="idle"){
                    direction=if(Math.random()<.5)-1 else 1;roamingUntil=now+2400+(Math.random()*3200).toLong();nextRoam=roamingUntil+4500+(Math.random()*6000).toLong()
                }
                if(roaming&&now<roamingUntil&&(action=="idle"||action=="walk"||action=="custom-fly-left")){
                    val desired=if(direction<0)clips.firstOrNull{it.action=="custom-fly-left"&&it.triggered} else null
                    val walking=desired?:MotionDefaults.eligible(clips,"walk",false).firstOrNull()?:fallback("walk")
                    if(playing!==walking||now>=actionUntil)play(walking)
                    params.x+=dp(1)*direction
                    if(params.x<=0||params.x>=resources.displayMetrics.widthPixels-params.width){direction*=-1;playing=null}
                    clamp();lastInteraction=now
                }
                if(!parked&&controls.visibility!=View.VISIBLE&&companions.settings.value.edgeHide&&chatPet!=art.id&&now-lastInteraction>companions.settings.value.edgeSeconds*1000L&&action=="idle")park()
                if(!parked&&controls.visibility!=View.VISIBLE&&!state.sleeping&&companions.settings.value.motion&&chatPet!=art.id&&action=="idle"&&now>=nextRandom){nextRandom=now+30000+(Math.random()*30000).toLong();MotionDefaults.eligible(clips,null,true).randomOrNull()?.let{restore();play(it)}}
                sprite.invalidate()
                if(!roaming&&!parked&&action=="walk"&&companions.settings.value.motion&&frame%4==0&&controls.visibility!=View.VISIBLE){params.x+=dp(2)*direction;if(params.x<=0||params.x>=resources.displayMetrics.widthPixels-params.width)direction*=-1;clamp()}
            }}
        }
        fun chatActivity(answer:String?){
            if(paused){if(answer!=null)pendingReply=answer;return}
            restore();lastInteraction=SystemClock.elapsedRealtime();state=state.copy(sleeping=false);closeMenu()
            play(MotionDefaults.eligible(clips,"wave",false).randomOrNull()?:fallback("wave"));say(answer?:"听见啦，让我想一想～")
        }
        private fun play(clip:MotionClip){playing=clip;action=clip.action;startedAt=SystemClock.elapsedRealtime();actionUntil=startedAt+clip.durationMs}
        private fun park(){
            if(parked)return;parked=true;awakeX=params.x;awakeY=params.y
            val sw=resources.displayMetrics.widthPixels;val sh=resources.displayMetrics.heightPixels
            parkedEdge=listOf("left" to params.x,"right" to max(0,sw-params.x-params.width),"top" to max(0,params.y-dp(24)),"bottom" to max(0,sh-params.y-root.height-dp(36))).minBy{it.second}.first
            parkedRight=parkedEdge=="right";closeMenu();hideBubble()
            val full=dp(companions.settings.value.petSize);val reveal=(full*.5f).roundToInt()
            params.width=if(parkedEdge in listOf("top","bottom"))full else reveal
            params.height=if(parkedEdge in listOf("top","bottom"))reveal else full
            sprite.contentDescription="${art.name}躲在屏幕边缘，点击唤回";root.post{(sprite.layoutParams as? FrameLayout.LayoutParams)?.gravity=Gravity.CENTER;clamp()}
        }
        private fun restore(){if(!parked)return;parked=false;params.width=dp(companions.settings.value.petSize);params.height=params.width;params.x=awakeX;params.y=awakeY;sprite.contentDescription="${art.name}，点击互动，长按菜单，拖动移动";lastInteraction=SystemClock.elapsedRealtime();clamp();sprite.invalidate()}
        private fun greetFromEdge(){restore();play(MotionDefaults.eligible(clips,"wave",false).randomOrNull()?:fallback("wave"));say(listOf("被你找到啦！小脑袋探出来 ♡","我在边边收集快乐，分你一半～","探头！今天也想陪着你。","躲猫猫结束，抱抱时间到！").random())}
        fun resize(){val changed=params.width!=dp(companions.settings.value.petSize)&&!parked;if(changed)closeMenu();params.alpha=companions.settings.value.opacity;bubbleParams.alpha=params.alpha;if(!companions.settings.value.edgeHide)restore();val full=dp(companions.settings.value.petSize);val horizontal=parkedEdge in listOf("top","bottom");params.width=if(parked&&!horizontal)full/2 else full;params.height=if(parked&&horizontal)full/2 else full;styleBubble();clamp();sprite.invalidate()}
        fun clamp(){
            val sw=resources.displayMetrics.widthPixels;val sh=resources.displayMetrics.heightPixels
            params.x=params.x.coerceIn(0,max(0,sw-params.width));params.y=params.y.coerceIn(dp(24),max(dp(24),sh-params.height-dp(32)))
            if(parked)when(parkedEdge){"left"->params.x=0;"right"->params.x=sw-params.width;"top"->params.y=dp(24);"bottom"->params.y=max(dp(24),sh-params.height-dp(32))}
            runCatching{wm.updateViewLayout(root,params)};positionBubble()
        }
        @android.annotation.SuppressLint("ClickableViewAccessibility")
        private fun attachGestures(){root.setOnTouchListener{_,event->if(event.actionMasked==MotionEvent.ACTION_OUTSIDE){closeMenu();lastInteraction=SystemClock.elapsedRealtime();root.post{(sprite.layoutParams as? FrameLayout.LayoutParams)?.gravity=Gravity.CENTER;clamp()}};false};var x=0f;var y=0f;var sx=0;var sy=0;var dragging=false
            sprite.contentDescription="${art.name}，点击互动，长按菜单，拖动移动";sprite.setOnClickListener{if(parked)greetFromEdge()else interact("pet")};sprite.setOnLongClickListener{restore();lastInteraction=SystemClock.elapsedRealtime();if(controls.visibility==View.VISIBLE)closeMenu()else sprite.post{openMenu()};true}

            val detector=GestureDetector(this@PetOverlayService,object:GestureDetector.SimpleOnGestureListener(){override fun onDown(e:MotionEvent)=true;override fun onSingleTapUp(e:MotionEvent):Boolean{if(!dragging)sprite.performClick();return true};override fun onLongPress(e:MotionEvent){if(!dragging)sprite.performLongClick()}})
            sprite.setOnTouchListener{_,e->when(e.actionMasked){MotionEvent.ACTION_DOWN->{x=e.rawX;y=e.rawY;sx=params.x;sy=params.y;dragging=false;draggingNow=false;roamingUntil=0;nextRoam=SystemClock.elapsedRealtime()+5000;lastInteraction=SystemClock.elapsedRealtime()};MotionEvent.ACTION_MOVE->{val dx=e.rawX-x;val dy=e.rawY-y;if(abs(dx)+abs(dy)>ViewConfiguration.get(this@PetOverlayService).scaledTouchSlop)dragging=true;draggingNow=dragging;if(dragging&&!parked){params.x=sx+dx.roundToInt();params.y=sy+dy.roundToInt();clamp()}};MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL->{draggingNow=false}};detector.onTouchEvent(e);true}
        }
        fun interact(kind:String){restore();lastInteraction=SystemClock.elapsedRealtime();state=store.petState(art.id)
            if(kind=="close"){remove(art.id);return}
            if(kind !in MotionDefaults.actions&&clips.none{it.action==kind}&&kind!="chat")return
            if(kind=="chat"){chat?.dismiss();chatPet=art.id;chat=DesktopChatWindow(this@PetOverlayService,art,companions,client,closed={chat=null;chatPet=null;lastInteraction=SystemClock.elapsedRealtime()},replied={});chat?.show();closeMenu();return}
            if(kind!="sleep")state=state.copy(sleeping=false)
            state=when(kind){"sleep"->state.rest(System.currentTimeMillis());else->state.pet(System.currentTimeMillis())};store.savePetState(art.id,state)
            play(MotionDefaults.eligible(clips,kind,false).randomOrNull()?:defaults[kind]?:fallback("pet"))
            say(when(kind){"sleep"->if(state.sleeping)"Zzz… 晚安" else "睡醒啦！";"wave"->"嗨！今天也很喜欢你 ♡";"walk"->"陪我散个小步吧～";"play"->"一起玩！轮到你啦 ♪";else->listOf("(づ｡◕‿‿◕｡)づ 抱抱收到了","(≧▽≦) 今天也喜欢你！","(｡･ω･｡)ﾉ♡ 贴贴","(〃ω〃) 被你摸到啦","٩(๑❛ᴗ❛๑)۶ 开心转圈","(=^･ω･^=) 喵～","(๑˃̵ᴗ˂̵)و 把小快乐分给你","(っ´▽｀)っ 陪你发会儿呆","ヽ(✿ﾟ▽ﾟ)ノ 小花送给你","(๑•̀ㅂ•́)و✧ 给你加油").random()})
        }
        /** Menu is a separate overlay. The pet window and its measured anchor never resize. */
        private fun openMenu(){
            if(controls.visibility==View.VISIBLE)return
            restore();hideBubble();lastInteraction=SystemClock.elapsedRealtime();roamingUntil=0;nextRoam=lastInteraction+6000;playing=null;action="idle";sprite.invalidate()
            val space=dp(128);val sw0=resources.displayMetrics.widthPixels;val sh0=resources.displayMetrics.heightPixels
            val nx=params.x.coerceIn(max(0,space-params.width/2),max(space-params.width/2,sw0-space-params.width/2))
            val ny=params.y.coerceIn(max(dp(24),space-params.height/2),max(space-params.height/2,sh0-space-dp(74)-params.height/2))
            if(nx!=params.x||ny!=params.y){val ox=params.x;val oy=params.y
                menuAnimator=android.animation.ValueAnimator.ofFloat(0f,1f).apply{duration=180;interpolator=android.view.animation.DecelerateInterpolator();addUpdateListener{v->val t=v.animatedValue as Float;params.x=(ox+(nx-ox)*t).roundToInt();params.y=(oy+(ny-oy)*t).roundToInt();clamp()};addListener(object:android.animation.AnimatorListenerAdapter(){private var cancelled=false;override fun onAnimationCancel(a:android.animation.Animator){cancelled=true};override fun onAnimationEnd(a:android.animation.Animator){if(!cancelled)sprite.post{openMenu()}}});start()};return}
            val location=IntArray(2);sprite.getLocationOnScreen(location)
            val menuImage=sprite.snapshot()
            val petCentre=sprite.visibleCenter();val cx=location[0]+petCentre.x;val cy=location[1]+petCentre.y
            val sw=resources.displayMetrics.widthPixels;val sh=resources.displayMetrics.heightPixels
            controls.removeAllViews();controls.visibility=View.VISIBLE
            controls.setOnClickListener{closeMenu()}
            val options=MotionDefaults.groups(clips).map{MotionDefaults.label(it,clips) to it}
            val pages=max(1,(options.size+11)/12);menuPage=menuPage.coerceIn(0,pages-1)
            val visible=options.drop(menuPage*12).take(12)
            val radius=min(dp(96).toFloat(),minOf(cx-dp(25),sw-cx-dp(25),cy-dp(25),sh-cy-dp(25))).coerceAtLeast(dp(65).toFloat())
            visible.forEachIndexed{i,(label,id)->
                val angle=(-90+i*360.0/max(1,visible.size))*Math.PI/180
                val bx=(cx+radius*cos(angle).toFloat()-dp(23)).coerceIn(0f,(sw-dp(46)).toFloat())
                val by=(cy+radius*sin(angle).toFloat()-dp(23)).coerceIn(dp(24).toFloat(),(sh-dp(70)).toFloat())
                val button=object:Button(this@PetOverlayService){override fun onTouchEvent(e:MotionEvent):Boolean{if(e.actionMasked==MotionEvent.ACTION_DOWN&&hypot(e.x-width/2f,e.y-height/2f)>width/2f)return false;return super.onTouchEvent(e)}}.apply{
                    text=label;textSize=10f;maxLines=2;minWidth=0;minimumWidth=0;minHeight=0;minimumHeight=0;setPadding(dp(3),0,dp(3),0)
                    setTextColor(ink());background=GradientDrawable().apply{shape=GradientDrawable.OVAL;setColor(pastel());setStroke(dp(1),Color.argb(160,Color.red(accent()),Color.green(accent()),Color.blue(accent())))}
                    stateListAnimator=null;elevation=dp(4).toFloat();contentDescription="桌面动作：$label"
                    tag=floatArrayOf(cx-dp(23)-bx,cy-dp(23)-by);translationX=cx-dp(23)-bx;translationY=cy-dp(23)-by;scaleX=.2f;scaleY=.2f;alpha=0f;setOnClickListener{closeMenu();interact(id)}
                }
                controls.addView(button,FrameLayout.LayoutParams(dp(46),dp(46)).apply{leftMargin=bx.roundToInt();topMargin=by.roundToInt()})
            }
            val tools=LinearLayout(this@PetOverlayService).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER}
            fun tool(label:String,click:()->Unit){tools.addView(Button(this@PetOverlayService).apply{text=label;textSize=13f;minWidth=0;minimumWidth=0;minHeight=0;minimumHeight=0;setPadding(dp(16),0,dp(16),0);setTextColor(ink());background=GradientDrawable().apply{cornerRadius=dp(23).toFloat();setColor(pastel(215));setStroke(dp(1),pastel(240))};stateListAnimator=null;setOnClickListener{click()}},LinearLayout.LayoutParams(-2,dp(46)).apply{leftMargin=dp(4);rightMargin=dp(4)})}
            tool("聊天"){closeMenu();interact("chat")};tool("收回"){closeMenu();interact("close")}
            if(pages>1)tool("${menuPage+1}/$pages →"){closeMenu();menuPage=(menuPage+1)%pages;openMenu()}
            tools.measure(View.MeasureSpec.UNSPECIFIED,View.MeasureSpec.UNSPECIFIED);controls.addView(tools,FrameLayout.LayoutParams(-2,dp(46)).apply{leftMargin=(cx-tools.measuredWidth/2).roundToInt().coerceIn(0,max(0,sw-tools.measuredWidth));topMargin=(cy+radius+dp(32)).roundToInt().coerceAtMost(sh-dp(78))})
            val menuParams=WindowManager.LayoutParams(-1,-1,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT).apply{gravity=Gravity.TOP or Gravity.START;x=0;y=0}
            wm.addView(controls,menuParams)
            controls.post{
                if(controls.visibility!=View.VISIBLE)return@post
                val origin=IntArray(2);controls.getLocationOnScreen(origin)
                for(n in 0 until controls.childCount){val child=controls.getChildAt(n);val lp=child.layoutParams as FrameLayout.LayoutParams;lp.leftMargin-=origin[0];lp.topMargin-=origin[1];child.layoutParams=lp}
                menuImage?.let{image->
                    val petImage=ImageView(this@PetOverlayService).apply{setImageBitmap(image);scaleType=ImageView.ScaleType.FIT_XY;alpha=companions.settings.value.opacity;contentDescription="圆环中央的${art.name}"}
                    controls.addView(petImage,0,FrameLayout.LayoutParams(sprite.width,sprite.height).apply{leftMargin=location[0]-origin[0];topMargin=location[1]-origin[1]})
                    root.alpha=0f
                }
            }
            menuAnimator=android.animation.ValueAnimator.ofFloat(0f,1f).apply{
                duration=440;interpolator=android.view.animation.LinearInterpolator()
                addUpdateListener{v->val t=v.animatedValue as Float;val spring=(1-exp(-7f*t)*cos(10f*t)).coerceIn(0f,1.12f)
                    for(n in 0 until controls.childCount){val child=controls.getChildAt(n);val offset=child.tag as? FloatArray?:continue
                        child.translationX=offset[0]*(1-spring);child.translationY=offset[1]*(1-spring);child.scaleX=.2f+.8f*spring;child.scaleY=.2f+.8f*spring;child.alpha=(t*5).coerceAtMost(1f)
                    }
                };start()
            }
        }
        private fun closeMenu(){
            menuAnimator?.cancel();menuAnimator=null
            if(controls.visibility!=View.VISIBLE)return
            controls.visibility=View.GONE;root.alpha=1f
            runCatching{wm.removeView(controls)};controls.removeAllViews()
            lastInteraction=SystemClock.elapsedRealtime()
        }
        private fun quiet():Boolean {val h=Calendar.getInstance().get(Calendar.HOUR_OF_DAY);return companions.settings.value.quietNight&&(h>=22||h<8)}
        private fun hideBubble(){bubbleJob?.cancel();bubbleJob=null;if(bubbleAttached){runCatching{wm.removeView(bubble)};bubbleAttached=false}}
        private fun positionBubble(){if(!bubbleAttached)return
            bubble.measure(View.MeasureSpec.makeMeasureSpec(dp(210),View.MeasureSpec.AT_MOST),View.MeasureSpec.makeMeasureSpec(dp(140),View.MeasureSpec.AT_MOST))
            val sw=resources.displayMetrics.widthPixels;val sh=resources.displayMetrics.heightPixels
            bubbleParams.x=(params.x+params.width/2-bubble.measuredWidth/2).coerceIn(0,max(0,sw-bubble.measuredWidth))
            bubbleParams.y=(params.y-bubble.measuredHeight+dp(3)).coerceIn(dp(24),max(dp(24),sh-bubble.measuredHeight-dp(32)))
            runCatching{wm.updateViewLayout(bubble,bubbleParams)}
        }
        private fun say(text:String){
            bubble.text=text.take(110);styleBubble();if(!paused&&!parked){if(!bubbleAttached){wm.addView(bubble,bubbleParams);bubbleAttached=true};positionBubble();bubbleJob?.cancel();bubbleJob=scope.launch{delay(if(text.length>35)7500 else 4500);hideBubble()}}
            if(companions.settings.value.voice&&speechReady&&!paused&&!quiet())tts?.speak(text.take(180),TextToSpeech.QUEUE_ADD,null,art.id)
        }
        fun maybeTalk(){if(paused||chatPet==art.id||state.sleeping||quiet()||talking?.isActive==true)return
            val p=companions.profile(art.id);if(!p.proactive||!companions.connected())return
            val now=SystemClock.elapsedRealtime();val interval=companions.settings.value.idleMinutes*60_000L
            if(now-lastInteraction<interval||now-lastAuto<interval)return
            val day=java.text.SimpleDateFormat("yyyy-MM-dd",Locale.ROOT).format(Date());val key="${art.id}-$day";val count=budget.getInt(key,0);if(count>=12)return
            lastAuto=now;budget.edit().putInt(key,count+1).apply()
            talking=scope.launch{try{val answer=client.send(art.id,"你的小伙伴有一会儿没有和你互动了。参考你的独立性格和历史记忆，用符合自己说话方式、不催促的一句话打招呼，不要声称看到了屏幕内容。",true);if(!paused&&!quiet()&&art.id in pets){restore();say(answer)}}catch(e:CancellationException){throw e}catch(_:Exception){/* Failed automatic requests stay quiet and retain their cooldown. */}}
        }
        fun dispose(){hideBubble();closeMenu();PetController.visibleIds.value=PetController.visibleIds.value-art.id;loop?.cancel();talking?.cancel();runCatching{wm.removeView(root)};sprite.dispose()}
        private inner class Sprite:View(this@PetOverlayService) {
            private val paint=Paint().apply{isAntiAlias=false;isFilterBitmap=false}
            private var bitmap:Bitmap?=null;private var cachedFrame:PixelFrame?=null;private val dst=RectF()
            private var lastRegionKey="";private var opaque=Region()
            fun snapshot():Bitmap?=if(width<=0||height<=0)null else Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888).also{draw(Canvas(it))}
            fun visibleCenter():PointF{
                val b=bitmap?:return PointF(width/2f,height/2f);val f=cachedFrame?:return PointF(width/2f,height/2f)
                var left=b.width;var right=0;var top=b.height;var bottom=0
                f.pixels.forEachIndexed{i,c->if(c ushr 24>=32){left=min(left,i%b.width);right=max(right,i%b.width);top=min(top,i/b.width);bottom=max(bottom,i/b.width)}}
                if(left>right)return PointF(width/2f,height/2f)
                return PointF(dst.left+(left+right+1)/2f*dst.width()/b.width,dst.top+(top+bottom+1)/2f*dst.height()/b.height)
            }
            override fun onDraw(canvas:Canvas){super.onDraw(canvas)
                val clip=if(state.sleeping)(MotionDefaults.eligible(clips,"sleep",false).firstOrNull()?:fallback("sleep"))else playing?:MotionDefaults.eligible(clips,"idle",false).firstOrNull()?:fallback("idle")
                val elapsed=if(!companions.settings.value.motion||parked)0L else if(playing!=null)SystemClock.elapsedRealtime()-startedAt else if(controls.visibility==View.VISIBLE)0L else SystemClock.elapsedRealtime()
                val f=clip.frameAt(elapsed,playing==null)
                if(cachedFrame!==f){bitmap?.recycle();bitmap=Bitmap.createBitmap(f.pixels.toIntArray(),clip.width,clip.height,Bitmap.Config.ARGB_8888);cachedFrame=f;lastRegionKey=""}
                val b=bitmap?:return
                // Fractional scaling permits a 96px source to be genuinely tiny on screen.
                val full=dp(companions.settings.value.petSize).toFloat();val scale=min(full/b.width,full/b.height)
                val w=b.width*scale;val h=b.height*scale;val matrix=Matrix()
                if(parked){
                    val reveal=h*.48f
                    dst.set(-w/2,0f,w/2,h)
                    when(parkedEdge){
                        "left"->{matrix.setRotate(90f);matrix.postTranslate(reveal,height/2f)}
                        "right"->{matrix.setRotate(-90f);matrix.postTranslate(width-reveal,height/2f)}
                        "top"->{matrix.setRotate(180f);matrix.postTranslate(width/2f,reveal)}
                        else->matrix.setTranslate(width/2f,height-reveal)
                    }
                }else dst.set((width-w)/2,(height-h)/2,(width+w)/2,(height+h)/2)
                val flip=clip.action=="walk"&&if(art.id.startsWith("pack16-"))direction>0 else direction<0
                if(!parked&&flip) {matrix.setScale(-1f,1f,width/2f,height/2f)}
                canvas.save();canvas.concat(matrix);canvas.drawBitmap(b,null,dst,paint);canvas.restore()
                val key="$width:$height:$parked:$parkedEdge:$direction"
                if(lastRegionKey!=key){lastRegionKey=key;val region=Region();val rr=RectF()
                    for(y in 0 until b.height){var x=0;while(x<b.width){while(x<b.width&&(f.pixels[y*b.width+x] ushr 24)<32)x++;val start=x;while(x<b.width&&(f.pixels[y*b.width+x] ushr 24)>=32)x++;if(x>start){rr.set(dst.left+start*scale,dst.top+y*scale,dst.left+x*scale,dst.top+(y+1)*scale);matrix.mapRect(rr);region.op(floor(rr.left).toInt(),floor(rr.top).toInt(),ceil(rr.right).toInt(),ceil(rr.bottom).toInt(),Region.Op.UNION)}}}
                    region.op(0,0,width,height,Region.Op.INTERSECT);opaque=region
                    // Public API, no hidden APIs or accessibility service permission.
                    if(Build.VERSION.SDK_INT>=33)root.rootSurfaceControl?.setTouchableRegion(region)
                }
            }
            override fun onTouchEvent(e:MotionEvent):Boolean{if(e.actionMasked==MotionEvent.ACTION_DOWN&&!opaque.contains(e.x.toInt(),e.y.toInt()))return false;return super.onTouchEvent(e)}
            override fun performClick():Boolean{super.performClick();return true}
            fun dispose(){bitmap?.recycle();bitmap=null;cachedFrame=null}
        }
    }
}
