# Selfdrop rendezvous Worker

Tiny Cloudflare Worker + KV record used by clients to rediscover the Selfdrop
server after its LAN address changes.

```bash
pnpm install
pnpm test
pnpm exec wrangler dev
pnpm exec wrangler deploy
pnpm exec wrangler secret put WRITE_TOKEN
```

## Endpoints

| Method | Path | Description |
| --- | --- | --- |
| `GET` | `/r` | Public record, CORS `*`, `Cache-Control: no-store` |
| `HEAD` | `/r` | Same headers as `GET`, without a body |
| `PUT` | `/r` | Bearer-token write of `{"url":"http://<private IPv4>:<port>","id":"<id>"}` |
| `OPTIONS` | `/r` | CORS preflight, `204` |
| `GET` | `/` | Alias for `/r` |

The record is `{ "url", "id", "updated" }`, where `updated` is an ISO 8601
timestamp. A `PUT` with an unchanged `url`/`id` returns
`{ "changed": false, "record": ... }` and does not write to KV. A changed write
returns `{ "changed": true, "record": ... }`. Rejected writes get `401` (bad
token), `413` (body over 1 KiB), `422` (not a private IPv4 HTTP URL, or an
invalid id), `400` (bad JSON), or `500` (the Worker has no `WRITE_TOKEN`
secret).

The write token is a Worker secret. The backend sends it as
`Authorization: Bearer <token>`; it is never sent to clients. Locally,
`wrangler dev` reads the same value from `.dev.vars` (git-ignored).

## First deploy

```bash
pnpm exec wrangler kv namespace create RECORD
# put the returned id in wrangler.toml
pnpm exec wrangler deploy
pnpm exec wrangler secret put WRITE_TOKEN
```

Then point the backend at the deployed URL with `WORKER_URL` and
`WORKER_TOKEN` (see the root README).
