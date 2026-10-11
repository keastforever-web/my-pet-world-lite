package studio.cyberbeads.core.model

import kotlin.math.*

data class WorldPoint(val x:Float,val y:Float){fun distance(other:WorldPoint)=hypot((x-other.x)*WorldAtlas.width,(y-other.y)*WorldAtlas.height)}
data class WorldBlock(val left:Float,val top:Float,val right:Float,val bottom:Float){
    fun contains(p:WorldPoint,pad:Float=0f)=p.x>=left-pad&&p.x<=right+pad&&p.y>=top-pad*.667f&&p.y<=bottom+pad*.667f
}
data class MapPortal(val id:String,val title:String,val point:WorldPoint,val approach:WorldPoint,val destination:String="",val spawnId:String="")
data class PlacedFurniture(val id:String,val itemId:String,val x:Float,val y:Float)
data class GardenFurniture(val id:String,val name:String,val width:Int,val height:Int,val pixels:List<Int>,val span:Int=2){
    init{require(width in 1..128&&height in 1..128&&pixels.size==width*height);require(pixels.any{it ushr 24>0});require(span in 1..4)}
    fun block(p:PlacedFurniture)=WorldBlock(p.x-span*.009f,p.y-.013f,p.x+span*.009f,p.y+.0035f)
}

/** 1280x1920 world units; 16-unit tiles. Props and furniture have separate footprints. */
class FlatParkMap(val scene:String,val furniture:List<WorldBlock> = emptyList()){
    val region=WorldAtlas.region(scene)
    val portals=region.portals
    val blocks=region.blocks+furniture
    private val columns=80;private val rows=120;private val count=columns*rows
    fun walkable(p:WorldPoint)=p.x in .025f.. .975f&&p.y in .025f.. .975f&&region.tiles.getOrNull((p.y*region.tiles.size).toInt())?.getOrNull((p.x*region.tiles.first().size).toInt()) !in listOf(8,9,23,27,31)&&blocks.none{it.contains(p,.0035f)}
    fun safe(p:WorldPoint):WorldPoint = if(walkable(p))p else (0 until count).map{center(it)}.filter{walkable(it)}.minBy{it.distance(p)}
    private fun cell(p:WorldPoint)=(p.y*rows).toInt().coerceIn(0,rows-1)*columns+(p.x*columns).toInt().coerceIn(0,columns-1)
    private fun center(i:Int)=WorldPoint((i%columns+.5f)/columns,(i/columns+.5f)/rows)
    private val navigable=BooleanArray(count){walkable(center(it))}
    fun path(from:WorldPoint,to:WorldPoint):List<WorldPoint>{
        if(!walkable(to))return emptyList()
        val start=cell(safe(from));var end=cell(to)
        if(!walkable(center(end)))end=cell(safe(center(end)))
        val parent=IntArray(count){-2};val q=ArrayDeque<Int>();q.add(start);parent[start]=-1
        while(q.isNotEmpty()){val i=q.removeFirst();if(i==end)break;val x=i%columns;val y=i/columns
            for(n in intArrayOf(if(x>0)i-1 else -1,if(x<columns-1)i+1 else -1,if(y>0)i-columns else -1,if(y<rows-1)i+columns else -1))if(n>=0&&parent[n]==-2&&navigable[n]){parent[n]=i;q.add(n)}
        }
        if(parent[end]==-2)return emptyList();val out=mutableListOf<WorldPoint>();var i=end
        while(i!=start&&i>=0){out.add(center(i));i=parent[i]};out.reverse();if(out.isEmpty()||out.last().distance(to)>1f)out.add(to);return out
    }
    /** Small swept steps and axis sliding: cannot tunnel through thin trunks or fences. */
    fun step(from:WorldPoint,dx:Float,dy:Float):WorldPoint {
        if(!dx.isFinite()||!dy.isFinite())return from
        val length=hypot(dx,dy);val count=ceil(length/2f).toInt().coerceIn(1,128)
        var p=from
        repeat(count){val x=dx/count/WorldAtlas.width;val y=dy/count/WorldAtlas.height
            val both=WorldPoint(p.x+x,p.y+y)
            p=if(walkable(both))both else {var next=p;val horizontal=WorldPoint(p.x+x,p.y);if(walkable(horizontal))next=horizontal;val vertical=WorldPoint(next.x,p.y+y);if(walkable(vertical))vertical else next}
        };return p
    }
    fun nearby(p:WorldPoint)=portals.filter{p.distance(it.approach)<=64f}.minByOrNull{p.distance(it.approach)}
    fun canPlace(block:WorldBlock):Boolean{val samples=listOf(WorldPoint(block.left,block.top),WorldPoint(block.right,block.bottom),WorldPoint(block.left,block.bottom),WorldPoint(block.right,block.top));return samples.all{walkable(it)}&&blocks.none{it.left<block.right&&it.right>block.left&&it.top<block.bottom&&it.bottom>block.top}&&portals.none{block.contains(it.approach,.07f)}&&FlatParkMap(scene,furniture+block).let{next->val start=next.safe(WorldAtlas.region(scene).spawn);portals.all{next.path(start,it.approach).isNotEmpty()}}}
}

