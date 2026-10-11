package studio.cyberbeads.core.imaging

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.*
import androidx.exifinterface.media.ExifInterface
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import studio.cyberbeads.core.model.BeadPattern
import java.nio.FloatBuffer
import kotlin.math.*

interface SubjectSegmenter { suspend fun isolate(input: Bitmap, detailed: Boolean = true): Bitmap }

class OnnxSubjectSegmenter(context: Context) : SubjectSegmenter {
    private val app = context.applicationContext
    private val environment = OrtEnvironment.getEnvironment()
    private fun openModel(name:String):OrtSession {
        val file=java.io.File(app.filesDir,name)
        if(name=="u2net.onnx") check(OptionalModel(app).available()) {"精细模型尚未准备，请到设置下载或导入模型，或选择快速提取"}
        if(!file.exists()) {
            val temp=java.io.File(app.filesDir,"$name.tmp")
            app.assets.open(name).use{source->temp.outputStream().use{source.copyTo(it)}}
            check(temp.renameTo(file)){"无法准备本地模型"}
        }
        return OrtSession.SessionOptions().use { options ->
            options.setIntraOpNumThreads(2); options.setInterOpNumThreads(1)
            options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            environment.createSession(file.absolutePath, options)
        }
    }
    private val fineSession by lazy { openModel("u2net.onnx") }
    private val fastSession by lazy { openModel("u2netp.onnx") }
    override suspend fun isolate(input: Bitmap, detailed:Boolean): Bitmap = withContext(Dispatchers.Default) {
        val original=IntArray(input.width*input.height);input.getPixels(original,0,input.width,0,0,input.width,input.height)
        // Already-cut PNGs keep their author-supplied alpha instead of being segmented again.
        if(original.count{it ushr 24 < 230}>original.size/100 && original.any{it ushr 24>230}) return@withContext input.copy(Bitmap.Config.ARGB_8888,false)
        val session=if(detailed)fineSession else fastSession
        val side = 320
        val resized = Bitmap.createScaledBitmap(input, side, side, true)
        val rgb = IntArray(side * side)
        resized.getPixels(rgb, 0, side, 0, 0, side, side)
        if (resized !== input) resized.recycle()
        val tensor = FloatArray(3 * side * side)
        val means = floatArrayOf(.485f, .456f, .406f); val std = floatArrayOf(.229f, .224f, .225f)
        // Match the upstream model's max-normalization, then ImageNet normalization.
        val maxChannel = rgb.maxOf { max(it shr 16 and 255, max(it shr 8 and 255, it and 255)) }.coerceAtLeast(1).toFloat()
        for (i in rgb.indices) for (channel in 0..2) {
            val value = (rgb[i] shr (16 - channel * 8) and 255) / maxChannel
            tensor[channel * side * side + i] = (value - means[channel]) / std[channel]
        }
        ensureActive()
        val mask = OnnxTensor.createTensor(environment, FloatBuffer.wrap(tensor), longArrayOf(1, 3, 320, 320)).use { value ->
            session.run(mapOf(session.inputNames.first() to value)).use { result ->
                val buffer = (result[0] as OnnxTensor).floatBuffer
                FloatArray(side * side).also { buffer.get(it) }
            }
        }
        ensureActive()
        val low = mask.minOrNull() ?: 0f; val high = mask.maxOrNull() ?: 0f
        require(high - low > .001f) { "这张图的主体不够明确，可以选择保留整图" }
        val normalized=FloatArray(mask.size){((mask[it]-low)/(high-low)).coerceIn(0f,1f)}
        // Remove only tiny disconnected specks; preserve substantial detached subject parts.
        val visited=BooleanArray(mask.size);val queue=IntArray(mask.size)
        for(start in normalized.indices) if(!visited[start] && normalized[start]>.35f) {
            var head=0;var tail=1;queue[0]=start;visited[start]=true
            while(head<tail){val i=queue[head++];val x=i%side;val y=i/side
                for(n in intArrayOf(if(x>0)i-1 else -1,if(x<side-1)i+1 else -1,if(y>0)i-side else -1,if(y<side-1)i+side else -1))
                    if(n>=0&&!visited[n]&&normalized[n]>.35f){visited[n]=true;queue[tail++]=n}}
            if(tail<10)for(k in 0 until tail)normalized[queue[k]]=0f
        }
        val maskColors = IntArray(mask.size) { i ->
            val t=((normalized[i]-.12f)/.76f).coerceIn(0f,1f)
            val a = (t*t*(3-2*t)*255).roundToInt()
            Color.rgb(a, a, a)
        }
        val smallMask = Bitmap.createBitmap(maskColors, side, side, Bitmap.Config.ARGB_8888)
        val fullMask = Bitmap.createScaledBitmap(smallMask, input.width, input.height, true)
        val values = IntArray(input.width * input.height); val alphas = IntArray(values.size)
        input.getPixels(values, 0, input.width, 0, 0, input.width, input.height)
        fullMask.getPixels(alphas, 0, input.width, 0, 0, input.width, input.height)
        for (i in values.indices) {
            val alpha = (alphas[i] and 255) * (values[i] ushr 24) / 255
            values[i] = (alpha shl 24) or (values[i] and 0xffffff)
        }
        if (fullMask !== smallMask) fullMask.recycle()
        smallMask.recycle()
        Bitmap.createBitmap(values, input.width, input.height, Bitmap.Config.ARGB_8888)
    }
}

