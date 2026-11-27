package cat.narezany.margycode.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** A badge as MargyT and the store show it: a white picture painted in the badge's colour. */
class MargyBadge(val id: String, val title: String, val text: String, val image: String, val colour: String) {
    companion object {
        fun from(o: JSONObject): MargyBadge {
            val lang = if (cat.narezany.margycode.Lang.ru) "ru" else "en"
            fun pick(base: String) = o.optJSONObject(base + "_tr")?.str(lang) ?: o.str(base) ?: ""
            return MargyBadge(o.str("id") ?: "", pick("title"), pick("text"), o.str("image") ?: "", o.str("colour") ?: "")
        }
    }
}

/** Who is signed in to Margy Store, as the store shows them. */
class MargyMe(
    val nick: String,
    val display: String,
    val avatar: String,
    val bio: String,
    val supporter: Boolean,
    val admin: Boolean,
    val nickSet: Boolean,
    val telegram: String,
    val google: String,
    val dmOpen: Boolean,
    val badges: List<MargyBadge>,
) {
    companion object {
        fun from(o: JSONObject) = MargyMe(
            nick = o.str("nick") ?: "",
            display = o.str("display") ?: o.str("nick") ?: "Margy",
            avatar = o.str("avatar") ?: "",
            bio = o.str("bio") ?: "",
            supporter = o.optBoolean("supporter"),
            admin = o.optBoolean("admin"),
            nickSet = o.optBoolean("nick_set", true),
            telegram = o.str("telegram") ?: "",
            google = o.str("google") ?: "",
            dmOpen = o.optBoolean("dm_open", true),
            badges = o.optJSONArray("badges").objects().map { MargyBadge.from(it) },
        )
    }
}

/**
 * The Margy Store account, signed in exactly as the store app does it: Telegram through
 * the project's bot, or Google in the browser. The same server and the same token, so
 * it is the same profile.
 */
class Margy(private val context: Context) {
    class Failure(message: String, val code: Int) : IOException(message)

    private val tokenFile get() = File(context.filesDir, "margy.token")
    val token: String get() = tokenFile.takeIf { it.exists() }?.readText()?.trim().orEmpty()
    val signedIn get() = token.isNotEmpty()

    private fun request(method: String, url: String, body: JSONObject? = null, bearer: String? = token,
                        raw: ByteArray? = null, type: String? = null): JSONObject =
        JSONObject(String(send(method, url, body, bearer, raw, type), Charsets.UTF_8).ifBlank { "{}" })

