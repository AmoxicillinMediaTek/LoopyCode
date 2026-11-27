package app.amoxi.loopycode

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.amoxi.loopycode.data.Ask
import app.amoxi.loopycode.data.Integration
import app.amoxi.loopycode.data.Message
import app.amoxi.loopycode.data.Model
import app.amoxi.loopycode.data.Part
import app.amoxi.loopycode.data.Prefs
import app.amoxi.loopycode.data.Session
import app.amoxi.loopycode.data.jsonOf
import app.amoxi.loopycode.data.objects
import app.amoxi.loopycode.data.str
import app.amoxi.loopycode.engine.Api
import app.amoxi.loopycode.engine.ApiError
import app.amoxi.loopycode.engine.ClaudeCode
import app.amoxi.loopycode.engine.Engine
import app.amoxi.loopycode.engine.Linux
import app.amoxi.loopycode.engine.Local
import app.amoxi.loopycode.engine.EngineService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Everything the screens show and do. The work itself is opencode's, running inside the app. */
class Brain(app: Application) : AndroidViewModel(app) {
    private val ctx: Application get() = getApplication()
    val engine = Engine(app)
    val api = Api(engine)
    val claude = ClaudeCode(app, engine)
    val linux = Linux(app, engine)
    val local = Local(app)
    /** What went wrong starting the models on the phone, if anything. */
    var localError by mutableStateOf<String?>(null)
        private set

    init {
        engine.extraEnv = { claude.env() + linux.env(prefs.linux) + mapOf("OPENCODE_CONFIG_CONTENT" to builtIn()) }
        engine.beforeStart = {
            claude.writeResolv()
            linux.writeResolv(claude.dns())
            writePrompt()
        }
    }

    val linuxActive get() = prefs.linux && linux.installed

    /** null while starting, "" when ready, else what went wrong. */
    var engineError by mutableStateOf<String?>(null)
        private set
    val ready get() = engineError == ""

    var prefs by mutableStateOf(Prefs.load(app))
        private set
    var integrations by mutableStateOf<List<Integration>>(emptyList())
        private set
    var models by mutableStateOf<List<Model>>(emptyList())
        private set
    var defaultModel by mutableStateOf<String?>(null)
        private set
    val sessions = mutableStateListOf<Session>()
    var open by mutableStateOf<String?>(null)
        private set
    var messages by mutableStateOf<List<Message>>(emptyList())
        private set
    val busy = mutableStateMapOf<String, Boolean>()
    var asks by mutableStateOf<List<Ask>>(emptyList())
        private set
    var forms by mutableStateOf<List<JSONObject>>(emptyList())
        private set
    /** Errors from actions (sending, connecting), shown once. */
    var notice by mutableStateOf<String?>(null)

    val screens = mutableStateListOf<String>()
    var shared by mutableStateOf<Pair<String, List<String>>?>(null)

    /**
     * Text streamed for the answer being written, in the order it began. Text and reasoning
     * are counted apart in opencode (each starts at ordinal 0), so a piece is known by all three.
     */
    private class Live(val message: String, val reasoning: Boolean, val ordinal: Int, val text: String)
    private val live = mutableStateListOf<Live>()
    private var refetchMessages: Job? = null
    private var refetchSessions: Job? = null

    init {
        viewModelScope.launch { boot() }
    }

    private suspend fun boot() {
        engineError = null
        // the local server goes first: opencode is told its address when it starts
        localError = runCatching { local.start(prefs.localContext, 0) }.exceptionOrNull()?.message
        try {
            engine.start()
        } catch (e: Exception) {
            engineError = e.message ?: e.toString()
            return
        }
        engineError = ""
        viewModelScope.launch { listen() }
        refreshAll()
    }

    fun restart() {
        engine.stop()
        local.stop()
        viewModelScope.launch { boot() }
    }

    override fun onCleared() {
        engine.stop()
        local.stop()
        EngineService.stop(ctx)
    }

