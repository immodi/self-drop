# Selfdrop

Selfdrop is a small, self-hosted inbox for your own devices. Drop text, links, images, or files from one device and pick them up on another over your local network. It runs as one Spring Boot application and ships with its own web UI — no separate frontend build, no accounts, and no cloud storage. An optional Cloudflare Worker only publishes the server's current LAN address so clients can find it again.

## Highlights

- Multiple chat rooms, each with its own timeline of text, links, images, and files
- Multi-file uploads with streaming writes and atomic renames
- File downloads streamed straight from disk
- Built-in responsive web UI served by the backend
- SQLite metadata and on-disk file storage
- Persistent server identity, exposed by a health endpoint
- JSON errors with consistent `ProblemDetail` responses (`416` ranges are header-only)
- Full controller, repository, and storage test suite

## Web UI

Open the application in a browser and use the built-in chat UI:

- Create as many chat rooms as you need and switch between them from a collapsible sidebar, like a chat app
- Rename a chat inline from its sidebar row; deleting a chat removes its items and files
- Every item belongs to exactly one chat
- Text and links render inline; links are clickable
- Images use the full card width on phones and open in a full-screen viewer; videos play inline with a dedicated fullscreen button
- Compose box with Enter to send and Shift+Enter for a newline
- Drag and drop, paste-a-screenshot, and a multi-file picker
- New items appear live without a manual refresh
- Copy, delete, and download actions on every item
- Connection status with automatic recovery
- Responsive layout for desktop and phone browsers

