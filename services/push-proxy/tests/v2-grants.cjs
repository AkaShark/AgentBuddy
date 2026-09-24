// Device grant rules (spec §5.1), idempotent registration and device revoke (§5.3).
const { test, beforeEach } = require("node:test");
const assert = require("node:assert/strict");
const h = require("./support/harness");

const worker = h.load("index").default;
const { RATE_LIMITS, MAX_UNSEEN_REVOCATIONS } = h.load("host-channel");
const { HOST_A, HOST_B, DEVICE_A, DEVICE_B } = h;

beforeEach(() => {
  h.clock.set(h.VECTOR_TIME * 1000);
  h.fetchMock.reset(() => new Response(null, { status: 200 }));
});

async function register(env, grant, host = HOST_A) {
  const response = await worker.fetch(h.hostRequest(host, "POST", "/v2/subscriptions", grant), env);
  return { status: response.status, body: await response.json() };
}

test("a valid grant registers a subscription with the spec storage layout", async () => {
  const { env, channel } = h.makeEnv();
  const grant = h.makeGrant(DEVICE_A, HOST_A.id);
  const { status, body } = await register(env, grant);
  assert.equal(status, 201);
  assert.match(body.subscriptionId, /^sub_[0-9a-f]{32}$/);
  assert.equal(body.expiresAt, grant.expiresAt);

  const ctx = channel(HOST_A.id);
  const sub = ctx.map.get(`sub:${body.subscriptionId}`);
  assert.deepEqual(
    { ...sub, createdAt: undefined },
    {
      subscriptionId: body.subscriptionId, hostId: HOST_A.id, deviceId: DEVICE_A.id, platform: "ios",
      pushToken: h.IOS_TOKEN, apnsEnvironment: "production", agent: "codex", threadId: "thread-1", turnId: "turn-1",
      issuedAt: grant.issuedAt, expiresAt: grant.expiresAt, grantNonce: grant.grantNonce, createdAt: undefined,
    }
  );
  assert.equal(JSON.stringify(sub).includes(grant.sealedTarget), false, "the sealed target itself is not stored");
  assert.equal(ctx.map.get(`idx:codex|thread-1|turn-1|${DEVICE_A.id}`), body.subscriptionId);
  assert.deepEqual(ctx.map.get(`grant:${grant.grantNonce}`), { exp: grant.expiresAt * 1000 });
  assert.ok(ctx.alarm !== null && ctx.alarm <= grant.expiresAt * 1000, "alarm armed for cleanup");
});

test("re-registering the same unique key returns the existing subscription (200)", async () => {
  const { env, channel } = h.makeEnv();
  const grant = h.makeGrant(DEVICE_A, HOST_A.id);
  const first = await register(env, grant);
  const second = await register(env, grant);
  assert.equal(first.status, 201);
  assert.equal(second.status, 200);
  assert.deepEqual(second.body, first.body);
  assert.equal(channel(HOST_A.id).keys("sub:").length, 1);
});

test("a newer grant for the same key keeps the id and adopts the rotated token; an older one does not", async () => {
  const { env, channel } = h.makeEnv();
  const first = await register(env, h.makeGrant(DEVICE_A, HOST_A.id));
  h.clock.advance(60_000);
  const rotated = h.makeGrant(DEVICE_A, HOST_A.id, { pushToken: h.IOS_TOKEN_2, apnsEnvironment: "sandbox", expiresAt: h.clock.sec + 7200 });
  const second = await register(env, rotated);
  assert.equal(second.status, 200);
  assert.equal(second.body.subscriptionId, first.body.subscriptionId);
  assert.equal(second.body.expiresAt, rotated.expiresAt);
  let sub = channel(HOST_A.id).map.get(`sub:${first.body.subscriptionId}`);
  assert.equal(sub.pushToken, h.IOS_TOKEN_2);
  assert.equal(sub.apnsEnvironment, "sandbox");

  const stale = h.makeGrant(DEVICE_A, HOST_A.id, { issuedAt: h.clock.sec - 120, expiresAt: h.clock.sec + 3600 });
  const third = await register(env, stale);
  assert.equal(third.status, 200);
  sub = channel(HOST_A.id).map.get(`sub:${first.body.subscriptionId}`);
  assert.equal(sub.pushToken, h.IOS_TOKEN_2, "older grant does not override");
});

