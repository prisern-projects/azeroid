# Azeroid

*World of Warcraft Classic in your pocket.*

Azeroid is an Android app that runs the **real Windows WoW Classic client** on your phone. It's built for the
**Samsung Galaxy S26 Ultra** (Snapdragon / Adreno), Android 16–17, used as a handheld with a controller like the
**GameSir G8 Galileo**.

No PC and no streaming. The game runs on the phone through Wine, FEX-Emu (x86-64 → ARM64) and DXVK
(DirectX 11 → Vulkan). Azeroid wraps all of that in a **five-step setup and one PLAY button**.

## What you get

- **Guided setup**: allow file access → install engine → create WoW environment → install Battle.net → install WoW.
  Each step is one button.
- **PLAY**: applies tuned settings (DX11, windowed fullscreen, FPS cap, gamepad mode) and starts the game.
- **Controller first**: a G8 Galileo (or any XInput-style pad) works out of the box, using WoW Classic's built-in gamepad
  mode. You can navigate Azeroid's menus with the pad too (A = select, B = back).
- **Optional on-screen controller**: `Auto` shows it only when no physical controller is connected, or force it `On`/`Off`.
- **Performance presets**: Performance / Balanced / Quality, FPS limit 30–120, optional FPS counter.
- **Self-updating**: when a new version is released here, the app shows *Update available*. One tap downloads
  it and Android installs it over the old one, keeping your game and settings.
- **Bug reports in two taps**: *Share log* and *Report a bug* (pre-fills a GitHub issue with your device info).

## Install

See **[docs/INSTALL.md](docs/INSTALL.md)**. Short version: download the latest `Azeroid-x.y.z.apk` from
[Releases](https://github.com/prisern-projects/azeroid/releases/latest), install it, open it, and follow the steps.

You need your own Battle.net account with WoW access. Azeroid downloads Blizzard's official installer and
contains no game files.

## Docs

| | |
|---|---|
| [Install & first run](docs/INSTALL.md) | Getting from zero to the login screen |
| [Controller guide](docs/CONTROLLER.md) | G8 Galileo, WoW gamepad mode, on-screen controller |
| [Troubleshooting](docs/TROUBLESHOOTING.md) | Black screen, crashes, stutter, Battle.net issues |
| [Architecture](docs/ARCHITECTURE.md) | How it works, and how the repo is put together |
| [Releasing](docs/RELEASING.md) | How a new version reaches the phone |
| [Changelog](CHANGELOG.md) | What changed in each version |

## Honest status

This is early software. Running a modern x86 Windows game through translation layers on a phone works,
but it's demanding. Expect **heat and battery drain**, shader-compile stutter the first time you visit a zone, and the
occasional crash. Battle.net's own launcher is the slowest part. The game itself runs better than the launcher does.
Please report problems with the in-app **Report a bug** button. That's how fixes get made.

Using compatibility layers isn't something Blizzard supports. Azeroid doesn't modify the game, inject code or
automate gameplay, but you use it at your own risk with your account.

## Credits

Azeroid is based on [WeHavePhones](https://github.com/ashmerra/wehavephones) by ashmerra (MIT), which builds on
[Winlator LLM](https://github.com/FrontMage/winlator-llm) by FrontMage and [Winlator](https://github.com/brunodev85/winlator)
by BrunoSX, with GE-Proton ARM64EC builds by [The412Banner](https://github.com/The412Banner/proton-wine), FEX-Emu,
DXVK, Mesa Turnip and [ConsolePort](https://github.com/seblindfors/ConsolePort). See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
World of Warcraft and Battle.net are trademarks of Blizzard Entertainment. Azeroid is not affiliated with Blizzard.
