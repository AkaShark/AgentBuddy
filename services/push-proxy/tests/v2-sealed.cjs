// Sealed push targets on POST /v2/subscriptions (spec §5.5): only a target the
// phone sealed for exactly this host and device opens, and every failure is
// the same bare 403.
const { test, beforeEach } = require("node:test");
const assert = require("node:assert/strict");
const nodeCrypto = require("node:crypto");
const h = require("./support/harness");

const worker = h.load("index").default;
const { HOST_A, HOST_B, DEVICE_A, DEVICE_B } = h;

beforeEach(() => {
  h.clock.set(h.VECTOR_TIME * 1000);
  h.fetchMock.reset(() => new Response(null, { status: 200 }));
});

async function register(env, grant, host = HOST_A) {
  const response = await worker.fetch(h.hostRequest(host, "POST", "/v2/subscriptions", grant), env);
  return { status: response.status, body: await response.json() };
}

// Every sealed-target failure: 403 with no hint of which check failed.
async function expectBareForbidden(env, grant, host = HOST_A, label = "") {
  const { status, body } = await register(env, grant, host);
  assert.equal(status, 403, label);
  assert.deepEqual(body, { error: "forbidden" }, label);
}

const iosTarget = (overrides = {}) => ({
  platform: "ios", token: h.IOS_TOKEN, apnsEnvironment: "production", deviceId: DEVICE_A.id, hostId: HOST_A.id, ...overrides,
});

// Same decoded bytes, but a set bit in the unused tail of the last character.
function nonCanonical(text) {
  const alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
  assert.notEqual(text.length % 4, 0, "the string must end with a partial group");
  const last = alphabet.indexOf(text[text.length - 1]);
  const altered = text.slice(0, -1) + alphabet[last ^ 1];
  assert.deepEqual(Buffer.from(altered, "base64url"), Buffer.from(text, "base64url"));
  return altered;
}

function publicHex(privateHex) {
  const key = nodeCrypto.createPrivateKey({ key: Buffer.concat([Buffer.from("302e020100300506032b656e04220420", "hex"), Buffer.from(privateHex, "hex")]), format: "der", type: "pkcs8" });
  return nodeCrypto.createPublicKey(key).export({ format: "der", type: "spki" }).subarray(12).toString("hex");
}

test("a host cannot reuse another device's sealed target with a self-minted device key", async () => {
  const { env, channel } = h.makeEnv();
  // The host legitimately holds DEVICE_A's sealed target (from a real grant).
  const legit = h.makeGrant(DEVICE_A, HOST_A.id);
  const minted = h.ed25519FromSeed(h.randHex(32));

  // Re-signed by the minted key as its own device: the AAD (host|device) no longer matches.
  await expectBareForbidden(env, h.makeGrant(minted, HOST_A.id, { sealedTarget: legit.sealedTarget }), HOST_A, "AAD mismatch");
  // A target sealed by the host for the minted key but naming DEVICE_A inside.
  await expectBareForbidden(env, h.makeGrant(minted, HOST_A.id, {}, {}, { aad: `${HOST_A.id}|${minted.id}`, deviceId: DEVICE_A.id }), HOST_A, "plaintext deviceId mismatch");
  // Or naming another host inside.
  await expectBareForbidden(env, h.makeGrant(DEVICE_A, HOST_A.id, {}, {}, { hostId: HOST_B.id, aad: `${HOST_A.id}|${DEVICE_A.id}` }), HOST_A, "plaintext hostId mismatch");
  assert.deepEqual(channel(HOST_A.id).keys("sub:"), []);

  // The genuine grant still works.
  assert.equal((await register(env, legit)).status, 201);
});

test("a target sealed for one host does not open for another", async () => {
  const { env, channel } = h.makeEnv();
  const forA = h.makeGrant(DEVICE_A, HOST_A.id);
  // DEVICE_A really did authorize host B, but host B submits A's target.
  const grant = h.makeGrant(DEVICE_A, HOST_B.id, { sealedTarget: forA.sealedTarget });
  await expectBareForbidden(env, grant, HOST_B);
  assert.equal(channel(HOST_B.id).map.size, 0);
});

