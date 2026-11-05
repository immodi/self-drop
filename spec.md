# Selfdrop API and Android Client Specification

*Status: implemented backend, rev 4, 2026-11-02.*

This document is the source of truth for a client that talks to a Selfdrop
server. It describes the HTTP API, the SSE stream, discovery, pairing, and the
requirements for the Android app. The operator-facing documentation is in
[README.md](README.md).

The current implementation is the reference. Where this document and the code
differ, the code wins; update this document when the API changes.

---

## 1. What Selfdrop is

Selfdrop is a self-hosted inbox for text, links, images, and arbitrary files.
One Spring Boot process serves:

- a JSON API under `/file`, `/upload`, `/download`, `/preview`, and `/ping`
- a live-update SSE stream at `/messages`
- a server-rendered browser UI at `/`
- a pairing page at `/pair`
- a loopback-only settings page at `/settings`

Clients normally run on the same home LAN as the server. There are no accounts,
no access key, no TLS, and no CORS configuration. The only internet dependency
is the optional rendezvous Worker, which publishes the server's current LAN
address so clients can find it after the address changes.

---

## 2. Architecture

```text
Android app ───────────────┐
Browser (this PC or LAN) ──┼── HTTP :8080 ──► Selfdrop backend (Spring Boot)
curl / other clients ──────┘                   ├─ SQLite: /app/data/app.db
                                               ├─ files:  /app/data/files/<id>
                                               ├─ server id: /app/data/server-id
                                               └─ Worker publisher ──PUT──► Cloudflare Worker + KV
                                                                              { url, id, updated }
```

| Component | Responsibility |
|---|---|
| Backend | JSON API, SSE, file storage, browser UI, LAN address detection, Worker publisher, `/pair`, `/settings` |
| Rendezvous Worker | Public read of the current `{ url, id, updated }` record; token-protected write |
| Android app | Share target, timeline, live updates, discovery, QR pairing, upload/download |

The backend binds `0.0.0.0:8080`. In Docker it uses host networking so the
resolver can see the host's real LAN interfaces.

---

## 3. Backend facts clients can rely on

- Base URL: `http://<host>:<server.port>` (normally `http://<host>:8080`), with
  no path prefix.
- No authentication. Anyone who can reach the port can read, create, update,
  and delete items.
- No TLS. Use plain HTTP on the LAN. Do not port-forward the server.
- No CORS headers on the API. Native Android clients do not need CORS; a
  browser page served from a different origin would.
- No Origin check, no CSRF token, no rate limit, no storage quota, and no
  automatic expiry.
- The server id is a random UUID stored at `server-id-path`
  (`./data/server-id` by default). It survives restarts and is returned by
  `/ping`. It is public and is **not** authentication.
- Item ids are UUID v4 strings, for example
  `8f14e45f-ea4b-4c3a-9f6e-0d3f6c2b1a77`.
- `createdAt` is Unix epoch **seconds**, not milliseconds.
- `seq` is the SQLite `AUTOINCREMENT` sequence. It is monotonic, may have gaps
  after deletes, and is the cursor for `after=` and SSE `id:`.
- All JSON responses are UTF-8. Most errors use RFC 9457 `ProblemDetail`
  (section 8); the `416` download-range response has no body.

### 3.1 Stack and persistence

- Java 27 toolchain, Spring Boot 4.1.x, Gradle, Spring Web MVC.
- Thymeleaf + vendored htmx for the browser UI; Tailwind CSS is prebuilt.
- SQLite via `spring-boot-starter-jdbc` and `org.xerial:sqlite-jdbc`.
- Table `files` (not `items`):

  | Column | Type | Notes |
  |---|---|---|
  | `seq` | INTEGER PRIMARY KEY AUTOINCREMENT | Ordering and cursors |
  | `id` | TEXT NOT NULL UNIQUE | UUID used in URLs |
  | `kind` | TEXT NOT NULL | `text` or `file` |
  | `text` | TEXT NULL | Text content for `kind=text` |
  | `name` | TEXT NULL | Original filename; never used as a path |
  | `mime` | TEXT NULL | Client-supplied MIME type |
  | `size` | INTEGER NULL | Text length or file size in bytes |
  | `created_at` | INTEGER NOT NULL | Unix epoch seconds |

- File bytes are stored at `storage.root/<id>` (`./data/files/<id>` by
  default), with no user-controlled path components.
- Uploads stream into a temporary file in the storage root and are atomically
  renamed into place.
- A scheduled orphan sweep removes files whose id has no database row and
  abandoned `.upload-*.tmp` files. Defaults: every 1800 seconds, with a 1800
  second grace period.
- There is no expiry job and no total-storage quota.

---

## 4. Item JSON

Every item response uses this shape:

```json
{
  "seq": 42,
  "id": "8f14e45f-ea4b-4c3a-9f6e-0d3f6c2b1a77",
  "kind": "file",
  "text": null,
  "name": "photo.jpg",
  "mime": "image/jpeg",
  "size": 183204,
  "createdAt": 1761300000
}
```

