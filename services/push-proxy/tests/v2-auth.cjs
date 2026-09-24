// v2 authentication, cross-host isolation, input validation, size and rate limits.
const { test, beforeEach } = require("node:test");
const assert = require("node:assert/strict");
const h = require("./support/harness");

const worker = h.load("index").default;
const { HOST_A, HOST_B, DEVICE_A, DEVICE_B } = h;

beforeEach(() => {
  h.clock.set(h.VECTOR_TIME * 1000);
  h.fetchMock.reset(() => new Response(null, { status: 200 }));
});

async function subscribe(env, host, grantFields = {}, opts = {}) {
  const response = await worker.fetch(h.hostRequest(host, "POST", "/v2/subscriptions", h.makeGrant(opts.device ?? DEVICE_A, host.id, grantFields), opts), env);
  return { status: response.status, body: await response.json() };
}

async function expectError(response, status, code) {
  assert.equal(response.status, status);
  assert.equal((await response.json()).error, code);
}

test("health reports the v2 features", async () => {
  const { env } = h.makeEnv();
  const response = await worker.fetch(new Request("https://proxy/v2/health"), env);
  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), { ok: true, features: ["subscriptions.v2", "events.v2"] });
});

test("unsigned and malformed-auth requests are rejected before any state is touched", async () => {
  const { env, channels } = h.makeEnv();
  const event = h.makeEvent();
  const unsigned = [
    new Request("https://proxy/v2/events", { method: "POST", body: JSON.stringify(event) }),
    new Request("https://proxy/v2/subscriptions", { method: "POST", body: JSON.stringify(h.makeGrant(DEVICE_A, HOST_A.id)) }),
    new Request(`https://proxy/v2/subscriptions/sub_${"0".repeat(32)}`, { method: "DELETE" }),
  ];
  for (const request of unsigned) await expectError(await worker.fetch(request, env), 401, "unauthorized");

  for (const header of ["x-agentbuddy-host", "x-agentbuddy-timestamp", "x-agentbuddy-nonce", "x-agentbuddy-signature"]) {
    const request = h.hostRequest(HOST_A, "POST", "/v2/events", event, { omit: [header] });
    await expectError(await worker.fetch(request, env), 401, "unauthorized");
  }
  const malformed = [
    { hostId: HOST_A.id.toUpperCase() },
    { hostId: HOST_A.id.slice(2) },
    { nonce: "00112233445566778899AABBCCDDEEFF" },
    { nonce: "0011" },
    { timestamp: "1790300000.5" },
    { timestamp: "-5" },
    { signature: "ab".repeat(63) },
  ];
  for (const opts of malformed) {
    await expectError(await worker.fetch(h.hostRequest(HOST_A, "POST", "/v2/events", event, opts), env), 401, "unauthorized");
  }
  assert.equal(channels.size, 0, "no HostChannel object was created");
  assert.equal(h.fetchMock.calls.length, 0);
});

test("bad signatures are rejected: wrong key, claimed host, tampered body/path/method", async () => {
  const { env, channels } = h.makeEnv();
  const event = h.makeEvent();
  const cases = [
    h.hostRequest(HOST_A, "POST", "/v2/events", event, { signer: HOST_B }),
    h.hostRequest(HOST_B, "POST", "/v2/events", event, { hostId: HOST_A.id }),
    h.hostRequest(HOST_A, "POST", "/v2/events", event, { signedBody: JSON.stringify({ ...event, type: "failed" }) }),
    h.hostRequest(HOST_A, "POST", "/v2/events", event, { signature: HOST_A.sign(["agentbuddy-push-host-v1", "POST", "/v2/subscriptions", String(h.clock.sec), "00".repeat(16), h.sha256(JSON.stringify(event))].join("\n")), nonce: "00".repeat(16) }),
    h.hostRequest(HOST_A, "POST", "/v2/events", event, { signature: HOST_A.sign(["agentbuddy-push-host-v1", "PUT", "/v2/events", String(h.clock.sec), "11".repeat(16), h.sha256(JSON.stringify(event))].join("\n")), nonce: "11".repeat(16) }),
  ];
  for (const request of cases) await expectError(await worker.fetch(request, env), 401, "unauthorized");
  assert.equal(channels.size, 0);
});

