package cat.narezany.margycode.data

import org.json.JSONObject

/** What opencode's API returns, cut down to what the screens show. */

class Model(
    val id: String,
    val provider: String,
    val name: String,
    val variants: List<String>,
    val images: Boolean,
    val context: Long,
    val costIn: Double?,
    val costOut: Double?,
    val released: Long,
) {
    val ref get() = "$provider/$id"

    companion object {
        fun from(o: JSONObject): Model {
            val cost = o.optJSONArray("cost")?.optJSONObject(0)
            return Model(
                id = o.str("id") ?: "",
                provider = o.str("providerID") ?: "",
                name = o.str("name") ?: o.str("id") ?: "",
                variants = o.optJSONArray("variants").objects().mapNotNull { it.str("id") },
                images = "image" in o.optJSONObject("capabilities")?.optJSONArray("input").strings(),
                context = o.optJSONObject("limit")?.optLong("context") ?: 0,
                costIn = cost?.takeIf { it.has("input") }?.optDouble("input"),
                costOut = cost?.takeIf { it.has("output") }?.optDouble("output"),
                released = o.optJSONObject("time")?.optLong("released") ?: 0,
            )
        }
    }
}

class Method(val id: String?, val type: String, val label: String?)

class Connection(val id: String, val label: String?, val active: Boolean)

/** A provider opencode can connect to, and how. */
class Integration(val id: String, val name: String, val methods: List<Method>, val connections: List<Connection>) {
    val connected get() = connections.isNotEmpty()
    val key get() = methods.any { it.type == "key" }
    val oauth get() = methods.filter { it.type == "oauth" }
    val external get() = methods.any { it.type == "external" }

    companion object {
        fun from(o: JSONObject) = Integration(
            id = o.str("id") ?: "",
            name = o.str("name") ?: o.str("id") ?: "",
            methods = o.optJSONArray("methods").objects().map { Method(it.str("id"), it.str("type") ?: "", it.str("label")) },
            connections = o.optJSONArray("connections").objects().map {
                Connection(it.str("id") ?: it.str("credentialID") ?: "", it.str("label"), it.optBoolean("active", true))
            },
        )
    }
}

class Session(
    val id: String,
    val title: String,
    val model: String?,
    val variant: String?,
    val updated: Long,
    val busy: Boolean,
    val directory: String?,
    val agent: String?,
) {
    companion object {
        fun from(o: JSONObject): Session {
            val time = o.optJSONObject("time")
            val busy = time?.optLong("busy") ?: 0
            val idle = time?.optLong("idle") ?: 0
            val m = o.optJSONObject("model")
            return Session(
                id = o.str("id") ?: "",
                title = o.str("title") ?: "",
                model = m?.let { "${it.str("providerID")}/${it.str("id")}" },
                variant = m?.str("variant"),
                updated = time?.optLong("updated") ?: 0,
                busy = busy > 0 && busy > idle,
                directory = o.optJSONObject("location")?.str("directory"),
                agent = o.str("agent"),
            )
        }
    }
}

sealed interface Part {
    class Text(val text: String) : Part
    class Reasoning(val text: String, val ms: Long) : Part
    class Tool(val name: String, val status: String, val input: JSONObject, val output: String, val error: String?) : Part
}

class Message(
    val id: String,
    /** user, assistant, or idle (the end of a turn, which carries its error) */
    val role: String,
    val text: String = "",
    val files: List<String> = emptyList(),
    val parts: List<Part> = emptyList(),
    val model: String? = null,
    val error: String? = null,
    val outcome: String? = null,
    val tokensIn: Long = 0,
    val tokensOut: Long = 0,
    val done: Boolean = true,
) {
    val user get() = role == "user"

    companion object {
        fun error(o: JSONObject?): String? = o?.let { it.str("message") ?: it.str("type") }

        /** Turns an attachment opencode keeps (its bytes, and where it came from) into a file the screen can show. */
        fun from(o: JSONObject, attach: (String, Int, JSONObject) -> String? = { _, _, _ -> null }): Message? {
            val id = o.str("id") ?: return null
            return when (o.str("type")) {
                "user" -> Message(id, "user", text = o.str("text") ?: "",
                    files = o.optJSONArray("files").objects().mapIndexedNotNull { i, f -> attach(id, i, f) })
                "assistant" -> {
                    val parts = o.optJSONArray("content").objects().mapNotNull { c ->
                        when (c.str("type")) {
                            "text" -> Part.Text(c.str("text") ?: "")
                            "reasoning" -> {
                                val t = c.optJSONObject("time")
                                val ms = if (t != null && t.has("completed")) t.optLong("completed") - t.optLong("created") else 0
                                Part.Reasoning(c.str("text") ?: "", ms)
                            }
                            "tool" -> {
                                val state = c.optJSONObject("state") ?: JSONObject()
                                val out = state.optJSONArray("content").objects().mapNotNull { it.str("text") }.joinToString("\n")
                                Part.Tool(c.str("name") ?: "tool", state.str("status") ?: "running",
                                    state.optJSONObject("input") ?: JSONObject(), out, error(state.optJSONObject("error")))
                            }
                            else -> null
                        }
                    }
                    val tokens = o.optJSONObject("tokens")
                    Message(id, "assistant", parts = parts,
                        model = o.optJSONObject("model")?.str("id"),
                        error = error(o.optJSONObject("error")),
                        tokensIn = tokens?.optLong("input") ?: 0,
                        tokensOut = (tokens?.optLong("output") ?: 0) + (tokens?.optLong("reasoning") ?: 0),
                        done = o.optJSONObject("time")?.has("completed") == true)
                }
                "idle" -> Message(id, "idle", outcome = o.str("outcome"), error = error(o.optJSONObject("error")))
                else -> null
            }
        }
    }
}

/** A tool asking to be allowed, as opencode asks in its own interface. */
class Ask(val id: String, val session: String, val action: String, val resources: List<String>, val message: String?) {
    companion object {
        fun from(o: JSONObject) = Ask(
            id = o.str("id") ?: "",
            session = o.str("sessionID") ?: "",
            action = o.str("action") ?: "",
            resources = o.optJSONArray("resources").strings(),
            message = o.str("message"),
        )
    }
}
