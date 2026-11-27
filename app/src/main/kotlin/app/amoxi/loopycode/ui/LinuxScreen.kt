package app.amoxi.loopycode.ui

import androidx.compose.foundation.clickable
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
    var command by remember { mutableStateOf("") }
    var log by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }

    fun run(cmd: String) {
        if (cmd.isBlank() || running) return
        running = true
        log += "$ $cmd\n"
        scope.launch {
            val out = runCatching { linux.run(cmd, brain.claude.dns()) }.getOrElse { it.message ?: it.toString() }
            log = (log + out + "\n\n").takeLast(20_000)
            running = false
        }
    }

    Page("Linux", back) { mod ->
        Column(mod.verticalScroll(rememberScrollState()).navigationBarsPadding().imePadding().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(tr("Alpine Linux внутри приложения, как в Termux. Модель запускает в нём свои команды и сама ставит что нужно через apk: python, node, git, компиляторы. Ваши папки видны по тем же путям.",
                "Alpine Linux inside the app, as in Termux. The model runs its commands there and installs what it needs with apk: python, node, git, compilers. Your folders keep the same paths."),
                color = MaterialTheme.colorScheme.onSurfaceVariant)

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
                Text("Alpine ${linux.version.orEmpty()} · " + tr("команды модели запускаются здесь", "model commands run here"),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Section(tr("Консоль", "Console"))
                Text(tr("Каждая команда запускается отдельно в папке приложения. Например: apk add python3",
                    "Each command runs on its own in the app folder. For example: apk add python3"),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (log.isNotEmpty()) Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()) {
                    SelectionContainer {
                        Text(log.trimEnd(), fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp,
                            modifier = Modifier.padding(12.dp).horizontalScroll(rememberScrollState()))
                    }
                }
                OutlinedTextField(command, { command = it }, singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    placeholder = { Text("apk add python3") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { run(command); command = "" }),
                    trailingIcon = {
                        if (running) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else IconButton({ run(command); command = "" }) { Icon(Icons.AutoMirrored.Outlined.Send, null) }
                    },
                    modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ run("cat /etc/alpine-release; uname -m; apk --version") }, enabled = !running) {
                        Text(tr("Проверить", "Check"))
                    }
                }
            }
            problem?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Spacer(Modifier.height(24.dp))
        }
    }

}
