package com.azeroid.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import com.azeroid.app.engine.Engine
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.azeroid.app.update.Updater
import com.winlator.cmod.BuildConfig
import java.net.URLEncoder

/** Bug reports: device facts + the app's own recent log, shared as plain text. */
object Diagnostics {
    fun deviceSummary(context: Context): String = buildString {
        appendLine("Azeroid ${Updater.currentVersion(context)}")
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
        appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("SoC: ${if (Build.VERSION.SDK_INT >= 31) Build.SOC_MODEL else Build.HARDWARE}")
        val prefs = AzeroidPrefs(context)
        appendLine("Settings: graphics=${prefs.graphics} gpu=${prefs.gpuDriver} emulation=${prefs.emulation} " +
            "launch=${prefs.launchMode} overlay=${prefs.overlay} fpsCap=${prefs.fpsCap} bnet=${prefs.bnetTranslator}")
    }

    private fun recentLog(): String = runCatching {
        val p = ProcessBuilder("logcat", "-d", "-t", "4000", "-v", "time").redirectErrorStream(true).start()
        p.inputStream.bufferedReader().readText()
    }.getOrElse { "(log unavailable: ${it.message})" }.takeLast(300_000)

    /** The engine's own session logs (Wine/FEX output, crash traces), newest first. */
    private fun engineLogs(context: Context): String = runCatching {
        Engine.logDir(context).listFiles { f -> f.name.endsWith(".txt") }
            ?.sortedByDescending { it.lastModified() }
            ?.take(3)
            ?.joinToString("\n") { f -> "----- ${f.name} -----\n" + f.readText().takeLast(400_000) }
            ?: "(no engine logs yet)"
    }.getOrElse { "(engine logs unavailable: ${it.message})" }

    /** Battle.net's and its Agent's own log files from inside the Windows environment (newest first). */
    private fun battleNetLogs(context: Context): String = runCatching {
        val c = Engine.findContainer(context) ?: return "(no WoW environment yet)"
        val dirs = listOf(
            Engine.driveC(c, "ProgramData/Battle.net/Agent"),
            Engine.driveC(c, "ProgramData/Battle.net/Setup"),
            File(Engine.windowsUserDir(c), "AppData/Local/Battle.net/Logs"),
        )
        dirs.flatMap { d -> d.walkTopDown().maxDepth(3).filter { it.isFile && it.extension in setOf("log", "txt") }.toList() }
            .sortedByDescending { it.lastModified() }
            .take(6)
            .joinToString("\n") { f -> "----- ${f.path.substringAfter("drive_c/")} -----\n" + f.readText().takeLast(80_000) }
            .ifEmpty { "(no Battle.net logs found)" }
    }.getOrElse { "(Battle.net logs unavailable: ${it.message})" }

    /** Writes one report file to Azeroid/logs on the phone's storage and opens the share sheet for it. */
    fun shareLogs(context: Context) {
        val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        val dir = File(Environment.getExternalStorageDirectory(), "Azeroid/logs").apply { mkdirs() }
        val report = File(dir, "azeroid-log-$stamp.txt")
        report.writeText(
            deviceSummary(context) +
                "\n===== Battle.net logs =====\n" + battleNetLogs(context) +
                "\n===== engine sessions =====\n" + engineLogs(context) +
                "\n===== app log =====\n" + recentLog()
        )
        val uri = FileProvider.getUriForFile(context, context.packageName + ".tileprovider", report)
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, "Azeroid log")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(
            Intent.createChooser(send, "Share Azeroid log (also saved in Azeroid/logs)")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        )
    }

    fun reportBug(context: Context) {
        val body = "**What happened?**\n\n\n**Steps to reproduce**\n1. \n\n**Device**\n```\n${deviceSummary(context)}```\n"
        val url = "https://github.com/${BuildConfig.AZEROID_REPO}/issues/new?labels=bug&title=" +
            URLEncoder.encode("[Bug] ", "UTF-8") + "&body=" + URLEncoder.encode(body, "UTF-8")
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
