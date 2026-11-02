import assert from "node:assert/strict";
import test from "node:test";

import worker, { isPrivateHttpUrl } from "../src/index.js";

function memoryKv() {
  const values = new Map();
  return {
    values,
    async get(key, type) {
      const value = values.get(key);
      if (value === undefined) {
        return null;
      }
      return type === "json" ? JSON.parse(value) : value;
    },
    async put(key, value) {
      values.set(key, value);
    },
  };
}

function environment(token = "test-token") {
  return { WRITE_TOKEN: token, RECORD: memoryKv() };
}

function request(method, path, { body, headers } = {}) {
  return new Request(`https://rendezvous.test${path}`, {
    method,
    body,
    headers,
  });
}

function put(body, token = "test-token") {
  return request("PUT", "/r", {
    body: typeof body === "string" ? body : JSON.stringify(body),
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
    },
  });
}

test("returns 404 before anything has been published", async () => {
  const response = await worker.fetch(request("GET", "/r"), environment());

  assert.equal(response.status, 404);
  assert.equal(response.headers.get("Access-Control-Allow-Origin"), "*");
});

test("rejects writes without the bearer token", async () => {
  const env = environment();
  const response = await worker.fetch(
    request("PUT", "/r", {
      body: JSON.stringify({ url: "http://192.168.1.50:8080", id: "abc123" }),
      headers: { "Content-Type": "application/json" },
    }),
    env,
  );

  assert.equal(response.status, 401);
  assert.equal(env.RECORD.values.size, 0);
});

test("rejects a public URL", async () => {
  const env = environment();
  const response = await worker.fetch(
    put({ url: "http://example.com:8080", id: "abc123" }),
    env,
  );

  assert.equal(response.status, 422);
  assert.equal(env.RECORD.values.size, 0);
});

test("rejects a private URL with a path or query", async () => {
  for (const url of [
    "http://192.168.1.50:8080/files",
    "http://192.168.1.50:8080/?x=1",
    "http://user@192.168.1.50:8080",
    "https://192.168.1.50:8080",
    "http://192.168.1.50",
  ]) {
    const env = environment();
    const response = await worker.fetch(put({ url, id: "abc123" }), env);
    assert.equal(response.status, 422, url);
    assert.equal(env.RECORD.values.size, 0, url);
  }
});

test("stores a private record and returns it on read", async () => {
  const env = environment();

  const written = await worker.fetch(
    put({ url: "http://192.168.1.50:8080", id: "abc123" }),
    env,
  );
  assert.equal(written.status, 200);
  assert.equal((await written.json()).changed, true);

  const read = await worker.fetch(request("GET", "/r"), env);
  const record = await read.json();

  assert.equal(read.status, 200);
  assert.equal(read.headers.get("Cache-Control"), "no-store");
  assert.equal(record.url, "http://192.168.1.50:8080");
  assert.equal(record.id, "abc123");
  assert.ok(record.updated);
});

test("does not rewrite an unchanged record", async () => {
  const env = environment();
  const body = { url: "http://192.168.1.50:8080", id: "abc123" };

  await worker.fetch(put(body), env);
  const before = env.RECORD.values.get("record");

  const again = await worker.fetch(put(body), env);
  const payload = await again.json();

  assert.equal(again.status, 200);
  assert.equal(payload.changed, false);
  assert.equal(env.RECORD.values.get("record"), before);
});

test("writes again when the address changes", async () => {
  const env = environment();

  await worker.fetch(put({ url: "http://192.168.1.50:8080", id: "abc123" }), env);
  const response = await worker.fetch(
    put({ url: "http://192.168.1.51:8080", id: "abc123" }),
    env,
  );

  assert.equal((await response.json()).changed, true);
  assert.equal((await (await worker.fetch(request("GET", "/r"), env)).json()).url,
    "http://192.168.1.51:8080");
});

test("rejects an oversized body", async () => {
  const env = environment();
  const response = await worker.fetch(
    put({ url: "http://192.168.1.50:8080", id: "x".repeat(2000) }),
    env,
  );

  assert.equal(response.status, 413);
});

test("answers CORS preflight", async () => {
  const response = await worker.fetch(request("OPTIONS", "/r"), environment());

  assert.equal(response.status, 204);
  assert.equal(response.headers.get("Access-Control-Allow-Origin"), "*");
  assert.match(response.headers.get("Access-Control-Allow-Methods"), /PUT/);
});

test("only accepts private IPv4 URLs", () => {
  assert.equal(isPrivateHttpUrl("http://10.0.0.2:8080"), true);
  assert.equal(isPrivateHttpUrl("http://172.16.0.2:8080"), true);
  assert.equal(isPrivateHttpUrl("http://172.31.255.254:8080"), true);
  assert.equal(isPrivateHttpUrl("http://192.168.0.10:8080"), true);

  assert.equal(isPrivateHttpUrl("http://8.8.8.8:8080"), false);
  assert.equal(isPrivateHttpUrl("http://172.32.0.1:8080"), false);
  assert.equal(isPrivateHttpUrl("http://127.0.0.1:8080"), false);
  assert.equal(isPrivateHttpUrl("http://169.254.1.1:8080"), false);
  assert.equal(isPrivateHttpUrl("http://192.168.1.50.example:8080"), false);
});
