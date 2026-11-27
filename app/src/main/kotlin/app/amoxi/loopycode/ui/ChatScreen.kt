package app.amoxi.loopycode.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.amoxi.loopycode.Brain
import app.amoxi.loopycode.data.Message
import app.amoxi.loopycode.data.Part
import app.amoxi.loopycode.data.str
import app.amoxi.loopycode.tr
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun thinkingLabel(v: String) = when (v) {
    "" -> tr("Авто", "Auto")
    "none" -> tr("Выкл", "Off")
    "minimal" -> tr("Минимум", "Minimal")
    "low" -> tr("Мало", "Low")
    "medium" -> tr("Средне", "Medium")
    "high" -> tr("Много", "High")
    "xhigh" -> tr("Очень много", "Extra high")
    "max" -> tr("Максимум", "Max")
    else -> v
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(brain: Brain, openDrawer: () -> Unit) {
    val id = brain.open
    val model = brain.currentModel()
    // a prompt still being handed over is already a turn: the composer must not send it twice
    val running = brain.admitting || (id != null && brain.busy[id] == true)
    var picker by remember { mutableStateOf(false) }
    var text by remember(id) { mutableStateOf("") }
    val pictures = remember(id) { mutableStateListOf<String>() }
    var editing by remember(id) { mutableStateOf<Message?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(brain.shared) {
        val s = brain.shared ?: return@LaunchedEffect
        text = s.first
        pictures.addAll(s.second)
        brain.shared = null
    }

    Column(Modifier.fillMaxSize()) {
        CenterAlignedTopAppBar(
            navigationIcon = { IconButton(openDrawer) { Icon(Icons.Outlined.Menu, tr("Меню", "Menu")) } },
            title = {
                Column(
                    Modifier.clip(RoundedCornerShape(14.dp)).clickable(enabled = brain.ready) { picker = true }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(model?.name ?: if (brain.ready) tr("Выберите модель", "Pick a model") else "LoopyCode",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 220.dp))
                        if (brain.ready) Icon(Icons.Outlined.KeyboardArrowDown, null, Modifier.size(20.dp))
                    }
                    model?.let { m ->
                        Text(brain.providerName(m.provider), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            actions = {
                IconButton({ brain.screens.add("terminal") }, enabled = brain.linuxInstalled) {
                    Icon(Icons.Outlined.Terminal, tr("Терминал Alpine", "Alpine terminal"))
                }
                IconButton({ brain.openSession(null) }, enabled = id != null) { Icon(Icons.Outlined.EditNote, tr("Новый чат", "New chat")) }
            },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        )

        Box(Modifier.weight(1f).fillMaxWidth()) {
            val shown by remember { derivedStateOf { brain.shown() } }
            when {
                !brain.ready -> Starting(brain)
                id == null || shown.isEmpty() -> Welcome(brain)
                else -> {
                    val list = rememberLazyListState()
                    LaunchedEffect(shown.size) { list.animateScrollToItem(0) }
                    val lastAnswer = remember(shown) { shown.lastOrNull { it.role == "assistant" }?.id }
                    // a failed turn ends with the error twice: on the answer and on the turn's end
                    val reversed = remember(shown) {
                        val repeated = shown.indices.filter { i ->
                            shown[i].role == "idle" && shown[i].error != null && shown.getOrNull(i - 1)?.error != null
                        }.map { shown[it].id }.toSet()
                        shown.filter { it.id !in repeated }.asReversed()
                    }
                    LazyColumn(state = list, reverseLayout = true, modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (running && shown.last().role != "assistant") item(key = "dots") { Dots() }
                        items(reversed, key = { it.id }) { m ->
                            when (m.role) {
                                "user" -> UserBubble(m, onEdit = { editing = m; text = m.text })
                                "assistant" -> Answer(m, live = running && !m.done,
                                    last = !running && m.id == lastAnswer, again = { brain.again() })
                                "idle" -> if (m.error != null) ErrorCard(m.error) else if (m.outcome == "interrupted")
                                    Text(tr("Остановлено", "Stopped"), style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                }
            }
        }

        Composer(
            brain = brain,
            text = text,
            onText = { text = it },
            pictures = pictures,
            editing = editing,
            cancelEdit = { editing = null; text = "" },
            running = running,
            onPick = { uris -> scope.launch { for (u in uris) brain.importPicture(u)?.let { pictures += it } } },
            send = {
                val e = editing
                if (e != null) brain.resend(e, text) else brain.send(text, pictures.toList())
                text = ""
                pictures.clear()
                editing = null
            },
            stop = { brain.stop() },
        )
    }

    if (picker) ModelSheet(brain) { picker = false }
    Asks(brain)
    brain.notice?.let { n ->
        AlertDialog(
            onDismissRequest = { brain.notice = null },
            title = { Text(tr("Не получилось", "That didn't work")) },
            text = { Text(n) },
            confirmButton = { TextButton({ brain.notice = null }) { Text("OK") } },
        )
    }
}

@Composable
private fun Starting(brain: Brain) {
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally) {
        Logo(72)
        Spacer(Modifier.height(24.dp))
        val err = brain.engineError
        if (err == null) {
            CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
            Text(tr("Запускаю opencode…", "Starting opencode…"), color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 14.dp))
        } else {
            Text(tr("opencode не запустился", "opencode didn't start"), style = MaterialTheme.typography.titleMedium)
            SelectionContainer {
                Text(err, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp))
            }
            Button({ brain.restart() }) { Text(tr("Ещё раз", "Try again")) }
        }
    }
}

@Composable
private fun Welcome(brain: Brain) {
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally) {
        Logo(72)
        Spacer(Modifier.height(20.dp))
        Text(tr("Чем помочь?", "How can I help?"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        if (brain.integrations.none { it.connected }) {
            Spacer(Modifier.height(8.dp))
            Text(tr("Бесплатные модели opencode работают сразу. Для остальных подключите OpenCode Go, Zen или другого провайдера.",
                "opencode's free models work right away. For the rest, connect OpenCode Go, Zen or another provider."),
                textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            OutlinedButton({ brain.screens.add("providers") }) { Text(tr("Провайдеры", "Providers")) }
        }
    }
}

@Composable
fun Thumb(path: String, size: Int, modifier: Modifier = Modifier) {
    val bitmap by produceState<ImageBitmap?>(null, path) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, bounds)
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= size * 3) sample *= 2
                BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
            }.getOrNull()
        }
    }
    Box(modifier.size(size.dp).clip(RoundedCornerShape(14.dp))) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.fillMaxSize()) {}
        bitmap?.let { Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UserBubble(m: Message, onEdit: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    var menu by remember { mutableStateOf(false) }
    val pictures = m.files
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        if (pictures.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 6.dp)) {
            for (p in pictures) Thumb(p, 120)
        }
        if (m.text.isNotEmpty()) Box {
            val shape = RoundedCornerShape(22.dp, 22.dp, 6.dp, 22.dp)
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer, shape = shape,
                modifier = Modifier.widthIn(max = 320.dp).clip(shape).combinedClickable(onClick = {}, onLongClick = { menu = true }),
            ) {
                Text(m.text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp))
            }
            DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem(text = { Text(tr("Копировать", "Copy")) }, leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) },
                    onClick = { clipboard.setText(AnnotatedString(m.text)); menu = false })
                DropdownMenuItem(text = { Text(tr("Изменить", "Edit")) }, leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                    onClick = { menu = false; onEdit() })
            }
        }
    }
}

