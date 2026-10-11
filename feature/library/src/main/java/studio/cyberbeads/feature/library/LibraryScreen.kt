package studio.cyberbeads.feature.library

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.*
import studio.cyberbeads.core.data.ArtworkStore
import studio.cyberbeads.core.imaging.BeadRenderer
import studio.cyberbeads.core.model.Artwork
import studio.cyberbeads.core.ui.*

@Composable fun LibraryScreen(store: ArtworkStore, initialId: String?, onCreate: () -> Unit, onPet: (String) -> Unit, onAnimate:(String)->Unit = {}) {
    val items by store.items.collectAsStateWithLifecycle()
    val warning by store.warning.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selected by rememberSaveable { mutableStateOf<String?>(initialId) }
    var selectedColor by rememberSaveable { mutableIntStateOf(0) }
    var deleteId by rememberSaveable { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var exporting by remember { mutableStateOf(false) }
    var exportId by rememberSaveable { mutableStateOf<String?>(null) }
    var exportChart by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(initialId) { if (initialId != null) selected = initialId }
    LaunchedEffect(Unit) { store.refresh() }
    val item = items.firstOrNull { it.id == selected }
    BackHandler(selected != null) { selected = null }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        if (uri != null) scope.launch {
            exporting = true
            try {
                val chosen = exportId?.let { store.get(it) } ?: error("作品已不存在")
                val bitmap = withContext(Dispatchers.Default) {
                    if (exportChart) BeadRenderer.chart(chosen.pattern, chosen.name) else BeadRenderer.sprite(chosen.pattern, 20)
                }
                try {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use {
                            check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)) { "写入失败" }
                        } ?: error("无法写入选定位置")
                    }
                } finally { bitmap.recycle() }
                android.widget.Toast.makeText(context,"已导出到你选择的位置",android.widget.Toast.LENGTH_SHORT).show();message=null
            } catch (e: Exception) { message = "导出失败：${e.message}" }
            finally { exporting = false }
        }
    }
    fun update(transform: (Artwork) -> Artwork) {
        val id = item?.id ?: return
        scope.launch { try { store.update(id, transform) } catch (e: Exception) { message = "保存失败：${e.message}" } }
    }
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(start=22.dp,end=22.dp,top=12.dp,bottom=30.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)) {
        if (item == null) {
            item { Column(verticalArrangement=Arrangement.spacedBy(18.dp)) {
            SectionLabel("YOUR LITTLE UNIVERSE", "作品收藏室", "慢慢拼好，也慢慢陪伴。")
            if (selected != null) {
                Text("这件作品当前无法读取，其他作品仍可使用。", color = Muted)
                OutlinedButton(onClick = { selected = null }) { Text("返回作品列表") }
            }
            warning?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (items.isEmpty()) CyberCard(Modifier.fillMaxWidth()) {
                BeadBoard(demoPattern(), Modifier.fillMaxWidth().height(190.dp))
                Text("这里还没有作品", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("从一张喜欢的图片开始，也可以先试试像素猫。", color = Muted, fontSize = 13.sp)
                Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) { Text("去创作第一件作品") }
            }
            } }
            items(items,key={it.id}) { art ->
                CyberCard(Modifier.fillMaxWidth().clickable { selected = art.id; selectedColor = 0 }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        ArtworkThumb(art, Modifier.size(78.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Text(art.name, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            Text("${art.pattern.width}×${art.pattern.height} · ${art.pattern.beadCount} 颗", color = Muted, fontSize = 12.sp)
                            Text(if (art.isComplete) "已拼好 · 可成为宠物" else "拼装进度 ${(art.progress * 100).toInt()}%", color = Mint, fontSize = 12.sp)
                        }
                        Icon(Icons.Outlined.ChevronRight, "打开作品", tint = Muted)
                    }
                }
            }
        } else { item { Column(verticalArrangement=Arrangement.spacedBy(18.dp)) {
            OutlinedButton(onClick = { selected = null }) { Icon(Icons.Outlined.ArrowBack, null); Text("  全部作品") }
            SectionLabel("ONE BEAD AT A TIME", item.name, "点亮一颗颗豆子，让它成为你的伙伴。")
            CyberCard(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (item.isComplete) "拼装完成！" else "正在拼装", color = Mint, fontWeight = FontWeight.Bold)
                    Text("${item.completedCount} / ${item.pattern.beadCount}", color = Muted)
                }
                LinearProgressIndicator(progress = { item.progress }, modifier = Modifier.fillMaxWidth())
                BeadBoard(item.pattern, Modifier.fillMaxWidth().height(310.dp), placed = item.placed,
                    onCell = { index -> update { it.place(index) } }, zoomable = true)
                Text("点格子放置 / 取下豆子，也可以按颜色批量拼装。", color = Muted, fontSize = 12.sp)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item.pattern.palette.forEachIndexed { index, color ->
                        ColorDot(color, "${item.pattern.code(index)} · ${item.pattern.colorCounts[index] ?: 0}颗", selectedColor == index) { selectedColor = index }
                    }
                }
                Button(onClick = { update { it.fillColor(selectedColor.coerceIn(it.pattern.palette.indices)) } }, modifier = Modifier.fillMaxWidth(), enabled = !item.isComplete) {
                    Text("拼好第 ${selectedColor.coerceIn(item.pattern.palette.indices) + 1} 号颜色")
                }
                OutlinedButton(onClick = { update { it.complete() } }, modifier = Modifier.fillMaxWidth(), enabled = !item.isComplete) { Text("一键拼好整件作品") }
            }
            Button(onClick = { onPet(item.id) }, enabled = item.isComplete, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)) {
                Icon(Icons.Outlined.Pets, null); Spacer(Modifier.width(8.dp)); Text(if (item.isComplete) "让它成为桌面宠物" else "拼装完成后，解锁桌面宠物")
            }
            OutlinedButton(onClick={onAnimate(item.id)},modifier=Modifier.fillMaxWidth()){Text("编辑静态图案")}
            CyberCard(Modifier.fillMaxWidth()) {
                Text("带到屏幕之外", fontWeight = FontWeight.Bold)
                Text("图纸包含格子、色号和用量；透明贴纸可用于分享或其他创作。", color = Muted, fontSize = 12.sp)
                OutlinedButton(onClick = { exportId = item.id; exportChart = true; exporter.launch("cyberbeads-${item.id.take(8)}-chart.png") },
                    enabled = !exporting, modifier = Modifier.fillMaxWidth()) { Text("导出拼豆图纸 PNG") }
                OutlinedButton(onClick = { exportId = item.id; exportChart = false; exporter.launch("cyberbeads-${item.id.take(8)}-sticker.png") },
                    enabled = !exporting, modifier = Modifier.fillMaxWidth()) { Text("导出透明贴纸 PNG") }
            }
            OutlinedButton(onClick = { deleteId = item.id }, modifier = Modifier.fillMaxWidth()) { Text("删除这件作品", color = MaterialTheme.colorScheme.error) }
        }
        } }
        if (exporting) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        message?.let { item { Text(it, color = Mint) } }
    }
    if (deleteId != null) AlertDialog(onDismissRequest = { deleteId = null }, title = { Text("删除这件作品？") },
        text = { Text("作品和拼装进度会从本机移除。已经导出的图片不受影响。") },
        confirmButton = { OutlinedButton(onClick = {
            val id = deleteId!!; deleteId = null
            scope.launch { try { store.delete(id); if (selected == id) selected = null } catch (e: Exception) { message = "删除失败：${e.message}" } }
        }) { Text("删除") } }, dismissButton = { OutlinedButton(onClick = { deleteId = null }) { Text("保留") } })
}

@Composable private fun ArtworkThumb(art:Artwork,modifier:Modifier){
 val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null,art.pattern){value=withContext(Dispatchers.Default){android.graphics.Bitmap.createBitmap(studio.cyberbeads.core.model.PixelOps.fromPattern(art.pattern).toIntArray(),art.pattern.width,art.pattern.height,android.graphics.Bitmap.Config.ARGB_8888).let{it.asImageBitmap()}}}
 androidx.compose.foundation.Canvas(modifier){bitmap?.let{im->val cell=minOf(size.width/im.width,size.height/im.height);val w=(im.width*cell).toInt();val h=(im.height*cell).toInt();drawImage(im,dstOffset=androidx.compose.ui.unit.IntOffset(((size.width-w)/2).toInt(),((size.height-h)/2).toInt()),dstSize=androidx.compose.ui.unit.IntSize(w,h),filterQuality=androidx.compose.ui.graphics.FilterQuality.None)}}
}