    /** The store's server takes PATCH and PUT as POST with an override header, as its own app sends them. */
    private fun send(method: String, url: String, body: JSONObject? = null, bearer: String? = token,
                     raw: ByteArray? = null, type: String? = null): ByteArray {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            if (method == "PATCH" || method == "PUT") {
                c.requestMethod = "POST"
                c.setRequestProperty("X-HTTP-Method-Override", method)
            } else c.requestMethod = method
            c.connectTimeout = 15_000
            c.readTimeout = 30_000
            c.setRequestProperty("User-Agent", "MargyCode")
            if (!bearer.isNullOrEmpty()) c.setRequestProperty("Authorization", "Bearer $bearer")
            if (body != null || raw != null) {
                val bytes = raw ?: body.toString().toByteArray()
                c.doOutput = true
                c.setFixedLengthStreamingMode(bytes.size)
                c.setRequestProperty("Content-Type", type ?: "application/json")
                c.outputStream.use { it.write(bytes) }
            }
            val code = c.responseCode
            val bytes = (if (code in 200..299) c.inputStream else c.errorStream)?.use { it.readBytes() } ?: ByteArray(0)
            if (code !in 200..299) {
                val said = runCatching { JSONObject(String(bytes, Charsets.UTF_8)) }.getOrDefault(JSONObject())
                throw Failure(said.str("error") ?: "HTTP $code", code)
            }
            return bytes
        } catch (f: Failure) {
            throw f
        } catch (e: IOException) {
            throw Failure(cat.narezany.margycode.tr("нет связи с сервером Margy", "no connection to the Margy server"), 0)
        } finally {
            c.disconnect()
        }
    }

    private fun store(method: String, path: String, body: JSONObject? = null) = request(method, STORE + path, body)

    private fun keep(said: JSONObject): MargyMe {
        tokenFile.writeText(said.getString("token"))
        return MargyMe.from(said.getJSONObject("me"))
    }

    suspend fun me(): MargyMe? = withContext(Dispatchers.IO) {
        if (!signedIn) return@withContext null
        try {
            MargyMe.from(store("GET", "/me"))
        } catch (f: Failure) {
            if (f.code == 401) tokenFile.delete()
            throw f
        }
    }

    /**
     * Telegram: the server hands out a one-time code, the bot link opens Telegram, and once
     * the person presses Start there the server knows who it was.
     */
    suspend fun telegram(open: (String) -> Unit): MargyMe = withContext(Dispatchers.IO) {
        val begun = request("POST", "$PATCHER/login/start", JSONObject(), bearer = null)
        val code = begun.getString("code")
        withContext(Dispatchers.Main) { open(begun.getString("link")) }
        repeat(150) {
            delay(2000)
            val who = runCatching { request("GET", "$PATCHER/login/check?code=$code", bearer = null) }.getOrNull()
            if (who?.str("state") == "ok") {
                return@withContext keep(store("POST", "/auth/telegram", JSONObject().put("token", who.getString("token"))))
            }
        }
        throw Failure(cat.narezany.margycode.tr("Вход через Telegram не завершён", "The Telegram sign-in didn't finish"), 0)
    }

    /** Google, in the browser: Google sends the person back to the server, which the app keeps asking. */
    suspend fun google(open: (String) -> Unit): MargyMe = withContext(Dispatchers.IO) {
        val begun = store("POST", "/auth/google/start")
        val code = begun.getString("code")
        withContext(Dispatchers.Main) { open(begun.getString("link")) }
        repeat(150) {
            delay(2000)
            val said = runCatching { store("GET", "/auth/google/check?code=$code") }.getOrNull()
            if (said?.str("state") == "ok") return@withContext keep(said)
        }
        throw Failure(cat.narezany.margycode.tr("Вход через Google не завершён", "The Google sign-in didn't finish"), 0)
    }

    /** Changes what is given: display name, nick, about, whether anyone may write first. */
    suspend fun edit(display: String? = null, nick: String? = null, bio: String? = null, dmOpen: Boolean? = null): MargyMe =
        withContext(Dispatchers.IO) {
            MargyMe.from(store("PATCH", "/me", jsonOf("display" to display, "nick" to nick, "bio" to bio, "dm_open" to dmOpen)))
        }

    suspend fun setAvatar(bytes: ByteArray, mime: String) = withContext(Dispatchers.IO) {
        request("POST", "$STORE/me/avatar", raw = bytes, type = mime)
    }

    /** Takes one way of signing in off the account; the server keeps at least one. */
    suspend fun unlink(what: String) = withContext(Dispatchers.IO) { store("POST", "/me/unlink", jsonOf("what" to what)) }

    // ---- chats kept on the server for their owner ----

    class Remote(val id: String, val title: String, val updated: Long)

    suspend fun chats(): List<Remote> = withContext(Dispatchers.IO) {
        store("GET", "/code/chats").optJSONArray("chats").objects().map { Remote(it.getString("id"), it.str("title") ?: "", it.optLong("updated")) }
    }

    suspend fun putChat(id: String, title: String, updated: Long, json: ByteArray) = withContext(Dispatchers.IO) {
        val gz = java.io.ByteArrayOutputStream().also { o -> java.util.zip.GZIPOutputStream(o).use { it.write(json) } }.toByteArray()
        val q = "?updated=$updated&title=" + java.net.URLEncoder.encode(title, "UTF-8")
        request("PUT", "$STORE/code/chats/$id$q", raw = gz, type = "application/gzip")
    }

    suspend fun getChat(id: String): ByteArray = withContext(Dispatchers.IO) {
        java.util.zip.GZIPInputStream(send("GET", "$STORE/code/chats/$id").inputStream()).use { it.readBytes() }
    }

    suspend fun deleteChat(id: String) = withContext(Dispatchers.IO) { store("DELETE", "/code/chats/$id") }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        runCatching { store("POST", "/auth/logout") }
        tokenFile.delete()
    }

    /** A picture from the store (avatar, badge), downloaded once and kept in the cache. */
    suspend fun avatar(url: String): Bitmap? = withContext(Dispatchers.IO) {
        if (url.isEmpty()) return@withContext null
        val full = if (url.startsWith("/")) "https://margy.narez.xyz$url" else url
        val file = File(context.cacheDir, "margy-avatar-" + full.hashCode().toUInt().toString(16))
        runCatching {
            if (!file.exists()) URL(full).openStream().use { i -> file.outputStream().use { i.copyTo(it) } }
            BitmapFactory.decodeFile(file.absolutePath)
        }.getOrNull()
    }

    companion object {
        private const val STORE = "https://margy.narez.xyz/store/api"
        private const val PATCHER = "https://margy.narez.xyz/api/patcher"
        const val SITE = "https://margy.narez.xyz"
    }
}
