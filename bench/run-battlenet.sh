#!/usr/bin/env bash
# Battle.net test bench: installs and starts Battle.net under Wine ARM64EC + FEX on an ARM64 Linux
# runner with a virtual display, then screenshots it. Not Android, but the same Windows-side stack.
set -uo pipefail
OUT="${OUT:-$PWD/bench-out}"; mkdir -p "$OUT"
export DISPLAY=:99 WINEPREFIX="$HOME/bnet" WINEDEBUG="${WINEDEBUG:-fixme-all}" WINEDLLOVERRIDES="locationapi=d"
Xvfb :99 -screen 0 1920x864x24 >/dev/null 2>&1 &
sleep 2
shot() { import -window root "$OUT/$1.png" 2>/dev/null || true; }

echo "== wine: $(wine --version)"
wineboot -i >"$OUT/wineboot.log" 2>&1; wineserver -w

mkdir -p "$WINEPREFIX/drive_c/users/$USER/AppData/Roaming/Battle.net"
echo '{"Client":{"HardwareAcceleration":"false","Streaming":{"StreamingEnabled":"false"},"GameLaunchWindowBehavior":"2"}}' \
  > "$WINEPREFIX/drive_c/users/$USER/AppData/Roaming/Battle.net/Battle.net.config"

curl -fsSL -o "$HOME/setup.exe" "https://downloader.battle.net/download/getInstallerForGame?os=win&gameProgram=BATTLENET_APP&version=Live"
BNET="$WINEPREFIX/drive_c/Program Files (x86)/Battle.net/Battle.net.exe"
echo "== installer (HODLL=${SETUP_HODLL:-libwow64fex.dll})"
HODLL="${SETUP_HODLL:-libwow64fex.dll}" wine "$HOME/setup.exe" --lang=enUS >"$OUT/setup.log" 2>&1 &
for i in $(seq 1 60); do [ -f "$BNET" ] && break; sleep 10; [ $((i % 6)) -eq 0 ] && shot "setup-$i"; done
sleep 30; shot setup-done
wineserver -k; sleep 3
[ -f "$BNET" ] || { echo "Battle.net not installed"; exit 1; }

echo "== Battle.net (HODLL=${BNET_HODLL:-libwow64fex.dll}) args: ${BNET_ARGS:-}"
HODLL="${BNET_HODLL:-libwow64fex.dll}" wine "$BNET" ${BNET_ARGS:-} >"$OUT/battlenet.log" 2>&1 &
for t in 30 60 90 120 180; do sleep 30; [ $t -le 60 ] || sleep $((t - 60 > 30 ? 0 : 0)); shot "bnet-${t}s"; done
cp -r "$WINEPREFIX/drive_c/users/$USER/AppData/Local/Battle.net/Logs" "$OUT/bnet-logs" 2>/dev/null || true
cp -r "$WINEPREFIX/drive_c/ProgramData/Battle.net/Agent/Agent."*/Logs "$OUT/agent-logs" 2>/dev/null || true
wineserver -k
echo "== done"; ls -la "$OUT"
