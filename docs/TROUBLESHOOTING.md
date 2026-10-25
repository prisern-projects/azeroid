# Troubleshooting

When something breaks: **Share log** (main screen) or **Report a bug** sends what's needed to fix it.
Please include what you pressed and what you saw.

## Black screen / game window never appears
1. Wait 60 seconds. The first start compiles a lot.
2. *Advanced → GPU driver*: try **Samsung/Qualcomm** first, then **Turnip**, then **Qualcomm v863**.
3. *Graphics → Performance*, then try again.

## How do I type (log in) without a keyboard?
Swipe **Back** once while Battle.net or the game is showing → side menu → **Show Keyboard**.

## Battle.net is a white/black window or crashes
- Azeroid turns Battle.net's hardware acceleration off automatically. Close it and open it again once.
- If it never draws: *Advanced → Play button → Start WoW directly* and log in from the game's own login screen.
  You still need Battle.net to install and patch the game.

## Stutter when entering new areas
DXVK compiles shaders the first time it sees them, in the background (async). Stutter fades after you've
visited an area once. *FPS limit 45* or *30* smooths things out and keeps the phone cooler.

## Crashes after a while
- *Advanced → x86 emulation → Stable* (the default).
- Heat: cap FPS at 45, use the *Performance* preset, and take the phone out of a case.

## "Update failed"
- The first update needs *Settings → Apps → Azeroid → Install unknown apps → Allow*.
- "Conflicting signature": you installed a test build that wasn't signed with the release key. Uninstall it and
  install the latest release. This erases the downloaded game.

## Starting over
*Advanced → Open engine tools* gives full access to the engine: containers, drivers, logs.
Uninstalling Azeroid removes everything, including the game files.
