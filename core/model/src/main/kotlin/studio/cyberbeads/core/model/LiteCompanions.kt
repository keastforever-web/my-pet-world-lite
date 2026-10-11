package studio.cyberbeads.core.model

/** Distribution defaults only; user-created and imported IDs are not restricted. */
object LiteCompanions {
    val animalNames = linkedMapOf("builtin-dog" to "草莓小狗", "builtin-snake" to "紫芋小蛇", "builtin-bunny" to "奶油兔兔", "builtin-cat" to "薄荷小猫")
    val spriteIds = (1..5).map { "pack16-$it" }.toSet() + listOf("honey", "rose", "lilac", "mint", "blue").map { "bear16-$it" }
    val builtinIds = animalNames.keys + spriteIds
    val retiredIds = setOf("builtin-nailong", "builtin-maodie")
}
