const { test, after } = require("node:test");
const assert = require("node:assert/strict");
const { execFileSync } = require("node:child_process");
const { generateKeyPairSync } = require("node:crypto");
const { mkdtempSync, rmSync } = require("node:fs");
const { tmpdir } = require("node:os");
const path = require("node:path");
const output = mkdtempSync(path.join(tmpdir(), "agentbuddy-push-test-"));
execFileSync(process.execPath, [require.resolve("typescript/bin/tsc"), "--noEmit", "false", "--module", "commonjs", "--moduleResolution", "node", "--outDir", output], { cwd: path.join(__dirname, "..") });
after(() => rmSync(output, { recursive: true, force: true }));
const { PushRegistration } = require(path.join(output, "durable-object.js"));
const { RateLimiter } = require(path.join(output, "rate-limiter.js"));
const { sendFCMPush } = require(path.join(output, "fcm.js"));
const worker = require(path.join(output, "index.js")).default;

const IOS_TOKEN = "a1b2c3d4".repeat(8);

function fakeStorage() {
  const map = new Map();
  const state = { alarm: null };
  const storage = {
    get: async (key) => structuredClone(map.get(key)),
    put: async (key, value) => { map.set(key, structuredClone(value)); },
    deleteAll: async () => { map.clear(); state.alarm = null; },
    setAlarm: async (time) => { state.alarm = time; },
  };
  return { map, state, storage };
}

function withNow(now, fn) {
  const realNow = Date.now;
  Date.now = () => now;
  return Promise.resolve().then(fn).finally(() => { Date.now = realNow; });
}

function registerEnv() {
  const stored = [];
  const env = {
    RATE_LIMITER: { idFromName: (name) => name, get: () => ({ fetch: async () => new Response("ok") }) },
    PUSH_REGISTRATION: {
      newUniqueId: () => ({ toString: () => "new-id" }),
      idFromString: (id) => id,
      get: () => ({ fetch: async (request) => { stored.push(await request.json()); return new Response("ok"); } }),
    },
  };
  return { env, stored };
}

function register(env, body) {
  const init = { method: "POST", body: typeof body === "string" ? body : JSON.stringify(body) };
  return worker.fetch(new Request("https://proxy/register", init), env);
}

test("register accepts the iOS client payload and schedules the first alarm", async () => {
  const { storage, map, state } = fakeStorage();
  const registration = new PushRegistration({ storage }, {});
  const env = registerEnv().env;
  env.PUSH_REGISTRATION.get = () => ({ fetch: (request) => registration.fetch(request) });
  await withNow(1_000_000, async () => {
    const response = await register(env, {
      platform: "ios", pushToken: IOS_TOKEN, apnsEnvironment: "sandbox", intervalSeconds: 30, ttlSeconds: 7200,
    });
    assert.equal(response.status, 200);
    assert.deepEqual(await response.json(), { id: "new-id" });
  });
  const reg = map.get("reg");
  assert.equal(reg.platform, "ios");
  assert.equal(reg.pushToken, IOS_TOKEN);
  assert.equal(reg.apnsEnvironment, "sandbox");
  assert.equal(reg.intervalSeconds, 30);
  assert.equal(reg.ttlSeconds, 7200);
  assert.equal(state.alarm, 1_000_000 + 30_000);
});

test("register accepts the Android client payload and keeps contentState", async () => {
  const { env, stored } = registerEnv();
  const contentState = { phase: "thinking", elapsedSeconds: 0, toolCallCount: 0, activeThreadCount: 1, serverId: "s", threadId: "t" };
  const response = await register(env, {
    platform: "android", pushToken: "fcm:APA91b-token_value", contentState, startTimestamp: 123, intervalSeconds: 30, ttlSeconds: 7200,
  });
  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), { id: "new-id" });
  assert.deepEqual(stored[0], {
    platform: "android", pushToken: "fcm:APA91b-token_value", apnsEnvironment: "production", intervalSeconds: 30, ttlSeconds: 7200, contentState,
  });
});

test("register applies defaults and clamps interval/ttl to safe bounds", async () => {
  const { env, stored } = registerEnv();
  const cases = [
    [{}, 30, 7200],
    [{ intervalSeconds: 1, ttlSeconds: 5 }, 10, 60],
    [{ intervalSeconds: 1e9, ttlSeconds: 1e9 }, 3600, 21600],
    [{ intervalSeconds: 45.9, ttlSeconds: null }, 45, 7200],
  ];
  for (const [extra, interval, ttl] of cases) {
    const response = await register(env, { platform: "ios", pushToken: IOS_TOKEN, ...extra });
    assert.equal(response.status, 200, JSON.stringify(extra));
    const body = stored.at(-1);
    assert.equal(body.intervalSeconds, interval, JSON.stringify(extra));
    assert.equal(body.ttlSeconds, ttl, JSON.stringify(extra));
  }
});

test("register rejects malformed bodies with 400 and stores nothing", async () => {
  const { env, stored } = registerEnv();
  const bad = [
    "{not json",
    [],
    { pushToken: IOS_TOKEN },
    { platform: "web", pushToken: IOS_TOKEN },
    { platform: "ios" },
    { platform: "ios", pushToken: "" },
    { platform: "android", pushToken: "   " },
    { platform: "android", pushToken: "x".repeat(1025) },
    { platform: "ios", pushToken: "../../3/device/abc?x=1" },
    { platform: "ios", pushToken: IOS_TOKEN, apnsEnvironment: "development" },
    { platform: "ios", pushToken: IOS_TOKEN, intervalSeconds: "30" },
    { platform: "ios", pushToken: IOS_TOKEN, ttlSeconds: "forever" },
    { platform: "android", pushToken: "tok", contentState: ["thinking"] },
    { platform: "android", pushToken: "tok", contentState: { phase: 1 } },
    { platform: "android", pushToken: "tok", contentState: { elapsedSeconds: "5" } },
    { platform: "android", pushToken: "tok", contentState: { toolCallCount: "5" } },
    { platform: "android", pushToken: "tok", contentState: { activeThreadCount: "5" } },
    { platform: "android", pushToken: "tok", contentState: { activeThreadCount: null } },
    { platform: "android", pushToken: "tok", contentState: { serverId: 5 } },
    { platform: "android", pushToken: "tok", contentState: { threadId: 5 } },
  ];
  for (const body of bad) {
    const response = await register(env, body);
    assert.equal(response.status, 400, JSON.stringify(body));
    const payload = await response.json();
    assert.equal(typeof payload.error, "string");
  }
  assert.equal(stored.length, 0);
});