@Composable
private fun Dots() {
    val t = rememberInfiniteTransition(label = "dots")
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.padding(vertical = 10.dp)) {
        for (i in 0 until 3) {
            val a by t.animateFloat(0.25f, 1f, infiniteRepeatable(tween(600, delayMillis = i * 150), RepeatMode.Reverse), label = "d$i")
            Surface(color = MaterialTheme.colorScheme.primary, shape = CircleShape, modifier = Modifier.size(8.dp).alpha(a)) {}
        }
    }
}

private fun seconds(ms: Long) = ((ms + 500) / 1000).coerceAtLeast(1)

private fun tokens(n: Long) = when {
    n >= 1_000_000 -> "%.1fM".format(n / 1e6)
    n >= 10_000 -> "${n / 1000}k"
    n >= 1000 -> "%.1fk".format(n / 1e3)
    else -> n.toString()
}

@Composable
private fun ErrorCard(e: String) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp)) {
            Icon(Icons.Outlined.ErrorOutline, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onErrorContainer)
            Text(e, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(start = 10.dp))
        }
    }
}

@Composable
private fun Thoughts(p: Part.Reasoning, live: Boolean) {
    var open by remember { mutableStateOf(false) }
    if (p.text.isBlank()) return
    Column {
        Row(
            Modifier.clip(RoundedCornerShape(12.dp)).clickable { open = !open }.padding(vertical = 6.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Psychology, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Text(
                if (live) tr("Думает…", "Thinking…")
                else if (p.ms > 0) tr("Думал ${seconds(p.ms)} с", "Thought for ${seconds(p.ms)} s")
                else tr("Размышления", "Thoughts"),
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 6.dp),
            )
            Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        AnimatedVisibility(open || live) {
            Row(Modifier.height(IntrinsicSize.Min).padding(start = 12.dp, top = 4.dp, bottom = 4.dp)) {
                Box(Modifier.padding(end = 12.dp).width(2.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant))
                Text(
                    if (live && !open) p.text.takeLast(600) else p.text.trim(),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (live && !open) 6 else Int.MAX_VALUE, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** What a tool did, in one line; the details open on tap. */
private fun summary(t: Part.Tool): String {
    val i = t.input
    val arg = i.str("command") ?: i.str("filePath")?.substringAfterLast('/') ?: i.str("path")?.substringAfterLast('/')
        ?: i.str("pattern") ?: i.str("url") ?: i.str("query") ?: i.str("description")
    return if (arg != null) "${t.name}: $arg" else t.name
}

@Composable
private fun ToolCard(t: Part.Tool) {
    var open by remember { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { open = !open }) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                when (t.status) {
                    "completed" -> Icon(Icons.Outlined.TaskAlt, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    "error" -> Icon(Icons.Outlined.ErrorOutline, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                    else -> CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                }
                Text(summary(t), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 8.dp).weight(1f))
                Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AnimatedVisibility(open) {
                val body = listOfNotNull(t.input.toString(2).takeIf { t.input.length() > 0 }, t.error, t.output.ifBlank { null })
                    .joinToString("\n\n")
                Text(body.take(6000), fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()))
            }
        }
    }
}

@Composable
private fun Answer(m: Message, live: Boolean, last: Boolean, again: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val text = m.parts.filterIsInstance<Part.Text>().joinToString("\n\n") { it.text }.trim()
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        m.parts.forEachIndexed { i, p ->
            when (p) {
                is Part.Reasoning -> Thoughts(p, live && i == m.parts.lastIndex)
                is Part.Text -> {
                    // some models write their thinking into the answer itself, between <think> tags
                    val t = p.text.trimStart()
                    if (t.startsWith("<think>")) {
                        val end = t.indexOf("</think>")
                        val thought = if (end < 0) t.removePrefix("<think>") else t.substring(7, end)
                        Thoughts(Part.Reasoning(thought, 0), live && end < 0)
                        val rest = if (end < 0) "" else t.substring(end + 8).trim()
                        if (rest.isNotEmpty()) SelectionContainer { Markdown(rest) }
                    } else if (p.text.isNotBlank()) SelectionContainer { Markdown(p.text.trim()) }
                }
                is Part.Tool -> ToolCard(p)
            }
        }
        if (live && m.parts.isEmpty()) Dots()
        m.error?.let { ErrorCard(it) }
        if (!live && (text.isNotEmpty() || last)) Row(verticalAlignment = Alignment.CenterVertically) {
            val small = Modifier.size(36.dp)
            val tint = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
            if (text.isNotEmpty()) IconButton({ clipboard.setText(AnnotatedString(text)) }, small, colors = tint) {
                Icon(Icons.Outlined.ContentCopy, tr("Копировать", "Copy"), Modifier.size(18.dp))
            }
            if (last) IconButton(again, small, colors = tint) {
                Icon(Icons.Outlined.Refresh, tr("Ответить заново", "Answer again"), Modifier.size(19.dp))
            }
            val info = listOfNotNull(m.model, if (m.tokensOut > 0) "${tokens(m.tokensIn)} → ${tokens(m.tokensOut)}" else null)
                .joinToString(" · ")
            Text(info, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 6.dp))
        }
    }
}

@Composable
private fun Composer(
    brain: Brain,
    text: String,
    onText: (String) -> Unit,
    pictures: MutableList<String>,
    editing: Message?,
    cancelEdit: () -> Unit,
    running: Boolean,
    onPick: (List<android.net.Uri>) -> Unit,
    send: () -> Unit,
    stop: () -> Unit,
) {
    val model = brain.currentModel()
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(6)) { onPick(it) }
    val canSend = brain.ready && model != null && (text.isNotBlank() || pictures.isNotEmpty())
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
    ) {
        Column(Modifier.padding(6.dp)) {
            editing?.let {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 14.dp)) {
                    Icon(Icons.Outlined.Edit, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(tr("Изменение сообщения", "Editing a message"), style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 6.dp).weight(1f))
                    IconButton(cancelEdit, Modifier.size(32.dp)) { Icon(Icons.Outlined.Close, tr("Отмена", "Cancel"), Modifier.size(18.dp)) }
                }
            }
            if (pictures.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()).padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (p in pictures.toList()) Box {
                    Thumb(p, 64)
                    Surface(color = Color.Black.copy(alpha = 0.55f), shape = CircleShape,
                        modifier = Modifier.align(Alignment.TopEnd).padding(3.dp).size(20.dp).clip(CircleShape).clickable { pictures.remove(p) }) {
                        Icon(Icons.Outlined.Close, null, tint = Color.White, modifier = Modifier.padding(3.dp))
                    }
                }
            }
            TextField(
                value = text, onValueChange = onText,
                placeholder = { Text(tr("Сообщение", "Message")) },
                maxLines = 8,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (model?.images == true && editing == null) IconButton({
                    pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) { Icon(Icons.Outlined.AddPhotoAlternate, tr("Картинка", "Picture")) }
                if (model != null && model.variants.isNotEmpty()) ThinkingChip(brain)
                if (model != null && brain.isLocal(model)) {
                    val tools = brain.prefs.localTools
                    Surface(
                        color = if (tools) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.clip(RoundedCornerShape(18.dp)).clickable { brain.setLocalTools(!tools) },
                    ) {
                        Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Build, null, Modifier.size(16.dp))
                            Text(if (tools) tr("Агент", "Agent") else tr("Чат", "Chat"), style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(start = 6.dp))
                        }
                    }
                }
                if (editing == null) Box(Modifier.weight(1f)) { FolderChip(brain) } else Spacer(Modifier.weight(1f))
                ContextRing(brain)
                if (running) FilledIconButton(stop) { Icon(Icons.Outlined.Stop, tr("Остановить", "Stop")) }
                else FilledIconButton(send, enabled = canSend) {
                    Icon(Icons.AutoMirrored.Outlined.Send, tr("Отправить", "Send"), Modifier.size(20.dp))
                }
            }
        }
    }
}