Field rules:

| Field | Type | Meaning |
|---|---|---|
| `seq` | number | Database sequence; use for `after=` and SSE ids |
| `id` | string | UUID; use in `/file/{id}`, `/download/{id}`, `/preview/{id}` |
| `kind` | string | `text` or `file` |
| `text` | string or null | Text content for `kind=text`; null for files |
| `name` | string or null | Original filename; may be null |
| `mime` | string or null | MIME type; may be null |
| `size` | number or null | Text: Java `String.length()`; file: uploaded byte count |
| `createdAt` | number | Unix epoch seconds |

Text items have `kind="text"`, non-null `text`, and normally null `name` and
`mime`. The text upload endpoint sets `size` to the character count reported by
`String.length()`, not the UTF-8 byte count.

File items have `kind="file"`, null `text`, and normally non-null `name`,
`mime`, and `size`. The server trusts the client's filename and MIME type; it
does not sniff content. `size` is metadata and may not match the bytes on disk.

---

## 5. HTTP API

All paths below are relative to the base URL. Request and response bodies are
JSON unless stated otherwise.

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/ping` | Server id |
| `GET` | `/file` | List items (`after`, `limit`) |
| `GET` | `/file/{id}` | One item |
| `PUT` | `/file` | Update item metadata |
| `DELETE` | `/file/{id}` | Delete item |
| `POST` | `/upload/text` | Create text item |
| `POST` | `/upload/file` | Upload files |
| `GET` | `/download/{id}` | Attachment download, ranges |
| `GET` | `/preview/{id}` | Inline raster image or video |
| `GET` | `/messages` | SSE live updates |
| `GET` | `/settings/address` | Advertised address (loopback) |
| `POST` | `/settings/address` | Override address (loopback) |

### 5.1 `GET /ping`

Returns the persistent server id.

```http
GET /ping
```

```json
{"id":"8f14e45f-ea4b-4c3a-9f6e-0d3f6c2b1a77"}
```

- `200 OK` always when the server is running.
- Use this for discovery and for the server-id check (section 9.4).
- There is no `app`, `version`, or `status` field.

### 5.2 `GET /file`

Returns an array of items.

| Query | Behavior |
|---|---|
| none | All items, ordered by `createdAt` ascending, then `seq` ascending |
| `after=<seq>` | Items with `seq > <seq>`, ordered by `seq` ascending |
| `limit=<n>` | Newest `n` items, ordered by `createdAt` descending, then `seq` descending; `n` is clamped to 1–100 |

- If both `after` and `limit` are present, `after` wins.
- `after` is not clamped. `after=0` returns all items in `seq` order.
- `limit=0` or a negative `limit` returns one item (clamped to 1).
- Non-numeric `after` or `limit` returns `400` (section 8).
- The response is a bare JSON array, not an envelope. An empty timeline returns
  `[]`.

Examples:

```http
GET /file
GET /file?limit=50
GET /file?after=123
```

```json
[
  {
    "seq": 123,
    "id": "8f14e45f-ea4b-4c3a-9f6e-0d3f6c2b1a77",
    "kind": "text",
    "text": "https://example.com",
    "name": null,
    "mime": null,
    "size": 19,
    "createdAt": 1761300000
  }
]
```

For a full timeline, use `GET /file`. For a recent-items shortcut, use
`GET /file?limit=...` and reverse the array in the client if the UI is
oldest-first. For catch-up, use `GET /file?after=<lastSeq>`.

### 5.3 `GET /file/{id}`

Returns one item's metadata.

```http
GET /file/8f14e45f-ea4b-4c3a-9f6e-0d3f6c2b1a77
```

- `200 OK` with an item object.
- `404` with `title: "File not found"` and
  `detail: "No file with id: <id>"` when the row does not exist.

### 5.4 `PUT /file`

Updates an item's metadata. This is a full replacement of the mutable fields,
not a patch.

```http
PUT /file
Content-Type: application/json
```

```json
{
  "id": "8f14e45f-ea4b-4c3a-9f6e-0d3f6c2b1a77",
  "kind": "text",
  "text": "updated",
  "name": null,
  "mime": null,
  "size": 7
}
```

Validation:

- `id` is required and must not be blank.
- `kind` is required and must be exactly `text` or `file`.
- `size`, if present, must be zero or positive.
- `text`, `name`, and `mime` are optional strings.

Behavior:

- `200 OK` with the updated item.
- `id`, `seq`, and `createdAt` are preserved from the existing row.
- `404` if the row does not exist.
- `400` with a validation `errors` map for an invalid body.
- This endpoint updates **metadata only**. It does not create, replace, or
  delete file bytes. Do not use it to change a file's content. Changing
  `kind` without changing the stored bytes can leave an item whose metadata and
  bytes disagree; clients should normally only update text items.

### 5.5 `DELETE /file/{id}`

Deletes the row and then best-effort deletes the stored file.

```http
DELETE /file/8f14e45f-ea4b-4c3a-9f6e-0d3f6c2b1a77
```

- `204 No Content` on success.
- `404` if the row does not exist.
- If the row is deleted but the bytes cannot be deleted, the response is still
  `204`; the orphan sweep reclaims the bytes later.
- Emits an `item-deleted` SSE event as part of the delete (the broadcaster may
  deliver it asynchronously).

### 5.6 `POST /upload/text`

Creates a text item from the raw request body.

```http
POST /upload/text
Content-Type: text/plain

