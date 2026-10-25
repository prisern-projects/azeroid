# Azeroid: project status and hard-won lessons

Read this first when picking the project up. It is the single summary of where things stand and why.

## Goal

Native WoW Classic on the owner's **Samsung Galaxy S26 Ultra** (SM-S948B, Snapdragon 8 Elite Gen 5 / SM8850,
Adreno 840, Android 17), used as a handheld with a **GameSir G8 Galileo**. Priorities: ease of use and
performance, and specifically **smoother than WeHavePhones**. Tuning targets this one device, not all of Android.

## Current state (0.2.0)

- The app is **WeHavePhones** (MIT, ashmerra) rebranded as Azeroid. It's built on Winlator LLM (FrontMage).
  Upstream commit: `UPSTREAM.txt`. Package `com.azeroid.app`, so it installs over 0.1.x.
- Runtime (from upstream): GE-Proton 10.0-34 ARM64EC (The412Banner), patched ntdll, FEX 2601-116,
  Battle.net flags `--disable-gpu --enable-features=NetworkServiceInProcess2`, Adreno 840 → system driver.
- WoW runs Blizzard's **native ARM64 client** (`WowClassic-ARM64.exe`), with no x86 translation for the game.
- Releases: bump `VERSION` and add a `CHANGELOG.md` section, then push to `main`. CI (`.github/workflows/build.yml`)
  builds, signs and publishes `v<VERSION>` with `Azeroid-<version>.apk`. The in-app updater
  (`app/src/wehavephones/java/com/wehavephones/mobile/updates/AppRelease.java`) installs it.
- Signing: `signing/azeroid-release.jks.enc` (AES-256, pbkdf2 600k). Its password is the repo secret
  `AZEROID_SIGNING_PASSWORD` (alias `azeroid`). The owner holds the password.
- `LegacyAzeroidCleanup` wipes 0.1.x engine data once on first start.

## Next work (in order)

1. Owner confirms 0.2.0 installs, Battle.net signs in and WoW Classic downloads and launches.
2. **Performance for the S26 Ultra**: frame-time logging (avg + 1% lows) first, then:
   GPU driver A/B (system vs newest Adreno 8xx Turnip), CPU affinity (2 prime cores for WoW main/render threads),
   DXVK async pipelines + persistent caches, sustained-performance/game mode, render resolution + sharpening,
   drop low-RAM memory tweaks that cost speed, Battle.net exits on game launch.
3. **Azeroid on-screen controller** replacing WeHavePhones' `VirtualGamepadController`: Xbox layout (twin sticks,
   D-pad, ABXY, bumpers, triggers), semi-transparent, thumb-sized for 6.9" landscape, *Auto* hides it when the
   G8 is connected, XInput so WoW gamepad mode and ConsolePort work.

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
