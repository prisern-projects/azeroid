# Azeroid: project status and hard-won lessons

Read this first when picking the project up. It is the single summary of where things stand and why.

## Goal

Native WoW Classic on the owner's **Samsung Galaxy S26 Ultra** (SM-S948B, Snapdragon 8 Elite Gen 5 / SM8850,
Adreno 840, Android 17), used as a handheld with a **GameSir G8 Galileo**. Priorities: ease of use and
performance, and specifically **smoother than WeHavePhones**. Tuning targets this one device, not all of Android.

## Current state (0.2.2)

- The app is **WeHavePhones** (MIT, ashmerra) rebranded as Azeroid. It's built on Winlator LLM (FrontMage).
  Upstream commit: `UPSTREAM.txt`. Package `com.azeroid.app`, so it installs over 0.1.x.
- Runtime (from upstream): GE-Proton 10.0-34 ARM64EC (The412Banner), patched ntdll, FEX 2601-116,
  Battle.net flags `--disable-gpu --enable-features=NetworkServiceInProcess2` plus Azeroid's
  `--renderer-process-limit=1 --disable-site-isolation-trials --disable-gpu-shader-disk-cache` (0.2.2), Adreno 840 → system driver.
- WoW runs Blizzard's **native ARM64 client** (`WowClassic-ARM64.exe`), with no x86 translation for the game.
- Releases: bump `VERSION` and add a `CHANGELOG.md` section, then push to `main`. CI (`.github/workflows/build.yml`)
  builds, signs and publishes `v<VERSION>` with `Azeroid-<version>.apk`. The in-app updater
  (`app/src/wehavephones/java/com/wehavephones/mobile/updates/AppRelease.java`) installs it.
- Signing: `signing/azeroid-release.jks.enc` (AES-256, pbkdf2 600k). Its password is the repo secret
  `AZEROID_SIGNING_PASSWORD` (alias `azeroid`). The owner holds the password.
- `LegacyAzeroidCleanup` wipes 0.1.x engine data once on first start.

## Next work (in order)

1. ~~Owner confirms install, sign-in and WoW Classic download~~ (done on 0.2.2). Next: confirm WoW launches.
2. **Performance for the S26 Ultra**: frame-time logging (avg + 1% lows) first, then:
   GPU driver A/B (system vs newest Adreno 8xx Turnip), CPU affinity (2 prime cores for WoW main/render threads),
   DXVK async pipelines + persistent caches, sustained-performance/game mode, render resolution + sharpening,
   only drop low-RAM tweaks that measurably cost speed (RAM is tight, see below), Battle.net exits on game launch.
3. **Azeroid on-screen controller** replacing WeHavePhones' `VirtualGamepadController`: Xbox layout (twin sticks,
   D-pad, ABXY, bumpers, triggers), semi-transparent, thumb-sized for 6.9" landscape, *Auto* hides it when the
   G8 is connected, XInput so WoW gamepad mode and ConsolePort work.

## Lessons from 0.2.x

- The "Battle.net crashes while installing WoW" seen in WeHavePhones and in Azeroid 0.2.0 was Android's
  low-memory killer, not a crash. Proof: `ApplicationExitInfo` showed `LOW_MEMORY` at importance 100 (foreground).
  On the 12 GB S26 Ultra, Android itself uses about 8 GB, leaving about 3 GB plus swap. Wine logs show nothing
  (WINEDEBUG=-all, SIGKILL).
- Fixes: fewer CEF processes (flags above) and Samsung **RAM Plus** at maximum (Settings → Device care → Memory).
  With both, the install finished.
- Diagnostics now has "Last session end" (exit status, RAM and swap at exit) and "Android exit reasons"
  (`SessionEndRecorder`). Check these first for any "it closed back to the app" report.

- Perf baseline (0.2.3, Classic Era, 1024x576, double buffering): about 100 FPS average, 1% lows about 77 FPS,
  no spikes after loading. Display is 120 Hz, so the target is a locked 120.
- 0.2.5 (triple buffering + 1 GB shader cache), same spot: **118–120 FPS average (often locked 120), 1% lows
  83–90 FPS**. `game=` was 140–183 FPS in places, so the game outruns the 120 Hz panel there. Remaining
  stutter is occasional single 140–500 ms hitches (1–3 per 30 s window). Next: find their cause (shader
  compiles vs streaming vs CPU), CPU placement on the prime cores, GPU driver A/B, higher resolution.
- CPU placement A/B (0.2.6, Stormwind AH↔bank, 1280x720, warm windows only): on = avg 50.1 FPS,
  1% lows 38.7, 1 hitch over 50 ms in 5 windows. Off = avg 47.9, 1% lows 34.2, 6 hitches in 7 windows.
  Modest but consistent, so it stays on by default. Stormwind is game-bound (`game=` equals the screen) at about 48 FPS.
- **Don't request a display mode or frame rate** (`preferredDisplayModeId`, `Surface.setFrameRate`) on the
  S26 Ultra. In 0.2.4 it switched the panel resolution and the X-server output ended up small in the corner at
  60 FPS. Reverted in 0.2.5. 120 Hz comes from Samsung's Motion smoothness = Adaptive.

## Lessons from 0.1.x (Ludashi engine), so they aren't relearned

- Engine X server bug: `SysVSharedMemory.delete()` was unsynchronised, causing a ClassCastException crash (fixed in 0.1.3).
- Battle.net Agent rejects the client on Wine/Proton 9 ("Failed Caller authorization due to signature", HTTP 401).
  It needs Wine 10+. This was not a missing-root-certificate problem: Wine imported Android's 150 roots fine.
- Proton 11 builds can't render Battle.net's CEF browser (renderer IPC times out).
- Ludashi's Proton 10.0-5 froze Battle.net (loader-lock deadlock). GE-Proton 10.0-34 got further.
- FEX 2610 (and other builds) overflow the 64-bit native stack in Battle.net.exe's main thread under the Ludashi
  engine. Box64 avoided that but froze.
- Blizzard's installer: Box64 works, FEX crashes. Battle.net app: FEX recommended (Box64's browser TLS fails).
- The installer auto-starts Battle.net *without* the app's flags. Exit it and start Battle.net from the app.
- Plain (non-Proton) Wine crashes CEF's renderer: Proton's `simulate_writecopy` hack for `Battle.net.exe` is required.
- Cold Battle.net start is 85–155 s, and the first ~60 s can be a black window.

## Useful CI pieces kept in the repo

- `.github/workflows/native.yml`: DXVK v3.1.1 for plain ARM64 and FEX 1268a39 bridges, published as pre-releases.
- Pre-releases never reach the in-app updater.