/**
 * How full the chat's context window is, as a ring beside the send button: plain while there is room,
 * amber as it fills and red at the point where the app compacts on its own.
 */
@Composable
private fun ContextRing(brain: Brain) {
    val limit = brain.contextLimit()
    val used = brain.contextUsed()
    if (limit <= 0 || used <= 0) return
    val ratio = (used.toFloat() / limit.toFloat()).coerceIn(0f, 1f)
    val full = ratio >= 0.8f
    val colour = when {
        full -> MaterialTheme.colorScheme.error
        ratio >= 0.6f -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clip(RoundedCornerShape(18.dp)).clickable { menu = true }
                .padding(horizontal = 6.dp, vertical = 6.dp),
        ) {
            Canvas(Modifier.size(20.dp)) {
                val stroke = 2.4.dp.toPx()
                val side = size.minDimension - stroke
                val corner = Offset((size.width - side) / 2f, (size.height - side) / 2f)
                drawArc(track, 0f, 360f, false, corner, Size(side, side), style = Stroke(stroke))
                drawArc(colour, -90f, 360f * ratio, false, corner, Size(side, side),
                    style = Stroke(stroke, cap = StrokeCap.Round))
            }
            Text("${(ratio * 100).roundToInt()}%", style = MaterialTheme.typography.labelSmall,
                color = if (full) colour else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 5.dp))
        }
        DropdownMenu(menu, { menu = false }) {
            DropdownMenuItem(text = { Text(tr("Контекст: ", "Context: ") + tokens(used) + " / " + tokens(limit)) },
                onClick = { menu = false })
            if (brain.session() != null) DropdownMenuItem(
                text = { Text(tr("Сжать сейчас", "Compact now")) },
                onClick = { menu = false; brain.compact() },
            )
            DropdownMenuItem(
                text = { Text("AutoCompact: " + if (brain.prefs.autoCompact) tr("вкл", "on") else tr("выкл", "off")) },
                onClick = { menu = false; brain.update { it.copy(autoCompact = !it.autoCompact) } },
            )
        }
    }
}

