package studio.cyberbeads.core.model

data class TravelPlace(val id:String,val name:String,val region:String,val caption:String,val tile:Int)
data class LuckyItem(val id:String,val name:String,val description:String,val cost:Int,val tile:Int)
data class InkPoint(val x:Int,val y:Int){init{require(x in 0..1000&&y in 0..1000)}}
data class CardNote(val author:String,val text:String,val time:Long,val ink:List<List<InkPoint>> = emptyList()){
 init{require(text.length<=240);require(ink.size<=80&&ink.sumOf{it.size}<=600);require(ink.all{it.size>=2})}
}
object TravelLife {
 val places=listOf(
  TravelPlace("huangshan","黄山","forest","松风与云海，替我抱抱今天的你。",0),
  TravelPlace("zhangjiajie","张家界","forest","在高高的山峰间，听见风的声音。",1),
  TravelPlace("jiuzhaigou","九寨沟","forest","把湖水的蓝，收藏在这一小张纸里。",2),
  TravelPlace("erhai","大理洱海","forest","沿着湖边走，今天慢一点也很好。",3),
  TravelPlace("westlake","杭州西湖","river","柳枝轻轻摇，想念也轻轻的。",4),
  TravelPlace("lijiangriver","桂林漓江","river","一江山水，留一段给你。",5),
  TravelPlace("gulangyu","厦门鼓浪屿","river","海风经过红屋顶，寄来一封蓝色的信。",6),
  TravelPlace("qingdao","青岛","river","到海边了！今天的烦恼交给浪花。",7),
  TravelPlace("lijiangtown","丽江古城","town","桥下流水，屋檐下是慢慢的时光。",8),
  TravelPlace("suzhou","苏州园林","town","穿过月洞门，看见一院温柔。",9),
  TravelPlace("dunhuang","敦煌月牙泉","town","沙漠里也有一弯不肯消失的月亮。",10),
  TravelPlace("xian","西安","town","灯亮了，带你逛逛远方的街。",11))
 val lucky=listOf(LuckyItem("lucky-clover","四叶草","优先带回当前路线尚未收藏的地点；不保证稀有概率。",4,4),LuckyItem("lucky-compass","远行指南针","本次可从全部12个地点中发现一处。",5,5),LuckyItem("lucky-ticket","旧火车票","带回第二张同地点明信片，留一张、漂流一张。",6,6))
 fun trailOrder(seed:Int):List<String>{val permutations=listOf(listOf("leaf","water","flower"),listOf("leaf","flower","water"),listOf("water","leaf","flower"),listOf("water","flower","leaf"),listOf("flower","leaf","water"),listOf("flower","water","leaf"));return permutations[Math.floorMod(seed,6)]}
 fun foodRegion(f:PixelFood):String {
  val colors=FoodRecipe.normalized(f.pixels,f.width,f.height).filter{it!=0}
  val r=colors.map{it ushr 16 and 255}.average();val g=colors.map{it ushr 8 and 255}.average();val b=colors.map{it and 255}.average()
  return when {g>r*1.08||b>r->"river";r>g*1.2&&b>g*.8->"forest";else->"town"}
 }
 fun choose(region:String,seed:Long,lucky:String,owned:Set<String>):TravelPlace {
  val all=if(lucky=="lucky-compass")places else places.filter{it.region==region}
  val options=if(lucky=="lucky-clover")all.filter{it.id !in owned}.ifEmpty{all}else all
  return options[Math.floorMod(seed,options.size.toLong()).toInt()]
 }
 fun append(c:TravelPostcard,n:CardNote):TravelPostcard {require(n.text.isNotBlank()||n.ink.isNotEmpty());require(c.notes.sumOf{it.ink.sumOf{p->p.size}}+n.ink.sumOf{it.size}<=4000){"信纸的手写空间已满"};require(c.notes.size<40){"这张信纸已经写满了"};require(c.status=="owned"){"明信片正在漂流，不能修改"};return c.copy(notes=c.notes+n)}
}
