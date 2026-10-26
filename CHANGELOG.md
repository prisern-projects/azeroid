# Changelog

All notable changes to Azeroid. Each version's section becomes its GitHub Release notes.

## [0.3.0] - 2026-10-26

- **Azeroid's own look.** Dark ink background, slate panels and one gold accent, with the Cinzel "AZEROID"
  wordmark. This replaces the WeHavePhones phone-and-LCD style across Home, Setup, Settings, Diagnostics,
  Add-ons and the launch screen. Home has a large gold PLAY button with Open Battle.net below it. The engine
  underneath is unchanged.
- **FPS overlay redesigned:** one compact line in the top-left corner showing FPS, frame time, **battery %**
  (⚡ while charging), battery temperature and free RAM. Turn it on in Settings → *Show performance overlay*.
- Next: Azeroid's own on-screen controller.

## [0.2.12] - 2026-10-26

- **Fewer hitches (test build).** WoW's DXVK is now v2.3.1 with the dxvk-gplasync patch. The phone's driver
  can't build shaders ahead of time (DXVK logged "Graphics pipeline libraries not supported"), so every new
  effect, model or spell stalled the frame while its shader compiled: the 100–700 ms hitches. Shaders now
  compile on background threads. The trade-off: something new may be invisible for a moment the first time
  it appears, instead of freezing the game. Compiled shaders are cached as before.

## [0.2.11] - 2026-10-26

- **Faster Play.** Battle.net ignores Play clicks until its page has rendered. Azeroid waited 10 s after each
  missed click, but a click that lands starts WoW in about 2.5 s. It now retries after 5 s (12 tries instead
  of 6, same total patience), which saves 5–20 s on most launches.

## [0.2.10] - 2026-10-26

- **Native texture decoding is now the default.** Back-to-back test in Stormwind (same crowd, 1280x720):
  driver decoding gave **48 FPS average and 36 FPS 1% lows**, against 36–39 FPS and 22–24 FPS with emulation.
  That is about +25–30% average and +50% lows, and the tester saw less tearing. Textures render correctly.
  The switch in Settings stays, in case a driver update breaks it.

## [0.2.9] - 2026-10-26

- **Experiment: native texture decoding.** WoW's DXVK log shows the driver path reports BC texture support.
  New switch in Settings: *let the graphics driver decode textures itself*. When it works this skips the
  GPU unpacking step that the Stormwind test points at. Off by default; turn it off again if textures look
  broken.
- The frame log tags each line `tex=emulated` or `tex=native`, next to the CPU tag.
- Removed 0.2.8's graphics-log switch. The wrapper never wrote the file on this phone.

## [0.2.8] - 2026-10-26

- **Screen tearing fix.** The game's frame is copied into a buffer that the GPU reads directly. The X server
  could start copying the next frame while the GPU was still drawing the previous one onto the screen, which
  showed half of each. The renderer now waits until the GPU has finished reading before the next copy can
  start (normally well under 1 ms per frame).
- Settings: *Write a graphics log for the next game session*. One session only, saved to
  `Download/WeHavePhones/wrapper.log`. It shows whether the phone's driver handles WoW's compressed
  textures natively or Azeroid has to unpack them on the GPU.

## [0.2.7] - 2026-10-26

- **Fixed:** Diagnostics' "Last session end" was never saved, because the app restarts itself right after a
  session ends and the note was still being written. It is now saved before the restart.
- Frame log says `cpu=prime[waiting-for-wow]` until WoW's threads have been placed, so a session where WoW
  never started can't be mistaken for one where pinning ran.

## [0.2.6] - 2026-10-26

- **WoW's main threads on the fastest cores.** Every 3 seconds Azeroid measures which WoW threads use the
  most CPU. The two busiest (in practice the main and render threads) go on the phone's two prime cores, and
  WoW's other threads are kept off them. The goal is better 1% lows in busy places like cities.
  Settings → *Run WoW's main threads on the fastest cores* (on by default) turns it off for A/B comparison.
- The frame log tags each line with the mode: `cpu=prime[6-7,6-7]` (the cores actually in use) or `cpu=auto`.

## [0.2.5] - 2026-10-26

- **Fixed:** 0.2.4's request for the screen's 120 Hz mode made Samsung switch the display to another
  resolution. The game was drawn small in the top-left corner and ran at 60 FPS. That request is removed.
  Triple buffering, the bigger shader cache and the `game=` FPS log from 0.2.4 stay.

## [0.2.4] - 2026-10-26

