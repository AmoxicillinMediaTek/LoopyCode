package cat.narezany.margycode.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cat.narezany.margycode.Brain
import cat.narezany.margycode.data.Margy
import cat.narezany.margycode.data.MargyBadge
import cat.narezany.margycode.data.MargyMe
import cat.narezany.margycode.tr
import kotlinx.coroutines.launch

/** Telegram's own blue, as the store's button has it. */
private val TelegramBlue = Color(0xFF2AABEE)
private val Mint = Color(0xFF8DD1B0)

private fun colour(hex: String): Color = runCatching { Color(0xFF000000 or hex.trim('#').toLong(16)) }.getOrDefault(Mint)

@Composable
private fun Picture(brain: Brain, url: String): ImageBitmap? =
    produceState<ImageBitmap?>(null, url) { value = brain.margy.avatar(url)?.asImageBitmap() }.value

@Composable
fun Avatar(brain: Brain, me: MargyMe, size: Int) {
    val picture = Picture(brain, me.avatar)
    Box(Modifier.size(size.dp).clip(CircleShape)) {
        if (picture != null) Image(picture, null, contentScale = ContentScale.Crop, modifier = Modifier.size(size.dp))
        else Surface(color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(size.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Text(me.display.take(1).uppercase().ifEmpty { "M" }, style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

/** A badge as MargyT shows it: its white picture painted in the badge's colour. Pressed, it says what it is. */
@Composable
fun BadgeIcon(brain: Brain, badge: MargyBadge, size: Dp) {
    var telling by remember { mutableStateOf(false) }
    val picture = if (badge.image.isNotEmpty()) Picture(brain, badge.image) else null
    @Composable
    fun drawn(at: Dp, modifier: Modifier) {
        if (picture != null) Image(picture, badge.title, modifier = modifier.size(at),
            colorFilter = if (badge.colour.isNotEmpty()) ColorFilter.tint(colour(badge.colour)) else null)
        else if (badge.image.isEmpty()) Icon(Icons.Rounded.MusicNote, badge.title, tint = colour(badge.colour), modifier = modifier.size(at))
        else Box(modifier.size(at))
    }
    drawn(size, Modifier.clip(CircleShape).clickable { telling = true })
    if (telling) AlertDialog(
        onDismissRequest = { telling = false },
        icon = { drawn(56.dp, Modifier) },
        title = { Text(badge.title) },
        text = badge.text.takeIf { it.isNotEmpty() }?.let { { Text(it) } },
        confirmButton = { TextButton({ telling = false }) { Text("OK") } },
    )
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun AccountScreen(brain: Brain, back: () -> Unit) {
    val uri = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    var waiting by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf(false) }
    val me = brain.margyMe

    fun work(block: suspend () -> Unit) {
        waiting = true
        problem = null
        scope.launch {
            runCatching { block() }.onFailure { problem = it.message ?: it.toString() }
            waiting = false
        }
    }
    fun signIn(google: Boolean) = work { brain.margySignIn(google) { runCatching { uri.openUri(it) } } }
    val pickAvatar = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { picked ->
        if (picked != null) work { brain.margyAvatar(picked) }
    }

    Page(tr("Аккаунт Margy", "Margy account"), back) { mod ->
        Column(mod.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (me == null) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Logo(72)
                    Text(tr("Тот же аккаунт, что в Margy Store", "The same account as in Margy Store"),
                        style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                    Text(tr("Войдите так же, как в магазине: через Telegram или Google. Профиль, ник и значки будут те же.",
                        "Sign in as in the store: with Telegram or Google. The profile, nick and badges are the same."),
                        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    Button({ signIn(false) }, enabled = !waiting, colors = ButtonDefaults.buttonColors(containerColor = TelegramBlue,
                        contentColor = Color.White), modifier = Modifier.fillMaxWidth()) { Text(tr("Войти через Telegram", "Sign in with Telegram")) }
                    OutlinedButton({ signIn(true) }, enabled = !waiting, modifier = Modifier.fillMaxWidth()) {
                        Text(tr("Войти через Google", "Sign in with Google"))
                    }
                }
            } else {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.clickable(enabled = !waiting) {
                        pickAvatar.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) {
                        Avatar(brain, me, 104)
                        Surface(color = MaterialTheme.colorScheme.primary, shape = CircleShape,
                            modifier = Modifier.align(Alignment.BottomEnd).size(32.dp)) {
                            Icon(Icons.Outlined.CameraAlt, tr("Сменить фото", "Change the photo"), Modifier.padding(7.dp),
                                tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                    Text(me.display, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center)
                    if (me.badges.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (b in me.badges) BadgeIcon(brain, b, 30.dp)
                    }
                    if (me.nick.isNotEmpty()) Text("@${me.nick}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(me.bio.ifEmpty { tr("Ничего о себе не рассказано", "Nothing about yourself yet") },
                        color = if (me.bio.isEmpty()) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center)
                    OutlinedButton({ editing = true }, enabled = !waiting) {
                        Icon(Icons.Outlined.Edit, null, Modifier.size(18.dp))
                        Text(tr("Изменить профиль", "Edit the profile"), Modifier.padding(start = 8.dp))
                    }
                }

                Section(tr("Вход", "Sign-in"))
                val both = me.telegram.isNotEmpty() && me.google.isNotEmpty()
                Way("Telegram", me.telegram, both, waiting, link = { signIn(false) }, unlink = { work { brain.margyUnlink("telegram") } })
                Way("Google", me.google, both, waiting, link = { signIn(true) }, unlink = { work { brain.margyUnlink("google") } })

                Section(tr("Чаты на сервере", "Chats on the server"))
                Row(Modifier.fillMaxWidth().clickable {
                    brain.update { it.copy(sync = !it.sync) }
                    brain.syncSoon(300)
                }, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("Синхронизировать чаты", "Sync chats"))
                        Text(tr("Копия чатов хранится на сервере Margy и приходит на другой телефон с этим аккаунтом. Другим людям они не видны, но сквозного шифрования нет",
                            "A copy of your chats is kept on the Margy server and comes to another phone with this account. Other people can't see them, but they aren't end-to-end encrypted"),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(brain.prefs.sync, { v -> brain.update { it.copy(sync = v) }; brain.syncSoon(300) })
                }
                if (brain.prefs.sync) Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton({ scope.launch { brain.syncNow() } }, enabled = !brain.syncing) {
                        Text(tr("Синхронизировать сейчас", "Sync now"))
                    }
                    if (brain.syncing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                }
                brain.syncError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ uri.openUri("${Margy.SITE}/u/${me.nick}") }, enabled = me.nick.isNotEmpty()) {
                        Text(tr("Профиль в магазине", "Profile in the store"))
                    }
                    TextButton({ brain.margySignOut() }) { Text(tr("Выйти", "Sign out")) }
                }
            }
            if (waiting) Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Text(tr("Минутку…", "One moment…"), Modifier.padding(start = 10.dp))
            }
            problem?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (editing && me != null) EditProfile(me, onCancel = { editing = false }) { display, nick, bio ->
        editing = false
        work { brain.margyEdit(display = display, nick = nick.takeIf { it != me.nick }, bio = bio) }
    }
}

@Composable
private fun Way(name: String, value: String, both: Boolean, waiting: Boolean, link: () -> Unit, unlink: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(name)
            Text(value.ifEmpty { tr("не привязан", "not linked") }, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (value.isEmpty()) TextButton(link, enabled = !waiting) { Text(tr("Привязать", "Link")) }
        else if (both) TextButton(unlink, enabled = !waiting) { Text(tr("Отвязать", "Unlink")) }
    }
}

@Composable
private fun EditProfile(me: MargyMe, onCancel: () -> Unit, onSave: (String, String, String) -> Unit) {
    var display by remember { mutableStateOf(me.display) }
    var nick by remember { mutableStateOf(me.nick) }
    var bio by remember { mutableStateOf(me.bio) }
    val nickOk = Regex("^[a-zA-Z0-9_.]{3,20}$").matches(nick)
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(tr("Профиль", "Profile")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).imePadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(display, { display = it.take(40) }, singleLine = true, label = { Text(tr("Имя", "Name")) },
                    modifier = Modifier.fillMaxWidth())
                OutlinedTextField(nick, { nick = it.trim().take(20) }, singleLine = true, label = { Text(tr("Ник", "Nick")) },
                    prefix = { Text("@") }, isError = !nickOk,
                    supportingText = { if (!nickOk) Text(tr("3–20 знаков: латиница, цифры, _ и .", "3–20 characters: latin letters, digits, _ and .")) },
                    modifier = Modifier.fillMaxWidth())
                OutlinedTextField(bio, { bio = it.take(300) }, minLines = 3, label = { Text(tr("О себе", "About")) },
                    supportingText = { Text("${bio.length}/300") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton({ onSave(display.trim(), nick, bio.trim()) }, enabled = nickOk && display.isNotBlank()) {
            Text(tr("Сохранить", "Save")) } },
        dismissButton = { TextButton(onCancel) { Text(tr("Отмена", "Cancel")) } },
    )
}
