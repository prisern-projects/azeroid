package com.azeroid.app.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.winlator.cmod.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

data class Release(
    val version: String,
    val notes: String,
    val apkUrl: String,
    val apkSize: Long,
    /** "sha256:<hex>" as published by GitHub for the asset, when available. */
    val digest: String?,
    val pageUrl: String,
)

/**
 * Self-updates from this project's GitHub Releases. Each release carries one
 * APK signed with the same key, so Android installs it over the current app
 * and keeps all data (engine, container, WoW install).
 */
object Updater {
    private val http = OkHttpClient()
    private val repo get() = BuildConfig.AZEROID_REPO

    fun currentVersion(context: Context): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.0.0"

    /** Blocking. Returns the newest release if it's newer than what's installed. */
    fun checkForUpdate(context: Context): Release? {
        val request = Request.Builder()
            .url("https://api.github.com/repos/$repo/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .build()
        val json = http.newCall(request).execute().use { r ->
            if (!r.isSuccessful) return null
            JSONObject(r.body?.string() ?: return null)
        }
        val version = json.getString("tag_name").removePrefix("v")
        if (!isNewer(version, currentVersion(context))) return null
        val assets = json.getJSONArray("assets")
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            if (a.getString("name").endsWith(".apk")) {
                return Release(
                    version = version,
                    notes = json.optString("body", ""),
                    apkUrl = a.getString("browser_download_url"),
                    apkSize = a.optLong("size", -1),
                    digest = a.optString("digest", "").ifBlank { null },
                    pageUrl = json.optString("html_url", ""),
                )
            }
        }
        return null
    }

    fun isNewer(remote: String, local: String): Boolean {
        fun parts(v: String) = v.split('.', '-').map { p -> p.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }
        val r = parts(remote)
        val l = parts(local)
        for (i in 0 until maxOf(r.size, l.size, 3)) {
            val a = r.getOrElse(i) { 0 }
            val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    /** True if Android lets Azeroid install updates (one-time "Install unknown apps" toggle). */
    fun canInstall(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /** Blocking. Downloads, verifies and hands the APK to Android's installer. */
    fun downloadAndInstall(context: Context, release: Release, progress: (Float) -> Unit) {
        val apk = File(context.cacheDir, "update.apk")
        val sha = MessageDigest.getInstance("SHA-256")
        http.newCall(Request.Builder().url(release.apkUrl).build()).execute().use { r ->
            check(r.isSuccessful) { "Download failed (HTTP ${r.code})" }
            val body = r.body ?: error("Empty download")
            val total = body.contentLength().takeIf { it > 0 } ?: release.apkSize
            body.byteStream().use { input ->
                apk.outputStream().use { out ->
                    val buf = ByteArray(256 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        sha.update(buf, 0, n)
                        done += n
                        if (total > 0) progress(done.toFloat() / total)
                    }
                }
            }
        }
        release.digest?.takeIf { it.startsWith("sha256:") }?.let { expected ->
            val actual = sha.digest().joinToString("") { "%02x".format(it) }
            check(expected.removePrefix("sha256:").equals(actual, ignoreCase = true)) {
                "Update file is corrupted (checksum mismatch). Try again."
            }
        }
        install(context, apk)
    }

    private fun install(context: Context, apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
            if (Build.VERSION.SDK_INT >= 31) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            apk.inputStream().use { input ->
                session.openWrite("azeroid.apk", 0, apk.length()).use { out ->
                    input.copyTo(out, 256 * 1024)
                    session.fsync(out)
                }
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val callback = PendingIntent.getBroadcast(
                context, sessionId, Intent(context, InstallResultReceiver::class.java), flags
            )
            session.commit(callback.intentSender)
        }
    }
}
