# Retail memory settings on the S23

Follow-up: [hidden settings and runtime tweak research](RETAIL_MEMORY_TWEAKS_RESEARCH_2026_10_22.md).

Research and device checks on 2026-10-22. Target: Galaxy S23+
SM-S916U1, Android 16, Snapdragon 8 Gen 2, 8 GB advertised RAM.
Android reports 7,242,412 KiB of usable physical memory, about 6.91 GiB.

Low textures and minimum scenery settings reduced Android-reported graphics
memory by about 16 MiB in the final check. The main game footprint remained
about 3.6 GiB including swapped pages. This is a modest device-only tuning
change, not a solution for all of Retail's memory pressure.

## What the research supports

Start with the game's supported graphics controls. On this phone graphics
allocations compete with the game and Android for physical memory. Lower
texture resolution and less distant scenery are useful candidates. Their
actual savings depend on the scene and must be measured on the phone.

Retail was updated from 12.1.5.70448 to 12.1.5.70529 before the comparison.
The matching Blizzard UI source establishes the values:

- [Retail 12.1.5 graphics definitions](https://github.com/Gethe/wow-ui-source/blob/12.1.5/Interface/AddOns/Blizzard_Settings_Shared/Mainline/GraphicsOverrides.lua)
  map texture resolution 0 to Low, 1 to Fair and 2 to High. Both normal and
  raid settings have their own controls.
- [Shared 12.1.5 graphics UI](https://github.com/Gethe/wow-ui-source/blob/12.1.5/Interface/AddOns/Blizzard_SettingsDefinitions_Shared/Graphics.lua)
  displays distance and detail slider indices 0 through 9 as levels 1 through
  10. It warns that disabling projected textures hides combat cues.
- The same UI exposes render scale and multisample anti-aliasing. Reducing
  render scale can reduce render-target memory, at the cost of sharpness.
  Disabling MSAA can save memory when it is enabled. Neither is proof of a
  reduction in total game RAM.
- [Blizzard's March 2026 hotfix history](https://news.blizzard.com/en-us/article/24266320/hotfixes-april-17-2026)
  includes a March 17 fix for memory-related performance problems. Client
  updates matter. A workaround for an older leak is not necessarily useful
  on 12.1.5.

The GitHub sources above mirror Blizzard's extracted UI code. They establish
supported settings and their meanings, not measured memory savings.

## What was already in place

The Dev install had D3D11, 1280x720, Fair textures, distance/detail sliders at
level 3, low particles, disabled SSAO and depth effects, and enabled projected
textures. The installed add-ons were ConsolePort and the controller setup
add-on. There were no installed damage meters or large general-purpose add-on
suites to remove.

The app already reduces DXVK allocation chunks to 32 MB, requests two wrapper
images, trims unused renderer caches and closes Battle.net after game launch.
The 256 MB Mesa shader cache limit concerns the disk cache. It is not a
256 MB cap on WoW's RAM. See [memory behavior](WEHAVEPHONES_APP.md#memory-behavior).

## Experiment scope

Change only this device's Retail settings, with a private on-device backup.
Keep app defaults, other WoW versions, drivers, Wine and DXVK unchanged.
Keep projected textures and controller add-ons enabled.

Candidate controls:

- `graphicsTextureResolution` and `raidGraphicsTextureResolution`: 1 to 0,
  Fair to Low.
- `graphicsViewDistance` and `raidGraphicsViewDistance`: 2 to 0,
  displayed level 3 to level 1.
- `graphicsEnvironmentDetail` and `raidGraphicsEnvironmentDetail`: 2 to 0.
- `graphicsGroundClutter` and `raidGraphicsGroundClutter`: 2 to 0.

Expect softer textures, closer scenery pop-in and less decorative ground
clutter. These are quality tradeoffs, not fixes for every memory leak.

## Observed result

The final fresh-launch check found a small graphics-memory saving. Medians
from the original-settings rollback and the confirmed lower-settings run
respectively:

- Android-reported Graphics: 864.6 MiB to 848.9 MiB, down 15.7 MiB, about 1.8%.
- Game PSS plus SwapPss: 3701.5 MiB to 3677.2 MiB, a difference of 24.3 MiB.
  This part of the game's footprint remains about 3.6 GiB.
- Combined allocation-footprint estimate: 4566.1 MiB to 4526.2 MiB, down
  39.9 MiB, about 0.9%. This is not equivalent to that much physical RAM freed.
- System MemAvailable: medians 973.5 MiB and 970.6 MiB, effectively unchanged,
  with overlapping sample ranges. There is no demonstrated gain here.

The confirmed lower-settings run has 11 samples and the rollback has 10.
Each covers roughly a minute. This is one matched pair in a small starting area, not
evidence of savings across all Retail content. The main game backing remains about 3.6 GiB before separate graphics
accounting. These changes do not recover gigabytes or establish that memory
pressure and long-session growth are solved.

The lower settings are retained for this Dev install's Retail client. Other
WoW versions and the app's defaults are unchanged. The final configuration is
read back after reapplication. The test game is closed afterward.

## Tips not adopted

- Do not copy old `gxTextureCacheSize`, `textureCacheSize`, `streamingSizeMB`
  or `GxAllowCachelessShaderMode` recipes without checking the running client.
  Old posts mix different renderers, client versions and stutter workarounds.
  Their existence does not demonstrate a useful RAM reduction here.
- Do not treat an advertised VRAM limit as a hard limit on total game RAM.
  This phone shares physical memory between CPU and GPU.
- Do not clear game or shader caches as routine memory tuning. Rebuilding
  caches adds work and changes the conditions of a comparison.
- Windows page-file instructions do not directly configure Android or Wine.
  Do not change Android swap settings to imitate a desktop guide.
- Do not remove ConsolePort to chase a small add-on number. It supplies the
  controller interface. Add-on Lua memory is only one part of game memory.

## Validation and rollback

Both configurations use D3D11 at 1280x720, the same character on the Exile's
Reach starting ship, and the same installed add-ons. The comparison uses
fresh game processes, a settling period after world entry, and approximately
one minute of idle samples per configuration. Screenshots verify the scene.

The regular project sampler records system memory and process RSS. Additional
read-only samples inspect the game's `/proc/<pid>/smaps_rollup` through
`run-as`, with `dumpsys meminfo <pid>` for Android's separate Graphics
accounting. The process's working directory identifies Retail.

Report PSS and SwapPss together to avoid mistaking swapped-out pages for
freed allocations. SwapPss is the uncompressed size of backed pages, not
physical compressed RAM consumption. Report Graphics separately. The sum
is an allocation-footprint estimate, not a measurement of physical RAM used.
The Android app process's PSS alone omits the native WoW process and is not
a useful total for this task.

The first standard launch included patching. An exploratory live-settings
change retained existing allocations. Those captures establish setup and
behavior, not a matched cold-launch memory comparison. Background Android
activity and compression vary, so these short checks cannot establish an
FPS improvement, long-session stability or memory use in cities and raids.

The first lower-settings fresh launch appeared to save 48.1 MiB of Graphics.
Its character-select scenery had not finished loading before entering the
world. A final repeat waited for the scenery and characters to render, then
sampled the same ship scene. Its smaller 15.7 MiB difference is the result
reported above. Startup and cached assets affect these small deltas.

The final comparison files are `candidate-confirmed-rollup.json` and
`rollback-cold-rollup.json`, with their `.summary.json` companions. The
`baseline-world.jsonl` and `candidate-world.jsonl` project-sampler captures
include loading and should not be compared as steady-state averages.

Local captures and measurements belong under
`out/s23-retail-memory-20261022/`. Do not publish full Config.wtf files, account
paths or sign-in captures.

The original Config.wtf was backed up beside it as
`Config.wtf.before-ram-research-20261022` in this Dev install's Retail `WTF`
directory. Restore only while WoW is closed. A targeted rollback sets the
texture controls above to 1 and the six distance/detail controls to 2 through
the game's console or settings UI. The engine also changes derived CVars,
including `worldBaseMip`, `farclip` and object LOD values. A file-only
rollback must restore those too. The local experiment records all 27 changed
graphics keys and verifies targeted restoration, preserving unrelated settings.

Host check: `scripts/test-wehavephones-memory.sh` passed, including the Java
memory transforms, Python sampler self-tests and device-selection checks.
No application source or bundled defaults changed, so an APK rebuild is not
needed for this experiment.
