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
- Uploads stream into a temporary file and are atomically renamed into place
- Downloads stream directly from disk without loading whole files into memory

## Configuration

Defaults are in `src/main/resources/application.properties`:

| Property | Default | Purpose |
| --- | --- | --- |
| `server.port` | `8080` | HTTP port |
| `spring.datasource.url` | `jdbc:sqlite:./data/app.db` | SQLite database |
| `storage.root` | `./data/files` | Stored file bytes |
| `server-id-path` | `./data/server-id` | Persistent server identity |
| `spring.servlet.multipart.max-file-size` | `2GB` | Per-file limit |
| `spring.servlet.multipart.max-request-size` | `10GB` | Total request limit |
| `spring.servlet.multipart.file-size-threshold` | `0` | Write uploads straight to disk |
| `spring.servlet.multipart.location` | `/var/tmp/selfdrop-uploads` | Multipart temp directory |

## Run locally

Requires JDK 27.

```bash
./gradlew bootRun
```

Then open:

```
http://localhost:8080/
```

The first start creates the storage directory and the persistent server id.

## Tests

```bash
./gradlew test
```

The suite covers the controllers, upload/download flows, repository queries, file storage, server-id persistence, and error handling.

## How it works

- **Controllers** expose the JSON API, multipart upload, download, and health endpoints.
- **Services** map between entities and DTOs, coordinate storage, and manage the persistent server id.
- **Repositories** use Spring `JdbcTemplate` against SQLite.
- **File storage** keeps one file per item id, using temp-file writes plus atomic moves and best-effort rollback cleanup.
- **Downloads** stream bytes from disk and set attachment/safety headers.
- **Errors** are normalized in one `@RestControllerAdvice`, so every failure has the same JSON shape.
