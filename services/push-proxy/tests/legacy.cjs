// Legacy keepalive retirement switch (spec §7.6).
const { test, beforeEach } = require("node:test");
const assert = require("node:assert/strict");
const h = require("./support/harness");

const worker = h.load("index").default;
const { PushRegistration } = h.load("durable-object");

beforeEach(() => {
  h.clock.set(h.VECTOR_TIME * 1000);
  h.fetchMock.reset(() => new Response(null, { status: 200 }));
});

function legacyEnv(vars) {
  const calls = { limiter: 0, registration: 0 };
  const env = {
    ...h.makeEnv(vars).env,
    RATE_LIMITER: { idFromName: (n) => n, get: () => ({ fetch: async () => { calls.limiter++; return new Response("ok"); } }) },
    PUSH_REGISTRATION: {
      newUniqueId: () => ({ toString: () => "new-id" }),
      idFromString: (id) => id,
      get: () => ({ fetch: async (request) => { calls.registration++; return request.url.endsWith("/deregister") ? Response.json({ ok: true }) : new Response("ok"); } }),
    },
  };
  return { env, calls };
}

const registerBody = JSON.stringify({ platform: "ios", pushToken: h.IOS_TOKEN, apnsEnvironment: "production" });

test("LEGACY_KEEPALIVE_ENABLED=false: /register answers 410 legacy_push_retired and creates nothing", async () => {
  const { env, calls } = legacyEnv({ LEGACY_KEEPALIVE_ENABLED: "false" });
  const response = await worker.fetch(new Request("https://proxy/register", { method: "POST", body: registerBody }), env);
  assert.equal(response.status, 410);
  assert.deepEqual(await response.json(), { error: "legacy_push_retired" });
  assert.deepEqual(calls, { limiter: 0, registration: 0 });
});

test("LEGACY_KEEPALIVE_ENABLED=false: /:id/deregister still returns 200", async () => {
  const { env } = legacyEnv({ LEGACY_KEEPALIVE_ENABLED: "false" });
  const response = await worker.fetch(new Request("https://proxy/abc123/deregister", { method: "POST" }), env);
  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), { ok: true });
});

test("default and \"true\" keep the legacy path unchanged", async () => {
  for (const vars of [{}, { LEGACY_KEEPALIVE_ENABLED: "true" }]) {
    const { env, calls } = legacyEnv(vars);
    const response = await worker.fetch(new Request("https://proxy/register", { method: "POST", body: registerBody }), env);
    assert.equal(response.status, 200);
    assert.deepEqual(await response.json(), { id: "new-id" });
    assert.deepEqual(calls, { limiter: 1, registration: 1 });
  }
});

function registrationWithStorage(env) {
  const ctx = h.fakeStorage();
  ctx.map.set("reg", {
    platform: "ios", pushToken: h.IOS_TOKEN, apnsEnvironment: "production", intervalSeconds: 30,
    ttlSeconds: 7200, pushCount: 3, createdAt: h.clock.now - 90_000,
  });
  ctx.alarm = null;
  return { ctx, registration: new PushRegistration({ storage: ctx.storage }, env) };
}

test("with the switch off, the old PushRegistration alarm deletes itself and never re-arms", async () => {
  const { ctx, registration } = registrationWithStorage({ ...h.makeEnv().env, LEGACY_KEEPALIVE_ENABLED: "false" });
  await registration.alarm();
  assert.equal(ctx.map.size, 0);
  assert.equal(ctx.alarm, null);
  assert.equal(h.fetchMock.calls.length, 0, "no silent push sent");
});

test("with the switch on, the old alarm keeps sending silent pushes and re-arms", async () => {
  const { ctx, registration } = registrationWithStorage({ ...h.makeEnv().env, LEGACY_KEEPALIVE_ENABLED: "true" });
  const logs = h.captureLogs();
  try {
    await registration.alarm();
  } finally {
    logs.restore();
  }
  assert.equal(h.fetchMock.calls.length, 1);
  assert.equal(h.fetchMock.calls[0].headers["apns-push-type"], "background");
  assert.equal(ctx.map.get("reg").pushCount, 4);
  assert.equal(ctx.alarm, h.clock.now + 30_000);
});