test("timestamps outside the 300s window are rejected; the boundary is accepted", async () => {
  const { env } = h.makeEnv();
  const now = h.clock.sec;
  for (const timestamp of [now - 301, now + 301, now - 3600]) {
    await expectError(await worker.fetch(h.hostRequest(HOST_A, "POST", "/v2/events", h.makeEvent(), { timestamp }), env), 401, "unauthorized");
  }
  for (const timestamp of [now - 300, now + 300]) {
    const response = await worker.fetch(h.hostRequest(HOST_A, "POST", "/v2/events", h.makeEvent(), { timestamp }), env);
    assert.equal(response.status, 202);
  }
});

test("a replayed host nonce is rejected and does not re-dispatch", async () => {
  const { env } = h.makeEnv();
  assert.equal((await subscribe(env, HOST_A)).status, 201);
  const event = h.makeEvent();
  const nonce = h.randHex(16);
  const first = await worker.fetch(h.hostRequest(HOST_A, "POST", "/v2/events", event, { nonce }), env);
  assert.equal(first.status, 202);
  assert.equal(h.fetchMock.providerCalls().length, 1);
  const replay = await worker.fetch(h.hostRequest(HOST_A, "POST", "/v2/events", event, { nonce }), env);
  await expectError(replay, 401, "unauthorized");
  // Same nonce on another endpoint is a replay too.
  const other = await worker.fetch(h.hostRequest(HOST_A, "DELETE", `/v2/subscriptions/sub_${"1".repeat(32)}`, undefined, { nonce }), env);
  await expectError(other, 401, "unauthorized");
  assert.equal(h.fetchMock.providerCalls().length, 1);
});

test("replay cache entries expire after 10 minutes (the signature window still applies)", async () => {
  const { env, channel } = h.makeEnv();
  const nonce = h.randHex(16);
  assert.equal((await worker.fetch(h.hostRequest(HOST_A, "POST", "/v2/events", h.makeEvent(), { nonce }), env)).status, 202);
  assert.ok(channel(HOST_A.id).map.has(`nonce:${nonce}`));
  h.clock.advance(10 * 60_000 + 1);
  await h.runAlarm(channel(HOST_A.id));
  assert.equal(channel(HOST_A.id).map.has(`nonce:${nonce}`), false);
});

test("BLOCKED_HOST_IDS forbids a host immediately; other hosts are unaffected", async () => {
  const { env, channels } = h.makeEnv({ BLOCKED_HOST_IDS: ` ${"f".repeat(64)}, ${HOST_A.id.toUpperCase()} ` });
  await expectError(await worker.fetch(h.hostRequest(HOST_A, "POST", "/v2/events", h.makeEvent()), env), 403, "forbidden");
  await expectError(await worker.fetch(h.hostRequest(HOST_A, "POST", "/v2/subscriptions", h.makeGrant(DEVICE_A, HOST_A.id)), env), 403, "forbidden");
  assert.equal(channels.has(HOST_A.id), false);
  assert.equal((await subscribe(env, HOST_B)).status, 201);
});

test("cross-host: host B cannot delete or trigger host A's subscriptions", async () => {
  const { env, channel } = h.makeEnv();
  const { body } = await subscribe(env, HOST_A);
  const subA = body.subscriptionId;

  const del = await worker.fetch(h.hostRequest(HOST_B, "DELETE", `/v2/subscriptions/${subA}`), env);
  assert.equal(del.status, 200);
  assert.deepEqual(await del.json(), { ok: true });
  assert.ok(channel(HOST_A.id).map.has(`sub:${subA}`), "A's subscription survives B's delete");

  const eventB = await worker.fetch(h.hostRequest(HOST_B, "POST", "/v2/events", h.makeEvent()), env);
  assert.equal(eventB.status, 202);
  assert.deepEqual((await eventB.json()).results, []);
  assert.equal(h.fetchMock.providerCalls().length, 0);

  // Host B cannot register a subscription using a grant the device issued for host A.
  const stolen = h.makeGrant(DEVICE_A, HOST_A.id, { turnId: "turn-2" });
  const forged = await worker.fetch(h.hostRequest(HOST_B, "POST", "/v2/subscriptions", stolen), env);
  await expectError(forged, 403, "forbidden");

  const eventA = await worker.fetch(h.hostRequest(HOST_A, "POST", "/v2/events", h.makeEvent()), env);
  assert.deepEqual((await eventA.json()).results, [{ subscriptionId: subA, state: "sent" }]);

  // A's owner can delete; the delete is idempotent.
  const again = await worker.fetch(h.hostRequest(HOST_A, "DELETE", `/v2/subscriptions/${subA}`), env);
  assert.equal(again.status, 200);
});