Performance round 1, measured against the 0.2.3 baseline (Classic Era, 1024x576: about 100 FPS average,
1% lows of about 77 FPS):

- **Triple buffering** (3 swapchain images, up from 2). With two, the game waits on the display path and
  misses 120 Hz refreshes.
- **Asks for the panel's 120 Hz mode** for the game surface, so adaptive refresh can't drop it to 60 Hz.
- **Bigger shader cache** (1 GB, up from 256 MB), so fewer shaders are rebuilt between sessions (less stutter
  in the first minutes).
- Frame log now also shows `game=` FPS, the frames WoW actually produced, next to the frames shown on screen.
  That tells whether the limit is the game itself or the display path.

## [0.2.3] - 2026-10-26

- **Frame-time logging.** Every game session now writes `Download/WeHavePhones/frametimes.log`: one line every
  30 seconds with average FPS, 1% and 0.1% lows (stutter), the worst frame and the count of frames over 50 ms,
  plus a whole-session summary. The latest lines also appear in Diagnostics. This is the yardstick for all
  upcoming performance work. It costs one timestamp per frame.

## [0.2.2] - 2026-10-25

- **Cause found:** the WoW install "crash" is Android killing Azeroid for low memory (Diagnostics showed
  `LOW_MEMORY` while the app was in the foreground). It is not a Battle.net bug.
- Battle.net now runs with fewer browser processes (one renderer, no per-site processes, no GPU shader
  cache). Each of those processes runs under x86 translation, so this frees real RAM during the install.

## [0.2.1] - 2026-10-25

- **Crash forensics for the WoW install crash.** When a session ends, Azeroid now writes how it ended to
  `wine.log` and Diagnostics: exit status (memory kill vs crash), free RAM and swap at that moment, and
  Android's own exit reasons. The next crash tells us the real cause instead of a guess.

## [0.2.0] - 2026-10-25