https://example.com
```

- The body is read as a string. Send the text directly; do not wrap it in JSON.
- `201 Created` with `Location: /file/<id>`.
- The response body is **only** `{"text":"<text>"}` (a `TextDto`), not the full
  item. Use `Location`, a follow-up `GET /file/{id}`, or the SSE stream to get
  the full item.
- The server generates the id and sets `kind="text"`, `name=null`,
  `mime=null`, and `size` to `text.length()`.
- There is no server-side text length limit. The browser UI has a client-side
  `maxlength` of 20000, but the API does not enforce it.
- The body is required. An empty body returns `400` (the shared handler reports
  `"Malformed or unreadable request body."`); a whitespace-only body is stored
  as text.

Example response:

```http
HTTP/1.1 201 Created
Location: /file/8f14e45f-ea4b-4c3a-9f6e-0d3f6c2b1a77
Content-Type: application/json
```

```json
{"text":"https://example.com"}
```

### 5.7 `POST /upload/file`

Uploads one or more files as multipart form data.

```http
POST /upload/file
Content-Type: multipart/form-data; boundary=...
```

Multipart parts:

- Part name must be exactly `files`.
- Repeat the `files` part for multiple files.
- Each part's filename and content type become the item's `name` and `mime`.
- The part's byte length becomes the item's `size`.

Example with curl:

```bash
curl -X POST http://localhost:8080/upload/file \
     -F 'files=@photo.jpg' \
     -F 'files=@notes.pdf'
```

Behavior:

- `200 OK` with a bare JSON array of created items, in upload order.
- Empty file parts are skipped. If every part is empty, the response is `[]`
  and no rows or bytes are created.
- A missing `files` part returns `400` with
  `detail: "Missing required request part: files."`.
- The whole request is all-or-nothing: if storing or row creation fails, the
  server rolls back rows and bytes it created and returns `500`. If some items
  were already created, the rollback emits `item-deleted` events for them, so
  SSE clients may briefly see adds followed by deletes; treat the response as
  authoritative.
- Per-file limit: 2 GB. Whole-request limit: 10 GB. Exceeding either returns
  `413`.
- The server does not enforce a total-storage quota.
- There is no idempotency key. Retrying an upload after an ambiguous failure
  can create duplicate items.

Example response:

```json
[
  {
    "seq": 43,
    "id": "2a4c0e4e-9f7b-4d9e-9a4e-0f0b3d2c1a11",
    "kind": "file",
    "text": null,
    "name": "photo.jpg",
    "mime": "image/jpeg",
    "size": 183204,
    "createdAt": 1761300001
  }
]
```

### 5.8 `GET /download/{id}`

Streams the stored file as an attachment.

```http
GET /download/8f14e45f-ea4b-4c3a-9f6e-0d3f6c2b1a77
```

Success headers:

- `Content-Type`: stored MIME type, or `application/octet-stream` when the MIME
  is missing or invalid.
- `Content-Disposition: attachment; filename="..."` (UTF-8 filename encoding).
  The original name is used when present; otherwise the id is used.
- `Content-Length`: for a full `200`, the **actual stored file size**; for a
  `206`, the partial body length. `Content-Range` carries the total size. Never
  use the metadata `size`.
- `Accept-Ranges: bytes`
- `X-Content-Type-Options: nosniff`
- `Content-Security-Policy: sandbox`

Range requests:

- One byte range is supported. `Range: bytes=start-end`, `bytes=start-`, and
  `bytes=-suffix` are supported.
- A satisfiable range returns `206 Partial Content` with
  `Content-Range: bytes <start>-<end>/<size>` and the partial body.
- An end past the file size is clamped.
- A start at or past the file size returns `416 Requested Range Not
  Satisfiable` with `Content-Range: bytes */<size>`, `Accept-Ranges: bytes`,
  and no body.
- Multiple ranges are ignored and the full file is returned with `200`.
- Malformed range headers are ignored and the full file is returned with `200`.
- There is no `ETag` or `Last-Modified`.

Resume example:

```bash
curl -C - -OJ http://localhost:8080/download/<id>
```

- `404` if the row or the stored bytes do not exist.
- The endpoint does not check `kind`; if bytes exist for the id, they are served.

### 5.9 `GET /preview/{id}`

Streams an image or video inline for the timeline preview.

```http
GET /preview/8f14e45f-ea4b-4c3a-9f6e-0d3f6c2b1a77
```

- Only `kind="file"` items whose MIME type starts with `image/` or `video/`
  are previewable.
- `image/svg+xml` is explicitly **not** previewable, because SVG can execute
  script in the server's origin.
- Everything else, including HTML, PDF, audio, and SVG, returns `404` from this
  endpoint. Use `/download/{id}` for those.
- `200 OK` with the stored MIME type, `Content-Disposition: inline`,
  `X-Content-Type-Options: nosniff`, `Content-Security-Policy: sandbox`, and
  `Cache-Control: private, max-age=3600`.
- `Content-Length` is the actual stored file size.
- Supports single byte ranges like `/download/{id}`: a satisfiable range
  returns `206 Partial Content` with `Content-Range`, and an unsatisfiable range
  returns `416` with `Content-Range: bytes */<size>`.
- `404` if the item is missing, not previewable, has an invalid MIME type, or
  the bytes are missing.

### 5.10 `GET /messages` (SSE)

Live updates use Server-Sent Events.

```http
GET /messages
Accept: text/event-stream
```

Optional cursor:

- `?since=<seq>`: replay stored items with `seq > <seq>` before live events.
- `Last-Event-ID: <seq>` header: takes precedence over `since` when it parses
  as a number; an invalid header falls back to `since`.
- Negative values are clamped to 0. The default is 0.
- A non-numeric `since` query value returns `400`.

Connection behavior:

1. The server registers the connection.
2. It immediately sends a `heartbeat` event so response headers flush.
3. It replays every stored item with `seq > since` as an `item-added` event,
   ordered by `seq`.
4. It then sends live events in publication order.
5. It sends a `heartbeat` event every 10 seconds.

Event framing:

```text
event: item-added
id: 42
data: {"type":"item-added","id":"8f14e45f-ea4b-4c3a-9f6e-0d3f6c2b1a77","seq":42,"item":{"seq":42,"id":"8f14e45f-ea4b-4c3a-9f6e-0d3f6c2b1a77","kind":"file","text":null,"name":"photo.jpg","mime":"image/jpeg","size":183204,"createdAt":1761300000}}

