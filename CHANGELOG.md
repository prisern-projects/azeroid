# Changelog

All notable changes to Azeroid. Each version's section becomes its GitHub Release notes.

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
