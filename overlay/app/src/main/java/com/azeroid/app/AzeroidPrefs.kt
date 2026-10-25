package com.azeroid.app

import android.content.Context
import android.content.SharedPreferences

enum class GraphicsPreset(val label: String, val screenSize: String, val wowQuality: Int, val renderScale: String) {
    PERFORMANCE("Performance", "1600x720", 2, "0.85"),
    BALANCED("Balanced", "1920x864", 4, "1.0"),
    QUALITY("Quality", "2400x1080", 6, "1.0"),
}

enum class ControllerOverlay(val label: String) {
    AUTO("Auto"),   // shown only when no physical controller is connected
    ON("On"),
    OFF("Off"),
}

enum class GpuDriver(val label: String, val engineVersion: String?) {
    AUTO("Auto", null),
    SYSTEM("Samsung/Qualcomm", "System"),
    TURNIP("Turnip (Mesa)", "turnip26.2.0"),
    QUALCOMM_V863("Qualcomm v863", "v863"),
}

enum class EmulationMode(val label: String, val fexPreset: String) {
    STABLE("Stable", "COMPATIBILITY"),
    FAST("Fast", "INTERMEDIATE"),
}

/**
 * How Battle.net's 32-bit programs (Battle.net.exe, Agent.exe) are translated to ARM.
 * Battle.net is picky under emulation, so this is separate from the game's setting.
 */
enum class BattleNetTranslator(val label: String, val emulator: String, val fexPreset: String, val box64Preset: String) {
    FEX_SAFE("FEX safe", "FEXCore", "COMPATIBILITY", "COMPATIBILITY"),
    FEX_FAST("FEX fast", "FEXCore", "INTERMEDIATE", "COMPATIBILITY"),
    BOX64("Box64", "Box64", "COMPATIBILITY", "COMPATIBILITY"),
}

enum class LaunchMode(val label: String) {
    BATTLE_NET("Through Battle.net"),
    DIRECT("Start WoW directly"),
}

/** Every user-facing setting, backed by one SharedPreferences file. */
class AzeroidPrefs(context: Context) {
    private val sp: SharedPreferences = context.getSharedPreferences("azeroid", Context.MODE_PRIVATE)

    var graphics: GraphicsPreset
        get() = enumOr(sp.getString("graphics", null), GraphicsPreset.BALANCED)
        set(v) = sp.edit().putString("graphics", v.name).apply()

    var overlay: ControllerOverlay
        get() = enumOr(sp.getString("overlay", null), ControllerOverlay.AUTO)
        set(v) = sp.edit().putString("overlay", v.name).apply()

    var gpuDriver: GpuDriver
        get() = enumOr(sp.getString("gpu", null), GpuDriver.AUTO)
        set(v) = sp.edit().putString("gpu", v.name).apply()

    var emulation: EmulationMode
        get() = enumOr(sp.getString("emulation", null), EmulationMode.STABLE)
        set(v) = sp.edit().putString("emulation", v.name).apply()

    var launchMode: LaunchMode
        get() = enumOr(sp.getString("launch", null), LaunchMode.BATTLE_NET)
        set(v) = sp.edit().putString("launch", v.name).apply()

    var bnetTranslator: BattleNetTranslator
        // Key renamed in 0.1.8 so everyone moves to the new default (Box64 breaks the sign-in page's TLS).
        get() = enumOr(sp.getString("bnetTranslator2", null), BattleNetTranslator.FEX_SAFE)
        set(v) = sp.edit().putString("bnetTranslator2", v.name).apply()

    var showFps: Boolean
        get() = sp.getBoolean("fps", false)
        set(v) = sp.edit().putBoolean("fps", v).apply()

    var fpsCap: Int
        get() = sp.getInt("fpsCap", 60)
        set(v) = sp.edit().putInt("fpsCap", v).apply()

    var autoUpdate: Boolean
        get() = sp.getBoolean("autoUpdate", true)
        set(v) = sp.edit().putBoolean("autoUpdate", v).apply()

    /** Folder name of the chosen WoW flavor, e.g. "_classic_era_". Empty = first found. */
    var wowFlavor: String
        get() = sp.getString("flavor", "") ?: ""
        set(v) = sp.edit().putString("flavor", v).apply()

    private inline fun <reified T : Enum<T>> enumOr(name: String?, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: fallback
}