event: item-updated
id: 42
data: {"type":"item-updated","id":"8f14e45f-ea4b-4c3a-9f6e-0d3f6c2b1a77","seq":42,"item":{...}}

event: item-deleted
id: 42
data: {"type":"item-deleted","id":"8f14e45f-ea4b-4c3a-9f6e-0d3f6c2b1a77","seq":42,"item":null}

event: heartbeat
data:
```

Event names and payloads:

| `event:` / `type` | `item` | Meaning |
|---|---|---|
| `item-added` | full item | A row was created |
| `item-updated` | full item | A row's metadata changed |
| `item-deleted` | `null` | A row was deleted |

- The SSE `id:` field is the item's `seq` (when non-null). EventSource clients
  send it back as `Last-Event-ID` on reconnect.
- The `data:` line is the JSON serialization of `FileEvent`
  `{type, id, seq, item}`. It is one line.
- `heartbeat` has an empty data payload and no id. Treat any heartbeat as proof
  the connection is alive.
- The stream has no server-side timeout; heartbeats keep it alive.
- The replay only contains `item-added` events for rows that still exist.
  Deletes and updates that happened while a client was offline are **not**
  replayed. On reconnect, do a full `GET /file` (or otherwise reconcile) to
  remove deleted items and pick up updates.
- There is no `retry:` field. Clients choose their own reconnect backoff.
- The endpoint is public and unauthenticated, like the rest of the API.

Recommended client pattern:

1. Load the full timeline with `GET /file`.
2. Open `/messages?since=<highest seq>` (or `/messages`).
3. Apply `item-added` and `item-updated` by replacing the item with the same
   id, and `item-deleted` by removing the id.
4. On every reconnect or foreground resume, reload `GET /file` before applying
   new events, because delete/update history is not replayed.

### 5.11 Settings endpoints (loopback only)

These are for the machine running the server. They are not part of the Android
client contract. Both JSON endpoints return `403 Forbidden` with
`title: "Forbidden"` and
`detail: "Settings are only available on the server itself."` for non-loopback
requests.

#### `GET /settings/address`

```http
GET /settings/address
```

```json
{
  "url": "http://192.168.1.50:8080",
  "serverId": "8f14e45f-ea4b-4c3a-9f6e-0d3f6c2b1a77",
  "localOnly": false
}
```

- `localOnly` is true when the advertised URL starts with `http://localhost`,
  `https://localhost`, `http://127.`, `https://127.`, `http://[::1]`, or
  `https://[::1]`.
- When no LAN address is detected, `url` is normally
  `http://localhost:<server.port>` (normally `http://localhost:8080`) and
  `localOnly` is true.

