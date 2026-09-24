// POST /debug/push (spec §7.5).
const { test, beforeEach } = require("node:test");
const assert = require("node:assert/strict");
const h = require("./support/harness");

const worker = h.load("index").default;

const ADMIN_TOKEN = "0123456789abcdef".repeat(4); // 64 chars, like `openssl rand -hex 32`
const HOST_ID = h.HOST_A.id;

beforeEach(() => {
  h.clock.set(h.VECTOR_TIME * 1000);
  h.fetchMock.reset(() => new Response(null, { status: 200 }));
});

function enabledEnv(vars = {}) {
  const ctx = h.makeEnv({ DEBUG_PUSH_ENABLED: "true", DEBUG_PUSH_ADMIN_TOKEN: ADMIN_TOKEN, ...vars });
  // The debug endpoint must never create registrations or alarms.
  const forbidden = { idFromName() { throw new Error("no DO use"); }, idFromString() { throw new Error("no DO use"); }, newUniqueId() { throw new Error("no DO use"); }, get() { throw new Error("no DO use"); } };
  ctx.env.HOST_CHANNEL = forbidden;
  ctx.env.PUSH_REGISTRATION = forbidden;
  return ctx;
}

function debugRequest(body, token = ADMIN_TOKEN, extraHeaders = {}) {
  const headers = { ...extraHeaders };
  if (token !== null) headers.authorization = `Bearer ${token}`;
  return new Request("https://proxy/debug/push", {
    method: "POST",
    headers,
    body: typeof body === "string" ? body : JSON.stringify(body),
  });
}

const iosAlert = { platform: "ios", pushToken: h.IOS_TOKEN, apnsEnvironment: "sandbox", mode: "alert", title: "测试标题", body: "测试正文" };

test("disabled unless DEBUG_PUSH_ENABLED is exactly \"true\" and the admin token has >= 32 chars", async () => {
  const configs = [
    {},
    { DEBUG_PUSH_ENABLED: "false", DEBUG_PUSH_ADMIN_TOKEN: ADMIN_TOKEN },
    { DEBUG_PUSH_ENABLED: "TRUE", DEBUG_PUSH_ADMIN_TOKEN: ADMIN_TOKEN },
    { DEBUG_PUSH_ENABLED: "true" },
    { DEBUG_PUSH_ENABLED: "true", DEBUG_PUSH_ADMIN_TOKEN: "x".repeat(31) },
  ];
  for (const vars of configs) {
    const { env, limiters } = h.makeEnv(vars);
    const token = vars.DEBUG_PUSH_ADMIN_TOKEN ?? ADMIN_TOKEN;
    const response = await worker.fetch(debugRequest(iosAlert, token), env);
    assert.equal(response.status, 404, JSON.stringify(vars));
    assert.deepEqual(await response.json(), { error: "not_found" });
    assert.equal(limiters.size, 0);
  }
  const { env } = enabledEnv();
  const get = await worker.fetch(new Request("https://proxy/debug/push", { headers: { authorization: `Bearer ${ADMIN_TOKEN}` } }), env);
  assert.equal(get.status, 404);
  assert.equal(h.fetchMock.calls.length, 0);
});

test("a wrong or missing admin token is unauthorized and calls no provider", async () => {
  const { env, limiters } = enabledEnv();
  const attempts = [
    debugRequest(iosAlert, "wrong-token"),
    debugRequest(iosAlert, ADMIN_TOKEN.slice(0, -1)),
    debugRequest(iosAlert, `${ADMIN_TOKEN}x`),
    debugRequest(iosAlert, null),
    debugRequest(iosAlert, null, { authorization: `Basic ${ADMIN_TOKEN}` }),
    debugRequest(iosAlert, null, { authorization: ADMIN_TOKEN }),
    debugRequest(iosAlert, null, { authorization: "Bearer " }),
  ];
  for (const request of attempts) {
    const response = await worker.fetch(request, env);
    assert.equal(response.status, 401);
    assert.deepEqual(await response.json(), { error: "unauthorized" });
  }
  assert.equal(h.fetchMock.calls.length, 0);
  assert.equal(limiters.size, 0, "unauthorized requests do not consume the debug budget");
});

