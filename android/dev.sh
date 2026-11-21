#!/usr/bin/env bash
#
# Run the Android build inside a Docker image that already contains the JDK,
# Android SDK and build tools. Nothing Android-related needs to be installed on
# the host.
#
#   android/dev.sh                          # assembleDebug (default)
#   android/dev.sh ./gradlew :app:testDebugUnitTest
#   android/dev.sh ./gradlew :app:lintDebug
#   android/dev.sh bash                     # interactive shell in the image
#
# Gradle caches and the wrapper distribution live under ~/.cache/selfdrop/android
# so repeated builds do not re-download the world.
set -euo pipefail

IMAGE="${SELFDROP_ANDROID_IMAGE:-ghcr.io/cirruslabs/android-sdk:35}"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CACHE_ROOT="${SELFDROP_ANDROID_CACHE:-${XDG_CACHE_HOME:-$HOME/.cache}/selfdrop/android}"
GRADLE_CACHE="$CACHE_ROOT/gradle"
HOME_CACHE="$CACHE_ROOT/home"

mkdir -p "$GRADLE_CACHE" "$HOME_CACHE"

if [ "$#" -eq 0 ]; then
  set -- ./gradlew --no-daemon :app:assembleDebug
fi

if ! docker image inspect "$IMAGE" >/dev/null 2>&1; then
  echo "Pulling $IMAGE ..."
  docker pull "$IMAGE"
fi

TTY_FLAGS=()
if [ -t 0 ] && [ -t 1 ]; then
  TTY_FLAGS=(-t)
fi

exec docker run --rm -i "${TTY_FLAGS[@]}" \
  --user "$(id -u):$(id -g)" \
  --env HOME=/cache/home \
  --env GRADLE_USER_HOME=/cache/gradle \
  --env XDG_CACHE_HOME=/cache/home/.cache \
  --volume "$HERE:/workspace" \
  --volume "$GRADLE_CACHE:/cache/gradle" \
  --volume "$HOME_CACHE:/cache/home" \
  --workdir /workspace \
  "$IMAGE" \
  "$@"