@Composable
private fun ThinkingChip(brain: Brain) {
    val model = brain.currentModel() ?: return
    var menu by remember { mutableStateOf(false) }
    val now = brain.variantOf(model)
    Box {
        Surface(
            color = if (now.isEmpty()) Color.Transparent else MaterialTheme.colorScheme.secondaryContainer,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.clip(RoundedCornerShape(18.dp)).clickable { menu = true },
        ) {
            Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Psychology, null, Modifier.size(18.dp))
                Text(thinkingLabel(now), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 6.dp))
            }
        }
        DropdownMenu(menu, { menu = false }) {
            Text(tr("Размышления", "Thinking"), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
            for (v in listOf("") + model.variants) DropdownMenuItem(
                text = { Text(thinkingLabel(v), fontWeight = if (v == now) FontWeight.Bold else FontWeight.Normal) },
                onClick = { brain.setVariant(model, v); menu = false },
            )
        }
    }
}

/** opencode asking before a tool runs, or a question the model put as a form. */
@Composable
private fun Asks(brain: Brain) {
    val ask = brain.asks.firstOrNull { it.session == brain.open }
    if (ask != null) {
        AlertDialog(
            onDismissRequest = {},
            icon = { Icon(Icons.Outlined.Build, null) },
            title = { Text(tr("Разрешить: ${ask.action}?", "Allow ${ask.action}?")) },
            text = {
                Column {
                    ask.message?.let { Text(it) }
                    for (r in ask.resources.take(6)) Text(r, fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                        modifier = Modifier.padding(top = 6.dp))
                }
            },
            confirmButton = {
                Row {
                    TextButton({ brain.reply(ask, "always") }) { Text(tr("Всегда", "Always")) }
                    TextButton({ brain.reply(ask, "once") }) { Text(tr("Разрешить", "Allow")) }
                }
            },
            dismissButton = { TextButton({ brain.reply(ask, "reject") }) { Text(tr("Запретить", "Deny")) } },
        )
        return
    }
    val form = brain.forms.firstOrNull() ?: return
    FormDialog(form, onAnswer = { brain.answer(form, it) }, onCancel = { brain.dismiss(form) })
}
