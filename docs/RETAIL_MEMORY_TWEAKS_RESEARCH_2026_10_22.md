# Further Retail memory research

Research on 2026-10-22, following the
[S23 graphics experiment](RETAIL_LOW_RAM_S23_2026_10_22.md).
The best new candidates are the smaller sound cache, disabling racial-model
preloading, and DXVK pipeline lifetime tracking. None has a measured saving
on this S23 yet. This follow-up changed no phone settings or app defaults.

## Evidence and device audit

The strongest CVar source found is a client-generated
[12.1.5.70448 settings dump](https://github.com/Ketho/BlizzardInterfaceResources/blob/f3b334b33bd19fa6a0250f7922ecfa83db7fe356/Resources/CVars.lua),
committed on 2026-10-22 as
[12.1.5 (70448)](https://github.com/Ketho/BlizzardInterfaceResources/commit/f3b334b33bd19fa6a0250f7922ecfa83db7fe356).
It records defaults, scope and client help text. It is community-hosted
extracted metadata, not a Blizzard recommendation to modify every setting.
The phone is on the slightly newer 12.1.5.70529 build. Query its effective
values and accepted settings before any experiment. A missing Config.wtf
entry does not prove the current runtime value.

A read-only audit of the Dev install found no explicit overrides for the
new preload, sound-cache, shader-mode or pipeline-allocator CVars. Its latest
`gx.log` reports D3D11 and a disabled per-thread allocator. Local excerpts,
source snapshots and the allowlisted settings audit are under
`out/s23-retail-memory-deep-20261022/`. No full account configuration is
retained in that research directory.

## Best candidates to test

### Small sound cache, then fewer audio channels

Retail's own [12.1.5 Audio settings implementation](https://github.com/Gethe/wow-ui-source/blob/12.1.5/Interface/AddOns/Blizzard_SettingsDefinitions_Shared/Audio.lua)
offers 64 MiB and 128 MiB sound-cache sizes. It also exposes a sound-channel
slider from 20 to 128. The settings dump's defaults are 128 MiB and 64
channels. There is no need to install an addon to access these controls.

The first isolated candidate is `Sound_MaxCacheSizeInBytes 67108864`.
It lowers cache capacity by 64 MiB if the current value is 134217728.
That is a capacity difference, not a promise of 64 MiB of free RAM. The cache
may not be full, and allocator behavior can affect what Android recovers.
More audio may need to be read again. Those consequences are inferences to
check during play.

Test `Sound_NumChannels 32` separately afterward. This limits concurrent
audio rather than game objects. It can affect overlapping sounds. Check
combat cues and audio quality, and do not present a CPU improvement as a
proven RAM saving. Leave reverb and accessibility audio unchanged for the
first comparison.

### Disable racial-model preloading

`preloadPlayerModels 0` is a plausible memory experiment. The client metadata
describes the default-on behavior as eagerly loading local racial models.
It does not give a byte saving.

My inference is that loading models only when needed could reduce initial
or lightly populated scene allocations. A busy area might eventually need
many of those models anyway. Check delayed character appearance and frame
time while encountering new players. Test this alone, with a complete game
restart, rather than combining it with seven other preload commands.

### Reclaim unused DXVK pipeline libraries

`dxvk.trackPipelineLifetime=True` is a real memory-management option in
[DXVK 2.3's configuration documentation](https://github.com/doitsujin/dxvk/blob/v2.3/dxvk.conf).
It aggressively releases pipeline libraries. The
[2.3 implementation](https://github.com/doitsujin/dxvk/blob/v2.3/src/dxvk/dxvk_device.cpp)
shows that Auto does not enable this policy for a 64-bit host, and that True
still requires usable graphics pipeline library support.

This is particularly relevant to the 64-bit ARM Retail/D3D11 path. Confirm
that the actual loaded DXVK build supports the option and uses graphics
pipeline libraries before testing it. Possible savings and extra compilation
or stutter remain unmeasured here. Do not force graphics pipeline library
support as part of the same experiment.

Merge this one option into the existing `DXVK_CONFIG`, preserving the app's
`dxvk.maxChunkSize=32` and other options. A container-wide environment edit
can also reach Battle.net. A useful trial must scope the setting to the test
game launch and restore the exact original environment afterward.

## Real controls with less immediate value

### The allocator with a roughly 300 MB tradeoff is already disabled

`ThreadPoolPerThreadAllocator` supports disabled, automatic permission and
forced modes. Its client help associates the forced mode with roughly
300 MB more memory and a speed tradeoff. Those figures are a client
description, not a measurement on ARM64 Android.

The S23's actual log already says `PerThreadAllocator:disabled`, with six
workers in each priority pool. Setting the allocator to 0 is therefore not
evidence of a new 300 MB saving here. Reducing `ThreadPoolLimitHP`, MP and LP
is a separate CPU and scheduling experiment, with no established RAM gain.

### Cacheless shaders need the opposite value from the old stutter recipe

The same client metadata describes `GxAllowCachelessShaderMode 1` as avoiding
resident shader storage when the graphics backend supports it. The snapshot
default is 0. The familiar `GxAllowCachelessShaderMode 0` workaround disables
that behavior. It is not a new memory-saving setting.

Enabling 1 is an experimental candidate, not a confirmed D3D11/Wine fix. It
may shift work to disk reads and cause first-use delays. Require an actual
memory difference, correct rendering and acceptable frame times before
retaining it. Do not combine it with DXVK pipeline lifetime tracking in the
first trial, as both affect shader or pipeline residency.

### Smaller character texture cache

The snapshot lists `componentTexCacheSize` with a default of 32 MB. Trying
16 is a lower-priority bounded-cache experiment. Its accepted value and
effective behavior still need verification. A smaller cache may increase
repeated texture work. `componentCompress` is already default-on in the
snapshot. Do not disable compression as a performance tweak on this phone.

### World preloading controls mostly describe loading policy

The `worldPreloadHighResTextures`, `worldPreloadNonCritical`,
`preloadLoadingDist*` and `preloadStreamingDist*` controls exist. Their names
and descriptions concern preloading and streaming distances. That does not
establish a hard residency budget after entering the world.

A [firsthand forum suggestion](https://eu.forums.blizzard.com/en/wow/t/cannot-enter-any-instance/591524/2)
sets most of them to zero to troubleshoot stuck loading screens. It supplies
no controlled memory measurements. Treat that as a loading workaround, not
proof of a lasting RAM reduction. Test one control at a time if initial
loading pressure remains a problem.

## Long-session growth and combat logs

The current [12.1.5 combat-log API](https://github.com/Gethe/wow-ui-source/blob/12.1.5/Interface/AddOns/Blizzard_APIDocumentationGenerated/CombatLogDocumentation.lua)
includes `C_CombatLog.ClearEntries()` and retention-time controls.
[Deprecated API compatibility code](https://github.com/Gethe/wow-ui-source/blob/12.1.5/Interface/AddOns/Blizzard_DeprecatedCombatLog/Deprecated_CombatLog.lua)
maps the older `CombatLogClearEntries` name to the new API when its fallback
is loaded. Use the current name when checking modern advice.

There are [firsthand reports of relief after clearing entries](https://us.forums.blizzard.com/en/wow/t/fps-issues-on-reload/2233741),
but that does not identify every source of memory growth. Clearing this
history loses combat-log entries. It is separate from deleting log files or
disabling advanced combat logging. It is a diagnostic for growth after
combat, not a reason to install an automatic every-frame cleanup addon.

The metadata marks `combatLogRetentionTime` as account-scoped, with a
300-second default. Do not silently change an account-wide preference for a
phone-only trial. Likewise, hiding the built-in damage meter is not proof
that the engine stops retaining combat information.

## Advice that does not transfer directly

- `gxTextureCacheSize`, `textureCacheSize`, `streamingSizeMB`, `Sound_CacheSize`
  and `releaseUITextures` are absent from the 12.1.5 snapshot. Verify an
  actual current-client registration rather than copying their old values.
- DXVK's `dxgi.maxDeviceMemory` and `dxgi.maxSharedMemory` change reported
  capacities. They do not impose a hard total memory limit. Lower reported
  capacity might change game heuristics, which would need separate testing.
- The [DXVK 2.3 config](https://github.com/doitsujin/dxvk/blob/v2.3/dxvk.conf)
  describes `d3d11.cachedDynamicResources` as a memory placement/readback
  control. It is not a cache-size cap. Newer-version defragmentation or budget
  recipes must not be assumed to exist in this build.
- `MALLOC_ARENA_MAX` controls the
  [glibc allocator](https://sourceware.org/glibc/manual/latest/html_node/Memory-Allocation-Tunables.html).
  This project's ARM64EC Wine launcher uses a Bionic-based path.
  [Bionic is Android's C library](https://android.googlesource.com/platform/bionic/+/refs/heads/main/README.md).
  There is no demonstrated effect from a glibc arena setting on that path.
- [Android's memory documentation](https://developer.android.com/topic/performance/memory-management)
  explains CPU/GPU shared RAM and compressed zRAM. More virtual capacity is
  not more physical RAM. There is no evidence from this research that
  changing Samsung RAM Plus improves this workload. Its exact backing was
  not established through the restricted `/proc/swaps` interface.
- A recent [anonymous-reporting anecdote](https://www.reddit.com/r/wow/comments/1wpknnd/possible_memory_leak/)
  explicitly identifies AMD GPU software as the leaking component. It is
  not evidence for a WoW telemetry toggle on the Snapdragon/Turnip setup.
- Retail is ARM64 and 64-bit. WoW 3.3.5a large-address patches, injected
  allocator replacements and 32-bit address-space recipes do not establish
  a compatible fix here. They are not candidates for this experiment.

## Proposed order and proof required

1. Read current and default values from the running 12.1.5.70529 client.
2. Test the 64 MiB sound cache by itself, then racial-model preloading off.
3. Check graphics pipeline library support and test DXVK lifetime tracking.
4. Consider cacheless shader mode and smaller texture caches only afterward.

For each trial, retain the original value, use a fresh game process and wait
for character-select assets to finish loading. Compare the same world scene
and a repeatable populated-area route, then restore the baseline between
candidates. Measure game PSS plus SwapPss, separate Graphics accounting,
system MemAvailable, memory pressure and frame times. Include a longer
session to distinguish a smaller initial cache from less memory growth.
The [earlier report](RETAIL_LOW_RAM_S23_2026_10_22.md) explains the accounting
and the startup-loading confound. New byte-saving claims require new device
measurements.
