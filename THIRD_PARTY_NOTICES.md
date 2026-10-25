# Third-party software

The Azeroid APK bundles the following projects. They aren't stored in this repository; CI fetches them at the
pinned versions in `engine.properties`.

| Component | License | Source |
|---|---|---|
| Winlator Bionic (Ludashi fork) | MIT, © 2023 BrunoSX and contributors | https://github.com/StevenMXZ/Winlator-Ludashi |
| Winlator Bionic | MIT | https://github.com/Pipetto-crypto/winlator |
| Wine / Proton | LGPL-2.1+ | https://www.winehq.org, https://github.com/ValveSoftware/Proton |
| FEX-Emu | MIT | https://github.com/FEX-Emu/FEX |
| Box64 | MIT | https://github.com/ptitSeb/box64 |
| DXVK | zlib | https://github.com/doitsujin/dxvk |
| VKD3D-Proton | LGPL-2.1 | https://github.com/HansKristian-Work/vkd3d-proton |
| Mesa (Turnip, Zink) | MIT | https://www.mesa3d.org |
| libadrenotools | BSD-2-Clause | https://github.com/bylaws/libadrenotools |
| Vulkan-ValidationLayers | Apache-2.0 | https://github.com/KhronosGroup/Vulkan-ValidationLayers |
| PulseAudio | LGPL-2.1 | https://www.freedesktop.org/wiki/Software/PulseAudio |
| Termux packages (glibc patches etc.) | various | https://github.com/termux/termux-packages |

LGPL components are dynamically loaded and can be replaced. Their corresponding source code is available from
the upstream projects above.