#### `POST /settings/address`

```http
POST /settings/address
Content-Type: application/json
```

```json
{"url":"http://192.168.1.51:8080"}
```

- The value is trimmed.
- It must be `http://<private IPv4>:<port>` with an explicit port, no
  credentials, no path beyond `/`, no query, and no fragment. Accepted private
  ranges: `10/8`, `172.16/12`, `192.168/16`.
- `400` with `title: "Invalid address"` and
  `detail: "The address must be a private http://<IPv4>:<port> URL."` when the
  URL is rejected.
- `400` with a validation `errors.url` map when the value is blank.
- `200 OK` with the same `AddressDto` shape as `GET /settings/address`.
- The override is in-memory and lasts until the server restarts. While an
  override is active, scheduled LAN re-detection is skipped.
- The server immediately calls the Worker publisher. If the Worker is
  configured but unreachable, the request may block for up to about 5 seconds
  while the publish attempt times out; the local override still succeeds.

#### `GET /settings`

The server-rendered settings page. It is loopback-only and returns `403` with
an HTML explanation for remote clients. Android does not need this page.

### 5.12 Browser UI routes (not for Android)

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/` | Timeline page |
| `GET` | `/ui/timeline` | Timeline HTML fragment |
| `POST` | `/ui/text` | Form-encoded text create for htmx |
| `GET` | `/pair` | Pairing page with two QR codes |
| `GET` | `/pair/codes` | Pairing QR fragment, polled every 10s |
| `GET` | `/settings` | Loopback-only settings page |
| `GET` | `/css/*`, `/js/*`, `/favicon.svg` | Static assets |

`POST /ui/text` accepts `application/x-www-form-urlencoded` with a `text` field
and returns an HTML item fragment. It is not a JSON API. Android should use
`POST /upload/text` instead.

---

## 6. Ordering and cursors

- `seq` is the only safe cursor for `after=` and SSE `Last-Event-ID`.
- `createdAt` is second-resolution and can tie; ordering falls back to `seq`.
- `GET /file` is oldest-first (`createdAt`, then `seq`).
- `GET /file?limit=N` is newest-first (`createdAt` descending, then `seq`
  descending), then clamped to 100.
- `GET /file?after=N` is `seq` ascending.
- SSE replay is `seq` ascending.

For an Android timeline, either reverse `GET /file` or sort by
`createdAt`/`seq` descending in the client.

---

## 7. Upload and download semantics

### 7.1 Uploads

- Text: raw body to `/upload/text`; response is `201` with only the text.
- Files: multipart to `/upload/file`, repeated part name `files`; response is
  `200` with an array of full items.
- Empty file parts are skipped.
- Multi-file upload is all-or-nothing on the server.
- The server streams to disk; it does not buffer whole files in heap.
- A failed upload rolls back rows and bytes it created.
- There is no idempotency key; retries can duplicate items.
- The server does not enforce a text length or total quota.
- Per-file and per-request limits come from Spring multipart configuration
  (section 11).

### 7.2 Downloads and resume

- Always use `/download/{id}` for attachments.
- For a full `200`, use `Content-Length` as the authoritative byte count. For a
  `206`, use the total from `Content-Range`; `Content-Length` is only the
  partial body length.
- To resume, send `Range: bytes=<bytesAlreadySaved>-`.
- On `206`, append the body and verify the final size.
- On `416`, the range is not satisfiable; if the local file size equals the
  server's total size from `Content-Range: bytes */<size>`, the download is
  already complete.
- On `200` to a ranged request, the server ignored the range; restart from
  zero.
- Do not assume the metadata `size` is the file size; it can be stale or null.

### 7.3 Previews

- Use `/preview/{id}` for raster images and videos.
- Play videos with the platform's native player against the preview URL; the
  endpoint supports byte ranges so seeking works.
- SVG, HTML, PDF, audio, and other types return `404`; use `/download/{id}` and
  open with an external viewer.
- Preview responses are cacheable for one hour privately.

---

## 8. Errors

Most API errors are RFC 9457 `ProblemDetail` JSON. The `416` download-range
response is the exception: it has headers but no body. Typical shape:

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

Status codes used by the API:

| Status | When |
|---|---|
| `400` | Invalid JSON, validation failure, bad query parameter, missing multipart part, invalid settings URL |
| `403` | Settings endpoints/page accessed from a non-loopback client |
| `404` | Missing item, missing bytes, unknown path |
| `405` | Wrong HTTP method |
| `413` | Upload exceeds the configured multipart limit |
| `415` | Unsupported request content type |
| `416` | Unsatisfiable download range |
| `500` | Unexpected server failure; detail is generic |

The `errors` map is present for validation failures and maps field/parameter
names to messages. Clients should display `detail` when present and can show
field errors when `errors` is present. `type` is normally `about:blank`.

---

## 9. Discovery, pairing, and the rendezvous Worker

### 9.1 Worker record

The Worker is a tiny Cloudflare Worker + KV store. Its public record is:

```json
{
  "url": "http://192.168.1.50:8080",
  "id": "8f14e45f-ea4b-4c3a-9f6e-0d3f6c2b1a77",
  "updated": "2026-11-02T09:00:00.000Z"
}
```

- `GET /r` returns the record, or `404` before the first publish.
- `GET /r` and `HEAD /r` are public, CORS `*`, `Cache-Control: no-store`.
- `OPTIONS /r` returns `204` with the CORS headers.
- `GET /` is an alias for `/r`.
- `PUT /r` requires `Authorization: Bearer <token>`.
- `PUT` body: `{"url":"http://<private IPv4>:<port>","id":"<server-id>"}`.
- The id must match `[A-Za-z0-9_-]{1,64}`; a Selfdrop UUID fits.
- The Worker rejects non-private URLs, invalid ids, oversized bodies, and bad
  JSON. Error codes: `401` bad token, `413` body over 1 KiB, `422` invalid URL
  or id, `400` bad JSON, `500` Worker not configured.
- An unchanged `url`/`id` returns `200 {"changed":false,"record":...}` and does
  not write KV. A changed record returns
  `200 {"changed":true,"record":...}`.
- The write token is server-side only and must never be shipped to clients.

The currently documented deployed record endpoint is:

```text
https://selfdrop-rendezvous.ay727468a.workers.dev/r
```

A self-hosted deployment can point its backend and Android app at a different
Worker URL. The Android app should make this URL configurable at build time or
in app settings rather than hard-coding one account's deployment forever.

### 9.2 Backend publisher and settings override

- On startup and every `worker.publish-interval-millis` (default 30s), the
  backend checks the current advertised address.
- Publishing is enabled only when `WORKER_URL` and `WORKER_TOKEN` are both set.
  If `WORKER_URL` does not already end in `/r`, the backend appends `/r`.
- The backend sends `{url, id}` to the Worker with the bearer token. It skips
  non-private addresses, including the localhost fallback, before making a
  request.
- It skips writes when the desired record equals the last successfully
  published record, and also persists that record in
  `worker.state-path` (`./data/worker-state.json` by default).
- The advertised address is detected at startup and re-checked every
  `address.refresh-millis` (default 30s). Detection prefers the outbound
  interface, then private, preferred physical interfaces; it ignores loopback,
  link-local, virtual, container, VPN, and point-to-point interfaces.
- When no LAN address is found, the fallback is
  `http://localhost:<server.port>` (normally `http://localhost:8080`).