test("iOS alert: exactly one APNs call with routing keys; response shape", async () => {
  const { env } = enabledEnv();
  const response = await worker.fetch(debugRequest({ ...iosAlert, hostId: HOST_ID, threadId: "thread-9", turnId: "turn-9" }), env);
  assert.equal(response.status, 200);
  const body = await response.json();
  assert.deepEqual(Object.keys(body), ["requestId", "provider", "accepted", "providerStatus", "error"]);
  assert.match(body.requestId, /^dbg_[0-9a-f]{32}$/);
  assert.deepEqual({ ...body, requestId: undefined }, { requestId: undefined, provider: "apns", accepted: true, providerStatus: 200, error: null });

  assert.equal(h.fetchMock.calls.length, 1);
  const call = h.fetchMock.calls[0];
  assert.equal(call.url, `https://api.sandbox.push.apple.com/3/device/${h.IOS_TOKEN}`);
  const { authorization, ...headers } = call.headers;
  assert.match(authorization, /^bearer /);
  assert.deepEqual(headers, {
    "apns-push-type": "alert",
    "apns-priority": "10",
    "apns-expiration": String(h.clock.sec + 86400),
    "apns-topic": "com.akashark.agentbuddy",
  });
  assert.deepEqual(call.body, {
    aps: { alert: { title: "测试标题", body: "测试正文" }, sound: "default" },
    "agentbuddy.notification.serverId": `alleycat:${HOST_ID}`,
    "agentbuddy.notification.threadId": "thread-9",
    "agentbuddy.notification.turnId": "turn-9",
  });
});

test("iOS background: content-available, push-type background, priority 5", async () => {
  const { env } = enabledEnv();
  const response = await worker.fetch(debugRequest({ platform: "ios", pushToken: h.IOS_TOKEN, apnsEnvironment: "production", mode: "background" }), env);
  assert.equal((await response.json()).accepted, true);
  assert.equal(h.fetchMock.calls.length, 1);
  const call = h.fetchMock.calls[0];
  assert.equal(call.url, `https://api.push.apple.com/3/device/${h.IOS_TOKEN}`);
  assert.equal(call.headers["apns-push-type"], "background");
  assert.equal(call.headers["apns-priority"], "5");
  assert.equal(call.headers["apns-topic"], "com.akashark.agentbuddy");
  assert.deepEqual(call.body, { aps: { "content-available": 1 } });
});

test("Android alert and background: exactly one FCM send each", async () => {
  const { env } = enabledEnv();
  const alert = await worker.fetch(debugRequest({ platform: "android", pushToken: h.ANDROID_TOKEN, mode: "alert", body: "你好", threadId: "t-1" }), env);
  assert.deepEqual({ ...(await alert.json()), requestId: undefined }, { requestId: undefined, provider: "fcm", accepted: true, providerStatus: 200, error: null });
  let sends = h.fetchMock.providerCalls();
  assert.equal(sends.length, 1);
  assert.deepEqual(sends[0].body, {
    message: {
      token: h.ANDROID_TOKEN,
      data: { "agentbuddy.notification.threadId": "t-1" },
      android: { priority: "HIGH", ttl: "86400s", notification: { channel_id: "turn_complete", title: "调试通知", body: "你好" } },
    },
  });

  h.fetchMock.reset(() => new Response("{}", { status: 200 }));
  const background = await worker.fetch(debugRequest({ platform: "android", pushToken: h.ANDROID_TOKEN, mode: "background", hostId: HOST_ID }), env);
  assert.equal((await background.json()).accepted, true);
  sends = h.fetchMock.providerCalls();
  assert.equal(sends.length, 1);
  assert.deepEqual(sends[0].body, {
    message: {
      token: h.ANDROID_TOKEN,
      data: { type: "debug_background", "agentbuddy.notification.serverId": `alleycat:${HOST_ID}` },
      android: { priority: "HIGH" },
    },
  });
});

test("provider rejections are reported, never retried", async () => {
  const { env } = enabledEnv();
  for (const [status, reason] of [[400, "BadDeviceToken"], [403, "ExpiredProviderToken"], [503, "ServiceUnavailable"], [410, "Unregistered"]]) {
    h.fetchMock.reset(() => new Response(JSON.stringify({ reason }), { status }));
    const response = await worker.fetch(debugRequest(iosAlert), env);
    assert.equal(response.status, 200);
    const body = await response.json();
    assert.deepEqual({ ...body, requestId: undefined }, { requestId: undefined, provider: "apns", accepted: false, providerStatus: status, error: reason });
    assert.equal(h.fetchMock.calls.length, 1, `one call for ${status}`);
  }
  h.fetchMock.reset(() => { throw new TypeError("fetch failed"); });
  const network = await (await worker.fetch(debugRequest(iosAlert), env)).json();
  assert.equal(network.accepted, false);
  assert.equal(network.providerStatus, null);
  assert.equal(network.error, "network_error");
  assert.equal(h.fetchMock.calls.length, 1);
});

