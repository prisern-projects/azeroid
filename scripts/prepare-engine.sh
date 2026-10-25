#!/usr/bin/env bash
# Builds the source tree that Gradle compiles:
#   1. checks out the pinned engine into ./engine
#   2. fetches pinned native deps + the verified imagefs
#   3. copies Azeroid's own code (./overlay) on top and wires it in
# Safe to re-run; it always starts from a clean engine checkout.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck disable=SC1091
source "$ROOT/engine.properties"
ENGINE="$ROOT/engine"

retry() { local n=0; until "$@"; do n=$((n+1)); [ $n -ge 4 ] && return 1; sleep $((2**n)); done; }

echo "==> Engine $ENGINE_REPO @ $ENGINE_COMMIT"
rm -rf "$ENGINE"
mkdir -p "$ENGINE"
git -C "$ENGINE" init -q
git -C "$ENGINE" remote add origin "$ENGINE_REPO"
retry git -C "$ENGINE" fetch -q --depth 1 origin "$ENGINE_COMMIT"
git -C "$ENGINE" -c advice.detachedHead=false checkout -q FETCH_HEAD

echo "==> Native dependencies"
CPP="$ENGINE/app/src/main/cpp"
rm -rf "$CPP/adrenotools"
retry git clone -q "$ADRENOTOOLS_REPO" "$CPP/adrenotools"
git -C "$CPP/adrenotools" -c advice.detachedHead=false checkout -q "$ADRENOTOOLS_COMMIT"
mkdir -p "$CPP/adrenotools/lib"
retry git clone -q "$LINKERNSBYPASS_REPO" "$CPP/adrenotools/lib/linkernsbypass"
git -C "$CPP/adrenotools/lib/linkernsbypass" -c advice.detachedHead=false checkout -q "$LINKERNSBYPASS_COMMIT"

echo "==> ImageFS"
ASSETS="$ENGINE/app/src/main/assets"
CACHE="${AZEROID_CACHE:-$ROOT/build/cache}"
mkdir -p "$CACHE"
IMG="$CACHE/imagefs-$IMAGEFS_SHA256.tar.zst"
if ! echo "$IMAGEFS_SHA256  $IMG" | sha256sum -c --status - 2>/dev/null; then
  rm -f "$IMG"
  for ((i = 0; i < IMAGEFS_PARTS; i++)); do
    part=$(printf "%02d" "$i")
    retry curl -fsSL "${IMAGEFS_URL_PREFIX}${part}" -o "$CACHE/imagefs.part"
    cat "$CACHE/imagefs.part" >> "$IMG"
  done
  rm -f "$CACHE/imagefs.part"
  echo "$IMAGEFS_SHA256  $IMG" | sha256sum -c -
fi
cp "$IMG" "$ASSETS/imagefs.tar.zst"

echo "==> Vulkan validation layer"
VVL="$CACHE/vvl-$VVL_VERSION.tar.gz"
if ! echo "$VVL_SHA256  $VVL" | sha256sum -c --status - 2>/dev/null; then
  retry curl -fsSL "https://github.com/KhronosGroup/Vulkan-ValidationLayers/releases/download/vulkan-sdk-$VVL_VERSION/android-binaries-$VVL_VERSION.tar.gz" -o "$VVL"
  echo "$VVL_SHA256  $VVL" | sha256sum -c -
fi
rm -rf "$CACHE/vvl" && mkdir -p "$CACHE/vvl"
tar -xzf "$VVL" -C "$CACHE/vvl"
LAYER="$(find "$CACHE/vvl" -type f -path '*/arm64-v8a/libVkLayer_khronos_validation.so' -print -quit)"
test -n "$LAYER"
mkdir -p "$ENGINE/app/src/main/jniLibs/arm64-v8a"
cp "$LAYER" "$ENGINE/app/src/main/jniLibs/arm64-v8a/"

echo "==> Azeroid overlay"
cp -R "$ROOT/overlay/." "$ENGINE/"
python3 "$ROOT/scripts/patch_engine.py" "$ENGINE"

echo "==> Ready: $ENGINE"