object ImageInput {
    suspend fun decode(context: Context, uri: Uri): Bitmap = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "无法读取这张图片，请选择 JPG、PNG 或 WebP" }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > 1400) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: error("图片已不可访问，请重新导入")
        val orientation = runCatching { resolver.openInputStream(uri)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1) } }.getOrNull() ?: 1
        val matrix = Matrix()
        when (orientation) {
            2 -> matrix.setScale(-1f, 1f)
            3 -> matrix.setRotate(180f)
            4 -> matrix.setScale(1f, -1f)
            5 -> { matrix.setRotate(90f); matrix.postScale(-1f, 1f) }
            6 -> matrix.setRotate(90f)
            7 -> { matrix.setRotate(270f); matrix.postScale(-1f, 1f) }
            8 -> matrix.setRotate(270f)
        }
        val oriented = if (matrix.isIdentity) decoded else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also { decoded.recycle() }
        val limit = 1024
        if (max(oriented.width, oriented.height) <= limit) oriented else {
            val factor = limit.toFloat() / max(oriented.width, oriented.height)
            Bitmap.createScaledBitmap(oriented, (oriented.width * factor).roundToInt().coerceAtLeast(1),
                (oriented.height * factor).roundToInt().coerceAtLeast(1), true).also { oriented.recycle() }
        }
    }
}

object BeadRenderer {
    fun sprite(pattern: BeadPattern, scale: Int = 14): Bitmap {
        val output = Bitmap.createBitmap(pattern.width * scale, pattern.height * scale, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output); val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        pattern.cells.forEachIndexed { i, cell -> if (cell >= 0) {
            val x = (i % pattern.width) * scale.toFloat(); val y = (i / pattern.width) * scale.toFloat()
            paint.color = pattern.palette[cell]
            canvas.drawRoundRect(x + .7f, y + .7f, x + scale - .7f, y + scale - .7f, scale * .2f, scale * .2f, paint)
            paint.color = Color.argb(85, 0, 0, 0)
            canvas.drawCircle(x + scale / 2f, y + scale / 2f, scale * .16f, paint)
            paint.color = Color.argb(65, 255, 255, 255)
            canvas.drawCircle(x + scale * .28f, y + scale * .25f, scale * .09f, paint)
        } }
        return output
    }
    fun chart(pattern: BeadPattern, name: String): Bitmap {
        val cell = 25; val margin = 44; val gridW = pattern.width * cell; val gridH = pattern.height * cell
        val width = max(720, gridW + margin * 2)
        val legendRows = (pattern.palette.size + 5) / 6
        val bitmap = Bitmap.createBitmap(width, gridH + 170 + legendRows * 48, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap); canvas.drawColor(Color.rgb(250, 251, 253))
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(25, 35, 50); textSize = 26f; typeface = Typeface.DEFAULT_BOLD }
        canvas.drawText(name.take(22), margin.toFloat(), 38f, p)
        p.textSize = 16f; p.typeface = Typeface.DEFAULT
        canvas.drawText("${pattern.width} × ${pattern.height} · ${pattern.beadCount} beads · ${pattern.brandId?.removePrefix("brand-")?:"RGB palette"}", margin.toFloat(), 67f, p)
        val top = 96f
        pattern.cells.forEachIndexed { i, color ->
            val x = margin + (i % pattern.width) * cell.toFloat(); val y = top + (i / pattern.width) * cell
            p.style = Paint.Style.FILL
            p.color = if (color >= 0) pattern.palette[color] else Color.WHITE
            canvas.drawRect(x, y, x + cell, y + cell, p)
            p.color = Color.rgb(195, 201, 210); p.style = Paint.Style.STROKE; p.strokeWidth = .6f
            canvas.drawRect(x, y, x + cell, y + cell, p)
            if (color >= 0) {
                val c = pattern.palette[color]; val light = (c shr 16 and 255) * .2126 + (c shr 8 and 255) * .7152 + (c and 255) * .0722
                p.style = Paint.Style.FILL; p.color = if (light > 140) Color.BLACK else Color.WHITE; p.textSize = 11f; p.textAlign = Paint.Align.CENTER
                canvas.drawText(pattern.code(color), x + cell / 2f, y + 17, p); p.textAlign = Paint.Align.LEFT
            }
        }
        p.style = Paint.Style.FILL; p.textSize = 11f; p.color = Color.DKGRAY
        for (x in 0 until pattern.width step 5) canvas.drawText("${x + 1}", margin + x * cell.toFloat(), top - 7, p)
        for (y in 0 until pattern.height step 5) canvas.drawText("${y + 1}", 17f, top + y * cell + 17, p)
        pattern.palette.forEachIndexed { i, color ->
            val x = margin + (i % 6) * ((width - margin * 2) / 6f); val y = top + gridH + 30 + (i / 6) * 48
            p.color = color; canvas.drawRoundRect(x, y, x + 22, y + 22, 4f, 4f, p)
            p.color = Color.DKGRAY; p.textSize = 12f
            canvas.drawText("${pattern.code(i)}: ${pattern.colorCounts[i] ?: 0}", x + 28, y + 10, p)
            p.textSize = 10f; canvas.drawText(String.format("#%06X", color and 0xffffff), x + 28, y + 25, p)
        }
        return bitmap
    }
}
