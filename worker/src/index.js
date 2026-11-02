const RECORD_KEY = "record";
const MAX_BODY_BYTES = 1024;
const ID_PATTERN = /^[A-Za-z0-9_-]{1,64}$/;
const IPV4_PATTERN = /^\d{1,3}(?:\.\d{1,3}){3}$/;

const CORS_HEADERS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, HEAD, PUT, OPTIONS",
  "Access-Control-Allow-Headers": "Authorization, Content-Type",
  "Access-Control-Max-Age": "86400",
};

/**
 * Tiny rendezvous record for Selfdrop.
 *
 * GET  /r  public read of { url, id, updated }
 * PUT  /r  token-protected write, private IPv4 URLs only
 */
export default {
  fetch(request, env) {
    return handleRequest(request, env);
  },
};

export async function handleRequest(request, env) {
  const { pathname } = new URL(request.url);

  if (pathname !== "/r" && pathname !== "/") {
    return json({ error: "not_found" }, 404);
  }

  if (request.method === "OPTIONS") {
    return new Response(null, { status: 204, headers: CORS_HEADERS });
  }

  if (request.method === "GET" || request.method === "HEAD") {
    return readRecord(request, env);
  }

  if (request.method === "PUT") {
    return writeRecord(request, env);
  }

  return json({ error: "method_not_allowed" }, 405, { Allow: "GET, HEAD, PUT, OPTIONS" });
}

async function readRecord(request, env) {
  const record = await env.RECORD.get(RECORD_KEY, "json");

  if (!record) {
    return json({ error: "not_found" }, 404, { "Cache-Control": "no-store" });
  }

  return json(record, 200, { "Cache-Control": "no-store" });
}

async function writeRecord(request, env) {
  if (!env.WRITE_TOKEN) {
    return json({ error: "worker_not_configured" }, 500);
  }

  const authorization = request.headers.get("Authorization") ?? "";
  if (!timingSafeEqual(authorization, `Bearer ${env.WRITE_TOKEN}`)) {
    return json({ error: "unauthorized" }, 401, { "WWW-Authenticate": "Bearer" });
  }

  const declaredLength = Number(request.headers.get("Content-Length") ?? "0");
  if (Number.isFinite(declaredLength) && declaredLength > MAX_BODY_BYTES) {
    return json({ error: "payload_too_large" }, 413);
  }

  const raw = await request.text();
  if (new TextEncoder().encode(raw).length > MAX_BODY_BYTES) {
    return json({ error: "payload_too_large" }, 413);
  }

  let payload;
  try {
    payload = JSON.parse(raw);
  } catch {
    return json({ error: "invalid_json" }, 400);
  }

  if (payload === null || typeof payload !== "object" || Array.isArray(payload)) {
    return json({ error: "invalid_json" }, 400);
  }

  const { url, id } = payload;

  if (typeof url !== "string" || !isPrivateHttpUrl(url)) {
    return json(
      { error: "invalid_url", detail: "url must be http://<private IPv4>:<port>" },
      422,
    );
  }

  if (typeof id !== "string" || !ID_PATTERN.test(id)) {
    return json(
      { error: "invalid_id", detail: "id must be 1-64 characters of [A-Za-z0-9_-]" },
      422,
    );
  }

  const existing = await env.RECORD.get(RECORD_KEY, "json");
  if (existing && existing.url === url && existing.id === id) {
    return json({ changed: false, record: existing }, 200, { "Cache-Control": "no-store" });
  }

  const record = { url, id, updated: new Date().toISOString() };
  await env.RECORD.put(RECORD_KEY, JSON.stringify(record));

  return json({ changed: true, record }, 200, { "Cache-Control": "no-store" });
}

/**
 * Accepts exactly http://<private IPv4>:<port>, with no credentials, path,
 * query or fragment. Rejecting everything else means a leaked write token
 * cannot redirect clients to a public host.
 */
export function isPrivateHttpUrl(value) {
  if (!value || value.length > 64) {
    return false;
  }

  let parsed;
  try {
    parsed = new URL(value);
  } catch {
    return false;
  }

  if (parsed.protocol !== "http:") {
    return false;
  }
  if (parsed.username || parsed.password) {
    return false;
  }
  if (parsed.pathname !== "/" || parsed.search || parsed.hash) {
    return false;
  }
  if (!parsed.port) {
    return false;
  }

  const port = Number(parsed.port);
  if (!Number.isInteger(port) || port < 1 || port > 65535) {
    return false;
  }

  const host = parsed.hostname;
  if (!IPV4_PATTERN.test(host)) {
    return false;
  }

  const octets = host.split(".").map(Number);
  if (octets.some((octet) => !Number.isInteger(octet) || octet < 0 || octet > 255)) {
    return false;
  }

  const [first, second] = octets;
  return first === 10
    || (first === 172 && second >= 16 && second <= 31)
    || (first === 192 && second === 168);
}

function timingSafeEqual(left, right) {
  const encoder = new TextEncoder();
  const a = encoder.encode(left);
  const b = encoder.encode(right);

  if (a.length !== b.length) {
    return false;
  }

  let difference = 0;
  for (let index = 0; index < a.length; index += 1) {
    difference |= a[index] ^ b[index];
  }
  return difference === 0;
}

function json(body, status = 200, extraHeaders = {}) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      ...CORS_HEADERS,
      ...extraHeaders,
    },
  });
}