test("different devices and different turns get separate subscriptions", async () => {
  const { env, channel } = h.makeEnv();
  const a = await register(env, h.makeGrant(DEVICE_A, HOST_A.id));
  const b = await register(env, h.makeGrant(DEVICE_B, HOST_A.id));
  const c = await register(env, h.makeGrant(DEVICE_A, HOST_A.id, { turnId: "turn-2" }));
  assert.deepEqual([a.status, b.status, c.status], [201, 201, 201]);
  assert.equal(new Set([a.body.subscriptionId, b.body.subscriptionId, c.body.subscriptionId]).size, 3);
  assert.equal(channel(HOST_A.id).keys("sub:").length, 3);
});

test("android grants sign environment=none", async () => {
  const { env } = h.makeEnv();
  const plain = await register(env, h.makeGrant(DEVICE_A, HOST_A.id, { platform: "android", pushToken: h.ANDROID_TOKEN, apnsEnvironment: undefined }));
  assert.equal(plain.status, 201);
  const explicitNone = await register(env, h.makeGrant(DEVICE_A, HOST_A.id, { platform: "android", pushToken: h.ANDROID_TOKEN, apnsEnvironment: "none", turnId: "turn-2" }));
  assert.equal(explicitNone.status, 201);
  const nullEnv = await register(env, h.makeGrant(DEVICE_A, HOST_A.id, { platform: "android", pushToken: h.ANDROID_TOKEN, apnsEnvironment: null, turnId: "turn-3" }));
  assert.equal(nullEnv.status, 201);
  const signedProduction = await register(env, h.makeGrant(DEVICE_A, HOST_A.id, { platform: "android", pushToken: h.ANDROID_TOKEN, apnsEnvironment: undefined, turnId: "turn-4" }, { environment: "production" }));
  assert.equal(signedProduction.status, 403);
});

test("grant rules: each violation is forbidden and stores nothing", async () => {
  const { env, channel } = h.makeEnv();
  const now = h.clock.sec;
  const cases = {
    "grant for another host": h.makeGrant(DEVICE_A, HOST_A.id, {}, { host: HOST_B.id }),
    "grant for another Worker (aud)": h.makeGrant(DEVICE_A, HOST_A.id, {}, { aud: "https://other.example.test" }),
    "target hash mismatch": h.makeGrant(DEVICE_A, HOST_A.id, {}, {
      target: h.sealTarget({ platform: "ios", token: h.IOS_TOKEN_2, apnsEnvironment: "production", deviceId: DEVICE_A.id, hostId: HOST_A.id }),
    }),
    "environment mismatch": h.makeGrant(DEVICE_A, HOST_A.id, { apnsEnvironment: "sandbox" }, { environment: "production" }),
    "thread mismatch": h.makeGrant(DEVICE_A, HOST_A.id, {}, { thread: "thread-2" }),
    "signed by another device": h.makeGrant(DEVICE_A, HOST_A.id, {}, { signer: DEVICE_B }),
    "expired": h.makeGrant(DEVICE_A, HOST_A.id, { issuedAt: now - 7200, expiresAt: now - 1 }),
    "expires now": h.makeGrant(DEVICE_A, HOST_A.id, { issuedAt: now - 7200, expiresAt: now }),
    "lifetime over 48h": h.makeGrant(DEVICE_A, HOST_A.id, { issuedAt: now, expiresAt: now + 48 * 3600 + 1 }),
    "expires before issued": h.makeGrant(DEVICE_A, HOST_A.id, { issuedAt: now + 10, expiresAt: now + 5 }),
    "issued in the future": h.makeGrant(DEVICE_A, HOST_A.id, { issuedAt: now + 301, expiresAt: now + 3600 }),
  };
  for (const [name, grant] of Object.entries(cases)) {
    const { status, body } = await register(env, grant);
    assert.equal(status, 403, name);
    assert.equal(body.error, "forbidden", name);
  }
  assert.deepEqual(channel(HOST_A.id).keys("sub:"), []);
  assert.deepEqual(channel(HOST_A.id).keys("grant:"), []);

  const maxLifetime = await register(env, h.makeGrant(DEVICE_A, HOST_A.id, { issuedAt: now, expiresAt: now + 48 * 3600 }));
  assert.equal(maxLifetime.status, 201);
});

