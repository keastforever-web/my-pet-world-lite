package studio.cyberbeads.feature.pixel

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.util.AtomicFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import studio.cyberbeads.core.data.*
import studio.cyberbeads.core.imaging.ImageInput
import studio.cyberbeads.core.model.*
import java.util.UUID

class PixelViewModel(private val app:Application,private val store:CreativeStore,private val artworks:ArtworkStore,private val projectId:String?,private val sourceId:String?,private val requestedAction:String?,private val clipId:String?,private val blank:Boolean):ViewModel(){
    private var copiedOrigin:Pair<Int,Int>?=null
    val symmetry=MutableStateFlow("normal");val clipboard=MutableStateFlow<PixelClipboard?>(null)
    val dirty=MutableStateFlow(false);val brushSize=MutableStateFlow(1);val region=MutableStateFlow<PixelRegion?>(null)
    private var baseline:PixelProject?=null;private var baselineReference:Bitmap?=null
    val project=MutableStateFlow<PixelProject?>(null);val reference=MutableStateFlow<Bitmap?>(null);val error=MutableStateFlow<String?>(null);val busy=MutableStateFlow(false);val status=MutableStateFlow("")
    val selected=MutableStateFlow(0);val color=MutableStateFlow(PixelOps.starterPalette[2]);val undoCount=MutableStateFlow(0);val redoCount=MutableStateFlow(0)
    var actionLabel="动作";private set
    val action get()=project.value?.motionAction?:requestedAction
    var triggered=true;var random=false;private var motionId=clipId?:UUID.randomUUID().toString();private val undo=ArrayDeque<PixelProject>();private val redo=ArrayDeque<PixelProject>();private var persist:Job?=null
    init{viewModelScope.launch(Dispatchers.Default){busy.value=true;try{
        store.refresh()
        val p=if(projectId!=null)store.get(projectId)?:if(projectId.startsWith("blank-")){val dims=projectId.split("-");val w=dims[1].toInt();val h=dims[2].toInt();PixelProject(projectId,SelectionOps.nextName(store.projects.value.map{it.name}+artworks.items.value.map{it.name}),w,h,PixelOps.starterPalette,listOf(PixelFrame(List(w*h){0})))}else kotlin.error("草稿不存在或已删除")else{
            val art=sourceId?.let{artworks.get(it)}?:kotlin.error("找不到来源作品")
            val action=requestedAction
            if(action!=null){val motions=store.ensureMotions(art);actionLabel=MotionDefaults.label(action,motions);val clip=motions.firstOrNull{it.id==clipId}?:MotionDefaults.clip(art,action).copy(id=motionId,frames=if(blank)listOf(PixelFrame(List(art.pattern.width*art.pattern.height){0}))else MotionDefaults.clip(art,action).frames,width=if(blank)art.pattern.width else MotionDefaults.clip(art,action).width,height=if(blank)art.pattern.height else MotionDefaults.clip(art,action).height,name=if(blank)"新${actionLabel}" else "${actionLabel}动画")
                triggered=clip.triggered;random=clip.random;val colors=clip.frames.asSequence().flatMap{it.pixels.asSequence()}.filter{it ushr 24>0}.distinct().take(32).toList().ifEmpty{PixelOps.starterPalette}
                store.linked(art.id,action,clip.id)?:PixelProject(UUID.randomUUID().toString(),clip.name,clip.width,clip.height,colors,clip.frames,artworkId=art.id,motionAction=action,motionId=clip.id,triggered=clip.triggered,random=clip.random)
            }else store.linked(art.id)?.copy(name=art.name)?:PixelProject("static-${art.id}",art.name,art.pattern.width,art.pattern.height,art.pattern.palette,listOf(PixelFrame(PixelOps.fromPattern(art.pattern))),artworkId=art.id,brandId=art.pattern.brandId,colorCodes=art.pattern.colorCodes,paletteId=art.pattern.paletteId).let{project->store.palettes.value.firstOrNull{it.id==art.pattern.paletteId}?.let{bank->project.copy(palette=bank.colors,colorCodes=bank.codes)}?:project}
        }
        if(p.motionAction!=null&&p.artworkId!=null)actionLabel=MotionDefaults.label(p.motionAction!!,store.motions(p.artworkId!!));project.value=p;triggered=p.triggered;random=p.random;motionId=p.motionId?:motionId;color.value=p.palette.first();baseline=p
        if(store.referenceFile(p.id).exists())reference.value=withContext(Dispatchers.IO){android.graphics.BitmapFactory.decodeFile(store.referenceFile(p.id).absolutePath)}
    baselineReference=reference.value
    }catch(e:CancellationException){throw e}catch(e:Exception){error.value=e.message?:"草稿加载失败"}finally{busy.value=false}}}
    private fun checkpoint(){project.value?.let{undo.addLast(it);if(undo.size>30)undo.removeFirst()};redo.clear();counts()}
    private fun counts(){undoCount.value=undo.size;redoCount.value=redo.size}
    fun beginStroke(){checkpoint()}
    fun stroke(from:Int,to:Int,erase:Boolean){val p=project.value?:return;val n=selected.value;val f=p.frames[n];project.value=p.copy(frames=p.frames.toMutableList().also{it[n]=f.copy(pixels=SelectionOps.stroke(f.pixels,p.width,from,to,if(erase)0 else color.value,brushSize.value,symmetry.value))})}
    fun endStroke(){autosave()}
    fun tap(index:Int,tool:String){val p=project.value?:return;val f=p.frames[selected.value];if(index !in f.pixels.indices)return
        if(tool=="pick"){if(f.pixels[index] ushr 24>0)color.value=f.pixels[index];return}
        if(tool=="fill"){checkpoint();updateFrame(f.copy(pixels=PixelOps.fill(f.pixels,p.width,index,color.value)))}}
    private fun updateFrame(f:PixelFrame){val p=project.value?:return;project.value=p.copy(frames=p.frames.toMutableList().also{it[selected.value]=f});autosave()}
    private fun autosave(){dirty.value=true;status.value="有未保存的修改"}
    fun selectRegion(from:Int,to:Int){val p=project.value?:return;region.value=PixelRegion(minOf(from%p.width,to%p.width),minOf(from/p.width,to/p.width),maxOf(from%p.width,to%p.width),maxOf(from/p.width,to/p.width))}
    fun movePixels(from:Int,to:Int){val p=project.value?:return;checkpoint();val dx=to%p.width-from%p.width;val dy=to/p.width-from/p.width;val f=p.frames[selected.value];updateFrame(f.copy(pixels=CanvasOps.move(f.pixels,p.width,region.value,dx,dy)));region.value=region.value?.let{r->PixelRegion((r.left+dx).coerceIn(0,p.width-1),(r.top+dy).coerceIn(0,p.height-1),(r.right+dx).coerceIn(0,p.width-1),(r.bottom+dy).coerceIn(0,p.height-1))}}
    fun selectionCommand(command:String){
        val p=project.value?:return;val f=p.frames[selected.value]
        val r=region.value?:PixelRegion(0,0,p.width-1,p.height-1)
        if(command=="copy"){clipboard.value=SelectionOps.copy(f.pixels,p.width,r);copiedOrigin=r.left to r.top;status.value="已复制：移动选区后，粘贴会在原位置留下副本";return}
        if(command in listOf("left","right","up","down")){
            val dx=if(command=="left")-1 else if(command=="right")1 else 0
            val dy=if(command=="up")-1 else if(command=="down")1 else 0
            checkpoint();updateFrame(f.copy(pixels=CanvasOps.move(f.pixels,p.width,r,dx,dy)))
            region.value=PixelRegion((r.left+dx).coerceIn(0,p.width-1),(r.top+dy).coerceIn(0,p.height-1),(r.right+dx).coerceIn(0,p.width-1),(r.bottom+dy).coerceIn(0,p.height-1));return
        }
        val clip=if(command=="paste")clipboard.value?:return else SelectionOps.transform(SelectionOps.copy(f.pixels,p.width,r),command)
        checkpoint()
        val base=if(command=="paste")f.pixels else f.pixels.mapIndexed{i,c->if(r.contains(i%p.width,i/p.width))0 else c}
        val origin=if(command=="paste")copiedOrigin?: (r.left to r.top) else r.left to r.top
        updateFrame(f.copy(pixels=SelectionOps.paste(base,p.width,clip,origin.first,origin.second)))
        if(command=="paste"){status.value="已在复制时的位置留下副本，可继续移动选区";return}
        region.value=PixelRegion(r.left,r.top,(r.left+clip.width-1).coerceAtMost(p.width-1),(r.top+clip.height-1).coerceAtMost(p.height-1))
    }
    suspend fun commitName(){
        val p=project.value?:return;val proposed=p.name.trim().ifBlank{SelectionOps.nextName(store.projects.value.map{it.name}+artworks.items.value.map{it.name})}
        val clean=store.availableName(p.copy(name=proposed));project.value=project.value?.copy(name=clean)
        if(p.artworkId!=null&&p.motionAction==null){artworks.update(p.artworkId!!){it.copy(name=clean)};store.renameArtwork(p.artworkId!!,clean)}
        else if(p.artworkId!=null&&p.motionId!=null){store.motions(p.artworkId!!).firstOrNull{it.id==p.motionId}?.let{store.saveMotion(p.artworkId!!,it.copy(name=clean))}}
        store.get(p.id)?.let{store.save(it.copy(name=clean))}
        baseline=baseline?.copy(name=clean)
    }
    fun resize(w:Int,h:Int){val p=project.value?:return;checkpoint();project.value=p.copy(width=w,height=h,frames=p.frames.map{it.copy(pixels=CanvasOps.resize(it.pixels,p.width,p.height,w,h))});region.value=null;autosave()}
    fun useBank(bank:ColorBank){val p=project.value?:return;checkpoint();val matches=if(bank.brand)CanvasOps.match(p.frames.flatMap{it.pixels}.filter{it ushr 24>0}.distinct(),bank)else emptyMap();project.value=p.copy(palette=bank.colors,paletteId=bank.id,brandId=if(bank.brand)bank.id else null,colorCodes=bank.codes,frames=if(bank.brand)p.frames.map{f->f.copy(pixels=f.pixels.map{if(it ushr 24==0)0 else matches.getValue(it)})}else p.frames);color.value=bank.colors.first();autosave()}
    fun chooseColor(c:Int){val p=project.value?:return;color.value=if(p.brandId!=null)CanvasOps.match(listOf(c),ColorBank(p.brandId!!,p.brandId!!,p.palette,p.colorCodes,true)).getValue(c)else c}
    suspend fun discard(){persist?.cancel();project.value=baseline;reference.value=baselineReference;dirty.value=false}
    fun undo(){if(undo.isNotEmpty()){project.value?.let{redo.addLast(it)};project.value=undo.removeLast();selected.value=selected.value.coerceAtMost(project.value!!.frames.lastIndex);counts();autosave()}}
    fun redo(){if(redo.isNotEmpty()){project.value?.let{undo.addLast(it)};project.value=redo.removeLast();selected.value=selected.value.coerceAtMost(project.value!!.frames.lastIndex);counts();autosave()}}
    fun flags(trigger:Boolean,randomly:Boolean){triggered=trigger;random=randomly;project.value=project.value?.copy(triggered=trigger,random=randomly);autosave()}
    fun rename(name:String){project.value=project.value?.copy(name=name.take(40));autosave()}
    fun duration(ms:Int){val p=project.value?:return;checkpoint();updateFrame(p.frames[selected.value].copy(durationMs=ms.coerceIn(40,2000)))}
    fun select(index:Int){selected.value=index.coerceIn(0,(project.value?.frames?.lastIndex?:0))}
    fun addFrame(copy:Boolean){val p=project.value?:return;if(p.frames.size>=32){error.value="每组动画最多 32 帧";return};checkpoint();val list=p.frames.toMutableList();list.add(selected.value+1,if(copy)p.frames[selected.value].copy() else PixelFrame(List(p.width*p.height){0},p.frames[selected.value].durationMs));project.value=p.copy(frames=list);selected.value++;autosave()}
    fun removeFrame(){val p=project.value?:return;if(p.frames.size<=1){error.value="至少保留一帧";return};checkpoint();project.value=p.copy(frames=p.frames.toMutableList().also{it.removeAt(selected.value)});selected.value=selected.value.coerceAtMost(project.value!!.frames.lastIndex);autosave()}
    fun reorderFrame(from:Int,to:Int){val p=project.value?:return;if(from !in p.frames.indices||to !in p.frames.indices||from==to)return;checkpoint();val fs=p.frames.toMutableList();fs.add(to,fs.removeAt(from));project.value=p.copy(frames=fs);selected.value=to;autosave()}
    fun moveFrame(direction:Int){val p=project.value?:return;val i=selected.value;val j=i+direction;if(j !in p.frames.indices)return;checkpoint();val fs=p.frames.toMutableList();val f=fs.removeAt(i);fs.add(j,f);project.value=p.copy(frames=fs);selected.value=j;autosave()}
    fun mirror(){val p=project.value?:return;checkpoint();val f=p.frames[selected.value];updateFrame(f.copy(pixels=PixelOps.mirror(f.pixels,p.width)))}
    fun shift(dx:Int,dy:Int){val p=project.value?:return;checkpoint();val f=p.frames[selected.value];updateFrame(f.copy(pixels=PixelOps.shift(f.pixels,p.width,dx,dy)))}
    fun palette(colors:List<Int>){project.value=project.value?.copy(palette=colors,paletteId=null,brandId=null,colorCodes=emptyList());color.value=colors.first();autosave()}
    fun opacity(v:Float){project.value=project.value?.copy(referenceOpacity=v);autosave()}
    fun importReference(uri:Uri){viewModelScope.launch{busy.value=true;try{reference.value=ImageInput.decode(app,uri);autosave()}catch(e:Exception){error.value=e.message}finally{busy.value=false}}}
    fun clearReference(){reference.value=null;autosave()}
    suspend fun save(){persist?.cancel();project.value?.let{p->
        if(p.motionAction==null&&p.artworkId!=null&&artworks.get(p.artworkId!!)?.isComplete==true)require(p.frames.first().pixels.any{it ushr 24>0}){"宠物形象不能全透明，请保留一些像素"}
        val clean=store.save(p.copy(name=p.name.trim().ifBlank{"我的像素作品"}))
        if(clean.motionAction==null&&clean.artworkId!=null){
            val before=artworks.get(clean.artworkId!!)
            if(before!=null&&before.isComplete&&clean.frames.first().pixels.any{it ushr 24>0}){
                val after=before.copy(name=clean.name,pattern=CanvasOps.pattern(clean),placed=emptySet()).complete()
                if(before.pattern!=after.pattern){store.ensureMotions(before);store.rebaseMotions(before,after)}
                artworks.save(after)
            }else artworks.update(clean.artworkId!!){it.copy(name=clean.name)}
            store.renameArtwork(clean.artworkId!!,clean.name)
        }
        withContext(Dispatchers.IO){val target=AtomicFile(store.referenceFile(clean.id));val bitmap=reference.value;if(bitmap==null)target.delete()else{val out=target.startWrite();try{check(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));target.finishWrite(out)}catch(e:Exception){target.failWrite(out);throw e}}}
        baselineReference=reference.value;baseline=clean;project.value=clean;dirty.value=false
    };status.value="草稿已保存"}
    suspend fun applyMotion(){val p=project.value?:return;val id=sourceId?:p.artworkId?:return;val a=action?:return;require(p.frames.any{f->f.pixels.any{it ushr 24>0}}){"动作还是空白，先画几帧"};save();val saved=project.value!!;val clip=store.saveMotion(id,MotionClip(motionId,saved.name,a,saved.width,saved.height,saved.frames,triggered,random));project.value=saved.copy(name=clip.name);save();status.value="${clip.name}已应用到${actionLabel}"}
    suspend fun publish(complete:Boolean):String{
        save();val p=project.value?:kotlin.error("草稿尚未加载");val pattern=CanvasOps.pattern(p)
        val existing=p.artworkId?.let{artworks.get(it)}
        val art=if(existing==null)artworks.create(p.name,pattern)else existing.copy(name=p.name.ifBlank{existing.name},pattern=pattern,placed=emptySet())
        artworks.save(if(complete||existing?.isComplete==true)art.complete()else art)
        // Completed pets keep their completion state; procedural motion regeneration runs on save.
        project.value=p.copy(artworkId=art.id);save();return art.id
    }
}
