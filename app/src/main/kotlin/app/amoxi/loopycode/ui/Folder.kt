package app.amoxi.loopycode.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.amoxi.loopycode.Brain
import app.amoxi.loopycode.tr

/** The folder a chat works in. It belongs to the chat, so it is picked when a chat starts. */
@Composable
fun FolderChip(brain: Brain) {
    var dialog by remember { mutableStateOf(false) }
    var hint by remember { mutableStateOf(false) }
    val session = brain.session()
    val label = if (session != null) brain.folderLabel(session.directory) else brain.folderLabel(brain.folder().absolutePath)
    Surface(color = Color.Transparent, shape = RoundedCornerShape(18.dp),
        modifier = Modifier.clip(RoundedCornerShape(18.dp)).clickable {
            // An open chat keeps the folder it was made with; changing it is a new chat's business.
            if (session == null) dialog = true else hint = true
        }) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Folder, null, Modifier.size(18.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 6.dp).widthIn(max = 130.dp))
        }
    }
    if (dialog) FolderDialog(brain) { dialog = false }
    if (hint) AlertDialog(
        onDismissRequest = { hint = false },
        icon = { Icon(Icons.Outlined.Folder, null) },
        title = { Text(tr("Папка — для нового чата", "The folder is for a new chat")) },
        text = {
            Text(tr("Этот чат работает в папке «$label» и дальше останется в ней. Начните новый чат и выберите папку на его чипе.",
                "This chat works in “$label” and stays there. Start a new chat and pick the folder on its chip."))
        },
        confirmButton = { TextButton({ hint = false; brain.openSession(null) }) { Text(tr("Новый чат", "New chat")) } },
        dismissButton = { TextButton({ hint = false }) { Text(tr("Понятно", "Got it")) } },
    )
}

@Composable
fun FolderDialog(brain: Brain, close: () -> Unit) {
    val context = LocalContext.current
    val phone = brain.phone.absolutePath
    var choice by remember {
        mutableStateOf(when (brain.prefs.folder) { "" -> "app"; phone -> "phone"; else -> "own" })
    }
    var path by remember { mutableStateOf(brain.prefs.folder.takeIf { it.isNotEmpty() && it != phone } ?: "$phone/LoopyCode") }
    // Android's own picker hands back a content:// tree; the agent still needs a real path.
    val browse = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val picked = uri?.let { pathOf(it, phone) }
        if (picked != null) { path = picked; choice = "own" }
    }
    fun askFiles() {
        if (Build.VERSION.SDK_INT >= 30) runCatching {
            context.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + context.packageName)))
        }.onFailure { context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
    }
    AlertDialog(
        onDismissRequest = close,
        icon = { Icon(Icons.Outlined.Folder, null) },
        title = { Text(tr("Где работает агент", "Where the agent works")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(tr("В этой папке модель читает, создаёт и меняет файлы и запускает команды. Для новых чатов.",
                    "In this folder the model reads, creates and changes files and runs commands. For new chats."),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                for ((v, label) in listOf(
                    "app" to tr("Папка приложения (видна только через adb)", "App folder (reachable only through adb)"),
                    "phone" to tr("Вся память телефона", "The whole phone storage"),
                    "own" to tr("Своя папка", "A folder of your own"),
                )) Row(Modifier.fillMaxWidth().clickable { choice = v }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(choice == v, { choice = v })
                    Text(label)
                }
                if (choice == "own") Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(path, { path = it.trim() }, singleLine = true, modifier = Modifier.weight(1f))
                    TextButton({ browse.launch(null) }) { Text(tr("Обзор", "Browse")) }
                }
                if (choice != "app" && !brain.filesGranted) {
                    Text(tr("Нужно разрешение «Доступ ко всем файлам».", "This needs the “Access all files” permission."),
                        color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 6.dp))
                    TextButton(::askFiles) { Text(tr("Разрешить", "Allow")) }
                }
            }
        },
        confirmButton = {
            TextButton({
                val folder = when (choice) { "phone" -> phone; "own" -> path; else -> "" }
                brain.update { it.copy(folder = folder) }
                if (folder.isNotEmpty() && !brain.filesGranted) askFiles()
                close()
            }) { Text(tr("Готово", "Done")) }
        },
        dismissButton = { TextButton(close) { Text(tr("Отмена", "Cancel")) } },
    )
}

/**
 * Android's tree picker names what it hands back after the volume it lives on: `primary:Download/Chats`
 * on the phone itself, `<card>:Folder` on a card, `raw:/storage/...` when it says it plainly.
 */
private fun pathOf(uri: Uri, phone: String): String? {
    val id = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull() ?: return null
    val volume = id.substringBefore(':')
    val rest = id.substringAfter(':', "").trim('/')
    return when {
        volume == "primary" -> if (rest.isEmpty()) phone else "$phone/$rest"
        volume == "raw" -> id.substringAfter(':', "").trimEnd('/').takeIf { it.startsWith("/") }
        volume.isEmpty() -> null
        else -> if (rest.isEmpty()) "/storage/$volume" else "/storage/$volume/$rest"
    }
}
