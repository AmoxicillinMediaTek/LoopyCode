package app.amoxi.loopycode.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.amoxi.loopycode.Brain
import app.amoxi.loopycode.tr
import kotlinx.coroutines.launch

/** Installation is automatic; this gate keeps the agent from starting without Alpine. */
@Composable
fun LinuxInstallScreen(brain: Brain) {
    val progress = brain.linuxProgress
    Box(Modifier.fillMaxSize().navigationBarsPadding().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("LoopyCode", style = MaterialTheme.typography.headlineMedium)
            Text(tr("Устанавливаем Alpine Linux", "Installing Alpine Linux"), style = MaterialTheme.typography.titleMedium)
            Text(tr("Подготавливаем среду для локальных MCP-серверов и команд модели. Это нужно только при первом запуске.",
                "Preparing the environment for local MCP servers and model commands. This only happens on first launch."),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LinearProgressIndicator(progress ?: 0f, Modifier.fillMaxWidth())
            Text(if (progress == null) tr("Начинаем загрузку…", "Starting download…")
                else tr("Скачиваем Alpine: ${(progress * 100).toInt()}%", "Downloading Alpine: ${(progress * 100).toInt()}%"),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            brain.linuxError?.let { error ->
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                androidx.compose.material3.Button(onClick = { brain.retryLinuxInstall() }) {
                    Text(tr("Повторить", "Retry"))
                }
            }
        }
    }
}

/** Alpine Linux for the agent, with a small console for one's own commands. */
@Composable
fun LinuxScreen(brain: Brain, back: () -> Unit) {
    val linux = brain.linux
    val scope = rememberCoroutineScope()
    val installed = linux.installed
    var problem by remember { mutableStateOf<String?>(null) }
    var command by remember { mutableStateOf(TextFieldValue("")) }
    var log by remember { mutableStateOf("") }
    val promptPath = linux.terminalCwd.let { path -> if (path == "/root") "~" else if (path.startsWith("/root/")) "~/${path.removePrefix("/root/")}" else path }
    val prompt = "root@localhost $promptPath $"
    var running by remember { mutableStateOf(false) }
    var shift by remember { mutableStateOf(false) }
    var control by remember { mutableStateOf(false) }

    fun run(cmd: String) {
        if (cmd.isBlank() || running) return
        running = true
        log += "$prompt $cmd\n"
        scope.launch {
            try {
                val out = linux.run(cmd, brain.claude.dns())
                log = (log + out + "\n\n").takeLast(20_000)
            } catch (error: Exception) {
                problem = error.message ?: error.toString()
            } finally {
                running = false
            }
        }
    }

    Page(tr("Терминал Alpine", "Alpine terminal"), back) { mod ->
        Column(mod.navigationBarsPadding().imePadding().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {

            if (!installed) {
                val p = brain.linuxProgress
                LinearProgressIndicator(p ?: 0f, Modifier.fillMaxWidth())
                Text(if (p == null) tr("Alpine устанавливается автоматически…", "Alpine is installing automatically…")
                    else tr("Скачиваю Alpine: ${(p * 100).toInt()}%", "Downloading Alpine: ${(p * 100).toInt()}%"),
                    style = MaterialTheme.typography.bodySmall)
                brain.linuxError?.let { error ->
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Terminal, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Text("Alpine ${linux.version.orEmpty()} · " + tr("команды модели запускаются здесь", "model commands run here"),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp))
                }

                if (log.isNotEmpty()) Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).fillMaxWidth()) {
                    SelectionContainer {
                        Text(log.trimEnd(), fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 17.sp,
                            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState()).padding(12.dp))
                    }
                } else Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    listOf("Esc", "Tab", "Ctrl", "Shift", "↑", "↓", "|", "/", "~", "-", "_", "←", "→").forEach { key ->
                        val selected = when (key) { "Shift" -> shift; "Ctrl" -> control; else -> false }
                        OutlinedButton(onClick = {
                            when (key) {
                                "Shift" -> shift = !shift
                                "Ctrl" -> control = !control
                                "Esc" -> {
                                    val start = minOf(command.selection.start, command.selection.end)
                                    val end = maxOf(command.selection.start, command.selection.end)
                                    val next = command.text.replaceRange(start, end, "\u001B")
                                    command = command.copy(text = next, selection = androidx.compose.ui.text.TextRange(start + 1))
                                    problem = null
                                    shift = false; control = false
                                }
                                else -> {
                                    val insert = when (key) {
                                        "↑" -> "\u001B[A"; "↓" -> "\u001B[B"; "←" -> "\u001B[D"; "→" -> "\u001B[C"
                                        "Tab" -> "\t"; else -> key
                                    }
                                    val value = if (control && insert.length == 1) (insert[0].uppercaseChar().code - 64).toChar().toString() else if (shift) insert.uppercase() else insert
                                    val start = minOf(command.selection.start, command.selection.end)
                                    val end = maxOf(command.selection.start, command.selection.end)
                                    val next = command.text.replaceRange(start, end, value)
                                    command = command.copy(text = next, selection = androidx.compose.ui.text.TextRange(start + value.length))
                                    problem = null
                                    control = false; shift = false
                                }
                            }
                        }, enabled = !running, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            modifier = Modifier.height(38.dp)) { Text(key, fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(command, { command = it }, singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        leadingIcon = { Text(prompt, fontFamily = FontFamily.Monospace, fontSize = 12.sp, maxLines = 1,
                            softWrap = false, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.primary) },
                        placeholder = { Text("apk add python3") },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { run(command.text); command = TextFieldValue("") }),
                        trailingIcon = {
                            if (running) IconButton({ linux.interrupt() }) { Icon(Icons.Outlined.Close, tr("Остановить", "Stop")) }
                            else IconButton({ run(command.text); command = TextFieldValue("") }) { Icon(Icons.AutoMirrored.Outlined.Send, tr("Выполнить", "Run")) }
                        }, modifier = Modifier.weight(1f))
                    if (running) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            }
            problem?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Spacer(Modifier.height(24.dp))
        }
    }

}