test("host DELETE removes the subscription and its index", async () => {
  const { env, channel } = h.makeEnv();
  const { body } = await subscribe(env, HOST_A);
  const response = await worker.fetch(h.hostRequest(HOST_A, "DELETE", `/v2/subscriptions/${body.subscriptionId}`), env);
  assert.equal(response.status, 200);
  assert.deepEqual(channel(HOST_A.id).keys("sub:"), []);
  assert.deepEqual(channel(HOST_A.id).keys("idx:"), []);
  const event = await worker.fetch(h.hostRequest(HOST_A, "POST", "/v2/events", h.makeEvent()), env);
  assert.equal((await event.json()).matched, 0);
});

test("subscription input validation returns bad_request", async () => {
  const { env, channel } = h.makeEnv();
  const invalid = [
    { deviceId: "zz".repeat(32) },
    { deviceId: DEVICE_A.id.toUpperCase() },
    { platform: "web" },
    { pushToken: "not-hex" },
    { pushToken: "a".repeat(201) },
    { pushToken: 42 },
    { platform: "android", pushToken: "has space", apnsEnvironment: undefined },
    { platform: "android", pushToken: "x".repeat(4097), apnsEnvironment: undefined },
    { platform: "android", pushToken: h.ANDROID_TOKEN, apnsEnvironment: "production" },
    { apnsEnvironment: undefined },
    { apnsEnvironment: "development" },
    { agent: "" },
    { agent: "co dex" },
    { agent: "a".repeat(65) },
    { threadId: "" },
    { threadId: "thread\nturn=x" },
    { threadId: "é".repeat(65) },
    { turnId: "t".repeat(129) },
    { turnId: 5 },
    { issuedAt: "1790300000" },
    { expiresAt: 1.5 },
    { issuedAt: -1 },
    { grantNonce: "FF".repeat(16) },
    { grantNonce: "ff".repeat(15) },
  ];
  for (const fields of invalid) {
    const body = h.makeGrant(DEVICE_A, HOST_A.id, fields);
    const response = await worker.fetch(h.hostRequest(HOST_A, "POST", "/v2/subscriptions", body), env);
    await expectError(response, 400, "bad_request");
  }
  const badSignature = { ...h.makeGrant(DEVICE_A, HOST_A.id), grantSignature: "ab".repeat(32) };
  await expectError(await worker.fetch(h.hostRequest(HOST_A, "POST", "/v2/subscriptions", badSignature), env), 400, "bad_request");
  for (const raw of ["{not json", "[]", "null", '"string"']) {
    await expectError(await worker.fetch(h.hostRequest(HOST_A, "POST", "/v2/subscriptions", raw), env), 400, "bad_request");
  }
  assert.deepEqual(channel(HOST_A.id).keys("sub:"), []);
});

test("event input validation returns bad_request", async () => {
  const { env } = h.makeEnv();
  const invalid = [
    { eventId: "evt_123" },
    { eventId: `EVT_${"a".repeat(32)}` },
    { eventId: `evt_${"A".repeat(32)}` },
    { type: "running" },
    { type: undefined },
    { reason: "timeout" },
    { occurredAt: -1 },
    { occurredAt: "now" },
    { agent: "codex|x" },
    { threadId: "x\ry" },
    { turnId: "" },
  ];
  for (const fields of invalid) {
    const response = await worker.fetch(h.hostRequest(HOST_A, "POST", "/v2/events", h.makeEvent(fields)), env);
    await expectError(response, 400, "bad_request");
  }
  for (const reason of [null, undefined, "interrupted", "error"]) {
    const response = await worker.fetch(h.hostRequest(HOST_A, "POST", "/v2/events", h.makeEvent({ type: "failed", reason })), env);
    assert.equal(response.status, 202);
  }
});

