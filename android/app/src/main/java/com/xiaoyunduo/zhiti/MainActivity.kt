package com.xiaoyunduo.zhiti

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiaoyunduo.zhiti.data.ContentBlock
import com.xiaoyunduo.zhiti.data.ContentPack
import com.xiaoyunduo.zhiti.data.Question
import com.xiaoyunduo.zhiti.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ZhitiTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    ZhitiApp(state, viewModel)
                }
            }
        }
    }
}

@Composable
private fun ZhitiApp(state: UiState, viewModel: MainViewModel) {
    when (state.page) {
        Page.ACTIVATION -> ActivationScreen(state, viewModel::activate)
        Page.DOWNLOADS -> DownloadsScreen(state, viewModel::download, viewModel::openHome)
        Page.HOME -> HomeScreen(state, viewModel)
        Page.SETTINGS -> SettingsScreen(state, viewModel::openHome, viewModel::setQuestionsPerSet)
        Page.PRACTICE -> PracticeScreen(state, viewModel)
        Page.SUMMARY -> SummaryScreen(state, viewModel)
        Page.COLLECTION -> EmptyCollectionScreen(state, viewModel::openHome)
    }
}

@Composable
private fun ActivationScreen(state: UiState, activate: (String) -> Unit) {
    var code by rememberSaveable { mutableStateOf("") }
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 420.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(shape = RoundedCornerShape(22.dp), color = Navy) {
                Icon(Icons.Outlined.CheckCircle, null, Modifier.padding(18.dp).size(42.dp), tint = Color.White)
            }
            Spacer(Modifier.height(24.dp))
            Text("知题", fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(28.dp))
            OutlinedTextField(
                value = code,
                onValueChange = { code = it.uppercase() },
                label = { Text("激活码") },
                singleLine = true,
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            )
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) }
            Spacer(Modifier.height(18.dp))
            Button(onClick = { activate(code) }, enabled = code.isNotBlank() && !state.busy, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                if (state.busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                else Text("激活")
            }
        }
    }
}

@Composable
private fun DownloadsScreen(state: UiState, download: (ContentPack) -> Unit, openHome: () -> Unit) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    ScreenColumn {
        TopTitle("题库")
        state.error?.let { InlineError(it) }
        state.catalog?.packs?.forEach { pack ->
            val installed = state.installedVersions[pack.packId] == pack.contentVersion
            val updateAvailable = pack.packId in state.installed && !installed
            val progress = state.downloads[pack.packId]
            PackCard(pack, installed, updateAvailable, progress) { download(pack) }
        }
        if (state.installed.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Button(onClick = openHome, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("开始练习") }
        }
    }
}

@Composable
private fun PackCard(pack: ContentPack, installed: Boolean, updateAvailable: Boolean, progress: Int?, action: () -> Unit) {
    val name = if (pack.packId == "judgment") "判断推理" else "资料分析"
    Surface(Modifier.fillMaxWidth().padding(bottom = 12.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Line)) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(name, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                    Text("${pack.questionCount} 题 · ${formatBytes(pack.size)}", color = Muted, fontSize = 14.sp)
                }
                when {
                    installed -> Icon(Icons.Outlined.CheckCircle, "已安装", tint = Green)
                    progress != null -> Text("$progress%", color = Navy, fontWeight = FontWeight.SemiBold)
                    else -> OutlinedButton(onClick = action) { Text(if (updateAvailable) "更新" else "下载") }
                }
            }
            if (progress != null) LinearProgressIndicator(progress = { progress / 100f }, Modifier.fillMaxWidth().padding(top = 14.dp))
        }
    }
}

