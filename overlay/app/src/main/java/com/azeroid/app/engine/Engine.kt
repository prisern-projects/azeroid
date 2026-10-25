package com.azeroid.app.engine

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.azeroid.app.AzeroidPrefs
import com.winlator.cmod.OnboardingActivity
import com.winlator.cmod.XServerDisplayActivity
import com.winlator.cmod.container.Container
import com.winlator.cmod.container.ContainerManager
import com.winlator.cmod.contentdialog.GraphicsDriverConfigDialog
import com.winlator.cmod.contents.ContentsManager
import com.winlator.cmod.core.DefaultVersion
import com.winlator.cmod.core.KeyValueSet
import com.winlator.cmod.core.OpenGLDriverDefaults
import com.winlator.cmod.core.ProtonPackageManager
import com.winlator.cmod.xenvironment.ImageFs
import com.winlator.cmod.xenvironment.ImageFsInstaller
import android.net.Uri
import com.winlator.cmod.contents.ContentProfile
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.io.File

/**
 * The only place Azeroid talks to the Winlator engine. Everything else in the
 * app works with plain Kotlin types, so engine bumps only ever touch this file.
 */
object Engine {
    private const val TAG = "Azeroid/Engine"
    const val CONTAINER_NAME = "Azeroid"

    /**
     * Wine runtime for the WoW environment (user-selectable). Proton 9 can't run current Battle.net: its Agent
     * rejects the client ("Failed Caller authorization due to signature"); Wine/Proton 10+ is fine.
     */
    fun runtime(context: Context): String = AzeroidPrefs(context).runtime.id
    private val offeredRuntimes = com.azeroid.app.Runtime.entries.map { it.id }.toSet()

    /**
     * x86 translator. The bundled FEX 2601 overflows Battle.net.exe's stack; 2610 is installed as an engine
     * content package (Windows DLLs + Unix-side libs), pinned to an exact commit and SHA-256.
     */
    const val FEX_VERSION = "2610"
    private const val FEX_URL =
        "https://raw.githubusercontent.com/StevenMXZ/Winlator-Contents/118607e86d60d24be885dc58d5cab74c84c5d8eb/FEXCore/2610.wcp"
    private const val FEX_SHA256 = "b3cff7e7a13d6d38a977ed569262ef2187b03da442dfb281179930545150fd61"

    /** Engine profile id of the bundled "Virtual Gamepad" on-screen layout (XInput buttons + sticks). */
    const val VIRTUAL_GAMEPAD_PROFILE_ID = 3

    // DXVK build with async pipeline compilation: far less shader-compile stutter in WoW.
    private const val DXVK_VERSION = "2.3.1-arm64ec-gplasync"

    fun isCoreInstalled(context: Context): Boolean {
        val fs = ImageFs.find(context)
        return fs.isValid && fs.version >= ImageFsInstaller.LATEST_VERSION
    }

    fun installCore(activity: AppCompatActivity, progress: (Int) -> Unit, done: (Boolean) -> Unit) {
        ImageFsInstaller.installFromAssetsSilently(activity, object : ImageFsInstaller.InstallationProgressListener {
            override fun onProgress(value: Int) = progress(value)
            override fun onFinished(success: Boolean) = done(success)
        })
    }

    fun isRuntimeInstalled(context: Context): Boolean =
        ProtonPackageManager.isInstalled(context, runtime(context)) && isFexInstalled(context)

    private fun isFexInstalled(context: Context): Boolean {
        val contents = ContentsManager(context).apply { syncContents() }
        return contents.getInstalledProfiles(ContentProfile.ContentType.CONTENT_TYPE_FEXCORE)
            .any { it.verName == FEX_VERSION }
    }