test("bodies over 16 KB are rejected with payload_too_large", async () => {
  const { env, channels } = h.makeEnv();
  const big = JSON.stringify({ ...h.makeEvent(), padding: "x".repeat(16 * 1024) });
  await expectError(await worker.fetch(h.hostRequest(HOST_A, "POST", "/v2/events", big), env), 413, "payload_too_large");

  // Streaming body without Content-Length: the limit applies while reading.
  let pulls = 0;
  const stream = new ReadableStream({
    pull(controller) {
      pulls++;
      controller.enqueue(new TextEncoder().encode("x".repeat(4096)));
      if (pulls > 100) controller.close();
    },
  });
  const streamed = new Request("https://proxy/v2/subscriptions", {
    method: "POST",
    duplex: "half",
    headers: { "x-agentbuddy-host": HOST_A.id },
    body: stream,
  });
  await expectError(await worker.fetch(streamed, env), 413, "payload_too_large");
  assert.ok(pulls < 10, "stopped reading shortly after the limit");

  // Declared Content-Length over the limit fails without reading.
  const declared = new Request("https://proxy/v2/events", {
    method: "POST",
    duplex: "half",
    headers: { "content-length": String(16 * 1024 + 1) },
    body: new ReadableStream({ pull() {} }),
  });
  await expectError(await worker.fetch(declared, env), 413, "payload_too_large");

  // Exactly 16 KB is allowed.
  const event = h.makeEvent();
  const base = JSON.stringify({ ...event, padding: "" });
  const exact = JSON.stringify({ ...event, padding: "x".repeat(16 * 1024 - base.length) });
  assert.equal(Buffer.byteLength(exact), 16 * 1024);
  assert.equal((await worker.fetch(h.hostRequest(HOST_A, "POST", "/v2/events", exact), env)).status, 202);
  assert.equal(channels.size, 1);
});

test("unknown v2 routes and malformed subscription ids are not_found", async () => {
  const { env } = h.makeEnv();
  for (const [method, path] of [["GET", "/v2/events"], ["POST", "/v2/unknown"], ["PUT", "/v2/subscriptions"], ["DELETE", "/v2/subscriptions/revoke"], ["DELETE", "/v2/subscriptions/sub_123"], ["POST", "/v2/health"]]) {
    await expectError(await worker.fetch(new Request(`https://proxy${path}`, { method }), env), 404, "not_found");
  }
});

test("per-host rate limit: 60 subscription registrations per minute", async () => {
  const { env } = h.makeEnv();
  const grant = h.makeGrant(DEVICE_A, HOST_A.id);
  for (let i = 0; i < 60; i++) {
    const response = await worker.fetch(h.hostRequest(HOST_A, "POST", "/v2/subscriptions", grant), env);
    assert.equal(response.status, i === 0 ? 201 : 200);
  }
  const limited = await worker.fetch(h.hostRequest(HOST_A, "POST", "/v2/subscriptions", grant), env);
  assert.equal(limited.status, 429);
  assert.deepEqual(await limited.json(), { error: "rate_limited" });
  const retryAfter = Number(limited.headers.get("retry-after"));
  assert.ok(retryAfter >= 1 && retryAfter <= 60, `Retry-After ${retryAfter}`);

  // Another host has its own budget; events use a separate bucket.
  assert.equal((await subscribe(env, HOST_B)).status, 201);
  assert.equal((await worker.fetch(h.hostRequest(HOST_A, "POST", "/v2/events", h.makeEvent({ turnId: "other" })), env)).status, 202);

  h.clock.advance(60_000);
  assert.equal((await worker.fetch(h.hostRequest(HOST_A, "POST", "/v2/subscriptions", grant), env)).status, 200);
});

test("per-host rate limit: 120 events per minute", async () => {
  const { env } = h.makeEnv();
  for (let i = 0; i < 120; i++) {
    const response = await worker.fetch(h.hostRequest(HOST_A, "POST", "/v2/events", h.makeEvent()), env);
    assert.equal(response.status, 202);
  }
  const limited = await worker.fetch(h.hostRequest(HOST_A, "POST", "/v2/events", h.makeEvent()), env);
  assert.equal(limited.status, 429);
  assert.equal((await limited.json()).error, "rate_limited");
  assert.ok(Number(limited.headers.get("retry-after")) >= 1);
});

test("device revoke is rate limited per IP", async () => {
  const { env } = h.makeEnv();
  for (let i = 0; i < 10; i++) {
    const response = await worker.fetch(h.revokeRequest(h.makeRevoke(DEVICE_B, HOST_A.id), "198.51.100.1"), env);
    assert.equal(response.status, 200);
  }
  const limited = await worker.fetch(h.revokeRequest(h.makeRevoke(DEVICE_B, HOST_A.id), "198.51.100.1"), env);
  assert.equal(limited.status, 429);
  assert.equal((await limited.json()).error, "rate_limited");
  assert.ok(Number(limited.headers.get("retry-after")) >= 1);
  const otherIp = await worker.fetch(h.revokeRequest(h.makeRevoke(DEVICE_B, HOST_A.id), "198.51.100.2"), env);
  assert.equal(otherIp.status, 200);
});