test("tampered sealed targets are forbidden", async () => {
  const { env } = h.makeEnv();
  const base = Buffer.from(h.makeGrant(DEVICE_A, HOST_A.id).sealedTarget, "base64url");
  const flip = (offset) => {
    const copy = Buffer.from(base);
    copy[offset] ^= 0x01;
    return copy.toString("base64url");
  };
  const cases = {
    "epk byte": flip(5),
    "nonce byte": flip(40),
    "ciphertext byte": flip(60),
    "tag byte": flip(base.length - 1),
    "truncated tag": base.subarray(0, base.length - 1).toString("base64url"),
    "header only": base.subarray(0, 46 + 16).toString("base64url"),
    "appended byte": Buffer.concat([base, Buffer.from([0])]).toString("base64url"),
  };
  for (const [name, sealedTarget] of Object.entries(cases)) {
    // The device signs over the tampered string, so only the seal can fail.
    await expectBareForbidden(env, h.makeGrant(DEVICE_A, HOST_A.id, { sealedTarget }), HOST_A, name);
  }
});

test("version, kid and encoding are strict", async () => {
  const { env } = h.makeEnv();
  const good = h.makeGrant(DEVICE_A, HOST_A.id).sealedTarget;
  const cases = {
    "unknown kid": h.sealTarget({ ...iosTarget(), kid: 2 }),
    "kid 0": h.sealTarget({ ...iosTarget(), kid: 0 }),
    "version 2": h.sealTarget({ ...iosTarget(), version: 2 }),
    "version 0": h.sealTarget({ ...iosTarget(), version: 0 }),
    "sealed to another Worker key": h.sealTarget({ ...iosTarget(), workerPublic: publicHex("07".repeat(32)) }),
    "padding": `${good}==`,
    "standard base64 alphabet": Buffer.from(good, "base64url").toString("base64"),
    "whitespace": ` ${good}`,
    // A length of 4k+1 characters cannot be produced by base64.
    "impossible length": good + "A".repeat(((1 - good.length) % 4 + 4) % 4 || 4),
    "non-canonical trailing bits": nonCanonical(good),
  };
  for (const [name, sealedTarget] of Object.entries(cases)) {
    await expectBareForbidden(env, h.makeGrant(DEVICE_A, HOST_A.id, { sealedTarget }), HOST_A, name);
  }
});

test("plaintext must be a v1 target matching the clear platform, environment and token rules", async () => {
  const { env } = h.makeEnv();
  const plain = (fields) => JSON.stringify({ v: 1, ...iosTarget(), ...fields });
  const cases = {
    "v 2": { plaintext: plain({ v: 2 }) },
    "v missing": { plaintext: JSON.stringify({ ...iosTarget() }) },
    "platform mismatch": { plaintext: plain({ platform: "android", token: h.ANDROID_TOKEN, apnsEnvironment: null }) },
    "environment mismatch": { plaintext: plain({ apnsEnvironment: "sandbox" }) },
    "environment missing": { plaintext: plain({ apnsEnvironment: undefined }) },
    "iOS token not hex": { plaintext: plain({ token: "not-hex" }) },
    "iOS token too long": { plaintext: plain({ token: "a".repeat(201) }) },
    "token not a string": { plaintext: plain({ token: 42 }) },
    "deviceId uppercase": { plaintext: plain({ deviceId: DEVICE_A.id.toUpperCase() }) },
    "not JSON": { plaintext: "token=a1" },
    "JSON array": { plaintext: "[]" },
    "invalid UTF-8": { plaintext: Buffer.from([0x7b, 0xff, 0xfe, 0x7d]) },
  };
  for (const [name, seal] of Object.entries(cases)) {
    await expectBareForbidden(env, h.makeGrant(DEVICE_A, HOST_A.id, {}, {}, seal), HOST_A, name);
  }

  // Android: environment must be null, token printable and short enough.
  const android = { platform: "android", apnsEnvironment: undefined };
  const androidCases = {
    "android with an APNs environment": { apnsEnvironment: "production" },
    "android token with a space": { token: "has space" },
    "android token too long": { token: "x".repeat(4097) },
  };
  for (const [name, seal] of Object.entries(androidCases)) {
    await expectBareForbidden(env, h.makeGrant(DEVICE_A, HOST_A.id, { ...android, pushToken: h.ANDROID_TOKEN }, {}, seal), HOST_A, name);
  }
  // A clear "none" environment for android with a null target environment is valid.
  assert.equal((await register(env, h.makeGrant(DEVICE_A, HOST_A.id, { platform: "android", pushToken: h.ANDROID_TOKEN, apnsEnvironment: "none" }))).status, 201);
});

