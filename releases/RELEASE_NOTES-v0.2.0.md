# Selfdrop Android v0.2.0

First published build of the native Selfdrop Android client.

## Highlights

- Kotlin + Jetpack Compose client for the Selfdrop HTTP API and SSE stream.
- Server discovery through the saved address, rendezvous Worker, `/24` LAN
  scan, `selfdrop://pair` QR code, or manual address entry, with `/ping`
  server-id verification and a confirmation before switching servers.
- Chats drawer with create, rename and delete.
- Text, link, image, video and file timeline with live SSE updates.
- Click-to-load image and video previews, like the web UI, so opening a chat
  does not download every preview.
- Inline video with ExoPlayer in a TextureView: one video plays at a time, and
  video surfaces are removed while the drawer is open.
- Text sending and streaming multi-file uploads with a progress bar.
- Share-sheet targets for text and files, with WorkManager queuing and retry.
- Resumable downloads with a progress bar. Files are saved directly into the
  phone's public `Downloads` folder, with no Selfdrop subdirectory.
- Copy, delete, download and open actions, plus clear connection states.

## Install

- Minimum Android 8.0 (API 26); target SDK 35.
- Install with:

  ```bash
  adb install -r selfdrop-v0.2.0.apk
  ```

- If a debug build of `com.projects.selfdrop` is already installed, uninstall it
  first: release builds use a different signing key.
- The server does not need an update. Pair with it by QR code, the LAN
  rendezvous Worker, automatic discovery, or its address; the default server
  API is unchanged.

## Release file

```text
1168f5fa2db13b68f38e1975cab5ae5ff289b4626d6f3d6d361b741e532b7dc9  selfdrop-v0.2.0.apk
```
