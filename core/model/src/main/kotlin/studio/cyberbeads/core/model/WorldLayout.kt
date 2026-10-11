package studio.cyberbeads.core.model

object WorldLayout{
 /** Preserve placement identity and every furniture item; move only footprints invalidated by a map update. */
 fun repair(placed:List<PlacedFurniture>,furniture:List<GardenFurniture>):List<PlacedFurniture>{
  val result=mutableListOf<PlacedFurniture>();val blocks=mutableListOf<WorldBlock>()
  placed.forEach{p->val f=furniture.firstOrNull{it.id==p.itemId};if(f==null){result.add(p);return@forEach}
   val map=FlatParkMap("garden",blocks)
   val next=if(map.canPlace(f.block(p)))p else (27..52).asSequence().flatMap{y->(7..33).asSequence().map{x->p.copy(x=x/40f,y=y/60f)}}.firstOrNull{map.canPlace(f.block(it))}?:error("家饰需要手动整理，旧布局已保留")
   result.add(next);blocks.add(f.block(next))
  };return result
 }
}
