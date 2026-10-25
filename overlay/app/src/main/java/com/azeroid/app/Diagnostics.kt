package com.azeroid.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
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
            "launch=${prefs.launchMode} overlay=${prefs.overlay} fpsCap=${prefs.fpsCap}")
    }

    private fun recentLog(): String = runCatching {
        val p = ProcessBuilder("logcat", "-d", "-t", "4000", "-v", "time").redirectErrorStream(true).start()
        p.inputStream.bufferedReader().readText()
    }.getOrElse { "(log unavailable: ${it.message})" }.takeLast(200_000)

    fun shareLogs(context: Context) {
        val text = deviceSummary(context) + "\n----- log -----\n" + recentLog()
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, "Azeroid log")
            .putExtra(Intent.EXTRA_TEXT, text)
        context.startActivity(Intent.createChooser(send, "Share Azeroid log").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun reportBug(context: Context) {
        val body = "**What happened?**\n\n\n**Steps to reproduce**\n1. \n\n**Device**\n```\n${deviceSummary(context)}```\n"
        val url = "https://github.com/${BuildConfig.AZEROID_REPO}/issues/new?labels=bug&title=" +
            URLEncoder.encode("[Bug] ", "UTF-8") + "&body=" + URLEncoder.encode(body, "UTF-8")
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