    private suspend fun guard(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            notice = e.message ?: e.toString()
        }
    }

    suspend fun refreshAll() {
        guard { loadProviders() }
        guard { loadSessions() }
        guard { loadAsks() }
    }

    suspend fun loadProviders() {
        integrations = api.get("/api/integration").optJSONArray("data").objects().map { Integration.from(it) }
            .sortedBy { it.name.lowercase() }
        models = api.get("/api/model").optJSONArray("data").objects().map { Model.from(it) }
            .sortedWith(compareBy<Model> { it.provider }.thenByDescending { it.released })
        defaultModel = runCatching { api.get("/api/model/default").optJSONObject("data") }.getOrNull()
            ?.let { "${it.str("providerID")}/${it.str("id")}" }
    }

    private suspend fun loadSessions() {
        val list = api.get("/api/session").optJSONArray("data").objects()
            .filter { it.str("parentID") == null }
            .map { Session.from(it) }
        sessions.clear()
        sessions.addAll(list.sortedByDescending { it.updated })
        for (s in list) busy[s.id] = s.busy
        updateService()
    }

    private suspend fun loadMessages(id: String) {
        val raw = api.get("/api/session/$id/message?limit=200").optJSONArray("data").objects()
        val list = withContext(Dispatchers.IO) { raw.mapNotNull { Message.from(it, ::attachment) }.reversed() }
        if (open == id) messages = list
    }

    /** The picture itself when it is still on the phone, else its bytes from opencode written to the cache once. */
    private fun attachment(message: String, index: Int, f: JSONObject): String? {
        if (f.str("mime")?.startsWith("image/") == false) return null
        f.optJSONObject("source")?.str("uri")?.takeIf { it.startsWith("file://") }?.removePrefix("file://")
            ?.let { if (File(it).exists()) return it }
        val data = f.str("data") ?: return null
        val out = File(ctx.cacheDir, "attachments/${message}_$index")
        if (!out.exists()) runCatching {
            out.parentFile?.mkdirs()
            out.writeBytes(android.util.Base64.decode(data, android.util.Base64.DEFAULT))
        }
        return out.absolutePath.takeIf { out.exists() }
    }

    private suspend fun loadAsks() {
        asks = api.get("/api/permission/request").optJSONArray("data").objects().map { Ask.from(it) }
        val id = open
        forms = if (id == null) emptyList() else runCatching {
            api.get("/api/session/$id/form").optJSONArray("data").objects()
        }.getOrDefault(emptyList())
    }

    private fun soonMessages(settled: Boolean = false) {
        val id = open ?: return
        refetchMessages?.cancel()
        refetchMessages = viewModelScope.launch {
            delay(120)
            guard { loadMessages(id) }
            // once a turn is over the stored messages hold everything that streamed
            if (settled && busy[id] != true) live.clear()
        }
    }

    private fun soonSessions() {
        refetchSessions?.cancel()
        refetchSessions = viewModelScope.launch {
            delay(300)
            guard { loadSessions() }
        }
    }

    /** opencode's event stream: deltas go straight to the screen, anything lasting triggers a re-read. */
    private suspend fun listen() {
        while (true) {
            try {
                api.events { e -> viewModelScope.launch { onEvent(e) } }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
            delay(1000)
            if (!engine.running) {
                engineError = tr("opencode остановился", "opencode stopped") + "\n" + engine.log.readText().takeLast(1200)
                return
            }
            guard { loadSessions() }
            soonMessages()
        }
    }

    private fun onEvent(e: JSONObject) {
        val type = e.str("type") ?: return
        val data = e.optJSONObject("data") ?: JSONObject()
        val sid = data.str("sessionID")
        when {
            type == "session.text.delta" || type == "session.reasoning.delta" -> {
                if (sid != open) return
                val message = data.str("assistantMessageID") ?: return
                val reasoning = type == "session.reasoning.delta"
                val ordinal = data.optInt("ordinal")
                val delta = data.str("delta") ?: ""
                val at = live.indexOfFirst { it.message == message && it.reasoning == reasoning && it.ordinal == ordinal }
                if (at < 0) live += Live(message, reasoning, ordinal, delta)
                else live[at] = Live(message, reasoning, ordinal, live[at].text + delta)
                return
            }
            type == "session.execution.started" && sid != null -> { busy[sid] = true; updateService() }
            type.startsWith("session.execution.") && sid != null -> {
                busy[sid] = false
                updateService()
                soonSessions()
                if (sid == open) { soonMessages(settled = true); return }
            }
            type == "session.created" || type == "session.renamed" || type == "session.deleted" ||
                type == "session.model.selected" -> soonSessions()
            type.startsWith("permission.") || type.startsWith("form.") || type.startsWith("session.form") -> {
                viewModelScope.launch { guard { loadAsks() } }
            }
            type.startsWith("integration.") || type.startsWith("credential.") || type.startsWith("config.") ||
                type.startsWith("provider.") || type.startsWith("model.") || type.startsWith("catalog.") -> {
                viewModelScope.launch { guard { loadProviders() } }
            }
        }
        if (sid != null && sid == open && type.startsWith("session.")) soonMessages()
    }

    private fun updateService() {
        if (busy.values.any { it }) EngineService.start(ctx) else EngineService.stop(ctx)
    }

    /** The open chat's messages with whatever is still streaming laid over them. */
    fun shown(): List<Message> {
        if (live.isEmpty()) return messages
        val byMessage = live.toList().groupBy { it.message }
        fun part(l: Live): Part = if (l.reasoning) Part.Reasoning(l.text, 0) else Part.Text(l.text)
        val out = messages.map { m ->
            val pieces = byMessage[m.id] ?: return@map m
            val parts = m.parts.toMutableList()
            for (l in pieces) {
                // the ordinal-th stored part of the same kind, if opencode has kept it already
                val same = parts.withIndex().filter { (_, p) -> if (l.reasoning) p is Part.Reasoning else p is Part.Text }
                val stored = same.getOrNull(l.ordinal)
                if (stored == null) parts += part(l)
                else {
                    val old = when (val p = stored.value) { is Part.Text -> p.text; is Part.Reasoning -> p.text; else -> "" }
                    if (old.length < l.text.length) parts[stored.index] = part(l)
                }
            }
            Message(m.id, m.role, parts = parts, model = m.model, error = m.error, tokensIn = m.tokensIn,
                tokensOut = m.tokensOut, done = m.done)
        }.toMutableList()
        for ((id, pieces) in byMessage) if (out.none { it.id == id }) {
            out += Message(id, "assistant", parts = pieces.map { part(it) }, done = false)
        }
        return out
    }

    // ---- models ----

    fun model(ref: String?) = models.firstOrNull { it.ref == ref }

    fun providerName(id: String) = if (id == Local.PROVIDER) tr("На телефоне", "On the phone")
        else integrations.firstOrNull { it.id == id }?.name ?: id

    fun session(id: String? = open) = sessions.firstOrNull { it.id == id }

    fun currentModel(): Model? = model(session()?.model) ?: model(prefs.model) ?: model(defaultModel) ?: models.firstOrNull()

    fun variantOf(m: Model) = prefs.variants[m.ref]?.takeIf { it in m.variants } ?: ""

    fun update(change: (Prefs) -> Prefs) {
        prefs = change(prefs)
        val now = prefs
        viewModelScope.launch(Dispatchers.IO) { Prefs.save(ctx, now) }
    }

    private fun ref(m: Model) = jsonOf("id" to m.id, "providerID" to m.provider, "variant" to variantOf(m).ifEmpty { null })

    fun pick(m: Model) {
        update { it.copy(model = m.ref, recent = (listOf(m.ref) + it.recent.filter { r -> r != m.ref }).take(8)) }
        val id = open ?: return
        viewModelScope.launch { guard { api.post("/api/session/$id/model", jsonOf("model" to ref(m))); loadSessions() } }
    }

    fun setVariant(m: Model, v: String) {
        update { it.copy(variants = if (v.isEmpty()) it.variants - m.ref else it.variants + (m.ref to v)) }
        val id = open ?: return
        if (session()?.model == m.ref) viewModelScope.launch { guard { api.post("/api/session/$id/model", jsonOf("model" to ref(m))) } }
    }

    fun isLocal(m: Model) = m.provider == Local.PROVIDER

    /** opencode's own agent with tools, or the plain chat for models on the phone unless asked otherwise. */
    fun agentFor(m: Model) = if (isLocal(m) && !prefs.localTools) "chat" else "build"

    fun setLocalTools(on: Boolean) {
        update { it.copy(localTools = on) }
        val id = open ?: return
        val m = currentModel() ?: return
        viewModelScope.launch { guard { api.post("/api/session/$id/agent", jsonOf("agent" to agentFor(m))); loadSessions() } }
    }

    fun favourite(m: Model) = update {
        it.copy(favourites = if (m.ref in it.favourites) it.favourites - m.ref else it.favourites + m.ref)
    }

    // ---- chats ----

    fun openSession(id: String?) {
        open = id
        messages = emptyList()
        live.clear()
        if (id != null) viewModelScope.launch { guard { loadMessages(id); loadAsks() } }
    }

    fun rename(s: Session, title: String) = viewModelScope.launch {
        guard { api.call("PATCH", "/api/session/${s.id}", jsonOf("title" to title.trim())); loadSessions() }
    }

    fun delete(s: Session) = viewModelScope.launch {
        guard {
            api.delete("/api/session/${s.id}")
            if (open == s.id) openSession(null)
            loadSessions()
        }
    }

    fun send(text: String, pictures: List<String>) {
        val m = currentModel() ?: return
        viewModelScope.launch {
            guard {
                var id = open
                if (id == null) {
                    val dir = folder()
                    id = api.post("/api/session", jsonOf("model" to ref(m), "agent" to agentFor(m),
                        "location" to jsonOf("directory" to dir.absolutePath))).getJSONObject("data").getString("id")
                    open = id
                    messages = emptyList()
                    live.clear()
                } else if (session(id)?.model != m.ref || session(id)?.variant.orEmpty() != variantOf(m)) {
                    api.post("/api/session/$id/model", jsonOf("model" to ref(m)))
                }
                if (session(id)?.agent != null && session(id)?.agent != agentFor(m)) {
                    runCatching { api.post("/api/session/$id/agent", jsonOf("agent" to agentFor(m))) }
                }
                prompt(id, text, pictures)
                loadSessions()
                loadMessages(id)
            }
        }
    }

    private suspend fun prompt(id: String, text: String, pictures: List<String>) {
        val files = JSONArray()
        for (p in pictures) files.put(jsonOf("uri" to "file://$p", "name" to File(p).name))
        busy[id] = true
        api.post("/api/session/$id/prompt", jsonOf("text" to text.trim(), "files" to files.takeIf { it.length() > 0 }))
    }

    fun stop() {
        val id = open ?: return
        viewModelScope.launch { guard { api.post("/api/session/$id/interrupt") } }
    }

    /** Rolls the chat back to before a message of the person's, then sends it again, changed or not. */
    fun resend(user: Message, text: String = user.text) {
        val id = open ?: return
        viewModelScope.launch {
            guard {
                api.post("/api/session/$id/revert/stage", jsonOf("messageID" to user.id))
                api.post("/api/session/$id/revert/commit")
                live.clear()
                prompt(id, text, user.files.filter { File(it).exists() })
                loadMessages(id)
            }
        }
    }

    fun again() {
        val last = messages.lastOrNull { it.user } ?: return
        resend(last)
    }

    fun reply(ask: Ask, decision: String) = viewModelScope.launch {
        guard {
            api.post("/api/session/${ask.session}/permission/${ask.id}/reply", jsonOf("decision" to decision))
            loadAsks()
        }
    }

    fun answer(form: JSONObject, answer: JSONObject) = viewModelScope.launch {
        guard {
            api.post("/api/session/${form.str("sessionID")}/form/${form.str("id")}/reply", jsonOf("answer" to answer))
            loadAsks()
        }
    }

    fun dismiss(form: JSONObject) = viewModelScope.launch {
        guard {
            api.delete("/api/session/${form.str("sessionID")}/form/${form.str("id")}")
            loadAsks()
        }
    }

    // ---- export and import, in opencode's own transfer format ----

    suspend fun exportSession(id: String, to: Uri) {
        val data = api.get("/api/experimental/session/$id/export").getJSONObject("data")
        withContext(Dispatchers.IO) {
            ctx.contentResolver.openOutputStream(to, "wt")!!.use { it.write(data.toString(1).toByteArray()) }
        }
    }

    fun importSession(from: Uri) = viewModelScope.launch {
        guard {
            val text = withContext(Dispatchers.IO) {
                ctx.contentResolver.openInputStream(from)!!.use { it.readBytes().toString(Charsets.UTF_8) }
            }
            val o = JSONObject(text).let { it.optJSONObject("data") ?: it }
            if (!o.has("info") || !o.has("messages")) error(tr("Это не выгрузка диалога opencode", "This isn't an opencode chat export"))
            val body = jsonOf("info" to o.getJSONObject("info"), "messages" to o.getJSONArray("messages"),
                "location" to jsonOf("directory" to folder().absolutePath))
            val made = try {
                api.post("/api/experimental/session/import", body)
            } catch (e: ApiError) {
                if (e.code == 409) error(tr("Этот диалог уже есть в приложении", "This chat is already in the app")) else throw e
            }
            loadSessions()
            made.optJSONObject("data")?.str("id")?.let { openSession(it) }
        }
    }

    // ---- providers ----

    suspend fun connectKey(integration: String, key: String) {
        api.post("/api/integration/${Api.enc(integration)}/connect/key", jsonOf("key" to key.trim()))
        loadProviders()
    }

    suspend fun disconnect(integration: String) {
        val creds = api.get("/api/credential").optJSONArray("data").objects().filter { it.str("integrationID") == integration }
        for (c in creds) api.delete("/api/credential/${Api.enc(c.str("id") ?: continue)}")
        loadProviders()
    }

    /** Starts a sign-in through the browser; returns the attempt (url, instructions, mode). */
    suspend fun oauth(integration: String, method: String?): JSONObject =
        api.post("/api/integration/${Api.enc(integration)}/connect/oauth", jsonOf("methodID" to method)).getJSONObject("data")

    suspend fun oauthStatus(integration: String, attempt: String): String =
        api.get("/api/integration/${Api.enc(integration)}/connect/oauth/${Api.enc(attempt)}").optJSONObject("data")?.str("status") ?: "pending"

    suspend fun oauthComplete(integration: String, attempt: String, code: String) {
        api.post("/api/integration/${Api.enc(integration)}/connect/oauth/${Api.enc(attempt)}/complete", jsonOf("code" to code.trim()))
    }

    /** Tells opencode the person chose Claude Code, once `claude auth login` has gone through. */
    suspend fun connectClaude() {
        api.post("/api/integration/${ClaudeCode.ID}/connect/external", jsonOf("methodID" to ClaudeCode.METHOD))
        // opencode asks Claude Code for its models in the background; they come a few seconds later
        repeat(20) {
            loadProviders()
            if (models.any { it.provider == ClaudeCode.ID }) return
            delay(1500)
        }
    }

    /** opencode's own limits: the Go allowance and Console budgets, when signed in to an opencode account. */
    suspend fun opencodeLimits(): JSONObject? = api.get("/api/experimental/usage/limits").optJSONObject("data")

    // ---- skills and MCP servers ----

    val skillsDir get() = File(engine.config, "skills")

    suspend fun skills(): List<JSONObject> = api.get("/api/skill").optJSONArray("data").objects()

    suspend fun mcpServers(): List<JSONObject> = api.get("/api/mcp").optJSONArray("data").objects()

    /** A skill from a file: a SKILL.md alone, or a zip with one (and its scripts and references). */
    suspend fun installSkill(uri: Uri, name: String) {
        withContext(Dispatchers.IO) {
            val bytes = ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
            if (name.endsWith(".zip", true) || (bytes.size > 2 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte())) {
                val entries = mutableListOf<Pair<String, ByteArray>>()
                java.util.zip.ZipInputStream(bytes.inputStream()).use { z ->
                    while (true) {
                        val e = z.nextEntry ?: break
                        if (!e.isDirectory && !e.name.contains("..")) entries += e.name to z.readBytes()
                    }
                }
                val skill = entries.map { it.first }.filter { it.substringAfterLast('/') == "SKILL.md" }.minByOrNull { it.count { c -> c == '/' } }
                    ?: error(tr("В архиве нет SKILL.md", "There's no SKILL.md in the archive"))
                val root = skill.substringBeforeLast('/', "")
                val folder = File(skillsDir, root.substringAfterLast('/').ifEmpty { name.substringBeforeLast('.') })
                folder.deleteRecursively()
                for ((path, data) in entries) {
                    if (root.isNotEmpty() && !path.startsWith("$root/")) continue
                    val out = File(folder, path.removePrefix(if (root.isEmpty()) "" else "$root/"))
                    out.parentFile?.mkdirs()
                    out.writeBytes(data)
                }
            } else {
                val text = bytes.toString(Charsets.UTF_8)
                val title = Regex("(?m)^name:\\s*[\"']?([^\"'\\n]+)").find(text)?.groupValues?.get(1)?.trim()
                    ?: name.substringBeforeLast('.').takeIf { it != "SKILL" } ?: "skill"
                val folder = File(skillsDir, title.lowercase().replace(Regex("[^a-z0-9._-]+"), "-"))
                folder.mkdirs()
                File(folder, "SKILL.md").writeText(text)
            }
        }
        reload()
    }

    /** Only the skills that live in the app's own skills folder can be removed from here. */
    fun removable(skillPath: String) = File(skillPath).absolutePath.startsWith(skillsDir.absolutePath + "/")

    suspend fun removeSkill(skillPath: String) {
        if (!removable(skillPath)) return
        withContext(Dispatchers.IO) { File(skillPath).parentFile?.deleteRecursively() }
        reload()
    }

    private suspend fun readConfig(): JSONObject = withContext(Dispatchers.IO) {
        val text = configFile.takeIf { it.exists() }?.readText().orEmpty()
        if (text.isBlank()) JSONObject().put("\$schema", "https://opencode.ai/config.json")
        else JSONObject(text.lines().filterNot { it.trimStart().startsWith("//") }.joinToString("\n"))
    }

    suspend fun addMcp(name: String, server: JSONObject) {
        val c = readConfig()
        val mcp = c.optJSONObject("mcp") ?: JSONObject().also { c.put("mcp", it) }
        mcp.put(name, server)
        saveConfig(c.toString(2))
    }

    suspend fun removeMcp(name: String) {
        val c = readConfig()
        c.optJSONObject("mcp")?.remove(name)
        saveConfig(c.toString(2))
    }

    // ---- the app's own instructions ----

    private val promptFile get() = File(engine.home, ".config/loopycode/loopycode.md")

    /** What the model is told about LoopyCode, kept apart from the person's own AGENTS.md. */
    private fun writePrompt() {
        promptFile.parentFile?.mkdirs()
        val base = ctx.assets.open("loopycode.md").use { it.readBytes().toString(Charsets.UTF_8) }
        val extra = ctx.assets.open(if (linuxActive) "linux.md" else "android.md").use { it.readBytes().toString(Charsets.UTF_8) }
        promptFile.writeText(base + "\n" + extra)
    }

    private fun builtIn(): String {
        val o = jsonOf("instructions" to JSONArray().put(promptFile.absolutePath))
        local.provider(prefs.localContext)?.let { o.put("providers", jsonOf(Local.PROVIDER to it)) }
        // A plain conversation without tools: opencode then sends ~600 tokens instead of ~6000, which on a
        // phone's processor is the difference between seconds and minutes before the first word.
        o.put("agents", jsonOf("chat" to jsonOf(
            "description" to "Conversation without tools",
            "mode" to "primary",
            "permissions" to JSONArray().put(jsonOf("action" to "*", "resource" to "*", "effect" to "deny")),
        )))
        return o.toString()
    }

    // ---- folders ----

    /** The phone's shared storage, where the person's own files are. */
    val phone: File get() = android.os.Environment.getExternalStorageDirectory()

    /** Whether the app may reach every file; re-read when the person comes back from the system settings. */
    var filesGranted by mutableStateOf(allFiles())
        private set

    fun checkFiles() { filesGranted = allFiles() }

    private fun allFiles() = Build.VERSION.SDK_INT < 30 || android.os.Environment.isExternalStorageManager()

    /** Where a new chat works: the chosen folder when the app may reach it, else its own workspace. */
    fun folder(): File {
        val f = prefs.folder.takeIf { it.isNotBlank() }?.let { File(it) }
        return if (f != null && filesGranted && (f.isDirectory || f.mkdirs())) f else engine.workspace
    }

    fun folderLabel(path: String?): String = when {
        path == null || path == engine.workspace.absolutePath -> tr("Папка приложения", "App folder")
        path == phone.absolutePath -> tr("Память телефона", "Phone storage")
        path.startsWith(phone.absolutePath + "/") -> path.removePrefix(phone.absolutePath + "/")
        else -> path
    }

    // ---- opencode's own files ----

    val configFile get() = File(engine.config, "opencode.json")
    val rulesFile get() = File(engine.config, "AGENTS.md")

    suspend fun saveConfig(text: String) {
        if (text.isNotBlank()) JSONObject(text.lines().filterNot { it.trimStart().startsWith("//") }.joinToString("\n"))
        withContext(Dispatchers.IO) { engine.config.mkdirs(); configFile.writeText(text) }
        reload()
    }

    suspend fun saveRules(text: String) {
        withContext(Dispatchers.IO) { engine.config.mkdirs(); rulesFile.writeText(text) }
        reload()
    }

    suspend fun reload() {
        runCatching { api.post("/api/location/reload") }
        loadProviders()
    }

    // ---- pictures ----

    /** Copies a picture into the app, no bigger than models look at anyway. */
    suspend fun importPicture(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = if (Build.VERSION.SDK_INT >= 28) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, uri)) { d, info, _ ->
                    val side = maxOf(info.size.width, info.size.height)
                    if (side > 1568) {
                        val k = 1568f / side
                        d.setTargetSize((info.size.width * k).toInt(), (info.size.height * k).toInt())
                    }
                    d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } else {
                val raw = ctx.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it) }
                val side = maxOf(raw.width, raw.height)
                if (side > 1568) Bitmap.createScaledBitmap(raw, raw.width * 1568 / side, raw.height * 1568 / side, true) else raw
            }
            val dir = File(ctx.filesDir, "media").apply { mkdirs() }
            val out = File(dir, UUID.randomUUID().toString() + ".jpg")
            out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            out.absolutePath
        }.getOrNull()
    }
}
