package studio.cyberbeads.core.model

import kotlin.math.*

data class BeadPattern(val width: Int, val height: Int, val palette: List<Int>, val cells: List<Int>, val brandId:String?=null, val colorCodes:List<String> = emptyList(), val paletteId:String?=brandId) {
    init {
        require(width in 1..128 && height in 1..128 && cells.size == width * height)
        require(palette.size in 1..512 && cells.all { it == -1 || it in palette.indices })
    }
    fun code(index:Int):String = colorCodes.getOrNull(index) ?: "${index+1}"
    val beadCount: Int get() = cells.count { it >= 0 }
    val colorCounts: Map<Int, Int> get() = cells.filter { it >= 0 }.groupingBy { it }.eachCount()
    fun paint(index: Int, color: Int): BeadPattern {
        require(index in cells.indices && (color == -1 || color in palette.indices))
        return copy(cells = cells.toMutableList().also { it[index] = color })
    }
}

data class Artwork(
    val id: String, val name: String, val createdAt: Long, val pattern: BeadPattern,
    val placed: Set<Int> = emptySet(), val schemaVersion: Int = 1
) {
    val completedCount get() = placed.count { it in pattern.cells.indices && pattern.cells[it] >= 0 }
    val isComplete get() = pattern.beadCount > 0 && completedCount == pattern.beadCount
    val progress get() = if (pattern.beadCount == 0) 0f else completedCount.toFloat() / pattern.beadCount
    fun place(index: Int): Artwork = if (index in pattern.cells.indices && pattern.cells[index] >= 0)
        copy(placed = if (index in placed) placed - index else placed + index) else this
    fun fillColor(color: Int) = copy(placed = placed + pattern.cells.indices.filter { pattern.cells[it] == color })
    fun complete() = copy(placed = pattern.cells.indices.filter { pattern.cells[it] >= 0 }.toSet())
}

/** All future image engines produce a BeadPattern; feature code does not depend on model internals. */
object BeadQuantizer {
    fun convert(pixels: IntArray, width: Int, height: Int, gridSize: Int, maxColors: Int, alphaCutoff: Int = 110): BeadPattern {
        require(width > 0 && height > 0 && pixels.size == width * height)
        require(gridSize in 8..128 && maxColors in 2..32)
        var left = width; var right = -1; var top = height; var bottom = -1
        for (y in 0 until height) for (x in 0 until width) {
            if ((pixels[y * width + x] ushr 24) >= alphaCutoff) {
                left = min(left, x); right = max(right, x); top = min(top, y); bottom = max(bottom, y)
            }
        }
        require(right >= left && bottom >= top) { "没有识别到主体，请保留整图或调整边缘后重试" }
        val sourceW = right - left + 1; val sourceH = bottom - top + 1
        val columns = if (sourceW >= sourceH) gridSize else max(1, (gridSize * sourceW.toFloat() / sourceH).roundToInt())
        val rows = if (sourceH >= sourceW) gridSize else max(1, (gridSize * sourceH.toFloat() / sourceW).roundToInt())
        val sampled = IntArray(columns * rows)
        for (gy in 0 until rows) for (gx in 0 until columns) {
            val x0 = left + gx * sourceW / columns; val x1 = min(right + 1, max(x0 + 1, left + (gx + 1) * sourceW / columns))
            val y0 = top + gy * sourceH / rows; val y1 = min(bottom + 1, max(y0 + 1, top + (gy + 1) * sourceH / rows))
            var r = 0L; var g = 0L; var b = 0L; var n = 0
            for (y in y0 until y1) for (x in x0 until x1) {
                val c = pixels[y * width + x]
                if ((c ushr 24) >= alphaCutoff) { r += c shr 16 and 255; g += c shr 8 and 255; b += c and 255; n++ }
            }
            if (n > 0 && n.toFloat() / ((x1 - x0) * (y1 - y0)) >= .32f)
                sampled[gy * columns + gx] = (0xff000000L or ((r / n) shl 16) or ((g / n) shl 8) or (b / n)).toInt()
        }
        val histogram = sampled.filter { it != 0 }.groupingBy { it }.eachCount()
        require(histogram.isNotEmpty()) { "主体太小，请换一张主体更清晰的图片" }
        val centers = mutableListOf(histogram.maxBy { it.value }.key)
        while (centers.size < min(maxColors, histogram.size)) {
            val next = histogram.keys.filter { it !in centers }.maxByOrNull { color ->
                centers.minOf { distance(color, it) } * sqrt(histogram.getValue(color).toDouble())
            } ?: break
            centers += next
        }
        repeat(10) {
            val totals = Array(centers.size) { LongArray(4) }
            histogram.forEach { (color, count) ->
                val i = centers.indices.minBy { distance(color, centers[it]) }
                totals[i][0] += (color shr 16 and 255).toLong() * count
                totals[i][1] += (color shr 8 and 255).toLong() * count
                totals[i][2] += (color and 255).toLong() * count
                totals[i][3] += count
            }
            totals.forEachIndexed { i, sum -> if (sum[3] > 0) centers[i] =
                (0xff000000L or ((sum[0] / sum[3]) shl 16) or ((sum[1] / sum[3]) shl 8) or (sum[2] / sum[3])).toInt() }
        }
        val palette = centers.distinct().sortedBy { (it shr 16 and 255) * .2126 + (it shr 8 and 255) * .7152 + (it and 255) * .0722 }
        return BeadPattern(columns, rows, palette, sampled.map { color ->
            if (color == 0) -1 else palette.indices.minBy { distance(color, palette[it]) }
        })
    }
    private fun distance(a: Int, b: Int): Double {
        val r = (a shr 16 and 255) - (b shr 16 and 255)
        val g = (a shr 8 and 255) - (b shr 8 and 255)
        val bl = (a and 255) - (b and 255)
        return (2 * r * r + 4 * g * g + 3 * bl * bl).toDouble()
    }
}

data class PetState(val affection:Int=50,val sleeping:Boolean=false,val lastUpdated:Long=0){
 fun tick(now:Long)=copy(lastUpdated=maxOf(lastUpdated,now))
 fun pet(now:Long)=tick(now).copy(affection=(affection+8).coerceAtMost(100))
 fun rest(now:Long)=tick(now).copy(sleeping=!sleeping)
}
