# Selfdrop for Android

A native Kotlin client for the Selfdrop server. It is built with Jetpack Compose
and talks to the same HTTP API and SSE stream as the browser UI.

The Android toolchain is **not** installed on the host. Everything compiles
inside a Docker image that already ships the JDK, Android SDK 35 and build tools.

## Build and test

Docker is the only host requirement:

```bash
make android-image    # pull ghcr.io/cirruslabs/android-sdk:35 once
make android-build    # debug APK
make android-test     # JVM unit tests
make android-lint     # Android lint
make android-release  # unsigned release APK
make android-install  # install on a USB device (Linux)
make android-shell    # interactive shell in the build image
```

Outputs:

```text
android/app/build/outputs/apk/debug/app-debug.apk
android/app/build/outputs/apk/release/app-release-unsigned.apk
```

The debug APK is signed with the Android debug key and can be installed
directly. The release APK is unsigned; configure a signing config in
`app/build.gradle.kts` before distributing it.

Install a debug build with the platform tools on any machine that has `adb`:

```bash
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
```

On Linux with a USB device, `make android-install` runs `adb` inside the same
SDK container, so `adb` does not need to be installed on the host. It keeps the
adb key under `~/.cache/selfdrop/android/adb`; accept the USB debugging prompt on
the phone the first time. The phone must have USB debugging enabled.

### How the container build works

`android/dev.sh` runs the official SDK image (`ghcr.io/cirruslabs/android-sdk:35`)
as the host user, mounts this directory at `/workspace`, and keeps the Gradle
wrapper distribution and dependency caches under `~/.cache/selfdrop/android`.
Repeated builds are incremental.

```bash
android/dev.sh                                   # assembleDebug
android/dev.sh ./gradlew :app:testDebugUnitTest  # any Gradle task
android/dev.sh bash                              # inspect the image
```

Override the image or cache location with `SELFDROP_ANDROID_IMAGE` and
`SELFDROP_ANDROID_CACHE`.

## Release builds

`make android-release` produces a signed APK when `android/keystore.properties`
exists. That file and `android/keystore/` are git-ignored and must never be
committed.

The v0.2.0 release key was generated once and backed up outside the repository:

```text
~/.config/selfdrop/android-release.jks
~/.config/selfdrop/android-release.properties
```

Keep that keystore safe. Android only accepts updates signed with the same key,
so losing it means future releases cannot be installed over this one.

Published APKs and checksums live in `releases/`. Pushing a `v*` tag runs
`.github/workflows/release.yml`, which verifies the checksum and creates or
updates the corresponding GitHub release.

## What the app does

- **Pairing and discovery.** Cached address → rendezvous Worker → `/24` LAN scan,
  always checking `/ping` against the stored server id. QR scanning
  (`selfdrop://pair?u=…&id=…`), manual address entry, and trust-on-first-use are
  also supported. A different server id asks before switching.
- **Chats.** Create, rename and delete rooms from the navigation drawer. The
  last selected chat is remembered.
- **Timeline.** Text, links, images, videos and files, oldest at the bottom.
  Links open in the browser. Images and videos show a click-to-load preview
  button, matching the browser UI, so opening a chat does not fetch every
  preview. Images open full-screen; videos play inline through `/preview/{id}`
  with ExoPlayer in a TextureView. Only one inline video plays at a time, and
  video surfaces are removed while the chat drawer is open so they cannot draw
  over the menu.
- **Live updates.** One SSE connection on `/messages`, filtered by chat. It
  reconnects with backoff, stops in the background, and reloads the chat on
  resume because deletes and updates are not replayed by the server.
- **Sending.** Text goes to `/upload/text`; the attachment picker streams files
  to `/upload/file` with a determinate progress bar. Shared items from other
  apps are queued through WorkManager so they survive the activity going away
  and retry with backoff when the network is unavailable.
- **Receiving.** Downloads show a progress bar, resume with HTTP `Range`, and
  are saved directly into the phone's public `Downloads` folder (no
  `Selfdrop/` subfolder). Android 10+ uses MediaStore, so no storage permission
  is needed; Android 9 and older request the legacy write permission. Saved
  files open through their MediaStore or `FileProvider` URI.
- **Offline state.** The top bar shows Connecting/Live/Reconnecting/Offline, and
  the pairing screen has an explicit "can't find your server" state with Retry,
  QR scan and manual entry.

## Project layout

```text
android/
├── dev.sh                     Docker build wrapper
├── app/src/main/java/com/projects/selfdrop/android/
│   ├── MainActivity.kt        Share targets, selfdrop:// deep links, lifecycle
│   ├── data/
│   │   ├── Models.kt          Item, Chat, events, share payloads
│   │   ├── SelfdropApi.kt     OkHttp API client, streaming multipart, resume
│   │   ├── SseClient.kt       Small SSE reader with cancellation
│   │   ├── Discovery.kt       Cached/Worker/subnet discovery and pairing URLs
│   │   ├── DownloadsPublisher.kt  MediaStore/legacy public Downloads writer
│   │   ├── SettingsStore.kt   DataStore: address, server id, Worker, chat
│   │   ├── UploadWorker.kt    WorkManager retry path for share-sheet uploads
│   │   └── Http.kt            Shared OkHttp client
│   ├── ui/                    Compose screens, drawer, timeline, composer
│   └── util/                  Link rendering, byte/time formatting
└── app/src/test/              Pairing and filename unit tests
```

## Configuration

The default rendezvous Worker is the reference deployment:

```text
https://selfdrop-rendezvous.ay727468a.workers.dev/r
```

Change it in the pairing screen under **Advanced**, or later under the server
settings button in the top bar. Leave it empty to rely only on the saved
address, LAN scan and QR/manual entry. Only the public record URL is used; the
Worker write token never leaves the server.

Cleartext HTTP is enabled explicitly because Selfdrop serves plain HTTP on the
LAN. Do not expose the server to the internet.

Android 16+ may require the local-network runtime permission; the app requests
it when the platform reports that permission.

## Deliberate gaps

- No offline mirror of the timeline; the server is the only store.
- No client-side upload de-duplication (the API has no idempotency key).
- Metadata editing (`PUT /file`) is not exposed in the first release.
- One server at a time. Switching servers is supported through pairing.
- Videos use Media3/ExoPlayer in a TextureView; only one inline video plays at
  a time.
