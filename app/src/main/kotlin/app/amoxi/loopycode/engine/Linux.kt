package app.amoxi.loopycode.engine

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Alpine Linux for the agent: its minirootfs, downloaded from Alpine, run through proot
 * (lib/libproot.so). Once it is in, opencode's SHELL is lib/liblinux.so and every command
 * the model runs happens there, with apk to install whatever it needs.
 */
class Linux(private val context: Context, private val engine: Engine) {
    private val dir get() = File(context.filesDir, "linux")
    val rootfs get() = File(dir, "rootfs")
    private val libs get() = context.applicationInfo.nativeLibraryDir

    val installed get() = File(rootfs, "etc/alpine-release").exists()
    val version get() = File(rootfs, "etc/alpine-release").takeIf { it.exists() }?.readText()?.trim()

    /** opencode runs its commands through SHELL, so pointing it here moves them all into Alpine. */
    fun env(): Map<String, String> {
        if (!installed) return emptyMap()
        val tmp = File(context.cacheDir, "proot").apply { mkdirs() }
        return mapOf(
            "SHELL" to "$libs/liblinux.so",
            "MARGY_PROOT" to "$libs/libproot.so",
            "MARGY_PROOT_LOADER" to "$libs/libprootloader.so",
            "MARGY_PROOT_TMP" to tmp.absolutePath,
            "MARGY_ROOTFS" to rootfs.absolutePath,
            "MARGY_FILES" to context.filesDir.absolutePath,
            "MARGY_CACHE" to context.cacheDir.absolutePath,
        )
    }

    /** Alpine has no idea of Android's DNS; it gets the phone's servers, as Claude Code does. */
    fun writeResolv(servers: String) {
        if (!installed) return
        runCatching { File(rootfs, "etc/resolv.conf").apply { delete() }.writeText(servers) }
    }

    /** Downloads the newest minirootfs of the pinned Alpine branch and unpacks it with Android's own tar. */
    suspend fun install(progress: (Long, Long) -> Unit) = withContext(Dispatchers.IO) {
        val yaml = URL("$BASE/latest-releases.yaml").openStream().bufferedReader().use { it.readText() }
        val block = yaml.split("\n-").firstOrNull { it.contains("flavor: alpine-minirootfs") }
            ?: error("Alpine: no minirootfs in latest-releases.yaml")
        fun field(name: String) = Regex("""(?m)^\s*$name:\s*(\S+)""").find(block)?.groupValues?.get(1)
            ?: error("Alpine: no $name")
        val file = field("file")
        val sha = field("sha256")
        dir.mkdirs()
        val archive = File(dir, "rootfs.tar.gz")
        val c = URL("$BASE/$file").openConnection() as HttpURLConnection
        c.connectTimeout = 20_000
        c.readTimeout = 60_000
        val digest = MessageDigest.getInstance("SHA-256")
        try {
            if (c.responseCode != 200) error("Alpine: HTTP ${c.responseCode}")
            val total = c.contentLengthLong
            var done = 0L
            c.inputStream.use { input ->
                archive.outputStream().use { out ->
                    val buf = ByteArray(1 shl 16)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        digest.update(buf, 0, n)
                        done += n
                        progress(done, total)
                    }
                }
            }
        } finally {
            c.disconnect()
        }
        if (digest.digest().joinToString("") { "%02x".format(it) } != sha) {
            archive.delete()
            error("Alpine: the download is damaged (sha256)")
        }
        val fresh = File(dir, "rootfs.new").apply { wipe(this); mkdirs() }
        val p = ProcessBuilder("/system/bin/tar", "xzf", archive.absolutePath, "-C", fresh.absolutePath)
            .redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        if (p.waitFor() != 0 || !File(fresh, "etc/alpine-release").exists()) error("tar: $out")
        archive.delete()
        wipe(rootfs)
        fresh.renameTo(rootfs)
        File(rootfs, "root").mkdirs()
    }

    /** Runs one command inside, the way opencode will, and returns what it printed. */
    suspend fun run(command: String, servers: String): String = withContext(Dispatchers.IO) {
        writeResolv(servers)
        val pb = ProcessBuilder("$libs/liblinux.so", "-c", command).directory(engine.workspace).redirectErrorStream(true)
        pb.environment().putAll(engine.baseEnv() + env())
        val p = pb.start()
        p.outputStream.close()
        val text = p.inputStream.bufferedReader().readText()
        p.waitFor()
        text.trim()
    }

    fun remove() {
        wipe(dir)
    }

    /**
     * Deletes a tree without following symlinks. The rootfs is full of them, and one that
     * points at /storage must not take the person's own files along.
     */
    private fun wipe(root: File) {
        val path = root.toPath()
        if (!java.nio.file.Files.exists(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)) return
        java.nio.file.Files.walkFileTree(path, object : java.nio.file.SimpleFileVisitor<java.nio.file.Path>() {
            override fun visitFile(file: java.nio.file.Path, attrs: java.nio.file.attribute.BasicFileAttributes) =
                java.nio.file.FileVisitResult.CONTINUE.also { java.nio.file.Files.deleteIfExists(file) }
            override fun visitFileFailed(file: java.nio.file.Path, exc: java.io.IOException) =
                java.nio.file.FileVisitResult.CONTINUE.also { runCatching { java.nio.file.Files.deleteIfExists(file) } }
            override fun postVisitDirectory(dir: java.nio.file.Path, exc: java.io.IOException?) =
                java.nio.file.FileVisitResult.CONTINUE.also { runCatching { java.nio.file.Files.deleteIfExists(dir) } }
        })
    }

    companion object {
        private const val BASE = "https://dl-cdn.alpinelinux.org/alpine/v3.22/releases/aarch64"
    }
}