- A manual override from `/settings` replaces the detected address and is
  published immediately. The override lasts until restart; scheduled
  re-detection is skipped while it is active.
- The Worker token is never exposed to the browser or API responses.

### 9.3 Pairing deep link and QR

The pairing deep link is:

```text
selfdrop://pair?u=<url-encoded base URL>&id=<url-encoded server id>
```

Example:

```text
selfdrop://pair?u=http%3A%2F%2F192.168.1.50%3A8080&id=8f14e45f-ea4b-4c3a-9f6e-0d3f6c2b1a77
```

- The `/pair` page shows two QR codes behind a selector: the pair deep link and
  the plain server URL. Both have copy buttons.
- The codes refresh every 10 seconds from `/pair/codes`.
- If the advertised URL is local-only, the page warns that the codes only point
  at the server itself.
- Android should register an intent filter for the `selfdrop` scheme and accept
  both the deep link and a plain `http://...` address from a QR scan or manual
  entry.

### 9.4 Recommended Android discovery sequence

On startup and on connection failure:

1. **Cached address:** `GET /ping` against the last known base URL with a short
   timeout (about 1s). Accept only if `id` matches the stored server id.
2. **Worker:** `GET <worker-url>` (about 3–5s timeout), read `url` and `id`,
   then `GET /ping` against that URL and check the id.
3. **Subnet scan (Android only):** probe the device's `/24` on port 8080 with a
   short timeout (about 300ms) in parallel, and accept only a `/ping` whose id
   matches. This is a fallback for when the Worker is unreachable.
4. **QR/manual:** scan the pair deep link or ask for an address. Verify `/ping`
   and the id.
5. **Fail visibly:** show "Can't find your server" with Retry and a
   QR/manual-entry option.

Rules:

- **Trust on first use:** a client with no stored id accepts the id from its
  first successful lookup and stores it. After that, every lookup must match.
- If a server answers with a different id (for example after the data folder
  was wiped), ask "Different server found. Switch to it?" instead of silently
  connecting.
- The id check prevents uploading to an unrelated device on a foreign network.
  It is not authentication; the id is public.
- Cache the last working base URL and server id. Update the cache on every
  successful lookup.
- Use short timeouts so a foreign network fails in about 1–2 seconds.

---

## 10. Android client requirements

### 10.1 Networking and permissions

