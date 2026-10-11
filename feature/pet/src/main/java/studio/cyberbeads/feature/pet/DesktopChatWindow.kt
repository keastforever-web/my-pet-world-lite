package studio.cyberbeads.feature.pet

import android.app.Service
import android.content.*
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.*
import kotlinx.coroutines.*
import studio.cyberbeads.core.data.*
import studio.cyberbeads.core.model.*
import kotlin.math.*

/** A focusable, separately dismissible desktop window. Pet windows themselves never capture typing. */
internal class DesktopChatWindow(private val service:Service,private val art:Artwork,private val store:CompanionStore,private val client:PetChatClient,private val closed:()->Unit,private val replied:(String)->Unit={}){
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private val wm=service.getSystemService(WindowManager::class.java)
    private val ime=service.getSystemService(InputMethodManager::class.java)
    private fun dp(v:Int)=(v*service.resources.displayMetrics.density).roundToInt()
    private val accent=art.pattern.palette.firstOrNull{Color.red(it)+Color.green(it)+Color.blue(it)>220&&Color.red(it)+Color.green(it)+Color.blue(it)<690}?:0xffff9fc4.toInt()
    private fun tint(amount:Float)=Color.rgb((Color.red(accent)*amount+255*(1-amount)).toInt(),(Color.green(accent)*amount+255*(1-amount)).toInt(),(Color.blue(accent)*amount+255*(1-amount)).toInt())
    private val ink=0xff403344.toInt()
    private fun bg(c:Int,r:Int=16)=GradientDrawable().apply{setColor(c);cornerRadius=dp(r).toFloat()}
    private val root=object:LinearLayout(service){override fun dispatchKeyEvent(e:KeyEvent):Boolean{if(e.keyCode==KeyEvent.KEYCODE_BACK&&e.action==KeyEvent.ACTION_UP){dismiss();return true};return super.dispatchKeyEvent(e)}}.apply{orientation=LinearLayout.VERTICAL;setPadding(dp(10),dp(8),dp(10),dp(8));background=bg(tint(.10f));elevation=dp(12).toFloat()}
    private val history=LinearLayout(service).apply{orientation=LinearLayout.VERTICAL}
    private val scroll=ScrollView(service).apply{addView(history)}
    private val status=TextView(service).apply{textSize=11f;setTextColor(ink);text="仅发送文字到这位伙伴的 API";setPadding(dp(4),dp(4),dp(4),dp(4))}
    private val input=EditText(service).apply{hint="对${art.name}说点什么…";setTextColor(ink);setHintTextColor(0xff817382.toInt());textSize=14f;maxLines=3;minHeight=dp(44);inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE;imeOptions=android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI;showSoftInputOnFocus=false;setPadding(dp(10),dp(6),dp(10),dp(6));background=bg(Color.WHITE,10);contentDescription="桌面聊天输入"}
    private val keyboard=LinearLayout(service).apply{orientation=LinearLayout.VERTICAL;contentDescription="宠物配色迷你键盘"}
    private var repeatDelete:Job?=null
    private var attached=false;private var sending=false;private var symbols=false;private var uppercase=false;private var chinese=true;private var spelling=""
    private var pinyin:PinyinInput?=null;private var candidateJob:Job?=null;private var choices:List<PinyinInput.Candidate> = emptyList();private var candidatePage=0
    private val candidateLabel=TextView(service).apply{textSize=12f;setTextColor(ink)}
    private val candidateButtons=mutableListOf<Button>()
    private val candidates=LinearLayout(service).apply{orientation=LinearLayout.VERTICAL}
    private val outer=ScrollView(service).apply{isFillViewport=false;addView(root)}
    private val params=WindowManager.LayoutParams(min(dp(350),service.resources.displayMetrics.widthPixels-dp(16)),WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,PixelFormat.TRANSLUCENT).apply{gravity=Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL;y=dp(30);softInputMode=WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE}
    private fun button(label:String,action:()->Unit)=Button(service).apply{text=label;textSize=12f;isAllCaps=false;minWidth=0;minimumWidth=0;minHeight=0;minimumHeight=0;setPadding(0,0,0,0);setTextColor(ink);background=GradientDrawable().apply{setColor(tint(.20f));cornerRadius=dp(9).toFloat();setStroke(dp(1),tint(.5f))};stateListAnimator=null;setOnClickListener{action()}
        setOnTouchListener{v,event->when(event.actionMasked){
            MotionEvent.ACTION_DOWN->{v.animate().scaleX(.91f).scaleY(.91f).setDuration(65).start();if(label=="⌫"){action();repeatDelete?.cancel();repeatDelete=scope.launch{delay(360);while(isActive){action();delay(65)}}}}
            MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL->{repeatDelete?.cancel();repeatDelete=null;v.animate().scaleX(1f).scaleY(1f).setDuration(110).start()}
        };label=="⌫"}}
    init{
        val header=LinearLayout(service).apply{gravity=Gravity.CENTER_VERTICAL};header.addView(TextView(service).apply{text="${art.name} · 桌面聊天";textSize=15f;setTextColor(ink)},LinearLayout.LayoutParams(0,dp(40),1f));header.addView(button("关闭"){dismiss()},LinearLayout.LayoutParams(dp(54),dp(40)));root.addView(header)
        root.addView(scroll,LinearLayout.LayoutParams(-1,dp(76)));root.addView(status);root.addView(input,LinearLayout.LayoutParams(-1,dp(52)))
        val actions=LinearLayout(service)
        listOf("使用手机键盘" to {systemKeyboard()},"迷你键盘" to {miniKeyboard()},"发送" to {send()}).forEach{(label,action)->actions.addView(button(label,action),LinearLayout.LayoutParams(0,dp(42),1f))};root.addView(actions);root.addView(candidates);root.addView(keyboard)
        if(!store.connected()){
            status.text="还未配置 API，请在设置页填写共享接口。"
            root.addView(button("设置接口与性格"){service.startActivity(service.packageManager.getLaunchIntentForPackage(service.packageName)!!.putExtra("chatPet",art.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP));dismiss()},LinearLayout.LayoutParams(-1,dp(42)))
        }
        candidates.addView(candidateLabel,LinearLayout.LayoutParams(-1,dp(22)))
        val candidateRow=LinearLayout(service)
        repeat(8){index->val b=button(""){choices.getOrNull(candidatePage*8+index)?.let{c->insert(c.text);spelling=spelling.drop(c.consumed);updateCandidates()}};candidateButtons.add(b);candidateRow.addView(b,LinearLayout.LayoutParams(dp(62),dp(38)))}
        candidates.addView(HorizontalScrollView(service).apply{addView(candidateRow)})
        val pages=LinearLayout(service);pages.addView(button("上一页"){candidatePage=(candidatePage-1).coerceAtLeast(0);renderCandidates()},LinearLayout.LayoutParams(0,dp(34),1f));pages.addView(button("下一页"){if((candidatePage+1)*8<choices.size)candidatePage++;renderCandidates()},LinearLayout.LayoutParams(0,dp(34),1f));candidates.addView(pages);candidates.visibility=View.VISIBLE
        scope.launch{pinyin=withContext(Dispatchers.IO){PinyinInput(service)};updateCandidates()}
        buildKeys()
    }
    fun show(){try{resize();wm.addView(outer,params);attached=true;root.alpha=0f;root.translationY=dp(18).toFloat();root.animate().alpha(1f).translationY(0f).setDuration(200).start();input.requestFocus();scope.launch{store.revision.collect{refresh()}}}catch(_:Exception){Toast.makeText(service,"聊天窗暂时无法显示，请检查悬浮窗权限",Toast.LENGTH_SHORT).show();dismiss()}}
    private suspend fun refresh(){try{val lines=store.page(art.id,limit=30);history.removeAllViews();if(lines.isEmpty())history.addView(TextView(service).apply{text="嗨，我在这里。和我聊聊吧 ♡";setTextColor(ink)})
        lines.forEach{line->val row=LinearLayout(service).apply{gravity=if(line.role=="user")Gravity.END else Gravity.START};val bubble=TextView(service).apply{text=(if(line.role=="user")"我\n" else "${art.name}\n")+line.text;textSize=13f;setTextColor(ink);setTextIsSelectable(true);setPadding(dp(10),dp(8),dp(10),dp(8));background=bg(if(line.role=="user")tint(.30f)else Color.WHITE,12)};row.addView(bubble,LinearLayout.LayoutParams(dp(260),-2).apply{topMargin=dp(5);bottomMargin=dp(5)});history.addView(row)};scroll.post{scroll.fullScroll(View.FOCUS_DOWN)}}catch(_:Exception){status.text="历史读取失败，原记录仍保留"}}
    private fun insert(s:String){val a=input.selectionStart.coerceAtLeast(0);val b=input.selectionEnd.coerceAtLeast(a);input.text.replace(a,b,s);input.requestFocus()}
    fun resize(){params.width=min(dp(350),service.resources.displayMetrics.widthPixels-dp(16));params.height=min(dp(570),service.resources.displayMetrics.heightPixels-dp(64));if(attached)runCatching{wm.updateViewLayout(outer,params)}}
    private fun renderCandidates(){candidates.visibility=if(keyboard.visibility==View.VISIBLE)View.VISIBLE else View.GONE;candidateLabel.text=if(chinese&&spelling.isNotEmpty())"拼音 $spelling · ${candidatePage+1}/${((choices.size+7)/8).coerceAtLeast(1)}页" else if(chinese)"输入拼音，在这里选择汉字" else "英文模式 · 直接输入字母";candidateButtons.forEachIndexed{i,b->val c=choices.getOrNull(candidatePage*8+i);b.text=c?.text.orEmpty();b.visibility=if(c==null)View.INVISIBLE else View.VISIBLE}}
    private fun updateCandidates(){candidateJob?.cancel();candidatePage=0;choices=emptyList();renderCandidates();if(!chinese||spelling.isEmpty())return;val query=spelling
        candidateJob=scope.launch{delay(35);val found=withContext(Dispatchers.Default){pinyin?.candidates(query).orEmpty()};if(query==spelling){choices=found;renderCandidates()}}
    }
    private fun letter(s:String){if(chinese&&!symbols&&s.all{it.lowercaseChar() in 'a'..'z'}){spelling=(spelling+s.lowercase()).take(40);updateCandidates()}else insert(s)}
    private fun backspace(){if(spelling.isNotEmpty()){spelling=spelling.dropLast(1);updateCandidates();return};val a=input.selectionStart.coerceAtLeast(0);val b=input.selectionEnd.coerceAtLeast(a);if(b>a)input.text.delete(a,b)else if(a>0)input.text.delete(Character.offsetByCodePoints(input.text,a,-1),a)}
    private fun buildKeys(){
        repeatDelete?.cancel();repeatDelete=null;keyboard.removeAllViews()
        val rows=if(symbols)listOf("1234567890",if(chinese)"，。？！：；、（）…" else "@#.,?!-+()",if(chinese)"《》【】‘’“”—·" else "[]{}%&*=:/")else listOf("qwertyuiop","asdfghjkl","zxcvbnm").map{if(uppercase)it.uppercase()else it}
        // Every mode has exactly three 10-unit rows. Short letter rows are centered.
        rows.forEachIndexed{index,letters->val row=LinearLayout(service).apply{gravity=Gravity.CENTER}
            val pad=if(index==2&&!symbols)0f else (10-letters.length)/2f
            if(index==2&&!symbols)row.addView(button(if(uppercase)"⇧"else"↑"){uppercase=!uppercase;buildKeys()},LinearLayout.LayoutParams(0,dp(36),1.5f).apply{setMargins(dp(1),dp(2),dp(1),dp(2))})
            if(pad>0)row.addView(Space(service),LinearLayout.LayoutParams(0,dp(40),pad))
            (if(index==2&&symbols)letters.take(8)else letters).forEach{c->row.addView(button(c.toString()){letter(c.toString())},LinearLayout.LayoutParams(0,dp(36),1f).apply{setMargins(dp(1),dp(2),dp(1),dp(2))})}
            if(pad>0)row.addView(Space(service),LinearLayout.LayoutParams(0,dp(40),pad))
            if(index==2)row.addView(button("⌫"){backspace()},LinearLayout.LayoutParams(0,dp(36),if(symbols)2f else 1.5f).apply{setMargins(dp(1),dp(2),dp(1),dp(2))})
            keyboard.addView(row,LinearLayout.LayoutParams(-1,dp(40)))
        }
        val row=LinearLayout(service)
        // Numbers and punctuation share one page; case belongs exclusively to Shift.
        listOf<Pair<String,()->Unit>>((if(chinese)"中/英" else "英/中") to {chinese=!chinese;spelling="";updateCandidates();buildKeys()},(if(symbols)"ABC" else "123") to {symbols=!symbols;buildKeys()},"空格" to {if(spelling.isNotEmpty()){choices.firstOrNull()?.let{insert(it.text);spelling=spelling.drop(it.consumed);updateCandidates()}}else insert(" ")}).forEach{(label,action)->row.addView(button(label,action),LinearLayout.LayoutParams(0,dp(36),if(label=="空格")2f else 1f).apply{setMargins(dp(1),dp(2),dp(1),dp(2))})};keyboard.addView(row,LinearLayout.LayoutParams(-1,dp(40)))
        val phrases=LinearLayout(service);listOf("你好呀","抱抱你","今天很开心").forEach{label->phrases.addView(button(label){insert(label)},LinearLayout.LayoutParams(0,dp(30),1f).apply{setMargins(dp(1),dp(2),dp(1),dp(2))})};keyboard.addView(phrases,LinearLayout.LayoutParams(-1,dp(34)))
    }
    private fun miniKeyboard(){input.showSoftInputOnFocus=false;ime.hideSoftInputFromWindow(input.windowToken,0);keyboard.visibility=View.VISIBLE;renderCandidates()}
    private fun systemKeyboard(){keyboard.visibility=View.GONE;candidates.visibility=View.GONE;input.showSoftInputOnFocus=true;input.requestFocus();input.post{ime.showSoftInput(input,InputMethodManager.SHOW_IMPLICIT)}}
    private fun send(){val text=input.text.toString().trim();if(text.isEmpty()||sending)return
        if(text.length>4000){status.text="一次最多 4000 字";return};sending=true;status.text="${art.name}正在想…";input.isEnabled=false
        scope.launch{try{client.send(art.id,text).also(replied);input.setText("");status.text="已回复 ♡";refresh()}catch(e:CancellationException){throw e}catch(e:Exception){status.text=e.message?:"发送失败，可保留文字后重试";refresh()}finally{sending=false;input.isEnabled=true}}
    }
    fun dismiss(){ime.hideSoftInputFromWindow(input.windowToken,0);if(attached){attached=false;runCatching{wm.removeView(outer)}};scope.cancel();closed()}
}