data class TravelFood(val id:String,val name:String,val region:String,val description:String,val cost:Int,val color:Int)
object ParkCatalog{
    val foods=listOf(TravelFood("berry-box","森林莓果便当","forest","偏向黄山、张家界、九寨沟与洱海路线",3,0xffda7193.toInt()),TravelFood("river-box","溪岸饭团","river","偏向西湖、漓江、鼓浪屿与青岛路线",4,0xff8abfa3.toInt()),TravelFood("town-box","小镇蜂蜜饼","town","偏向丽江古城、苏州、敦煌与西安路线",5,0xffe7b561.toInt()))
    fun builtFurniture():List<GardenFurniture> = listOf("bench" to "奶油长椅","planter" to "莓果花盆","lamp" to "晚安小灯").mapIndexed{n,(id,name)->
        val p=MutableList(32*32){0};fun r(x:Int,y:Int,w:Int,h:Int,c:Long){for(yy in y until y+h)for(xx in x until x+w)if(xx in 0..31&&yy in 0..31)p[yy*32+xx]=c.toInt()}
        when(n){0->{r(3,10,26,4,0xff976246);r(3,15,26,7,0xffddb882);r(5,22,3,6,0xff6d4b40);r(24,22,3,6,0xff6d4b40)};1->{r(9,16,14,13,0xffc68166);r(7,14,18,4,0xffe0ac8c);r(13,5,5,12,0xff75a17a);r(6,8,20,5,0xffa6cd87);r(8,6,4,4,0xffdf82a5);r(22,9,4,4,0xffdf82a5)};else->{r(14,13,4,16,0xff715d59);r(8,6,16,10,0xffffd97c);r(11,8,10,5,0xfffff6c4);r(9,27,14,3,0xff715d59)}}
        GardenFurniture("furn-$id",name,32,32,p,if(n==0)3 else 2)
    }
    fun destination(region:String,seed:Long):Pair<String,String> = when(region){"river"->"溪桥岸边" to "流水慢慢，心事也可以慢慢。";"town"->"蜂蜜小镇" to "把黄昏的香甜，留一份带给你。";else->"花语森林" to listOf("遇到一朵开得很勇敢的小花。","风送来一片叶子，我想寄给你。","在树荫里发了会儿呆，也算美好的一天。")[Math.floorMod(seed,3L).toInt()]}
}

data class PetJourney(val id:String,val petId:String,val petName:String,val foodId:String,val region:String,val departure:Long,val arrival:Long,val claimed:Boolean=false,val luckyId:String="",val placeId:String=""){init{require(arrival>departure)}}
data class TravelPostcard(val id:String,val petId:String,val petName:String,val region:String,val location:String,val caption:String,val time:Long,val width:Int,val height:Int,val pixels:List<Int>,val placeId:String="",val notes:List<CardNote> = emptyList(),val status:String="owned",val remote:Boolean=false,val hop:Int=0)
data class GardenProgress(val leaves:Int=6,val inventory:Map<String,Int> = mapOf("berry-box" to 1),val furniture:List<GardenFurniture> = ParkCatalog.builtFurniture(),val placed:List<PlacedFurniture> = emptyList(),val journeys:List<PetJourney> = emptyList(),val cards:List<TravelPostcard> = emptyList(),val rewards:Set<String> = emptySet())

object GardenRules{
    fun reward(s:GardenProgress,runId:String,amount:Int):GardenProgress{require(amount in 1..3);if(runId in s.rewards)return s;require(s.rewards.size<100000){"本地活动记录已满"};return s.copy(leaves=(s.leaves+amount).coerceAtMost(999999),rewards=s.rewards+runId)}
    fun redeem(s:GardenProgress,id:String):GardenProgress{val cost=ParkCatalog.foods.firstOrNull{it.id==id}?.cost?:TravelLife.lucky.first{it.id==id}.cost;require(s.leaves>=cost){"小叶币还不够，去钓鱼或探索吧"};require((s.inventory[id]?:0)<9999){"背包已满"};return s.copy(leaves=s.leaves-cost,inventory=s.inventory+(id to ((s.inventory[id]?:0)+1)))}
    fun depart(s:GardenProgress,j:PetJourney):GardenProgress{require(s.journeys.none{it.petId==j.petId&&!it.claimed}){"伙伴已在旅行"};require(s.cards.size+(if(j.luckyId=="lucky-ticket")2 else 1)<=500){"旅行相册已满，先整理一下收藏"};require((s.inventory[j.foodId]?:0)>0){"这份旅行补给已经用完"};require(j.region in listOf("forest","river","town"));if(j.luckyId.isNotBlank()){require(TravelLife.lucky.any{it.id==j.luckyId}){"未知幸运道具"}};if(j.luckyId.isNotBlank())require((s.inventory[j.luckyId]?:0)>0){"幸运道具已经用完"};val inv=s.inventory+(j.foodId to (s.inventory.getValue(j.foodId)-1));return s.copy(inventory=if(j.luckyId.isBlank())inv else inv+(j.luckyId to (s.inventory.getValue(j.luckyId)-1)),journeys=s.journeys+j)}
    fun collect(s:GardenProgress,id:String,now:Long,pixels:List<Int>,w:Int,h:Int):GardenProgress{val j=s.journeys.first{it.id==id};require(now>=j.arrival){"伙伴还在路上"};if(j.claimed)return s;val place=TravelLife.places.firstOrNull{it.id==j.placeId};val d=if(place!=null)place.name to place.caption else ParkCatalog.destination(j.region,j.departure);val card=TravelPostcard(j.id,j.petId,j.petName,place?.region?:j.region,d.first,d.second,now,w,h,pixels,j.placeId);return s.copy(journeys=s.journeys.map{if(it.id==id)it.copy(claimed=true)else it},cards=s.cards+card+(if(j.luckyId=="lucky-ticket")listOf(card.copy(id=j.id+"-extra"))else emptyList()),leaves=(s.leaves+1).coerceAtMost(999999))}
}