- The API is plain HTTP. Android 9+ blocks cleartext by default. Add a network
  security config or `android:usesCleartextTraffic="true"` for the app, and
  restrict requests in code to the stored/verified server address.
- Newer Android versions may gate local-network access behind a runtime
  permission. Request it when the platform requires it; otherwise the app
  cannot reach a LAN address.
- Do not use HTTPS for the LAN server unless the server is changed to support
  it.
- Use a client with streaming request bodies (OkHttp/Retrofit is a good fit).
  Do not load whole shared files into memory.

### 10.2 State and identity

- Persist the last known base URL and server id (DataStore/SharedPreferences).
- Persist the highest seen `seq` for SSE catch-up, but do not rely on it for
  delete reconciliation.
- Never persist or send the Worker write token. It is server-side only.
- Treat the server id as an identity check, not a secret.

### 10.3 Timeline and live updates

- Load the timeline with `GET /file` (oldest-first; reverse for display).
- Render text and links; preview raster images with `/preview/{id}`; play
  videos inline with the native player against `/preview/{id}`; show other
  files with name, size, and a download action.
- Open `/messages?since=<highest seq>` while the app is in the foreground.
- Handle `item-added`, `item-updated`, and `item-deleted` by id.
- Treat `heartbeat` as a liveness signal and update the connection status.
- Reconnect with exponential backoff. On every reconnect or foreground resume,
  reload `GET /file` to reconcile deletes and updates that happened while
  offline.
- The SSE stream is not a background service; do not rely on it when the app is
  closed.

### 10.4 Sharing and uploading

- Register `ACTION_SEND` and `ACTION_SEND_MULTIPLE` for `text/plain` and
  `*/*`.
- Text/link shares: `POST /upload/text` with the text as the raw `text/plain`
  body. Do not let a JSON converter wrap it in quotes.
- File shares: `POST /upload/file` as multipart with one or more `files` parts.
  Stream each part from its `content://` URI.
- Use WorkManager with a network constraint for background uploads. Retry with
  backoff on network failure.
- Because uploads are not idempotent, avoid blind retries after a response may
  have been received. If a retry is unavoidable, expect possible duplicates.
- The server returns full items for file uploads, so the app can insert them
  immediately. For text uploads, use the `Location` header or the SSE event to
  obtain the full item.
- Respect the server's 2 GB per-file and 10 GB per-request limits; show a clear
  error for `413`.

### 10.5 Download, preview, and open

- Use `/download/{id}` for attachments and `/preview/{id}` for image and video
  previews.
- For downloads, save to app storage and support resume with a `Range` header.
- Handle `206` (append), `200` (restart), and `416` (already complete or
  invalid).
- Use the response `Content-Length`/`Content-Range` total, not the item's
  metadata `size`.
- Open downloaded files through a `FileProvider` with the stored MIME type.
- Fall back to `/download/{id}` when `/preview/{id}` returns `404`.

### 10.6 Deletion and updates

- Delete with `DELETE /file/{id}`; remove the item on `204`.
- `PUT /file` is available for metadata updates but is not needed for the core
  timeline. It is a full replacement of the mutable fields and does not change
  file bytes.
- Handle `404` on delete/update as "already gone" and remove it locally.

### 10.7 UI behavior

- Show the connection state: connecting, live, reconnecting, offline.
- Show upload progress from the request body; the server does not provide a
  progress endpoint.
- Show errors from `ProblemDetail.detail`; use `errors` for field-level
  messages.
- Show a clear empty state and a retry action when discovery fails.
- Provide a QR scanner and manual address entry as the fallback path.
- Do not show a settings page; `/settings` is loopback-only on the server.

### 10.8 Suggested stack

This is a suggestion, not a server requirement:

- Kotlin, min SDK 26+.
- OkHttp + Retrofit for HTTP, or OkHttp directly.
- OkHttp EventSource or a small SSE parser for `/messages`.
- WorkManager for uploads.
- Coil/Glide for `/preview` images.
- ZXing or ML Kit for QR scanning.
- DataStore for the cached URL/id.

---

## 11. Security model and limitations

- **Threat model:** every device on the home LAN is trusted. This is not a
  hostile network and the server must not be exposed to the internet.
- **Provided:** server-id checks in clients, safe download/preview headers,
  private-URL validation in the Worker and settings, and loopback-only
  settings.
- **Not provided:** authentication, authorization, confidentiality, integrity,
  CORS, Origin checks, rate limiting, storage quota, expiry, text limits, or
  per-device labels.
- Anyone on the LAN can read, create, update, and delete everything. Traffic is
  unencrypted. Do not store sensitive data.
- Never port-forward port 8080 or put the backend on a public address.
- The rendezvous record is public and contains only the LAN URL, server id, and
  update timestamp.
- The Worker write token stays in the backend environment and must never be
  returned to clients.
- If untrusted devices share the network, the next upgrades are a shared access
  key and HTTPS with a local CA.

---