The UI is served from `/` by the backend itself, so opening `http://localhost:8080/` (or the machine's LAN address) is enough.

The page is rendered with Thymeleaf and enhanced with a vendored copy of htmx. Styles are a prebuilt Tailwind CSS file committed as `src/main/resources/static/css/style.css` and generated from `tailwind/input.css` with `make css`; the web UI deliberately keeps no `package.json`, `node_modules`, or other Node build files. The Cloudflare Worker lives in its own `worker/` project (see below).

## Address discovery

The backend keeps the address it currently advertises in `AddressService`. It detects the LAN address at startup and re-checks it every 30 seconds, so the footer and the `/pair` page share one stored value instead of resolving it separately.

The `/pair` fallback page offers a selector between two QR codes: the `selfdrop://pair?u=<url>&id=<server-id>` deep link and the plain server address. Each has a copy button. The codes poll themselves every 10 seconds, so a change to the stored address shows up without a reload. When no LAN address can be detected, the page warns that the codes only point at the server itself.

The backend publishes the address it advertises to a Cloudflare Worker + KV, so clients can find the server again after its LAN IP changes:

- The Java application owns publication: on startup and on a periodic check (every 30 seconds by default), `WorkerPublisher` writes the `{ url, id, updated }` record to the Worker when the stored address or server id changed. There is no separate updater script or sidecar.
- Publication is opt-in. It runs only when `worker.url` and `worker.token` are configured; an app without a Worker keeps working exactly as before.
- The last successfully published values are kept in `./data/worker-state.json`, so restarts and unchanged addresses cause no writes. The Worker also skips unchanged records.
- The record is publicly readable and contains only the LAN URL, server id, and update timestamp. The Worker rejects anything that is not `http://<private IPv4>:<port>`, so a leaked write token cannot redirect clients to a public host.
- The Worker write token stays in the backend environment (`WORKER_TOKEN`), never in the page or its responses.
- The built-in settings page at `/settings` shows the current address and server id and lets the machine's owner save a manual override. It is loopback-only, and the JSON endpoints it uses (`GET`/`POST /settings/address`) enforce the same rule.

### Running the Worker

The Worker is a dependency-free ES module with its own KV namespace, in `worker/`:

```bash
cd worker
pnpm install
pnpm test                 # node:test, no Cloudflare account needed
pnpm exec wrangler dev    # local Worker on http://localhost:8787
pnpm exec wrangler deploy
pnpm exec wrangler secret put WRITE_TOKEN
```

`wrangler.toml` points at the `selfdrop-rendezvous` Worker and its `RECORD` KV namespace. The deployed record endpoint is:

```text
https://selfdrop-rendezvous.ay727468a.workers.dev/r
```

`WRITE_TOKEN` is a Worker secret; the same value goes in the backend environment:

```bash
# .env (git-ignored), loaded automatically by `make run`
WORKER_URL=https://selfdrop-rendezvous.<account>.workers.dev/r
WORKER_TOKEN=<the same secret>
```

The Worker endpoints are:

| Method | Path | Description |
| --- | --- | --- |
| `GET` | `/r` | Public record (`404` before the first publish), CORS `*`, `Cache-Control: no-store` |
| `HEAD` | `/r` | Same headers as `GET`, without a body |
| `PUT` | `/r` | Bearer-token write of `{"url":"http://<private IPv4>:<port>","id":"<server-id>"}` |
| `OPTIONS` | `/r` | CORS preflight, `204` |
| `GET` | `/` | Alias for `/r` |

The record is `{ "url", "id", "updated" }`. Rejected writes return `401` (bad token), `413` (body over 1 KiB), `422` (invalid URL/id), `400` (bad JSON), or `500` (Worker not configured). An unchanged `url`/`id` returns `{ "changed": false, "record": ... }` without writing KV.

## API

The full client contract, including SSE framing, discovery, and Android guidance, is in [spec.md](spec.md). This section is the quick reference.

Full item responses use this shape (the text-upload response is only `{"text":"..."}`):

```json
{
  "seq": 42,
  "id": "8f14e45f-ea4b-4c3a-9f6e-0d3f6c2b1a77",
  "chatId": "3c1d8d7a-5f60-4a7d-9f2e-1b6a0c4d8e21",
  "kind": "file",
  "text": null,
  "name": "photo.jpg",
  "mime": "image/jpeg",
  "size": 183204,
  "createdAt": 1761300000
}
```

`chatId` is the owning chat. `kind` is either `text` or `file`. `createdAt` is Unix epoch **seconds**. `seq` is the SQLite sequence and is the cursor for `after=` and SSE. Item ids are UUID strings. For text items, `size` is Java `String.length()`; for file items it is the uploaded byte count and may not match the bytes on disk.

Chat objects use `{"seq":1,"id":"<uuid>","name":"General","createdAt":1761300000}`.

| Method | Path | Description |
| --- | --- | --- |
| `GET` | `/ping` | Server identity: `{"id":"<server-id>"}` |
| `GET` | `/chat` | All chats, oldest first |
| `GET` | `/chat/{id}` | One chat |
| `POST` | `/chat` | Create a chat from `{"name":"..."}` (201, `Location: /chat/<id>`) |
| `PUT` | `/chat` | Rename a chat from `{"id":"...","name":"..."}` |
| `DELETE` | `/chat/{id}` | Delete a chat, its items, and their files (204 No Content) |
| `GET` | `/file` | All items across chats, oldest first by `createdAt` then `seq` |
| `GET` | `/file?chatId=<id>` | Items in one chat, oldest first |
| `GET` | `/file?chatId=<id>&limit=N` | Newest `N` items in one chat (`N` is clamped to 1–100) |
| `GET` | `/file?chatId=<id>&after=<seq>` | Items in one chat with `seq > <seq>` |
| `GET` | `/file/{id}` | One item's metadata |
| `PUT` | `/file` | Replace an item's kind, text, name, mime, and size |
| `DELETE` | `/file/{id}` | Remove an item (204 No Content) |
| `POST` | `/upload/text?chatId=<id>` | Create a text item in a chat from a raw `text/plain` body |
| `POST` | `/upload/file?chatId=<id>` | Upload one or more multipart `files` to a chat |
| `GET` | `/download/{id}` | Download the stored file as an attachment; supports single byte ranges |
| `GET` | `/preview/{id}` | Inline preview for raster images and videos (SVG is excluded) |
| `GET` | `/messages` | Server-Sent Events stream for live updates |
| `GET` | `/settings/address` | Current advertised address (loopback only) |
| `POST` | `/settings/address` | Save an address override (loopback only) |

`after` takes precedence when both `after` and `limit` are supplied. The `after` response is ordered by sequence; the `limit` response is newest-first. `GET /file` is a bare array, not an envelope. Omitting `chatId` lists every item; passing an unknown `chatId` returns `404`.

There is no authentication, CORS, Origin check, storage quota, or automatic expiry. Anyone who can reach port 8080 can read, post, update, and delete items. Keep the server on the LAN.

### Uploads

```bash
# Create a chat first (the response contains its id)
curl -X POST http://localhost:8080/chat \
     -H 'Content-Type: application/json' \
     -d '{"name":"General"}'

# Text or link
curl -X POST 'http://localhost:8080/upload/text?chatId=<chat-id>' \
     -H 'Content-Type: text/plain' \
     --data 'https://example.com'

# One or more files
curl -X POST 'http://localhost:8080/upload/file?chatId=<chat-id>' \
     -F 'files=@photo.jpg' \
     -F 'files=@notes.pdf'
```

Text uploads return `201 Created` with `Location: /file/<id>` and a body of only `{"text":"..."}`. Use `Location`, `GET /file/{id}`, or the SSE stream to get the full item. The text body is required; an empty body returns `400`. File uploads return `200 OK` with a bare array of full items, in upload order. The multipart part name must be `files`; repeat it for multiple files. Empty file parts are skipped. Missing `files` returns `400`. Both upload endpoints require `chatId`; omitting it returns `400`, and an unknown chat returns `404`.

Uploads are streamed to disk, not buffered in memory. A failed multi-file upload rolls back the rows and bytes it created. There is no idempotency key, so retrying an ambiguous upload can create duplicates.

### Download

```bash
curl -OJ http://localhost:8080/download/<id>
```

Downloads are streamed from disk, sent as an attachment with the original filename, and include `X-Content-Type-Options: nosniff` and a sandboxing `Content-Security-Policy`. For a full response, `Content-Length` is the actual stored file size, not the metadata `size`; a `206` response's `Content-Length` is the partial body length and `Content-Range` carries the total. Responses advertise `Accept-Ranges: bytes` and support single byte-range requests, so interrupted downloads can be resumed:

```bash
curl -C - -OJ http://localhost:8080/download/<id>
```

A satisfiable range returns `206 Partial Content` with `Content-Range`; an unsatisfiable range returns `416` with `Content-Range: bytes */<size>`. Multiple or malformed ranges fall back to the full file with `200`. There is no `ETag` or `Last-Modified`.

`GET /preview/{id}` serves raster images and videos inline for the timeline; videos use the browser's native player. SVG is deliberately excluded, and every non-previewable type returns `404` there; use `/download/{id}` instead. Preview responses are streamed from disk with `Content-Length` from the stored file size and support the same single byte ranges as downloads, so video playback can seek. The web UI shows a placeholder for each image or video and only requests the preview after you press its preview button, so opening a chat does not download every preview up front.

### Update

```bash
curl -X PUT http://localhost:8080/file \
     -H 'Content-Type: application/json' \
     -d '{"id":"<id>","kind":"text","text":"updated","name":null,"mime":null,"size":7}'
```

### Live updates (SSE)

```http
GET /messages?since=<seq>
Accept: text/event-stream
```

`/messages` is the live-update stream. The optional `since` query and the standard `Last-Event-ID` header set the cursor; the header wins when it parses as a number. The server sends an immediate `heartbeat`, replays existing items with `seq > since` as `item-added`, then streams live events and a heartbeat every 10 seconds.

Events are named `item-added`, `item-updated`, and `item-deleted`. The SSE `id:` is the item's `seq`, and the `data:` line is a JSON `FileEvent`:

```json
{"type":"item-added","id":"<item-id>","seq":42,"item":{...}}
```

`item` is the full item for adds/updates and `null` for deletes. Every event carries `chatId`, so a client can filter the stream to the open chat. Replay only covers adds, so a client that was offline can miss deletes and updates. Reload `GET /file?chatId=<id>` on reconnect or foreground resume, then apply events.

### Settings

The settings page at `/settings` and the JSON endpoints at `/settings/address` are loopback-only. `GET /settings/address` returns:

```json
{"url":"http://192.168.1.50:8080","serverId":"<server-id>","localOnly":false}
```

`POST /settings/address` accepts `{"url":"http://<private IPv4>:<port>"}`. It trims the value, requires plain HTTP on a private IPv4 literal with an explicit port and no path beyond `/`, query, or fragment, and returns the same shape. The override lasts until the server restarts; the Worker publisher is asked to publish it immediately. Non-loopback callers get `403`.

### Errors

Most errors are RFC 9457 `ProblemDetail` responses; the `416` download-range response has headers but no body. Validation failures include an `errors` map:

```json
{
  "type": "about:blank",
  "title": "Validation failed",
  "status": 400,
  "detail": "Validation failed.",
  "errors": {
    "kind": "must not be blank"
  }
}
```

The API returns `400` for invalid input, `403` for non-loopback settings access, `404` for missing items or chats, `405` for unsupported methods, `413` for oversized uploads, `415` for unsupported content types, `416` for unsatisfiable download ranges, and `500` for unexpected failures. There is no `401`; the API has no authentication.

## Android client

The Android app is a separate client of this HTTP API. The important pieces are:

- Use plain HTTP to `http://<host>:8080`; Android needs a cleartext network security config, and newer versions may also require a local-network permission.
- Discover the server with a cached address, the Worker record, an optional `/24` scan on port 8080, or the `selfdrop://pair?u=<url>&id=<id>` QR link. Verify `/ping`'s `id` before using an address.
- Load the chat list with `GET /chat`, then load the open chat with `GET /file?chatId=<id>` and stream `/messages?since=<seq>` while in the foreground. Handle `item-added`, `item-updated`, `item-deleted`, and `heartbeat`, filtering events by `item.chatId`; reload the open chat on reconnect because deletes are not replayed.
- Upload text with `POST /upload/text?chatId=<id>` (raw body) and files with multipart `POST /upload/file?chatId=<id>` using repeated `files` parts. Stream from `content://` URIs; do not buffer whole files.
- Download with `/download/{id}` and resume using `Range`; use `/preview/{id}` for raster images and videos. Use `Content-Length` for a full response and `Content-Range` for the total on a `206`, not the metadata `size`.
- There is no authentication, no idempotency key, and no upload quota. Keep the server on the LAN.

See [spec.md](spec.md) for the complete request/response contract, SSE framing, discovery sequence, and Android acceptance criteria.

## Storage and persistence

- Chat metadata lives in SQLite (default `./data/app.db`), table `chats`
- Item metadata lives in SQLite, table `files`, with a `chat_id` foreign key
- File bytes live under `storage.root` (default `./data/files`), named by item id
- The server id is stored in `./data/server-id` and survives restarts
- The last published Worker record is remembered in `./data/worker-state.json`
- Uploads stream into a temporary file and are atomically renamed into place
- Downloads stream directly from disk without loading whole files into memory
- A scheduled orphan sweep removes files with no database row and abandoned `.upload-*.tmp` files (default: every 30 minutes, with a 30-minute grace period)
- There is no automatic expiry job and no total-storage quota

## Configuration

Defaults are in `src/main/resources/application.properties`:

| Property | Default | Purpose |
| --- | --- | --- |
| `server.address` | `0.0.0.0` | Bind address (all interfaces) |
| `server.port` | `8080` | HTTP port |
| `spring.mvc.async.request-timeout` | `-1` | No timeout for streamed responses |
| `spring.datasource.url` | `jdbc:sqlite:./data/app.db` | SQLite database |
| `storage.root` | `./data/files` | Stored file bytes |
| `storage.sweep.grace-seconds` | `1800` | Orphan/temp-file grace period |
| `storage.sweep.interval-seconds` | `1800` | Orphan/temp sweep interval |
| `server-id-path` | `./data/server-id` | Persistent server identity |
| `address.refresh-millis` | `30000` | LAN re-detection interval |
| `spring.servlet.multipart.max-file-size` | `2GB` | Per-file limit |
| `spring.servlet.multipart.max-request-size` | `10GB` | Total request limit |
| `spring.servlet.multipart.file-size-threshold` | `0` | Write uploads straight to disk |
| `spring.servlet.multipart.location` | `/var/tmp/selfdrop-uploads` | Multipart temp directory |
| `worker.url` | empty (`WORKER_URL`) | Rendezvous Worker endpoint; empty disables publishing |
| `worker.token` | empty (`WORKER_TOKEN`) | Worker write token, kept server-side |
| `worker.state-path` | `./data/worker-state.json` | Last successfully published values |
| `worker.publish-interval-millis` | `30000` | How often to check for an address change |
| `worker.publish-initial-delay-millis` | `3000` | Initial publish delay after startup |

## Common commands

A `Makefile` wraps the day-to-day workflows:

| Command | What it does |
| --- | --- |
| `make run` | Start the app on `http://localhost:8080/` |
| `make dev` | Run the app and Tailwind watch mode together |
| `make test` | Run the test suite (`make check` is an alias) |
| `make build` | Compile, test, and assemble the application |
| `make css` | Regenerate `src/main/resources/static/css/style.css` |
| `make css-watch` | Regenerate the CSS on every template/JS change |
| `make clean` | Remove Gradle build output |
| `make clean-data` | Remove `./data` (database and stored files) |
| `make clean-tailwind` | Remove the cached Tailwind CLI |

The Worker has its own commands, run inside `worker/`:

| Command | What it does |
| --- | --- |
| `pnpm test` | Run the Worker unit tests with Node's test runner |
| `pnpm exec wrangler dev` | Run the Worker locally |
| `pnpm exec wrangler deploy` | Deploy the Worker to Cloudflare |
| `pnpm exec wrangler secret put WRITE_TOKEN` | Set the write token as a Worker secret |

`make css` installs the Tailwind CLI into `~/.cache/selfdrop/tailwind-<version>` (override with `TAILWIND_CACHE=...`) and writes only the committed CSS file, so no `package.json`, `node_modules`, or other Node files appear in the repository.

## Run locally

Requires JDK 27. CSS changes also need Node.js with npm.

```bash
make run
# or: ./gradlew bootRun
```

Then open:

```
http://localhost:8080/
```

The first start creates the storage directory and the persistent server id. If a `.env` file exists in the project root, Spring loads it, so `WORKER_URL` and `WORKER_TOKEN` work with both `make run` and `./gradlew bootRun`; leave them out and Worker publishing stays disabled.

The multipart temp directory (`/var/tmp/selfdrop-uploads` by default) must exist and be writable. The Docker image creates it; for a local run, create it or override `spring.servlet.multipart.location`.

The app listens on every interface, and the footer shows the detected LAN URL (for example `http://192.168.1.104:8080/`) so you can open it from other devices on the same network. When running in Docker, use host networking (`--network host`) so the resolver can see the host's interfaces.

## Docker

Build and start the container with Compose:

```bash
docker compose up -d --build
docker compose logs -f
docker compose down
```

The container uses `network_mode: host`, so it binds directly to the host's interfaces and the detected LAN address is the host's real address. It mounts `./data` at `/app/data`, reads `WORKER_URL` and `WORKER_TOKEN` from the shell or `.env`, and uses `restart: unless-stopped`, so it starts again whenever Docker starts, including after a reboot.

Make sure the Docker daemon itself starts at boot. On Linux with systemd:

```bash
sudo systemctl enable --now docker
```

On Docker Desktop, enable **Start Docker Desktop when you log in**. An explicit `docker compose stop` keeps the container stopped until you start it again; `docker compose down` removes it entirely.

## Tests

```bash
make test
# or: ./gradlew test
```

The suite covers the controllers, upload/download/preview flows, the server-rendered UI, repository queries, file storage, server-id persistence, and error handling.

## How it works

- **Controllers** expose the JSON API, multipart upload, download/preview, SSE, settings, health, and server-rendered UI endpoints.
- **UI** is rendered with Thymeleaf, enhanced with vendored htmx, and refreshed from the SSE stream as items change.
- **Live updates** use one single-threaded broadcaster, per-connection emitters, replay from `seq`, and a 10-second heartbeat.
- **Services** map between entities and DTOs, coordinate storage, manage the persistent server id, detect the advertised LAN address, and publish changes to the Worker.
- **Repositories** use Spring `JdbcTemplate` against SQLite.
- **File storage** keeps one file per item id, using temp-file writes plus atomic moves and best-effort rollback cleanup; a scheduled sweep reclaims orphans.
- **Downloads** stream bytes from disk and set attachment/safety headers; previews are inline for raster images and videos.
- **Settings** are loopback-only because an override changes where pairing clients are sent.
- **Errors** are normalized in one `@RestControllerAdvice`, so every failure has the same JSON shape.
- **Access control** is intentionally absent: there is no authentication, CORS, Origin check, quota, or expiry.
