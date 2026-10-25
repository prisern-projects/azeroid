#!/usr/bin/env bash
# Battle.net test bench: installs and starts Battle.net under Wine ARM64EC + FEX on an ARM64 Linux
# runner with a virtual display, then screenshots it. Not Android, but the same Windows-side stack.
set -uo pipefail
OUT="${OUT:-$PWD/bench-out}"; mkdir -p "$OUT"
export DISPLAY=:99 WINEPREFIX="$HOME/bnet" WINEDEBUG="${WINEDEBUG:-fixme-all}" WINEDLLOVERRIDES="locationapi=d"
Xvfb :99 -screen 0 1920x864x24 >/dev/null 2>&1 &
sleep 2
shot() { import -window root "$OUT/$1.png" 2>/dev/null || true; }
note() { echo "::notice title=bench::$(date +%T) $*"; echo "$(date +%T) $*" >> "$OUT/progress.txt"; }

echo "== wine: $(wine --version)"
note "wineboot start"
# No Mono/Gecko prompts: they block wineboot on a dialog nobody can click.
WINEDLLOVERRIDES="mscoree=d;mshtml=d" timeout 600 wineboot -i >"$OUT/wineboot.log" 2>&1; note "wineboot exit=$? kernel32(32-bit)=$([ -f "$WINEPREFIX/drive_c/windows/syswow64/kernel32.dll" ] && echo yes || echo no)"
timeout 120 wineserver -w
# Crashes print a backtrace to the log instead of opening a dialog.
wine reg add 'HKCU\Software\Wine\WineDbg' /v ShowCrashDialog /t REG_DWORD /d 0 /f >/dev/null 2>&1
note "prefix ready"

mkdir -p "$WINEPREFIX/drive_c/users/$USER/AppData/Roaming/Battle.net"
echo '{"Client":{"HardwareAcceleration":"false","Streaming":{"StreamingEnabled":"false"},"GameLaunchWindowBehavior":"2"}}' \
  > "$WINEPREFIX/drive_c/users/$USER/AppData/Roaming/Battle.net/Battle.net.config"

curl -fsSL -o "$HOME/setup.exe" "https://downloader.battle.net/download/getInstallerForGame?os=win&gameProgram=BATTLENET_APP&version=Live"
BNET="$WINEPREFIX/drive_c/Program Files (x86)/Battle.net/Battle.net.exe"
echo "== installer (HODLL=${SETUP_HODLL:-libwow64fex.dll})"
HODLL="${SETUP_HODLL:-libwow64fex.dll}" wine "$HOME/setup.exe" --lang=enUS >"$OUT/setup.log" 2>&1 &
note "installer started"
for i in $(seq 1 90); do [ -f "$BNET" ] && break; sleep 10
  # The installer waits on "Continue" (bottom right of its 720x450 window, centred at the top of the screen).
  [ $((i % 3)) -eq 0 ] && xdotool mousemove 1168 469 click 1 2>/dev/null; [ $((i % 6)) -eq 0 ] && { shot "setup-$i"; note "installer waiting ${i}0s"; }; done
note "installer: Battle.net.exe present=$([ -f "$BNET" ] && echo yes || echo no)"
sleep 30; shot setup-done
note "syswow64 kernel32 present=$([ -f "$WINEPREFIX/drive_c/windows/syswow64/kernel32.dll" ] && echo yes || echo no)"
wineserver -k; sleep 3
[ -f "$BNET" ] || { echo "Battle.net not installed"; exit 1; }

echo "== Battle.net (HODLL=${BNET_HODLL:-libwow64fex.dll}) args: ${BNET_ARGS:-}"
HODLL="${BNET_HODLL:-libwow64fex.dll}" wine "$BNET" ${BNET_ARGS:-} >"$OUT/battlenet.log" 2>&1 &
for t in 30 60 90 120 150 180; do sleep 30; shot "bnet-${t}s"; note "battle.net ${t}s, processes: $(pgrep -fc Battle.net)"
  [ $t -eq 30 ] && ps -eo pid,args | grep -- '--type=renderer' | head -2 | cut -c1-2000 > "$OUT/renderer-cmdline.txt"
  [ $t -eq 30 ] && ps -eo pid,args | grep -iE 'battle|agent' | grep -v grep | cut -c1-300 > "$OUT/processes-30s.txt"
  [ $t -eq 120 ] && ps -eo pid,args | grep -iE 'battle|agent' | grep -v grep | cut -c1-300 > "$OUT/processes-120s.txt"
done
cp -r "$WINEPREFIX/drive_c/users/$USER/AppData/Local/Battle.net/Logs" "$OUT/bnet-logs" 2>/dev/null || true
cp -r "$WINEPREFIX/drive_c/ProgramData/Battle.net/Agent/Agent."*/Logs "$OUT/agent-logs" 2>/dev/null || true
wineserver -k
echo "== done"; ls -la "$OUT"
