package cat.narezany.margycode.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** The app's own choices. Keys, chats and providers live in opencode. */
data class Prefs(
    val model: String? = null,
    val favourites: List<String> = emptyList(),
    val recent: List<String> = emptyList(),
    /** Chosen variant (thinking level) per model ref; missing means the model's default. */
    val variants: Map<String, String> = emptyMap(),
    val theme: String = "system",
    val dynamic: Boolean = false,
    /** Where new chats work; empty means the app's own workspace. */
    val folder: String = "",
    /** Run the agent's commands in Alpine once it is installed. */
    val linux: Boolean = true,
    /** Keep chats on the Margy server too, for the same account on other phones. */
    val sync: Boolean = false,
    /** Context for models on the phone: more remembers more but needs more memory. */
    val localContext: Int = 16384,
    /** Models on the phone get opencode's tools too; off, they just talk, with a prompt ten times shorter. */
    val localTools: Boolean = false,
) {
    fun json() = jsonOf(
        "model" to model,
        "favourites" to JSONArray(favourites),
        "recent" to JSONArray(recent),
        "variants" to JSONObject(variants),
        "theme" to theme,
        "dynamic" to dynamic,
        "folder" to folder,
        "linux" to linux,
        "sync" to sync,
        "localContext" to localContext,
        "localTools" to localTools,
    )

    companion object {
        private fun file(context: Context) = File(context.filesDir, "prefs.json")

        fun load(context: Context): Prefs {
            val o = runCatching { JSONObject(file(context).readText()) }.getOrNull() ?: return Prefs()
            return Prefs(
                model = o.str("model"),
                favourites = o.optJSONArray("favourites").strings(),
                recent = o.optJSONArray("recent").strings(),
                variants = o.optJSONObject("variants")?.let { m -> m.keys().asSequence().associateWith { m.optString(it) } } ?: emptyMap(),
                theme = o.str("theme") ?: "system",
                dynamic = o.optBoolean("dynamic"),
                folder = o.str("folder") ?: "",
                linux = o.optBoolean("linux", true),
                sync = o.optBoolean("sync"),
                localContext = o.optInt("localContext", 16384),
                localTools = o.optBoolean("localTools"),
            )
        }

        fun save(context: Context, prefs: Prefs) {
            val tmp = File(context.filesDir, "prefs.json.tmp")
            tmp.writeText(prefs.json().toString(1))
            tmp.renameTo(file(context))
        }
    }
}