test("a grant nonce is single-use until the grant expires", async () => {
  const { env, channel } = h.makeEnv();
  const grant = h.makeGrant(DEVICE_A, HOST_A.id);
  const first = await register(env, grant);
  assert.equal(first.status, 201);

  // Same nonce, validly signed, for a different turn.
  const reused = h.makeGrant(DEVICE_A, HOST_A.id, { turnId: "turn-2", grantNonce: grant.grantNonce });
  const second = await register(env, reused);
  assert.equal(second.status, 403);

  // After the subscription is deleted, the consumed grant cannot recreate it.
  const del = await worker.fetch(h.hostRequest(HOST_A, "DELETE", `/v2/subscriptions/${first.body.subscriptionId}`), env);
  assert.equal(del.status, 200);
  const replay = await register(env, grant);
  assert.equal(replay.status, 403);
  assert.equal(replay.body.error, "forbidden");

  // The nonce record is dropped once the grant has expired.
  h.clock.set((grant.expiresAt + 1) * 1000);
  await h.runAlarm(channel(HOST_A.id));
  assert.deepEqual(channel(HOST_A.id).keys("grant:"), []);
});

test("device revoke deletes that device's subscriptions and blocks older grants", async () => {
  const { env, channel } = h.makeEnv();
  await register(env, h.makeGrant(DEVICE_A, HOST_A.id));
  await register(env, h.makeGrant(DEVICE_A, HOST_A.id, { turnId: "turn-2" }));
  const other = await register(env, h.makeGrant(DEVICE_B, HOST_A.id));
  const elsewhere = await register(env, h.makeGrant(DEVICE_A, HOST_B.id), HOST_B);
  const preRevokeGrant = h.makeGrant(DEVICE_A, HOST_A.id, { turnId: "turn-3" });

  h.clock.advance(5_000);
  const revoke = h.makeRevoke(DEVICE_A, HOST_A.id);
  const response = await worker.fetch(h.revokeRequest(revoke), env);
  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), { revoked: 2 });

  const ctxA = channel(HOST_A.id);
  assert.deepEqual(ctxA.keys("sub:"), [`sub:${other.body.subscriptionId}`]);
  assert.deepEqual(ctxA.map.get(`revoked:${DEVICE_A.id}`).at, revoke.timestamp);
  assert.ok(channel(HOST_B.id).map.has(`sub:${elsewhere.body.subscriptionId}`), "other hosts are unaffected");

  const old = await register(env, preRevokeGrant);
  assert.equal(old.status, 403);
  assert.equal(old.body.message, "grant issued before device revocation");
  // Issued in the same second as the revocation: also rejected (LOW-7).
  const sameSecond = await register(env, h.makeGrant(DEVICE_A, HOST_A.id, { turnId: "turn-4", issuedAt: revoke.timestamp }));
  assert.equal(sameSecond.status, 403);
  assert.equal(sameSecond.body.message, "grant issued before device revocation");
  const fresh = await register(env, h.makeGrant(DEVICE_A, HOST_A.id, { turnId: "turn-4", issuedAt: revoke.timestamp + 1 }));
  assert.equal(fresh.status, 201);

  // An older revoke never moves the revocation point backwards.
  const older = h.makeRevoke(DEVICE_A, HOST_A.id, { timestamp: revoke.timestamp - 60 });
  assert.equal((await worker.fetch(h.revokeRequest(older), env)).status, 200);
  assert.equal(ctxA.map.get(`revoked:${DEVICE_A.id}`).at, revoke.timestamp);
});

