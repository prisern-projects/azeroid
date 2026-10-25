package com.azeroid.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.azeroid.app.engine.Engine
import com.azeroid.app.ui.AzeroidScreen
import com.azeroid.app.update.Release
import com.azeroid.app.update.Updater
import com.azeroid.app.wow.Wow
import com.azeroid.app.wow.WowInstall
import com.winlator.cmod.container.Container
import java.util.concurrent.Executors

enum class Step { PERMISSIONS, ENGINE, CONTAINER, BATTLE_NET, WOW, READY }

/** What the screen shows; recomputed from disk every time the app comes to the front. */
data class UiState(
    val step: Step = Step.PERMISSIONS,
    val installs: List<WowInstall> = emptyList(),
    val selected: WowInstall? = null,
    val busy: String? = null,
    /** 0..1, or null for an indeterminate spinner. */
    val progress: Float? = null,
    val error: String? = null,
    val update: Release? = null,
    val updateChecked: Boolean = false,
    val controllerConnected: Boolean = false,
    val version: String = "",
    /** Bumped whenever a setting changes so screens that read prefs recompose. */
    val settingsRevision: Int = 0,
)

class AzeroidActivity : AppCompatActivity() {
    private val io = Executors.newSingleThreadExecutor()
    lateinit var prefs: AzeroidPrefs
        private set
    var state by mutableStateOf(UiState())
        private set
    private var container: Container? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = AzeroidPrefs(this)
        state = state.copy(version = Updater.currentVersion(this))
        setContent { AzeroidScreen(this) }
        if (prefs.autoUpdate) checkForUpdate(silent = true)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    // A/B on the controller drive the menus like a TV remote: A = select, B = back.
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val mapped = when (event.keyCode) {
            KeyEvent.KEYCODE_BUTTON_A -> KeyEvent.KEYCODE_DPAD_CENTER
            KeyEvent.KEYCODE_BUTTON_B -> KeyEvent.KEYCODE_BACK
            else -> return super.dispatchKeyEvent(event)
        }
        return super.dispatchKeyEvent(KeyEvent(event.downTime, event.eventTime, event.action, mapped, event.repeatCount))
    }

    fun refresh() {
        if (state.busy != null) return
        val step = when {
            !hasStorageAccess() -> Step.PERMISSIONS
            !Engine.isCoreInstalled(this) -> Step.ENGINE
            else -> {
                container = Engine.findContainer(this)
                val c = container
                when {
                    c == null -> Step.CONTAINER
                    !Wow.isBattleNetInstalled(c) -> Step.BATTLE_NET
                    Wow.findInstalls(c).isEmpty() -> Step.WOW
                    else -> Step.READY
                }
            }
        }
        val installs = container?.takeIf { step >= Step.WOW }?.let(Wow::findInstalls).orEmpty()
        val selected = installs.firstOrNull { it.folder.name == prefs.wowFlavor } ?: installs.firstOrNull()
        state = state.copy(step = step, installs = installs, selected = selected, controllerConnected = hasGamepad())
    }

    // ---- Setup actions ----------------------------------------------------------------------

    fun primaryAction() {
        when (state.step) {
            Step.PERMISSIONS -> requestPermissions()
            Step.ENGINE -> installEngine()
            Step.CONTAINER -> createContainer()
            Step.BATTLE_NET -> installBattleNet()
            Step.WOW -> openBattleNet()
            Step.READY -> play()
        }
    }

    private fun hasStorageAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    private fun requestPermissions() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        if (Build.VERSION.SDK_INT >= 30) {
            startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName")))
        } else {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE), 2
            )
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refresh()
    }

    private fun installEngine() {
        busy("Installing the game engine… (one time, about a minute)", 0f)
        Engine.installCore(this,
            progress = { p -> state = state.copy(progress = p / 100f) },
            done = { ok ->
                done(if (ok) null else "Engine install failed. Make sure you have ~3 GB free, then try again.")
            })
    }

    private fun createContainer() {
        busy("Creating your WoW environment…", null)
        Engine.createContainer(this) { c ->
            container = c
            done(if (c != null) null else "Could not create the WoW environment. Try again, or share the log.")
        }
    }

    private fun installBattleNet() {
        val c = container ?: return refresh()
        busy("Downloading Battle.net from Blizzard…", 0f)
        background({
            Wow.downloadInstaller(c) { p -> runOnUiThread { state = state.copy(progress = p) } }
            Wow.tuneBattleNet(c)
        }) {
            launch(c, "Battle.net Setup", Wow.installerWindowsPath())
        }
    }

    fun openBattleNet() {
        val c = container ?: return refresh()
        background({ Wow.tuneBattleNet(c) }) {
            launch(c, "Battle.net", Wow.BATTLE_NET_EXE)
        }
    }

    fun selectInstall(install: WowInstall) {
        prefs.wowFlavor = install.folder.name
        state = state.copy(selected = install)
    }

    fun play() {
        val c = container ?: return refresh()
        val install = state.selected ?: return refresh()
        background({
            Wow.applyConfig(install, prefs, controller = true)
            Wow.tuneBattleNet(c)
        }) {
            when (prefs.launchMode) {
                LaunchMode.BATTLE_NET -> launch(c, "World of Warcraft", Wow.BATTLE_NET_EXE)
                LaunchMode.DIRECT -> launch(c, "World of Warcraft", install.windowsExe)
            }
        }
    }

    private fun launch(c: Container, title: String, windowsExe: String) {
        val overlay = when (prefs.overlay) {
            ControllerOverlay.AUTO -> !hasGamepad()
            ControllerOverlay.ON -> true
            ControllerOverlay.OFF -> false
        }
        Engine.launch(this, c, Engine.LaunchSpec(
            title = title,
            windowsExe = windowsExe,
            screenSize = prefs.graphics.screenSize,
            fpsCap = prefs.fpsCap,
            showFps = prefs.showFps,
            onScreenController = overlay,
            fexPreset = prefs.emulation.fexPreset,
            gpuDriverVersion = prefs.gpuDriver.engineVersion,
        ))
    }

    fun openEngineTools() = Engine.openEngineTools(this)

    // ---- Updates ----------------------------------------------------------------------------

    fun checkForUpdate(silent: Boolean = false) {
        io.execute {
            val release = runCatching { Updater.checkForUpdate(this) }
                .onFailure { Log.w(TAG, "Update check failed", it) }
                .getOrNull()
            runOnUiThread {
                state = state.copy(update = release, updateChecked = true)
                if (!silent && release == null) toast("You're on the latest version.")
            }
        }
    }

    fun installUpdate() {
        val release = state.update ?: return
        if (!Updater.canInstall(this)) {
            toast("Allow Azeroid to install updates, then tap Update again.")
            Updater.openInstallPermissionSettings(this)
            return
        }
        busy("Downloading Azeroid ${release.version}…", 0f)
        background({
            Updater.downloadAndInstall(this, release) { p -> runOnUiThread { state = state.copy(progress = p) } }
        }) {}
    }

    // ---- Helpers ----------------------------------------------------------------------------

    fun dismissError() {
        state = state.copy(error = null)
    }

    fun settingsChanged() {
        state = state.copy(settingsRevision = state.settingsRevision + 1)
        refresh()
    }

    private fun hasGamepad(): Boolean = InputDevice.getDeviceIds().any { id ->
        val d = InputDevice.getDevice(id) ?: return@any false
        !d.isVirtual && (d.sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
            d.sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK)
    }

    private fun busy(text: String, progress: Float?) {
        state = state.copy(busy = text, progress = progress, error = null)
    }

    private fun done(error: String?) {
        state = state.copy(busy = null, progress = null, error = error)
        refresh()
    }

    /** Runs [work] off the main thread, then [then] on it; failures become an on-screen error. */
    private fun background(work: () -> Unit, then: () -> Unit) {
        io.execute {
            val result = runCatching(work)
            runOnUiThread {
                result.exceptionOrNull()?.let { Log.e(TAG, "Action failed", it) }
                done(result.exceptionOrNull()?.let { it.message ?: it.javaClass.simpleName })
                if (result.isSuccess) then()
            }
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    companion object {
        private const val TAG = "Azeroid"
    }
}
