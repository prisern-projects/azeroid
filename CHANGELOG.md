# Changelog

All notable changes to Azeroid. Each version's section becomes its GitHub Release notes.

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