test("strict validation rejects bad requests without calling a provider", async () => {
  const invalid = [
    "{nope",
    "[]",
    { ...iosAlert, topic: "com.evil" },
    { ...iosAlert, url: "https://example.com" },
    { ...iosAlert, payload: { aps: {} } },
    { ...iosAlert, platform: "web" },
    { ...iosAlert, pushToken: "zz" },
    { ...iosAlert, pushToken: "a".repeat(201) },
    { ...iosAlert, apnsEnvironment: undefined },
    { ...iosAlert, apnsEnvironment: "development" },
    { platform: "android", pushToken: "x".repeat(4097), mode: "alert", body: "b" },
    { platform: "android", pushToken: "has space", mode: "alert", body: "b" },
    { platform: "android", pushToken: h.ANDROID_TOKEN, apnsEnvironment: "production", mode: "alert", body: "b" },
    { ...iosAlert, mode: undefined },
    { ...iosAlert, mode: "silent" },
    { ...iosAlert, body: undefined },
    { ...iosAlert, body: "" },
    { ...iosAlert, body: "b".repeat(201) },
    { ...iosAlert, title: "t".repeat(65) },
    { ...iosAlert, title: 5 },
    { ...iosAlert, hostId: "abc" },
    { ...iosAlert, hostId: HOST_ID.toUpperCase() },
    { ...iosAlert, threadId: "x".repeat(129) },
    { ...iosAlert, turnId: "a\nb" },
  ];
  for (const body of invalid) {
    // Fresh env per case so the 20/min debug budget is not the limiting factor.
    const response = await worker.fetch(debugRequest(body), enabledEnv().env);
    assert.equal(response.status, 400, JSON.stringify(body));
    assert.equal((await response.json()).error, "bad_request");
  }
  const { env } = enabledEnv();
  const tooLarge = await worker.fetch(debugRequest({ ...iosAlert, body: "x".repeat(17 * 1024) }), env);
  assert.equal(tooLarge.status, 413);
  assert.equal(h.fetchMock.calls.length, 0);

  // Boundaries are accepted: 64-char title, 200-char body (counted in characters).
  const ok = await worker.fetch(debugRequest({ ...iosAlert, title: "标".repeat(64), body: "文".repeat(200) }), env);
  assert.equal(ok.status, 200);
});

test("global rate limit of 20 requests per minute", async () => {
  const { env } = enabledEnv();
  for (let i = 0; i < 20; i++) {
    assert.equal((await worker.fetch(debugRequest(iosAlert), env)).status, 200);
  }
  const limited = await worker.fetch(debugRequest(iosAlert), env);
  assert.equal(limited.status, 429);
  assert.deepEqual(await limited.json(), { error: "rate_limited" });
  assert.ok(Number(limited.headers.get("retry-after")) >= 1);
  assert.equal(h.fetchMock.calls.length, 20);

  h.clock.advance(60_000);
  assert.equal((await worker.fetch(debugRequest(iosAlert), env)).status, 200);
});

test("logs never contain the token, Authorization, text or keys", async () => {
  const { env } = enabledEnv();
  const logs = h.captureLogs();
  try {
    await worker.fetch(debugRequest({ ...iosAlert, hostId: HOST_ID }), env);
    h.fetchMock.reset(() => new Response(JSON.stringify({ reason: "BadDeviceToken" }), { status: 400 }));
    await worker.fetch(debugRequest(iosAlert), env);
    h.fetchMock.reset(() => new Response("{}", { status: 200 }));
    await worker.fetch(debugRequest({ platform: "android", pushToken: h.ANDROID_TOKEN, mode: "alert", body: "安卓正文" }), env);
    await worker.fetch(debugRequest(iosAlert, "wrong-token"), env);
  } finally {
    logs.restore();
  }
  const text = logs.lines.join("\n");
  assert.ok(logs.lines.length >= 3);
  for (const secret of [h.IOS_TOKEN, h.ANDROID_TOKEN, ADMIN_TOKEN, "Bearer", "测试标题", "测试正文", "安卓正文", env.APNS_PRIVATE_KEY.split("\n")[1], env.FCM_PRIVATE_KEY.split("\n")[1]]) {
    assert.equal(text.includes(secret), false, `log leaked ${secret.slice(0, 12)}`);
  }
  assert.ok(text.includes(`token_sha256=${h.sha256(h.IOS_TOKEN).slice(0, 8)}`));
  assert.ok(text.includes(`token_sha256=${h.sha256(h.ANDROID_TOKEN).slice(0, 8)}`));
  assert.match(text, /debug push dbg_[0-9a-f]{32} platform=ios mode=alert providerStatus=200/);
});