@Composable
private fun HomeScreen(state: UiState, viewModel: MainViewModel) {
    var confirmClear by remember { mutableStateOf(false) }
    var about by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    ScreenColumn {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TopTitle("知题", Modifier.weight(1f))
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "更多") }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem({ Text("管理题库") }, onClick = { menu = false; viewModel.openDownloads() }, leadingIcon = { Icon(Icons.Outlined.Download, null) })
                    DropdownMenuItem({ Text("设置") }, onClick = { menu = false; viewModel.openSettings() }, leadingIcon = { Icon(Icons.Outlined.Settings, null) })
                    DropdownMenuItem({ Text("来源与许可") }, onClick = { menu = false; about = true }, leadingIcon = { Icon(Icons.Outlined.Info, null) })
                    DropdownMenuItem({ Text("清空记录") }, onClick = { menu = false; confirmClear = true }, leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null) })
                }
            }
        }
        if (state.session != null) {
            FilledTonalButton(onClick = viewModel::continueSession, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Icon(Icons.Outlined.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text("继续 ${state.session.title}  ${state.session.index + 1}/${state.session.qids.size}")
            }
            Spacer(Modifier.height(18.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatButton("已答", state.answeredCount, Icons.Outlined.TaskAlt, Modifier.weight(1f)) { }
            StatButton("错题", state.wrongCount, Icons.Outlined.ErrorOutline, Modifier.weight(1f)) { viewModel.openCollection(CollectionType.WRONG) }
            StatButton("收藏", state.favoriteCount, Icons.Outlined.BookmarkBorder, Modifier.weight(1f)) { viewModel.openCollection(CollectionType.FAVORITES) }
        }
        Spacer(Modifier.height(28.dp))
        if ("judgment" in state.installed) {
            SectionTitle("判断推理")
            val categories = listOf("图形推理", "定义判断", "类比推理", "逻辑判断", "科学推理")
            categories.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { category ->
                        CategoryCard(category, state.categories[category] ?: 0, state.categoryProgress[category] ?: 0, Modifier.weight(1f)) { viewModel.startJudgment(category) }
                    }
                }
            }
        }
        if ("data-analysis" in state.installed) {
            Spacer(Modifier.height(18.dp))
            SectionTitle("资料分析")
            CategoryCard(
                "完整材料",
                state.catalog?.packs?.firstOrNull { it.packId == "data-analysis" }?.materialCount ?: 717,
                state.completedMaterialCount,
                Modifier.fillMaxWidth(),
            ) { viewModel.startDataAnalysis() }
        }
    }
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text("清空练习记录？") },
        text = { Text("作答、错题和收藏将全部删除。") },
        confirmButton = { TextButton(onClick = { confirmClear = false; viewModel.clearRecords() }) { Text("清空", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("取消") } },
    )
    if (about) AlertDialog(
        onDismissRequest = { about = false },
        title = { Text("来源与许可") },
        text = { Text("题库整理：ERRRC/kaogongzhentizhengliu\n整理内容采用 CC BY-NC 4.0；真题版权归原出题机构和原出版方。") },
        confirmButton = { TextButton(onClick = { about = false }) { Text("关闭") } },
    )
}

