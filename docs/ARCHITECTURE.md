# Architecture

## The stack on the phone

```
 WowClassic.exe / Battle.net  (x86-64 Windows programs)
        │  DirectX 11
 DXVK 2.3.1 gplasync ──► Vulkan ──► Adreno driver (Qualcomm system driver or Mesa Turnip, via adrenotools)
        │  Win32 API
 Wine / Proton 10 (ARM64EC build): Windows APIs run as native ARM64 code
        │  x86-64 instructions in game code
 FEX-Emu (ARM64EC JIT): translates only the game's own x86 code
        │
 Android (Bionic) + an X server and renderer in the app, which draw into a SurfaceView
```

ARM64EC matters for performance. Wine's own DLLs (graphics, audio, input, file system) run as **native ARM64**,
and only WoW's own code goes through the x86 translator.

## Repo layout

Azeroid doesn't copy the engine source into this repo. CI builds on top of a **pinned upstream commit**:

```
engine.properties          pinned engine commit + SHA-256 of every downloaded binary
scripts/prepare-engine.sh  checkout engine → fetch deps/imagefs (hash-checked) → copy overlay → patch
scripts/patch_engine.py    small, asserted edits: launcher activity, branding, provider authorities, gradle hook
overlay/                   everything Azeroid adds, copied over the engine tree
  app/azeroid.gradle       applicationId com.azeroid.app, version from git tag, release signing
  app/src/main/java/com/azeroid/app/
    AzeroidActivity.kt     setup state machine + actions (state is always recomputed from disk)
    AzeroidPrefs.kt        user settings
    engine/Engine.kt       the ONLY file that calls engine (com.winlator.cmod) APIs
    wow/Wow.kt             Battle.net download/tuning, WoW detection, Config.wtf tuning
    update/                GitHub Releases self-updater (PackageInstaller)
    ui/AzeroidScreen.kt    Compose UI, controller-navigable
    Diagnostics.kt         share log / report bug
```

Upgrading the engine means bumping `ENGINE_COMMIT`. If upstream moved something we patch,
`patch_engine.py` fails loudly in CI instead of shipping a broken APK.

To build locally (Linux/macOS, Android SDK + NDK installed):

```sh
./scripts/prepare-engine.sh
cd engine && ./gradlew assembleRelease
```

## How PLAY works

1. `Wow.applyConfig` merges tuned keys into `WTF/Config.wtf` (D3D11, windowed-fullscreen, resolution of the
   preset, FPS cap, `GamePadEnable 1`). Other settings the player changed are left alone.
2. `Engine.launch` writes a `.desktop` launch file with per-launch overrides (screen size, DXVK config, FEX preset,
   GPU driver, on-screen controls profile, env vars) and starts the engine's `XServerDisplayActivity`.
3. When the game exits, the engine restarts the app's launcher activity, so you're back on Azeroid's home screen.

## Updates

`update/Updater.kt` asks `api.github.com/repos/<repo>/releases/latest`. If the tag is newer than the installed
`versionName`, it shows a banner. The APK is downloaded, checked against GitHub's published SHA-256 digest,
and committed through `PackageInstaller`. Android only accepts it if it's signed with the same key, so
nobody else can push an update to your phone.
