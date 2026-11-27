package app.amoxi.loopycode.ui

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.amoxi.loopycode.Brain
import app.amoxi.loopycode.tr

@Composable
fun SettingsScreen(brain: Brain, back: () -> Unit) {
    var rules by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { rules = withContext(Dispatchers.IO) { brain.rulesFile.takeIf { it.exists() }?.readText().orEmpty() } }
    var wipe by remember { mutableStateOf(false) }
    Page(tr("Настройки", "Settings"), back) { mod ->
        Column(mod.verticalScroll(rememberScrollState()).navigationBarsPadding().imePadding()) {
            Section(tr("Оформление", "Look"))
            for ((v, label) in listOf("system" to tr("Как в системе", "As the system"), "light" to tr("Светлая", "Light"), "dark" to tr("Тёмная", "Dark"), "oled" to tr("Чёрная (OLED)", "Black (OLED)"))) {
                Row(Modifier.fillMaxWidth().clickable { brain.update { it.copy(theme = v) } }.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(brain.prefs.theme == v, { brain.update { it.copy(theme = v) } })
                    Text(label)
                }
            }
            if (Build.VERSION.SDK_INT >= 31) Row(
                Modifier.fillMaxWidth().clickable { brain.update { it.copy(dynamic = !it.dynamic) } }.padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(tr("Цвета обоев", "Wallpaper colours"))
                    Text(tr("Вместо мятного", "Instead of the mint"), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(brain.prefs.dynamic, { v -> brain.update { it.copy(dynamic = v) } })
            }

            Section(tr("Правила для модели", "Rules for the model"))
            Text(tr("Общий AGENTS.md opencode: что модель должна помнить в каждом чате.",
                "opencode's global AGENTS.md: what the model keeps in mind in every chat."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
            rules?.let { r ->
                OutlinedTextField(r, { rules = it }, minLines = 3, maxLines = 12,
                    placeholder = { Text(tr("Например: отвечай кратко и по-русски", "For example: answer briefly")) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp))
                TextButton({ scope.launch { runCatching { brain.saveRules(r) } } }, Modifier.padding(start = 12.dp)) {
                    Text(tr("Сохранить правила", "Save the rules"))
                }
            }

            Section(tr("Файлы", "Files"))
            var folders by remember { mutableStateOf(false) }
            Row(Modifier.fillMaxWidth().clickable { folders = true }.padding(horizontal = 24.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tr("Папка для новых чатов", "Folder for new chats"))
                    Text(brain.folderLabel(brain.folder().absolutePath), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (folders) FolderDialog(brain) { folders = false }
            Row(Modifier.fillMaxWidth().clickable { brain.screens.add("linux") }.padding(horizontal = 24.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tr("Linux для модели", "Linux for the model"))
                    Text(when {
                        !brain.linux.installed -> tr("Не установлен: команды идут в оболочку Android", "Not installed: commands go to Android's shell")
                        brain.prefs.linux -> "Alpine ${brain.linux.version.orEmpty()}"
                        else -> tr("Установлен, выключен", "Installed, off")
                    }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Section(tr("Данные", "Data"))
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ brain.screens.add("config") }) { Text(tr("Настройки opencode (opencode.json)", "opencode settings (opencode.json)")) }
                OutlinedButton({ brain.restart() }) { Text(tr("Перезапустить opencode", "Restart opencode")) }
                OutlinedButton({ wipe = true }, enabled = brain.sessions.isNotEmpty()) { Text(tr("Удалить все чаты", "Delete every chat")) }
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    if (wipe) AlertDialog(
        onDismissRequest = { wipe = false },
        title = { Text(tr("Удалить все чаты?", "Delete every chat?")) },
        text = { Text(tr("Вернуть их будет нельзя.", "They can't be brought back.")) },
        confirmButton = { TextButton({ brain.sessions.toList().forEach { brain.delete(it) }; wipe = false }) { Text(tr("Удалить", "Delete")) } },
        dismissButton = { TextButton({ wipe = false }) { Text(tr("Отмена", "Cancel")) } },
    )
}