test("key rotation: every listed kid opens its own targets", async () => {
  const rotated = "07".repeat(32);
  const { env } = h.makeEnv({ PUSH_TARGET_SEAL_KEY: `2:${rotated}, 1:${h.TEST_SEAL_PRIVATE}` });
  const old = h.makeGrant(DEVICE_A, HOST_A.id, { turnId: "turn-old" });
  const current = h.makeGrant(DEVICE_A, HOST_A.id, { turnId: "turn-new" }, {}, { kid: 2, workerPublic: publicHex(rotated) });
  assert.equal((await register(env, old)).status, 201);
  assert.equal((await register(env, current)).status, 201);
  // kid 2 sealed to the kid-1 key does not open.
  await expectBareForbidden(env, h.makeGrant(DEVICE_A, HOST_A.id, { turnId: "turn-x" }, {}, { kid: 2 }));

  // After retiring kid 1, its targets are forbidden.
  const retired = h.makeEnv({ PUSH_TARGET_SEAL_KEY: `2:${rotated}` }).env;
  await expectBareForbidden(retired, h.makeGrant(DEVICE_A, HOST_A.id));
});

test("a missing or malformed PUSH_TARGET_SEAL_KEY is a 500 that never logs key material", async () => {
  const configs = [undefined, "", " , ", "1", `1:${"03".repeat(31)}`, `1:${"zz".repeat(32)}`, `256:${"03".repeat(32)}`, `x:${"03".repeat(32)}`, `1:${"03".repeat(32)},1:${"07".repeat(32)}`, `1:${"03".repeat(32)};2:${"07".repeat(32)}`];
  for (const value of configs) {
    const { env, channels } = h.makeEnv({ PUSH_TARGET_SEAL_KEY: value });
    const logs = h.captureLogs();
    let response;
    try {
      response = await worker.fetch(h.hostRequest(HOST_A, "POST", "/v2/subscriptions", h.makeGrant(DEVICE_A, HOST_A.id)), env);
    } finally {
      logs.restore();
    }
    assert.equal(response.status, 500, String(value));
    assert.deepEqual(await response.json(), { error: "internal" });
    assert.equal(channels.size, 0);
    const text = logs.lines.join("\n");
    assert.match(text, /PUSH_TARGET_SEAL_KEY is missing or invalid/);
    assert.equal(text.includes("03".repeat(31)), false);
    assert.equal(text.includes("07".repeat(32)), false);
  }
});

test("the decrypted token is used for delivery and never logged", async () => {
  const { env } = h.makeEnv();
  const logs = h.captureLogs();
  try {
    assert.equal((await register(env, h.makeGrant(DEVICE_A, HOST_A.id, { pushToken: h.IOS_TOKEN_2 }))).status, 201);
    const android = h.makeGrant(DEVICE_B, HOST_A.id, { platform: "android", pushToken: h.ANDROID_TOKEN, apnsEnvironment: undefined });
    assert.equal((await register(env, android)).status, 201);
    await expectBareForbidden(env, h.makeGrant(DEVICE_A, HOST_A.id, { turnId: "turn-2", pushToken: h.IOS_TOKEN }, {}, { deviceId: DEVICE_B.id }));
    const event = await worker.fetch(h.hostRequest(HOST_A, "POST", "/v2/events", h.makeEvent()), env);
    assert.equal((await event.json()).matched, 2);
  } finally {
    logs.restore();
  }
  const urls = h.fetchMock.providerCalls().map((c) => c.url);
  assert.ok(urls.includes(`https://api.push.apple.com/3/device/${h.IOS_TOKEN_2}`));
  assert.equal(h.fetchMock.providerCalls().find((c) => c.url.includes("fcm")).body.message.token, h.ANDROID_TOKEN);
  const text = logs.lines.join("\n");
  for (const secret of [h.IOS_TOKEN, h.IOS_TOKEN_2, h.ANDROID_TOKEN]) assert.equal(text.includes(secret), false);
});
