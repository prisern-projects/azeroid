# Install & first run

## What you need

- Galaxy S26 Ultra (any Snapdragon/Adreno phone from the last few years should work)
- **~25 GB free storage**: engine about 3 GB, plus WoW Classic Era about 10 GB, plus room for patches
- Wi-Fi for the big downloads
- A Battle.net account with WoW access

## 1. Install the app

1. On the phone, open the [latest release](https://github.com/prisern-projects/azeroid/releases/latest) and download `Azeroid-x.y.z.apk`.
2. Tap the downloaded file. If Android asks, allow your browser to **install unknown apps**.
3. Android may warn that the app comes from an unknown developer. Tap *Install anyway* / *More details → Install*.

> **Android 17 sideloading note:** Google is rolling out "developer verification" for sideloaded apps
> region by region. If your phone blocks the install, check the release notes for current instructions
> (typically a one-time confirmation in Settings, or installing via a computer with `adb install`).

## 2. First run (about 5 minutes plus the WoW download)

Open **Azeroid**. The big gold button always does the next step:

| Step | What happens | Time |
|---|---|---|
| 1. Allow file access | Opens Android settings. Enable *Allow access to manage all files*, then go back. | 10 s |
| 2. Install engine | Unpacks Wine, FEX and the GPU drivers inside the app. | ~1 min |
| 3. Create environment | Builds the Windows environment tuned for WoW. | ~30 s |
| 4. Install Battle.net | Downloads Blizzard's installer and runs it. Click through it. | 2–5 min |
| 5. Install WoW Classic | Battle.net opens. Log in, choose **World of Warcraft → Classic** in the version menu, install. | depends on your Wi-Fi |

When WoW is installed, close Battle.net (or press the phone's back button and choose *Exit*). Azeroid
shows **PLAY**.

Tips:
- Keep the phone **plugged in with the screen on** during the WoW download.
- Battle.net is slow under emulation. Give it time to draw its window, and don't tap repeatedly.

## 3. Playing

- **PLAY** starts the game through Battle.net, which keeps the client patched. Press *Play* inside Battle.net.
  Under *Advanced → Play button* you can switch to **Start WoW directly** for faster startup, but then
  you have to open Battle.net yourself after each patch.
- If you have several Classic versions installed (Era, Anniversary, progression), pick one with the chips above PLAY.
- Press the phone's **Back** gesture in-game for the engine menu: keyboard, on-screen controls, exit.

## Updating

When a new version exists, Azeroid shows **Update available** at the top. Tap **Update now**. The first time,
Android asks you to allow Azeroid to install apps; turn that on and tap Update again. After that each update
is one confirmation tap. Your game and settings are kept.