## 12. Configuration and deployment

Defaults are in `src/main/resources/application.properties`:

| Property | Default | Purpose |
|---|---|---|
| `server.address` | `0.0.0.0` | Bind address |
| `server.port` | `8080` | HTTP port |
| `spring.mvc.async.request-timeout` | `-1` | No timeout for streamed responses |
| `spring.datasource.url` | `jdbc:sqlite:./data/app.db` | SQLite database |
| `storage.root` | `./data/files` | Stored file bytes |
| `storage.sweep.grace-seconds` | `1800` | Orphan/temp-file grace period |
| `storage.sweep.interval-seconds` | `1800` | Orphan/temp sweep interval |
| `server-id-path` | `./data/server-id` | Persistent server id |
| `address.refresh-millis` | `30000` | LAN re-detection interval |
| `spring.servlet.multipart.max-file-size` | `2GB` | Per-file upload limit |
| `spring.servlet.multipart.max-request-size` | `10GB` | Whole multipart request limit |
| `spring.servlet.multipart.file-size-threshold` | `0` | Stream uploads to disk |
| `spring.servlet.multipart.location` | `/var/tmp/selfdrop-uploads` | Multipart temp directory |
| `worker.url` | empty (`WORKER_URL`) | Rendezvous Worker endpoint; empty disables publishing |
| `worker.token` | empty (`WORKER_TOKEN`) | Worker write token |
| `worker.state-path` | `./data/worker-state.json` | Last successfully published record |
| `worker.publish-interval-millis` | `30000` | Publish check interval |
| `worker.publish-initial-delay-millis` | `3000` | Initial publish delay |

The app loads a root `.env` file when present
(`spring.config.import=optional:file:.env[.properties]`). The Makefile also
includes `.env` for `make run`.

### 12.1 Local run

Requires JDK 27.

```bash
make run
# or: ./gradlew bootRun
```

Then open `http://localhost:8080/`. The first start creates `./data`, the
SQLite database, the storage directory, and the persistent server id. The
multipart temp directory (`/var/tmp/selfdrop-uploads` by default) must exist
and be writable; the Docker image creates it, but a local run may need
`mkdir -p /var/tmp/selfdrop-uploads` or a custom
`spring.servlet.multipart.location`.

### 12.2 Docker

```bash
docker compose up -d --build
docker compose logs -f
docker compose down
```

- `network_mode: host`, so the container binds the host's interfaces and the
  detected LAN address is the host's real address.
- `./data:/app/data` persists the database, files, server id, and Worker state.
- `restart: unless-stopped` brings the container back when Docker starts,
  including after a reboot. An explicit `docker compose stop` keeps it stopped.
- The Docker daemon must start at boot. On Linux with systemd:
  `sudo systemctl enable --now docker`. On Docker Desktop, enable "Start Docker
  Desktop when you log in".
- The image runs as a non-root `selfdrop` user and uses
  `JAVA_TOOL_OPTIONS=-Xmx256m`.

### 12.3 Worker

The Worker lives in `worker/` and is a dependency-free ES module with a KV
namespace. See `worker/README.md` for deployment. The backend needs
`WORKER_URL` and `WORKER_TOKEN` to publish.

---

## 13. Acceptance criteria for the Android app

- A fresh install can find the server without typing an address, using the
  Worker or a QR/manual fallback.
- The app verifies `/ping`'s `id` and stores it for trust-on-first-use.
- Sharing a text or link creates an item visible on the web UI.
- Sharing one or more files creates file items visible on the web UI, without
  loading whole files into memory.
- The timeline loads with `GET /file`, renders text/images/files, and updates
  live over `/messages` while in the foreground.
- Deleting an item removes it locally and on other clients.
- Downloading a file works and can resume after interruption.
- Images and videos preview through `/preview/{id}`; SVG/HTML do not.
- A wrong server id is detected and the user is asked before switching.
- A foreign network or unreachable server shows an offline state within about
  1–2 seconds, and queued uploads retry later.
- The app never sends the Worker write token and never assumes authentication.

---

## 14. Not implemented / future work

These are intentionally absent from the current backend and should not be
assumed by the Android app:

- authentication, access keys, per-user accounts
- TLS/HTTPS
- CORS and a hosted web client
- Origin/CSRF checks
- storage quota and automatic expiry
- server-side text length limits
- `X-Device`/device labels
- idempotency keys for uploads
- thumbnails, push notifications, background SSE
- PWA install / Web Share Target / iOS app

---

## 15. Open decisions for the Android project

- Language and UI toolkit: Kotlin + Jetpack Compose, Kotlin + Views, or Java.
- Minimum SDK and target SDK.
- Whether the Worker URL is a build constant, a remote config value, or an
  advanced setting.
- Whether to keep a local mirror of the timeline in a database for offline
  viewing.
- Whether to deduplicate retried uploads client-side.
- Whether to support metadata editing (`PUT /file`) in the first release.
