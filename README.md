# Selfdrop

Selfdrop is a small, self-hosted inbox for your own devices. Drop text, links, images, or files from one device and pick them up on another over your local network. It runs as one Spring Boot application and ships with its own web UI — no separate frontend build, no accounts, no cloud.

## Highlights

- Text, links, images, and arbitrary files in one timeline
- Multi-file uploads with streaming writes and atomic renames
- File downloads streamed straight from disk
- Built-in responsive web UI served by the backend
- SQLite metadata and on-disk file storage
- Persistent server identity, exposed by a health endpoint
- JSON errors with consistent `ProblemDetail` responses
- Full controller, repository, and storage test suite

## Web UI

Open the application in a browser and use the built-in timeline:

- Text and links render inline; links are clickable
- Images are previewed inline; other files show name, size, and a download action
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

- The Java application owns publication: on startup and whenever the stored address or server id changes, `WorkerPublisher` writes the `{ url, id, updated }` record to the Worker. There is no separate updater script or sidecar.
- Publication is opt-in. It runs only when `worker.url` and `worker.token` are configured; an app without a Worker keeps working exactly as before.
- The last successfully published values are kept in `./data/worker-state.json`, so restarts and unchanged addresses cause no writes. The Worker also skips unchanged records.
- The record is publicly readable and contains only the LAN URL and server id. The Worker rejects anything that is not `http://<private IPv4>:<port>`, so a leaked write token cannot redirect clients to a public host.
- The Worker write token stays in the backend environment (`WORKER_TOKEN`), never in the page or its responses.
- A built-in settings page for manual overrides is still planned at `/settings/address`.

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
| `PUT` | `/r` | Bearer-token write of `{"url":"http://<private IPv4>:<port>","id":"<server-id>"}` |

## API

All item responses use this shape:

```json
{
  "id": "8f14e45f-ea4b-4c3a-9f6e-0d3f6c2b1a77",
  "kind": "file",
  "text": null,
  "name": "photo.jpg",
  "mime": "image/jpeg",
  "size": 183204,
  "createdAt": 1761300000
}
```

`kind` is either `text` or `file`. `createdAt` is Unix epoch seconds.

| Method | Path | Description |
| --- | --- | --- |
| `GET` | `/ping` | Server identity: `{"id":"<server-id>"}` |
| `GET` | `/file` | All items in timeline order |
| `GET` | `/file?limit=N` | Newest `N` items (`N` is clamped to 1–100) |
| `GET` | `/file?after=<seq>` | Items newer than the given sequence cursor |
| `GET` | `/file/{id}` | One item's metadata |
| `PUT` | `/file` | Update an item's kind, text, name, mime, and size |
| `DELETE` | `/file/{id}` | Remove an item (204 No Content) |
| `POST` | `/upload/text` | Create a text item from a `text/plain` body |
| `POST` | `/upload/file` | Upload one or more multipart `files` |
| `GET` | `/download/{id}` | Download the stored file as an attachment |
| `GET` | `/preview/{id}` | Inline preview for raster images (SVG is excluded) |

`after` takes precedence when both `after` and `limit` are supplied. The `after` response is ordered by sequence; the `limit` response is newest-first.

### Uploads

```bash
# Text or link
curl -X POST http://localhost:8080/upload/text \
     -H 'Content-Type: text/plain' \
     --data 'https://example.com'

# One or more files
curl -X POST http://localhost:8080/upload/file \
     -F 'files=@photo.jpg' \
     -F 'files=@notes.pdf'
```

Uploads are streamed to disk, not buffered in memory. A failed multi-file upload rolls back the rows and bytes it created.

### Download

```bash
curl -OJ http://localhost:8080/download/<id>
```

Downloads are streamed from disk, sent as an attachment with the original filename, and include `X-Content-Type-Options: nosniff` and a sandboxing `Content-Security-Policy`.

### Update

```bash
curl -X PUT http://localhost:8080/file \
     -H 'Content-Type: application/json' \
     -d '{"id":"<id>","kind":"text","text":"updated","name":null,"mime":null,"size":7}'
```

### Errors

Errors are RFC 9457 `ProblemDetail` responses. Validation failures include an `errors` map:

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

The API returns `400` for invalid input, `404` for missing items, `405` for unsupported methods, `413` for oversized uploads, `415` for unsupported content types, and `500` for unexpected failures.

## Storage and persistence

- Item metadata lives in SQLite (default `./data/app.db`)
- File bytes live under `storage.root` (default `./data/files`), named by item id
- The server id is stored in `./data/server-id` and survives restarts
- The last published Worker record is remembered in `./data/worker-state.json`
- Uploads stream into a temporary file and are atomically renamed into place
- Downloads stream directly from disk without loading whole files into memory

## Configuration

Defaults are in `src/main/resources/application.properties`:

| Property | Default | Purpose |
| --- | --- | --- |
| `server.address` | `0.0.0.0` | Bind address (all interfaces) |
| `server.port` | `8080` | HTTP port |
| `spring.datasource.url` | `jdbc:sqlite:./data/app.db` | SQLite database |
| `storage.root` | `./data/files` | Stored file bytes |
| `server-id-path` | `./data/server-id` | Persistent server identity |
| `spring.servlet.multipart.max-file-size` | `2GB` | Per-file limit |
| `spring.servlet.multipart.max-request-size` | `10GB` | Total request limit |
| `spring.servlet.multipart.file-size-threshold` | `0` | Write uploads straight to disk |
| `spring.servlet.multipart.location` | `/var/tmp/selfdrop-uploads` | Multipart temp directory |
| `worker.url` | empty (`WORKER_URL`) | Rendezvous Worker endpoint; empty disables publishing |
| `worker.token` | empty (`WORKER_TOKEN`) | Worker write token, kept server-side |
| `worker.state-path` | `./data/worker-state.json` | Last successfully published values |
| `worker.publish-interval-millis` | `30000` | How often to check for an address change |

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

The app listens on every interface, and the footer shows the detected LAN URL (for example `http://192.168.1.104:8080/`) so you can open it from other devices on the same network. When running in Docker, use host networking (`--network host`) so the resolver can see the host's interfaces.

## Tests

```bash
make test
# or: ./gradlew test
```

The suite covers the controllers, upload/download/preview flows, the server-rendered UI, repository queries, file storage, server-id persistence, and error handling.

## How it works

- **Controllers** expose the JSON API, multipart upload, download/preview, health, and server-rendered UI endpoints.
- **UI** is rendered with Thymeleaf, enhanced with vendored htmx, and refreshed from the SSE stream as items change.
- **Services** map between entities and DTOs, coordinate storage, and manage the persistent server id.
- **Repositories** use Spring `JdbcTemplate` against SQLite.
- **File storage** keeps one file per item id, using temp-file writes plus atomic moves and best-effort rollback cleanup.
- **Downloads** stream bytes from disk and set attachment/safety headers; previews are inline and limited to raster images.
- **Errors** are normalized in one `@RestControllerAdvice`, so every failure has the same JSON shape.