test("revoke requests: bad signature, stale timestamp and replay are unauthorized; bad shape is bad_request", async () => {
  const { env, channels } = h.makeEnv();
  const cases = [
    [h.makeRevoke(DEVICE_A, HOST_A.id, {}, DEVICE_B), 401, "unauthorized"],
    [{ ...h.makeRevoke(DEVICE_A, HOST_A.id), hostId: HOST_B.id }, 401, "unauthorized"],
    [h.makeRevoke(DEVICE_A, HOST_A.id, { timestamp: h.clock.sec - 301 }), 401, "unauthorized"],
    [h.makeRevoke(DEVICE_A, HOST_A.id, {}, DEVICE_A, "https://other.example.test"), 401, "unauthorized"],
    [h.makeRevoke(DEVICE_A, HOST_A.id, { scope: "one" }), 400, "bad_request"],
    [h.makeRevoke(DEVICE_A, HOST_A.id, { nonce: "xyz" }), 400, "bad_request"],
    [{ ...h.makeRevoke(DEVICE_A, HOST_A.id), signature: undefined }, 400, "bad_request"],
    ["{broken", 400, "bad_request"],
  ];
  for (const [body, status, code] of cases) {
    const response = await worker.fetch(h.revokeRequest(body, `192.0.2.${status}`), env);
    assert.equal(response.status, status, JSON.stringify(body));
    assert.equal((await response.json()).error, code);
  }
  assert.equal(channels.size, 0);

  const revoke = h.makeRevoke(DEVICE_A, HOST_A.id);
  assert.equal((await worker.fetch(h.revokeRequest(revoke), env)).status, 200);
  const replay = await worker.fetch(h.revokeRequest(revoke), env);
  assert.equal(replay.status, 401);
});

test("revocation records expire after the longest grant lifetime", async () => {
  const { env, channel } = h.makeEnv();
  await worker.fetch(h.revokeRequest(h.makeRevoke(DEVICE_A, HOST_A.id)), env);
  const ctx = channel(HOST_A.id);
  assert.ok(ctx.map.has(`revoked:${DEVICE_A.id}`));
  h.clock.advance(48 * 3600_000);
  await h.runAlarm(ctx);
  assert.ok(ctx.map.has(`revoked:${DEVICE_A.id}`), "kept while an older grant could still be unexpired");
  h.clock.advance(3600_000 + 1);
  await h.runAlarm(ctx);
  assert.equal(ctx.map.size, 0);
  assert.equal(ctx.alarm, null, "nothing left, no alarm re-armed");
});

test("revoke nonces live apart from host nonces: neither can pre-claim the other", async () => {
  const { env, channel } = h.makeEnv();
  await register(env, h.makeGrant(DEVICE_A, HOST_A.id));
  const ctx = channel(HOST_A.id);

  // A device revoke (anyone can mint a device key) using nonce N ...
  const nonce = h.randHex(16);
  const stranger = h.ed25519FromSeed(h.randHex(32));
  assert.equal((await worker.fetch(h.revokeRequest(h.makeRevoke(stranger, HOST_A.id, { nonce })), env)).status, 200);
  assert.ok(ctx.map.has(`rnonce:${nonce}`));
  assert.equal(ctx.map.has(`nonce:${nonce}`), false);
  // ... does not block the host's own request with the same nonce.
  const event = await worker.fetch(h.hostRequest(HOST_A, "POST", "/v2/events", h.makeEvent(), { nonce }), env);
  assert.equal(event.status, 202);
  assert.equal((await event.json()).matched, 1);
  assert.ok(ctx.map.has(`nonce:${nonce}`));

  // And a host nonce does not block a revoke; a replayed revoke nonce does.
  const hostNonce = h.randHex(16);
  const sub = await worker.fetch(h.hostRequest(HOST_A, "POST", "/v2/subscriptions", h.makeGrant(DEVICE_A, HOST_A.id, { turnId: "turn-9" }), { nonce: hostNonce }), env);
  assert.equal(sub.status, 201);
  assert.ok(ctx.map.has(`nonce:${hostNonce}`));
  const revoke = h.makeRevoke(DEVICE_B, HOST_A.id, { nonce: hostNonce });
  assert.equal((await worker.fetch(h.revokeRequest(revoke, "198.51.100.77"), env)).status, 200);
  const replay = await worker.fetch(h.revokeRequest(h.makeRevoke(DEVICE_B, HOST_A.id, { nonce: hostNonce }), "198.51.100.78"), env);
  assert.equal(replay.status, 401);

  // Revoke nonces expire like host nonces.
  h.clock.advance(10 * 60_000 + 1);
  await h.runAlarm(ctx);
  assert.deepEqual(ctx.keys("rnonce:"), []);
});