@Composable
private fun SettingsScreen(state: UiState, back: () -> Unit, setQuestionsPerSet: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ScreenColumn {
        Row(
            Modifier.fillMaxWidth().height(56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = back) { Icon(Icons.Outlined.Close, "返回") }
            Text(
                "设置",
                Modifier.weight(1f).padding(start = 4.dp),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(18.dp))
        SectionTitle("每组题数")
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(15.dp), border = BorderStroke(1.dp, Line), color = Color.White) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("判断推理", Modifier.weight(1f), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Box {
                    OutlinedButton(
                        onClick = { expanded = true },
                        modifier = Modifier.height(44.dp),
                        contentPadding = PaddingValues(start = 16.dp, end = 8.dp),
                    ) {
                        Text("${state.questionsPerSet} 题")
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Outlined.ArrowDropDown, null, Modifier.size(18.dp))
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        listOf(5, 10, 15, 20).forEach { count ->
                            DropdownMenuItem(
                                text = { Text("$count 题") },
                                onClick = {
                                    expanded = false
                                    setQuestionsPerSet(count)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PracticeScreen(state: UiState, viewModel: MainViewModel) {
    val question = state.question ?: return
    val session = state.session ?: return
    Scaffold(
        topBar = {
            Surface(shadowElevation = 1.dp) {
                Row(Modifier.fillMaxWidth().statusBarsPadding().height(58.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = viewModel::openHome) { Icon(Icons.Outlined.Close, "退出") }
                    Text("${session.index + 1} / ${session.qids.size}", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    IconButton(onClick = viewModel::toggleFavorite) {
                        Icon(if (state.favorite) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder, "收藏", tint = if (state.favorite) Navy else Ink)
                    }
                }
            }
        },
        bottomBar = {
            Surface(shadowElevation = 6.dp) {
                Button(
                    onClick = if (state.submitted) viewModel::next else viewModel::submit,
                    enabled = state.submitted || state.selected != null,
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(14.dp).height(50.dp),
                ) { Text(if (state.submitted) if (session.index == session.qids.lastIndex) "查看小结" else "下一题" else "提交") }
            }
        },
    ) { padding ->
        QuestionLayout(question, state, viewModel, Modifier.padding(padding))
    }
}

@Composable
private fun QuestionLayout(question: Question, state: UiState, viewModel: MainViewModel, modifier: Modifier) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val wide = maxWidth >= 840.dp && question.material.isNotEmpty()
        if (wide) {
            Row(Modifier.fillMaxSize().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(Modifier.weight(0.9f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(vertical = 22.dp)) {
                    SectionTitle("材料")
                    RichContent(question.material, question.module)
                }
                QuestionBody(question, state, viewModel, Modifier.weight(1.1f).fillMaxHeight())
            }
        } else {
            QuestionBody(question, state, viewModel, Modifier.fillMaxSize(), showMaterial = true)
        }
    }
}

@Composable
private fun QuestionBody(question: Question, state: UiState, viewModel: MainViewModel, modifier: Modifier, showMaterial: Boolean = false) {
    Column(modifier.verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text(listOfNotNull(question.year?.toString(), question.region.takeIf { it.isNotBlank() }, "题号 ${question.qid}").joinToString(" · "), color = Muted, fontSize = 13.sp)
        if (showMaterial && question.module == "data-analysis" && question.material.isNotEmpty()) MaterialDisclosure(question)
        Spacer(Modifier.height(16.dp))
        RichContent(question.stem, question.module, textSize = 19)
        Spacer(Modifier.height(18.dp))
        question.options.forEach { (key, blocks) ->
            OptionCard(key, blocks, question, state) { viewModel.selectAnswer(key) }
        }
        if (state.submitted) AnswerDetails(question, state.selected == question.answer)
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun MaterialDisclosure(question: Question) {
    var expanded by rememberSaveable(question.qid) { mutableStateOf(false) }
    Surface(
        Modifier.fillMaxWidth().padding(top = 14.dp).clickable { expanded = !expanded },
        color = Color(0xFFEDF3FA), shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("材料", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null)
            }
            if (expanded) { Spacer(Modifier.height(10.dp)); RichContent(question.material, question.module) }
        }
    }
}

@Composable
private fun OptionCard(key: String, blocks: List<ContentBlock>, question: Question, state: UiState, select: () -> Unit) {
    val selected = state.selected == key
    val correct = state.submitted && key == question.answer
    val wrong = state.submitted && selected && key != question.answer
    val border = when { correct -> Green; wrong -> MaterialTheme.colorScheme.error; selected -> Navy; else -> Line }
    val background = when { correct -> Color(0xFFEAF7F0); wrong -> Color(0xFFFFEEEE); selected -> Color(0xFFEDF3FA); else -> Color.White }
    val visibleBlocks = blocks.takeUnless {
        it.size == 1 && it.single().type == "text" && it.single().text.trim() == key
    }.orEmpty()
    Surface(
        Modifier.fillMaxWidth().padding(bottom = 11.dp).clip(RoundedCornerShape(14.dp)).clickable(enabled = !state.submitted, onClick = select).border(1.5.dp, border, RoundedCornerShape(14.dp)),
        color = background,
    ) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.Top) {
            Text(key, fontWeight = FontWeight.Bold, color = border, modifier = Modifier.width(28.dp))
            Column(Modifier.weight(1f)) { RichContent(visibleBlocks, question.module, textSize = 17) }
            if (correct) Icon(Icons.Outlined.CheckCircle, null, tint = Green)
            else if (wrong) Icon(Icons.Outlined.Cancel, null, tint = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun AnswerDetails(question: Question, correct: Boolean) {
    Spacer(Modifier.height(10.dp))
    Surface(color = if (correct) Color(0xFFEAF7F0) else Color(0xFFFFEEEE), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (correct) Icons.Outlined.CheckCircle else Icons.Outlined.Cancel, null, tint = if (correct) Green else MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(10.dp))
            Text(if (correct) "回答正确" else "正确答案 ${question.answer}", fontWeight = FontWeight.SemiBold)
        }
    }
    if (question.explanation.isNotEmpty()) {
        Spacer(Modifier.height(22.dp)); SectionTitle("解析"); RichContent(question.explanation, question.module)
    }
    DetailDisclosure("快速解法", question.fastSolution, question.module)
    DetailDisclosure("推理步骤", question.reasoning, question.module)
    DetailDisclosure("易错点", question.pitfalls, question.module)
    Text("解析来自整理资料，可能有错漏。", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 16.dp))
}

@Composable
private fun DetailDisclosure(title: String, blocks: List<ContentBlock>, module: String) {
    if (blocks.isEmpty()) return
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        TextButton(onClick = { expanded = !expanded }, contentPadding = PaddingValues(0.dp)) {
            Text(title); Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null)
        }
        if (expanded) RichContent(blocks, module)
    }
}

@Composable
private fun SummaryScreen(state: UiState, viewModel: MainViewModel) {
    val session = state.session ?: return
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 420.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.TaskAlt, null, Modifier.size(54.dp), tint = Green)
            Spacer(Modifier.height(16.dp)); Text("本组完成", fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                SummaryValue("答题", session.answered.toString())
                SummaryValue("正确", session.correct.toString())
                SummaryValue("正确率", if (session.answered == 0) "0%" else "${(session.correct * 100f / session.answered).roundToInt()}%")
            }
            Spacer(Modifier.height(28.dp))
            Button(onClick = viewModel::continueLearning, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("继续学习") }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = viewModel::openHome, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("返回首页") }
            if (state.wrongCount > 0) TextButton(onClick = { viewModel.openCollection(CollectionType.WRONG) }) { Text("查看错题") }
        }
    }
}