    /** Blocking. Downloads, verifies and installs FEX [FEX_VERSION] through the engine's content installer. */
    fun installFex(context: Context, progress: (Int) -> Unit): Boolean {
        if (isFexInstalled(context)) return true
        val file = File(context.cacheDir, "fexcore-$FEX_VERSION.wcp")
        try {
            val sha = MessageDigest.getInstance("SHA-256")
            OkHttpClient().newCall(Request.Builder().url(FEX_URL).build()).execute().use { r ->
                if (!r.isSuccessful) return false
                val body = r.body ?: return false
                val total = body.contentLength()
                body.byteStream().use { input ->
                    file.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n); sha.update(buf, 0, n); done += n
                            if (total > 0) progress((done * 100 / total).toInt())
                        }
                    }
                }
            }
            if (sha.digest().joinToString("") { "%02x".format(it) } != FEX_SHA256) {
                Log.e(TAG, "FEX package checksum mismatch")
                return false
            }
            val contents = ContentsManager(context).apply { syncContents() }
            var ok = false
            val done = CountDownLatch(1)
            val finish = object : ContentsManager.OnInstallFinishedCallback {
                override fun onFailed(reason: ContentsManager.InstallFailedReason, e: Exception?) {
                    ok = reason == ContentsManager.InstallFailedReason.ERROR_EXIST
                    if (!ok) Log.e(TAG, "FEX install failed: $reason", e)
                    done.countDown()
                }
                override fun onSucceed(profile: ContentProfile) { ok = true; done.countDown() }
            }
            contents.extraContentFile(Uri.fromFile(file), object : ContentsManager.OnInstallFinishedCallback {
                override fun onFailed(reason: ContentsManager.InstallFailedReason, e: Exception?) = finish.onFailed(reason, e)
                override fun onSucceed(profile: ContentProfile) = contents.finishInstallContent(profile, finish)
            })
            done.await(2, TimeUnit.MINUTES)
            return ok && isFexInstalled(context)
        } finally {
            file.delete()
        }
    }

    /** Blocking. Downloads (SHA-256 checked by the engine) and installs the selected runtime. */
    fun installRuntime(context: Context, progress: (Int) -> Unit): Boolean {
        val id = runtime(context)
        val pkg = ProtonPackageManager.getPackage(id) ?: return false
        val archive = File(context.cacheDir, pkg.fileName)
        try {
            if (!ProtonPackageManager.downloadPackage(pkg, archive) { p -> progress(p) }) return false
            return ProtonPackageManager.installPackage(context, id, archive)
        } finally {
            archive.delete()
        }
    }

    /** The WoW environment on the selected runtime. */
    fun findContainer(context: Context): Container? {
        val id = runtime(context)
        return ContainerManager(context).containers.firstOrNull { it.getName() == CONTAINER_NAME && it.getWineVersion() == id }
    }

    /** An Azeroid environment from an older release, on a runtime Azeroid no longer offers (Proton 9). */
    fun findLegacyContainer(context: Context): Container? =
        ContainerManager(context).containers.firstOrNull { it.getName() == CONTAINER_NAME && it.getWineVersion() !in offeredRuntimes }

    /** Blocking. Deletes a container and everything in it. */
    fun deleteContainer(context: Context, container: Container) {
        com.winlator.cmod.core.FileUtils.delete(container.getRootDir())
    }

    /** Creates the single WoW-tuned container. Calls back on the main thread. */
    fun createContainer(context: Context, onDone: (Container?) -> Unit) {
        val contents = ContentsManager(context)
        contents.syncContents()
        val manager = ContainerManager(context)

        val data = JSONObject().apply {
            put("name", CONTAINER_NAME)
            put("screenSize", Container.DEFAULT_SCREEN_SIZE)
            put("envVars", Container.DEFAULT_ENV_VARS)
            put("graphicsDriver", Container.DEFAULT_GRAPHICS_DRIVER)
            put("graphicsDriverConfig", Container.DEFAULT_GRAPHICSDRIVERCONFIG)
            put("rendererNative", false)
            put("rendererPresentMode", "fifo")
            put("dxwrapper", Container.DEFAULT_DXWRAPPER)
            put("dxwrapperConfig", dxvkConfig(Container.DEFAULT_DXWRAPPERCONFIG, 0))
            put("audioDriver", Container.DEFAULT_AUDIO_DRIVER)
            put("emulator", "FEXCore")
            put("wincomponents", Container.DEFAULT_WINCOMPONENTS)
            put("drives", Container.DEFAULT_DRIVES)
            put("box64Version", DefaultVersion.WOWBOX64)
            put("box64Preset", "COMPATIBILITY")
            put("fexcoreVersion", DefaultVersion.FEXCORE)
            put("fexcorePreset", "INTERMEDIATE")
            put("wineVersion", runtime(context))
        }
        OpenGLDriverDefaults.initialize(context, data)

        manager.createContainerAsync(data, contents) { container ->
            if (container != null) {
                // Opening the engine's own UI later must not re-run its first-time wizard.
                PreferenceManager.getDefaultSharedPreferences(context).edit()
                    .putBoolean(OnboardingActivity.PREF_ONBOARDING_COMPLETE, true)
                    .apply()
            } else {
                Log.e(TAG, "Container creation failed")
            }
            onDone(container)
        }
    }

    /** Host path of something inside the container's C: drive, e.g. "Program Files (x86)/Battle.net". */
    fun driveC(container: Container, relative: String = ""): File =
        File(container.getRootDir(), ".wine/drive_c/$relative")

    fun windowsUserDir(container: Container): File = driveC(container, "users/${ImageFs.USER}")

    data class LaunchSpec(
        val title: String,
        /** Windows path, e.g. C:\Program Files (x86)\Battle.net\Battle.net Launcher.exe */
        val windowsExe: String,
        val args: String = "",
        val screenSize: String,
        val fpsCap: Int,
        val showFps: Boolean,
        val onScreenController: Boolean,
        val fexPreset: String,
        val fexVersion: String = FEX_VERSION,
        /** "FEXCore" or "Box64": which translator runs 32-bit programs. Null = container default (FEX). */
        val emulator32: String? = null,
        val box64Preset: String? = null,
        val gpuDriverVersion: String?,
        val extraEnv: Map<String, String> = emptyMap(),
        /** A tap clicks exactly where the finger is (instead of laptop-touchpad style). */
        val directTouch: Boolean = true,
        /** Keep gamepads away from the Windows program entirely. */
        val gamepadToGame: Boolean = true,
    )

    fun launch(context: Context, container: Container, spec: LaunchSpec) {
        val desktop = writeLaunchFile(context, container, spec)

        // The engine reads the overlay choice from its own preferences when the
        // shortcut has no controls profile.
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putBoolean("show_touchscreen_controls_enabled", spec.onScreenController)
            .putInt("selected_profile_index", -1)
            // Wine/FEX output + crash traces go to Winlator/logs, which "Share log" collects.
            .putBoolean("enable_winlator_logs", true)
            .apply()

        val intent = Intent(context, XServerDisplayActivity::class.java)
            .putExtra("container_id", container.id)
            .putExtra("shortcut_path", desktop.absolutePath)
            .putExtra("shortcut_name", spec.title)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /** Opens the full engine UI (containers, drivers, controls editor) for troubleshooting. */
    fun openEngineTools(context: Context) {
        context.startActivity(
            Intent(context, com.winlator.cmod.MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun writeLaunchFile(context: Context, container: Container, spec: LaunchSpec): File {
        val dir = File(context.filesDir, "azeroid").apply { mkdirs() }
        val file = File(dir, "launch.desktop")

        // The engine un-escapes the Exec path once, so every backslash is doubled twice.
        val exec = "\"" + spec.windowsExe.replace("\\", "\\\\\\\\") + "\""
        val winePrefix = "/home/${ImageFs.USER}/.wine"

        val extras = linkedMapOf(
            "screenSize" to spec.screenSize,
            "dxwrapperConfig" to dxvkConfig(container.getDXWrapperConfig(), spec.fpsCap),
            "fexcorePreset" to spec.fexPreset,
            "fexcoreVersion" to spec.fexVersion,
            "envVars" to launchEnv(spec),
        )
        if (spec.args.isNotBlank()) extras["execArgs"] = spec.args
        spec.emulator32?.let { extras["emulator"] = it }
        spec.box64Preset?.let { extras["box64Preset"] = it }
        if (spec.directTouch) extras["simTouchScreen"] = "1"
        extras["disableXinput"] = if (spec.gamepadToGame) "0" else "1"
        if (spec.onScreenController) extras["controlsProfile"] = VIRTUAL_GAMEPAD_PROFILE_ID.toString()
        spec.gpuDriverVersion?.let { version ->
            val config = GraphicsDriverConfigDialog.parseGraphicsDriverConfig(container.getGraphicsDriverConfig())
            config["version"] = version
            extras["graphicsDriverConfig"] = GraphicsDriverConfigDialog.toGraphicsDriverConfig(config)
            extras["graphicsDriver"] = if (OpenGLDriverDefaults.isTurnipDriver(version)) "freedreno" else Container.DEFAULT_GRAPHICS_DRIVER
        }

        file.writeText(buildString {
            append("[Desktop Entry]\n")
            append("Name=${spec.title}\n")
            append("Exec=env WINEPREFIX=\"$winePrefix\" wine $exec\n")
            append("Type=Application\n")
            append("container_id:${container.id}\n")
            append("\n[Extra Data]\n")
            extras.forEach { (k, v) -> append("$k=$v\n") }
        })
        return file
    }

    private fun launchEnv(spec: LaunchSpec): String = (linkedMapOf(
        "DXVK_HUD" to if (spec.showFps) "fps" else "0",
        "DXVK_ASYNC" to "1",
        "DXVK_GPLASYNCCACHE" to "1",
        "WINEESYNC" to "1",
    ) + spec.extraEnv).entries.joinToString(" ") { "${it.key}=${it.value}" }

    /** Folder the engine writes its session logs to (Winlator/logs on shared storage). */
    fun logDir(context: Context): File = com.winlator.cmod.widget.LogView.getWinlatorLogFile(context).parentFile!!

    private fun dxvkConfig(base: String, fpsCap: Int): String {
        val config = KeyValueSet(base)
        config.put("version", DXVK_VERSION)
        config.put("async", "1")
        config.put("asyncCache", "1")
        config.put("framerate", fpsCap.toString())
        return config.toString()
    }
}