test("device revoke is rate limited per target host", async () => {
  const { env } = h.makeEnv();
  const limit = RATE_LIMITS.revokes;
  for (let i = 0; i < limit; i++) {
    // A different client IP each time, so only the per-host budget applies.
    const response = await worker.fetch(h.revokeRequest(h.makeRevoke(DEVICE_A, HOST_A.id), `198.51.100.${i + 1}`), env);
    assert.equal(response.status, 200, `revoke ${i}`);
  }
  const limited = await worker.fetch(h.revokeRequest(h.makeRevoke(DEVICE_A, HOST_A.id), "203.0.113.200"), env);
  assert.equal(limited.status, 429);
  assert.equal((await limited.json()).error, "rate_limited");
  assert.ok(Number(limited.headers.get("retry-after")) >= 1);
  // Another host has its own budget.
  assert.equal((await worker.fetch(h.revokeRequest(h.makeRevoke(DEVICE_A, HOST_B.id), "203.0.113.201"), env)).status, 200);
  h.clock.advance(60_000);
  assert.equal((await worker.fetch(h.revokeRequest(h.makeRevoke(DEVICE_A, HOST_A.id), "203.0.113.202"), env)).status, 200);
});

test("revocation records for never-seen devices are capped per host", async () => {
  const { env, channel } = h.makeEnv();
  await register(env, h.makeGrant(DEVICE_A, HOST_A.id));
  const ctx = channel(HOST_A.id);
  // Pre-fill the object with the maximum number of live revocation records.
  const exp = h.clock.now + 3600_000;
  for (let i = 0; i < MAX_UNSEEN_REVOCATIONS; i++) {
    ctx.map.set(`revoked:${i.toString(16).padStart(64, "0")}`, { at: h.clock.sec, exp });
  }
  const minted = h.ed25519FromSeed(h.randHex(32));
  const capped = await worker.fetch(h.revokeRequest(h.makeRevoke(minted, HOST_A.id), "198.51.100.1"), env);
  assert.equal(capped.status, 429);
  assert.equal((await capped.json()).error, "rate_limited");
  assert.ok(Number(capped.headers.get("retry-after")) >= 60);
  assert.equal(ctx.map.has(`revoked:${minted.id}`), false);
  assert.equal(ctx.keys("rnonce:").length, 0, "a capped revoke stores nothing");

  // A device with a subscription here can still revoke ...
  const seen = await worker.fetch(h.revokeRequest(h.makeRevoke(DEVICE_A, HOST_A.id), "198.51.100.2"), env);
  assert.equal(seen.status, 200);
  assert.deepEqual(await seen.json(), { revoked: 1 });
  // ... and so can a device that already has a revocation record.
  const again = await worker.fetch(h.revokeRequest(h.makeRevoke(DEVICE_A, HOST_A.id), "198.51.100.3"), env);
  assert.equal(again.status, 200);
  // Other hosts are unaffected.
  assert.equal((await worker.fetch(h.revokeRequest(h.makeRevoke(minted, HOST_B.id), "198.51.100.4"), env)).status, 200);
});