- **New foundation.** Azeroid is now built on [WeHavePhones](https://github.com/ashmerra/wehavephones) (MIT) and
  Winlator LLM. That stack already runs Battle.net and Blizzard's native ARM64 WoW client on Android phones,
  including the S26 Ultra. It replaces the Ludashi-based engine of 0.1.x.
- Keeps Azeroid's app id, signing key and update channel, so this installs over 0.1.x.
- First start after updating removes 0.1.x's engine data once (no game files existed yet).
- Next: performance work to make it smoother than WeHavePhones.

## [0.1.14] - 2026-10-25

- Battle.net crashed under FEX 2610 (stack overflow inside FEX itself). Azeroid now builds FEX at revision
  1268a39 in its own CI. That revision is known to run Battle.net on GE-Proton 10.0-34. The runtime step installs it.
- Note: when Blizzard's installer finishes, it opens Battle.net by itself without Azeroid's settings.
  Exit that window and use **Open Battle.net** in Azeroid.

## [0.1.13] - 2026-10-25

- **New default runtime: GE-Proton 10.0-34 ARM64EC** ([The412Banner/proton-wine](https://github.com/The412Banner/proton-wine),
  pinned + SHA-256 checked). It carries Android fixes and a Battle.net game-fix. Proton 11 can't render Battle.net's
  browser at all, and the Proton 10.0-5 package froze it.
- Battle.net now starts directly with `--disable-gpu --enable-features=NetworkServiceInProcess2`:
  Chromium's separate network process crashes under Wine on Android, so the page never loaded.
- GPU driver "Auto" now picks Qualcomm's own driver on Adreno 8xx (S26 Ultra), because Turnip draws glitches there.
- Battle.net translator reset to FEX safe (multiblock off).
- Battle.net's first start can take 2–3 minutes and may show an empty window for the first minute.

## [0.1.12] - 2026-10-25

- *Advanced → Detailed logs*: traces Wine's network, connectivity and TLS layers in Battle.net sessions,
  to find out why Battle.net freezes after opening the login page on Proton 10/11.

## [0.1.11] - 2026-10-25

- New: *Advanced → Windows runtime*: Proton 10 or Proton 11 (each pinned + SHA-256 checked).
  On Proton 10, Battle.net's main thread freezes right after it opens the login page, so the window stays blank.
  Each runtime gets its own environment, so switching means installing Battle.net once more.

## [0.1.10] - 2026-10-25

- Battle.net's blank window: FEX 2610 still overflowed its own native stack while translating Battle.net
  (multiblock compilation). "FEX safe", the default, now uses FEX's STABILITY preset with multiblock off.
  If it still stays blank, try *Advanced → Battle.net translator → Box64*.

## [0.1.9] - 2026-10-25

- Proton 10 fixed the sign-in loop: Battle.net's Agent now accepts the client.
- New blank-window cause found: Battle.net.exe hit a stack overflow under the bundled FEX 2601 (Jan 2026).
  Azeroid now installs **FEX 2610** (Oct 2026, pinned + SHA-256 checked) through the engine's content installer
  during the runtime step, and uses it for every launch.

## [0.1.8] - 2026-10-25

- Battle.net installer now always runs under Box64 (it crashes under FEX). The Battle.net app runs under
  FEX by default: under Box64 its built-in browser fails TLS and the sign-in page stays blank.
  Your earlier translator choice is reset to the new default.
- Battle.net now exits completely when WoW starts, freeing ~1.7 GB of RAM for the game.

## [0.1.7] - 2026-10-25

- **Real fix for the Battle.net sign-in loop: switch to Proton 10.** Battle.net's Agent rejects the client
  on Wine/Proton 9 ("Failed Caller authorization due to signature", response 401). Wine/Proton 10+ works.
  Azeroid now downloads Proton 10.0-5 (ARM64EC, ~130 MB, SHA-256 checked) as a new setup step and rebuilds the
  WoW environment on it. A WoW install from an older environment is moved over, not re-downloaded.
  You'll need to install Battle.net again (a few minutes).
- Reverted 0.1.6's certificate import: the log showed Wine already had Android's 150 trusted roots.

## [0.1.6] - 2026-10-25

- **Fix for the Battle.net sign-in loop.** Battle.net's Agent logged "Failed Caller authorization due to
  signature" and shut down every ~15 s. Wine couldn't verify Blizzard's code signature because it found no trusted
  root certificates on Android. Azeroid now copies Android's trusted root certificates into the Windows
  certificate store before Battle.net starts.
- Share log reports which certificate stores Wine sees.

## [0.1.5] - 2026-10-25

- New: *Advanced → Battle.net translator* (Box64 / FEX safe / FEX fast). Battle.net and its Agent are
  32-bit programs. If Battle.net loops or freezes, try another translator. Box64 is the new default.
- Share log now also includes Battle.net's and its Agent's own log files from inside the Windows environment.

## [0.1.4] - 2026-10-25

- Battle.net sessions now record Wine's error messages and crash reports in the session log.
  This is needed to diagnose why Battle.net's background service (Agent.exe) restarts every ~15 seconds,
  which makes the sign-in page loop and go black.

## [0.1.3] - 2026-10-25

- **Fixed the Battle.net crash** ("closes back to Azeroid" while the login window loads). A race condition
  in the engine's X server shared-memory code crashed the app whenever Battle.net's window updated quickly.
  This was also the likely cause of the earlier D-pad crash.

## [0.1.2] - 2026-10-25

- Battle.net is now operated by touch: a tap clicks exactly where your finger is (email box, buttons, …).
- Installing Battle.net and "Open Battle.net" no longer show the on-screen controller and no longer pass
  controller input to Battle.net. Its controller handling crashed under Wine (e.g. D-pad on the sign-in page).
- WoW sessions keep full controller support, and taps click directly there too.

## [0.1.1] - 2026-10-25

- Battle.net stability: Battle.net (and its installer) now always run with full x86 memory-ordering
  emulation, and with Windows' location API disabled. That API is a known cause of Battle.net crashing or hanging at sign-in under Wine.
- "Stable" emulation mode is now safer (full memory ordering); "Fast" is the previous default.
- Keyboard hint: to type, swipe Back once → Show Keyboard (shown on screen when Battle.net starts).
- Share log now saves a full report (including the engine's Wine/FEX logs and crash traces) to
  `Azeroid/logs` on your phone and shares it as a file.

## [0.1.0] - 2026-10-25

First release.

- Guided 5-step setup: file access, engine, WoW environment, Battle.net, WoW Classic.
- One-tap PLAY with tuned WoW settings (DX11 via DXVK async, windowed fullscreen, FPS cap, gamepad mode).
- Engine: Winlator Bionic (ARM64EC Proton 9 + FEX-Emu), pinned and hash-verified.
- Graphics presets Performance / Balanced / Quality, FPS limit, FPS counter.
- Controller support (XInput) with WoW's native gamepad mode; menus navigable by controller.
- Optional on-screen controller: Auto / On / Off.
- Advanced: GPU driver (Qualcomm system / Turnip / v863), emulation mode, launch mode.
- Self-update from GitHub Releases with SHA-256 verification.
- Share log and Report a bug buttons.
