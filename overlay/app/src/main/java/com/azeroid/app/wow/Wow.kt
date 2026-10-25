package com.azeroid.app.wow

import com.azeroid.app.AzeroidPrefs
import com.azeroid.app.engine.Engine
import com.winlator.cmod.container.Container
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File

/** One installed WoW Classic game, e.g. Classic Era in "World of Warcraft/_classic_era_". */
data class WowInstall(val label: String, val folder: File, val exe: File, val windowsExe: String)

object Wow {
    private const val BNET_INSTALLER_URL =
        "https://downloader.battle.net/download/getInstallerForGame?os=win&gameProgram=BATTLENET_APP&version=Live"
    private const val BNET_DIR = "Program Files (x86)/Battle.net"
    private const val BNET_LAUNCHER = "Battle.net Launcher.exe"

    private val flavors = linkedMapOf(
        "_classic_era_" to "Classic Era / Hardcore / Season of Discovery",
        "_anniversary_" to "Classic Anniversary",
        "_classic_" to "Classic (progression)",
    )

    // ---- Battle.net -------------------------------------------------------------------------

    fun isBattleNetInstalled(container: Container): Boolean =
        File(Engine.driveC(container, BNET_DIR), BNET_LAUNCHER).isFile

    const val BATTLE_NET_EXE = "C:\\Program Files (x86)\\Battle.net\\Battle.net Launcher.exe"

    /** Battle.net itself, started directly so it gets our Chromium flags. */
    const val BATTLE_NET_APP_EXE = "C:\\Program Files (x86)\\Battle.net\\Battle.net.exe"

    /**
     * No GPU process (it can't work through Wine's wow64 graphics path), and Chromium's network service inside the
     * main process: as a separate utility process it crashes under Wine on Android and the page never loads.
     */
    const val BATTLE_NET_ARGS = "--disable-gpu --enable-features=NetworkServiceInProcess2"

    fun battleNetAppExists(container: Container): Boolean = File(Engine.driveC(container, BNET_DIR), "Battle.net.exe").isFile

    fun installerFile(container: Container): File =
        File(Engine.windowsUserDir(container), "Downloads/Battle.net-Setup.exe")

    fun installerWindowsPath(): String =
        "C:\\users\\${com.winlator.cmod.xenvironment.ImageFs.USER}\\Downloads\\Battle.net-Setup.exe"