test("register rejects oversized bodies with 413", async () => {
  const { env, stored } = registerEnv();
  const response = await register(env, { platform: "android", pushToken: "tok", contentState: { phase: "x".repeat(20_000) } });
  assert.equal(response.status, 413);
  assert.equal(stored.length, 0);
});

test("register rejects an oversized body up front using Content-Length, without waiting to read it", async () => {
  const { env, stored } = registerEnv();
  let bytesRead = 0;
  const body = new ReadableStream({
    pull(controller) {
      // Never actually deliver 20KB of data; if the handler read the body
      // before checking Content-Length, this stream would hang instead of
      // the request failing fast with 413.
      bytesRead += 1;
      controller.enqueue(new TextEncoder().encode("x"));
    },
  });
  const request = new Request("https://proxy/register", {
    method: "POST",
    // @ts-ignore duplex is required by undici for streaming bodies
    duplex: "half",
    headers: { "content-length": String(20 * 1024) },
    body,
  });
  const response = await worker.fetch(request, env);
  assert.equal(response.status, 413);
  assert.equal(stored.length, 0);
});

test("register accepts a partial contentState with only some fields set", async () => {
  const { env, stored } = registerEnv();
  const response = await register(env, {
    platform: "android", pushToken: "tok", contentState: { phase: "thinking", serverId: "s" },
  });
  assert.equal(response.status, 200);
  assert.deepEqual(stored[0].contentState, { phase: "thinking", serverId: "s" });
});

test("malformed registration ids return 400 instead of throwing", async () => {
  const { env } = registerEnv();
  env.PUSH_REGISTRATION.idFromString = () => { throw new TypeError("Invalid Durable Object ID"); };
  const response = await worker.fetch(new Request("https://proxy/not-a-hex-id/deregister", { method: "POST" }), env);
  assert.equal(response.status, 400);
  assert.deepEqual(await response.json(), { error: "invalid registration id" });

  const unknown = await worker.fetch(new Request("https://proxy/not-a-hex-id/update", { method: "POST" }), env);
  assert.equal(unknown.status, 404);
});

test("rate limiter window survives instance eviction and cleans up after expiry", async () => {
  const { storage, map, state } = fakeStorage();
  const check = (limiter) => limiter.fetch(new Request("https://rl/check"));
  await withNow(5_000_000, async () => {
    const first = new RateLimiter({ storage });
    for (let i = 0; i < 10; i++) assert.equal((await check(first)).status, 200);
    assert.equal((await check(first)).status, 429);
    // A fresh instance (DO evicted and recreated) must still see the window.
    const second = new RateLimiter({ storage });
    assert.equal((await check(second)).status, 429);
    assert.equal(state.alarm, 5_000_000 + 60_000);
  });
  await withNow(5_000_000 + 60_001, async () => {
    const third = new RateLimiter({ storage });
    await third.alarm();
    assert.equal(map.size, 0);
    assert.equal((await check(third)).status, 200);
  });
});

test("FCM OAuth failures are not cached and do not throw", async () => {
  const { privateKey } = generateKeyPairSync("rsa", {
    modulusLength: 2048,
    privateKeyEncoding: { type: "pkcs8", format: "pem" },
    publicKeyEncoding: { type: "spki", format: "pem" },
  });
  const env = { FCM_PROJECT_ID: "proj", FCM_CLIENT_EMAIL: "svc@example.com", FCM_PRIVATE_KEY: privateKey };
  const tokenResponses = [
    () => new Response(JSON.stringify({ error: "invalid_grant" }), { status: 400 }),
    () => new Response(JSON.stringify({ token_type: "Bearer" }), { status: 200 }),
    () => new Response(JSON.stringify({ access_token: "good-token", expires_in: 3599 }), { status: 200 }),
  ];
  let tokenCalls = 0;
  const sendAuth = [];
  const realFetch = globalThis.fetch;
  globalThis.fetch = async (input, init) => {
    const url = typeof input === "string" ? input : input.url;
    if (url === "https://oauth2.googleapis.com/token") return tokenResponses[tokenCalls++]();
    sendAuth.push(init.headers.authorization);
    return new Response("{}", { status: 200 });
  };
  try {
    assert.deepEqual(await sendFCMPush(env, "tok", {}), { ok: false, unregistered: false });
    assert.deepEqual(await sendFCMPush(env, "tok", {}), { ok: false, unregistered: false });
    assert.equal(tokenCalls, 2);
    assert.equal(sendAuth.length, 0);

    assert.deepEqual(await sendFCMPush(env, "tok", {}), { ok: true, unregistered: false });
    assert.deepEqual(await sendFCMPush(env, "tok", {}), { ok: true, unregistered: false });
    assert.equal(tokenCalls, 3);
    assert.deepEqual(sendAuth, ["Bearer good-token", "Bearer good-token"]);
  } finally {
    globalThis.fetch = realFetch;
  }
});