@Composable
private fun EmptyCollectionScreen(state: UiState, back: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(if (state.collectionType == CollectionType.WRONG) Icons.Outlined.TaskAlt else Icons.Outlined.BookmarkBorder, null, Modifier.size(48.dp), tint = Muted)
            Spacer(Modifier.height(14.dp)); Text(if (state.collectionType == CollectionType.WRONG) "暂无错题" else "暂无收藏", fontSize = 20.sp)
            TextButton(onClick = back) { Text("返回首页") }
        }
    }
}

@Composable
private fun RichContent(blocks: List<ContentBlock>, module: String, textSize: Int = 16) {
    val context = LocalContext.current
    val root = remember(module) { File(context.filesDir, "content/$module") }
    blocks.forEach { block ->
        when (block.type) {
            "text" -> Text(block.text, fontSize = textSize.sp, lineHeight = (textSize * 1.65).sp, modifier = Modifier.padding(bottom = 9.dp))
            "image" -> LocalImage(File(root, block.asset), block.alt)
        }
    }
}

@Composable
private fun LocalImage(file: File, alt: String) {
    var fullScreen by remember { mutableStateOf(false) }
    val bitmap by produceState<android.graphics.Bitmap?>(null, file.path) {
        value = withContext(Dispatchers.IO) { android.graphics.BitmapFactory.decodeFile(file.path) }
    }
    if (bitmap == null) {
        Surface(color = Color(0xFFF1F3F6), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth().height(100.dp)) {
            Box(contentAlignment = Alignment.Center) { Text("图片无法显示", color = Muted) }
        }
    } else {
        Image(bitmap!!.asImageBitmap(), alt, Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(8.dp)).clickable { fullScreen = true }, contentScale = ContentScale.FillWidth)
    }
    if (fullScreen && bitmap != null) ZoomDialog(bitmap!!.asImageBitmap()) { fullScreen = false }
}

@Composable
private fun ZoomDialog(bitmap: androidx.compose.ui.graphics.ImageBitmap, close: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black).pointerInput(Unit) {
            detectTransformGestures { _, pan, zoom, _ -> scale = (scale * zoom).coerceIn(1f, 5f); offsetX += pan.x; offsetY += pan.y }
        }) {
            Image(bitmap, null, Modifier.fillMaxSize().graphicsLayer(scaleX = scale, scaleY = scale, translationX = offsetX, translationY = offsetY), contentScale = ContentScale.Fit)
            IconButton(close, Modifier.align(Alignment.TopEnd).statusBarsPadding()) { Icon(Icons.Outlined.Close, "关闭", tint = Color.White) }
        }
    }
}

@Composable private fun ScreenColumn(content: @Composable ColumnScope.() -> Unit) = Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(20.dp), content = content)
@Composable private fun TopTitle(text: String, modifier: Modifier = Modifier) = Text(text, modifier.padding(bottom = 22.dp), fontSize = 28.sp, fontWeight = FontWeight.Bold)
@Composable private fun SectionTitle(text: String) = Text(text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 12.dp))
@Composable private fun InlineError(text: String) = Text(text, color = MaterialTheme.colorScheme.error, modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp))
@Composable
private fun CategoryCard(title: String, total: Int, completed: Int, modifier: Modifier, click: () -> Unit) {
    Surface(modifier.padding(bottom = 12.dp).height(88.dp).clickable(onClick = click), shape = RoundedCornerShape(15.dp), border = BorderStroke(1.dp, Line), color = Color.White) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.Center) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text("$completed / $total", color = Muted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun StatButton(title: String, value: Int, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, click: () -> Unit) {
    Surface(modifier.heightIn(min = 92.dp).clickable(onClick = click), shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, Line), color = Color.White) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
        ) {
            Icon(icon, null, Modifier.size(22.dp), tint = Navy)
            Text(value.toString(), fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold)
            Text(title, fontSize = 13.sp, lineHeight = 18.sp, color = Muted, maxLines = 1)
        }
    }
}

@Composable private fun SummaryValue(label: String, value: String) = Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold); Text(label, color = Muted) }
private fun formatBytes(bytes: Long): String = if (bytes < 1024 * 1024) "${bytes / 1024} KB" else "${bytes / 1024 / 1024} MB"