    /** Blocking. Downloads the official installer straight from Blizzard. */
    fun downloadInstaller(container: Container, progress: (Float) -> Unit) {
        val target = installerFile(container)
        target.parentFile?.mkdirs()
        val tmp = File(target.path + ".part")
        val request = Request.Builder().url(BNET_INSTALLER_URL).build()
        OkHttpClient().newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Blizzard download failed (HTTP ${response.code})" }
            val body = response.body ?: error("Empty download")
            val total = body.contentLength().takeIf { it > 0 } ?: -1L
            body.byteStream().use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0) progress(done.toFloat() / total)
                    }
                }
            }
        }
        check(tmp.length() > 1_000_000) { "Downloaded installer looks incomplete" }
        tmp.renameTo(target)
    }

    /**
     * Battle.net's Chromium UI renders through software paths under Wine;
     * hardware acceleration and streaming just cause black windows and crashes.
     */
    fun tuneBattleNet(container: Container) {
        val file = File(Engine.windowsUserDir(container), "AppData/Roaming/Battle.net/Battle.net.config")
        file.parentFile?.mkdirs()
        val json = runCatching { JSONObject(file.readText()) }.getOrElse { JSONObject() }
        val client = json.optJSONObject("Client") ?: JSONObject().also { json.put("Client", it) }
        client.put("HardwareAcceleration", "false")
        val streaming = client.optJSONObject("Streaming") ?: JSONObject().also { client.put("Streaming", it) }
        streaming.put("StreamingEnabled", "false")
        // "Exit Battle.net completely" once a game starts: frees ~1.7 GB of RAM for WoW.
        client.put("GameLaunchWindowBehavior", "2")
        file.writeText(json.toString(2))
    }

    // ---- Migration --------------------------------------------------------------------------

    /** Moves an existing WoW install from an old environment into a new one (same storage, so instant). */
    fun moveGame(from: Container, to: Container): Boolean {
        val src = Engine.driveC(from, "Program Files (x86)/World of Warcraft")
        if (!src.isDirectory) return true
        val dst = Engine.driveC(to, "Program Files (x86)/World of Warcraft")
        dst.parentFile?.mkdirs()
        return !dst.exists() && src.renameTo(dst)
    }

    // ---- Game detection ---------------------------------------------------------------------

    fun findInstalls(container: Container): List<WowInstall> {
        val roots = listOf("Program Files (x86)", "Program Files", "Games").flatMap { base ->
            listOf(Engine.driveC(container, "$base/World of Warcraft"))
        }
        val found = mutableListOf<WowInstall>()
        for (root in roots) {
            for ((folder, label) in flavors) {
                val dir = File(root, folder)
                val exe = dir.listFiles()
                    ?.firstOrNull { it.isFile && it.name.startsWith("Wow", ignoreCase = true) && it.name.endsWith(".exe", ignoreCase = true) &&
                        !it.name.contains("Launcher", ignoreCase = true) }
                    ?: continue
                found += WowInstall(label, dir, exe, toWindowsPath(container, exe))
            }
        }
        return found
    }

    private fun toWindowsPath(container: Container, file: File): String {
        val driveC = Engine.driveC(container).canonicalPath
        val rel = file.canonicalPath.removePrefix(driveC).trimStart('/')
        return "C:\\" + rel.replace('/', '\\')
    }

    // ---- Game config ------------------------------------------------------------------------

    /**
     * Merges Azeroid's tuned values into WTF/Config.wtf. Keys the player changed
     * in-game that we don't manage are kept as they are.
     */
    fun applyConfig(install: WowInstall, prefs: AzeroidPrefs, controller: Boolean) {
        val preset = prefs.graphics
        val (w, h) = preset.screenSize.split("x")
        val managed = linkedMapOf(
            "gxApi" to "D3D11",
            "gxWindow" to "1",
            "gxMaximize" to "1",
            "gxWindowedResolution" to "${w}x$h",
            "gxFullscreenResolution" to "${w}x$h",
            "hwDetect" to "0",
            "graphicsQuality" to preset.wowQuality.toString(),
            "renderScale" to preset.renderScale,
            "maxFPS" to prefs.fpsCap.toString(),
            "maxFPSBk" to "10",
            "vsync" to "0",
            "ffxAntiAliasingMode" to "0",
            "MSAAQuality" to "0",
            "SSAO" to "0",
            "shadowMode" to if (preset == com.azeroid.app.GraphicsPreset.QUALITY) "1" else "0",
            "GamePadEnable" to if (controller) "1" else "0",
        )

        val wtf = File(install.folder, "WTF/Config.wtf")
        wtf.parentFile?.mkdirs()
        val lines = if (wtf.isFile) wtf.readLines().toMutableList() else mutableListOf()
        val keyPattern = Regex("""^\s*SET\s+(\S+)\s+""", RegexOption.IGNORE_CASE)
        val seen = mutableSetOf<String>()
        for (i in lines.indices) {
            val key = keyPattern.find(lines[i])?.groupValues?.get(1) ?: continue
            val match = managed.keys.firstOrNull { it.equals(key, ignoreCase = true) } ?: continue
            lines[i] = "SET $match \"${managed[match]}\""
            seen += match
        }
        managed.filterKeys { it !in seen }.forEach { (k, v) -> lines += "SET $k \"$v\"" }
        wtf.writeText(lines.joinToString("\r\n", postfix = "\r\n"))
    }
}
