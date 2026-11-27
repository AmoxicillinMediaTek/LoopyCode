package cat.narezany.margycode.engine

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URL
import java.security.SecureRandom
import java.util.Base64

/**
 * opencode itself, running inside the app as `opencode serve` on localhost.
 *
 * The binary is upstream opencode compiled onto the official Bun for Android
 * (engine/build-engine.sh). It ships as lib/arm64-v8a/libopencode.so because
 * the library folder is the one place Android lets an app execute files from.
 */
class Engine(private val context: Context) {
    private var process: Process? = null
    var port = 0
        private set
    val password: String = ByteArray(24).also { SecureRandom().nextBytes(it) }
        .let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }

    val home get() = File(context.filesDir, "home")
    /** The folder opencode treats as the project: its tools read and write here. */
    val workspace get() = File(context.filesDir, "workspace")
    val config get() = File(home, ".config/opencode")
    val log get() = File(context.filesDir, "opencode.log")

    val running get() = process?.isAlive == true
    private val tmp get() = File(context.cacheDir, "tmp")

    /** More environment for opencode and what it runs (Claude Code's paths). */
    var extraEnv: () -> Map<String, String> = { emptyMap() }
    var beforeStart: () -> Unit = {}

    /** A Linux-like home for opencode and anything it starts. */
    fun baseEnv(): Map<String, String> = mapOf(
        "HOME" to home.absolutePath,
        "XDG_CONFIG_HOME" to File(home, ".config").absolutePath,
        "XDG_DATA_HOME" to File(home, ".local/share").absolutePath,
        "XDG_STATE_HOME" to File(home, ".local/state").absolutePath,
        "XDG_CACHE_HOME" to File(home, ".cache").absolutePath,
        "TMPDIR" to tmp.absolutePath,
        "SHELL" to "/system/bin/sh",
        "PATH" to (System.getenv("PATH") ?: "/system/bin"),
    )

    suspend fun start(): Unit = withContext(Dispatchers.IO) {
        if (running) return@withContext
        val bin = File(context.applicationInfo.nativeLibraryDir, "libopencode.so")
        if (!bin.exists()) error(
            "opencode is missing from the app: $bin. This apk was packed without the engines; " +
                "run engine/build-engine.sh (and the other engine/build-*.sh) before assembling.",
        )
        port = ServerSocket(0).use { it.localPort }
        for (d in listOf(home, workspace, tmp, config)) d.mkdirs()
        beforeStart()
        val pb = ProcessBuilder(bin.absolutePath, "serve", "--hostname", "127.0.0.1", "--port", port.toString())
            .directory(workspace)
            .redirectErrorStream(true)
            .redirectOutput(log)
        pb.environment().putAll(baseEnv() + extraEnv() + ("OPENCODE_PASSWORD" to password))
        process = pb.start()
        // Ready once it answers; a first start sets up its database and takes a few seconds.
        val deadline = System.currentTimeMillis() + 60_000
        while (System.currentTimeMillis() < deadline) {
            if (!running) error("opencode stopped: " + log.readText().takeLast(1500))
            if (ping()) return@withContext
            delay(250)
        }
        error("opencode didn't start in a minute: " + log.readText().takeLast(1500))
    }

    private fun ping(): Boolean = runCatching {
        val c = URL("http://127.0.0.1:$port/api/info").openConnection() as HttpURLConnection
        c.connectTimeout = 500
        c.readTimeout = 2000
        c.setRequestProperty("Authorization", auth())
        try { c.responseCode == 200 } finally { c.disconnect() }
    }.getOrDefault(false)

    fun auth() = "Basic " + Base64.getEncoder().encodeToString("opencode:$password".toByteArray())

    fun stop() {
        process?.destroy()
        process = null
    }
}
