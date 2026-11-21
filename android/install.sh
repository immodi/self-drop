#!/usr/bin/env bash
#
# Install the debug APK on a USB-connected Android device using adb from the
# same SDK image that builds the app. Nothing Android-related is installed on
# the host.
#
#   android/install.sh
#   android/install.sh maybe/path/to/app.apk
#
# Linux only: the container needs access to /dev/bus/usb. The first run creates
# an adb key in ~/.cache/selfdrop/android/adb; accept the USB debugging prompt
# on the device once.
set -euo pipefail

IMAGE="${SELFDROP_ANDROID_IMAGE:-ghcr.io/cirruslabs/android-sdk:35}"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CACHE_ROOT="${SELFDROP_ANDROID_CACHE:-${XDG_CACHE_HOME:-$HOME/.cache}/selfdrop/android}"
ADB_HOME="$CACHE_ROOT/adb"
APK="${1:-$HERE/app/build/outputs/apk/debug/app-debug.apk}"

if [ ! -f "$APK" ]; then
  echo "APK not found: $APK"
  echo "Run 'make android-build' first."
  exit 1
fi

if [ ! -d /dev/bus/usb ]; then
  echo "/dev/bus/usb is not available on this host."
  exit 1
fi

mkdir -p "$ADB_HOME"

if ! docker image inspect "$IMAGE" >/dev/null 2>&1; then
  echo "Pulling $IMAGE ..."
  docker pull "$IMAGE"
fi

case "$APK" in
  "$HERE"/*) CONTAINER_APK="/workspace/${APK#"$HERE"/}" ;;
  *) CONTAINER_APK="/apk/$(basename "$APK")" ;;
esac

TTY_FLAGS=()
if [ -t 0 ] && [ -t 1 ]; then
  TTY_FLAGS=(-t)
fi

exec docker run --rm -i "${TTY_FLAGS[@]}" \
  --privileged \
  --network host \
  -v /dev/bus/usb:/dev/bus/usb \
  -v "$HERE:/workspace:ro" \
  -v "$(cd "$(dirname "$APK")" && pwd):/apk:ro" \
  -v "$ADB_HOME:/root/.android" \
  "$IMAGE" \
  bash -lc "
    set -e
    adb start-server >/dev/null
    if ! timeout 60 adb wait-for-device; then
      echo 'No device appeared. Connect the phone with USB debugging enabled.'
      adb devices -l || true
      exit 1
    fi
    state=\$(adb get-state 2>/dev/null || true)
    if [ \"\$state\" != \"device\" ]; then
      echo \"Device state is '\$state'. Unlock the phone and accept the USB debugging prompt.\"
      adb devices -l || true
      exit 1
    fi
    adb install -r '$CONTAINER_APK'
    adb shell am start -W -n com.projects.selfdrop/com.projects.selfdrop.android.MainActivity
  "
