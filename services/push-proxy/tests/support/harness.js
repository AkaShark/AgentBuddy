// Shared harness for the v2/debug/legacy tests. Like the other test files it
// compiles src/ with tsc into a temp dir, then drives the compiled Worker with
// fake Durable Object storage, a controllable clock and a mocked fetch.
// Not matched by `node --test tests/*.cjs` on purpose (it has no tests).
const { after } = require("node:test");
const { execFileSync } = require("node:child_process");
const nodeCrypto = require("node:crypto");
const { mkdtempSync, rmSync } = require("node:fs");
const { tmpdir } = require("node:os");
const path = require("node:path");

const output = mkdtempSync(path.join(tmpdir(), "agentbuddy-push-test-"));
execFileSync(process.execPath, [require.resolve("typescript/bin/tsc"), "--noEmit", "false", "--module", "commonjs", "--moduleResolution", "node", "--outDir", output], { cwd: path.join(__dirname, "..", "..") });
after(() => rmSync(output, { recursive: true, force: true }));
const load = (name) => require(path.join(output, `${name}.js`));

// Workers-only WebCrypto extension used by the debug endpoint.
if (typeof crypto.subtle.timingSafeEqual !== "function") {
  crypto.subtle.timingSafeEqual = (a, b) => {
    const x = Buffer.from(ArrayBuffer.isView(a) ? a.buffer.slice(a.byteOffset, a.byteOffset + a.byteLength) : a);
    const y = Buffer.from(ArrayBuffer.isView(b) ? b.buffer.slice(b.byteOffset, b.byteOffset + b.byteLength) : b);
    if (x.length !== y.length) throw new TypeError("timingSafeEqual inputs must have equal length");
    return nodeCrypto.timingSafeEqual(x, y);
  };
}

// Controllable clock, starting at the spec §12 vector timestamp.
const VECTOR_TIME = 1790300000;
const realNow = Date.now;
const clock = {
  now: VECTOR_TIME * 1000,
  get sec() { return Math.floor(this.now / 1000); },
  set(ms) { this.now = ms; },
  advance(ms) { this.now += ms; },
};
Date.now = () => clock.now;
after(() => { Date.now = realNow; });

const sha256 = (value) => nodeCrypto.createHash("sha256").update(value).digest("hex");
const randHex = (bytes) => nodeCrypto.randomBytes(bytes).toString("hex");

// Spec §12: every v2 string carries the Worker origin; test requests are sent
// to this origin so the Worker's default audience (request origin) matches.
const AUD = "https://push.example.test";

// Spec §12 test-only Worker seal key (never the production key), kid 1.
const TEST_SEAL_PRIVATE = "03".repeat(32);
const TEST_SEAL_PUBLIC = "5dfedd3b6bd47f6fa28ee15d969d5bb0ea53774d488bdaf9df1c6e0124b3ef22";
const TEST_SEAL_KEY = `1:${TEST_SEAL_PRIVATE}`;

function ed25519FromSeed(seedHex) {
  const privateKey = nodeCrypto.createPrivateKey({
    key: Buffer.concat([Buffer.from("302e020100300506032b657004220420", "hex"), Buffer.from(seedHex, "hex")]),
    format: "der",
    type: "pkcs8",
  });
  const id = nodeCrypto.createPublicKey(privateKey).export({ format: "der", type: "spki" }).subarray(12).toString("hex");
  return { id, sign: (message) => nodeCrypto.sign(null, Buffer.from(message, "utf8"), privateKey).toString("hex") };
}

// Spec §12 seeds for host A / device A; B keys are extra identities.
const HOST_A = ed25519FromSeed("01".repeat(32));
const DEVICE_A = ed25519FromSeed("02".repeat(32));
const HOST_B = ed25519FromSeed("03".repeat(32));
const DEVICE_B = ed25519FromSeed("04".repeat(32));
const IOS_TOKEN = "a1".repeat(32);
const IOS_TOKEN_2 = "b2".repeat(32);
const ANDROID_TOKEN = "fcm:APA91b-token_value";

let providerKeys;
function keys() {
  if (!providerKeys) {
    providerKeys = {
      apns: nodeCrypto.generateKeyPairSync("ec", { namedCurve: "prime256v1", privateKeyEncoding: { type: "pkcs8", format: "pem" }, publicKeyEncoding: { type: "spki", format: "pem" } }).privateKey,
      fcm: nodeCrypto.generateKeyPairSync("rsa", { modulusLength: 2048, privateKeyEncoding: { type: "pkcs8", format: "pem" }, publicKeyEncoding: { type: "spki", format: "pem" } }).privateKey,
    };
  }
  return providerKeys;
}

function fakeStorage() {
  const map = new Map();
  const clone = (value) => (value === undefined ? undefined : structuredClone(value));
  const ctx = {
    map,
    alarm: null,
    listCalls: [],
    storage: {
      async get(key) {
        if (Array.isArray(key)) return new Map(key.filter((k) => map.has(k)).map((k) => [k, clone(map.get(k))]));
        return clone(map.get(key));
      },
      async put(key, value) {
        if (typeof key === "object") for (const [k, v] of Object.entries(key)) map.set(k, clone(v));
        else map.set(key, clone(value));
      },
      async delete(key) {
        if (Array.isArray(key)) return key.filter((k) => map.delete(k)).length;
        return map.delete(key);
      },
      // Durable Object list(): prefix, start (inclusive), startAfter, end
      // (exclusive), reverse and limit, in key order.
      async list(options = {}) {
        ctx.listCalls.push({ ...options });
        let selected = [...map.keys()]
          .filter((k) => !options.prefix || k.startsWith(options.prefix))
          .filter((k) => options.start === undefined || k >= options.start)
          .filter((k) => options.startAfter === undefined || k > options.startAfter)
          .filter((k) => options.end === undefined || k < options.end)
          .sort();
        if (options.reverse) selected.reverse();
        if (options.limit !== undefined) selected = selected.slice(0, options.limit);
        return new Map(selected.map((k) => [k, clone(map.get(k))]));
      },
      async deleteAll() { map.clear(); },
      async getAlarm() { return ctx.alarm; },
      async setAlarm(time) { ctx.alarm = typeof time === "number" ? time : time.getTime(); },
      async deleteAlarm() { ctx.alarm = null; },
    },
    keys(prefix) { return [...map.keys()].filter((k) => k.startsWith(prefix)).sort(); },
  };
  return ctx;
}

// Env with real HostChannel / RateLimiter classes over fake storage.
function makeEnv(vars = {}) {
  const { HostChannel } = load("host-channel");
  const { RateLimiter } = load("rate-limiter");
  const channels = new Map();
  const limiters = new Map();
  const env = {
    APNS_TEAM_ID: "TEAMID1234",
    APNS_KEY_ID: "KEYID12345",
    APNS_PRIVATE_KEY: keys().apns,
    FCM_PROJECT_ID: "agentbuddy-test",
    FCM_CLIENT_EMAIL: "svc@agentbuddy-test.iam.gserviceaccount.com",
    FCM_PRIVATE_KEY: keys().fcm,
    PUSH_TARGET_SEAL_KEY: TEST_SEAL_KEY,
    ...vars,
  };
  const channel = (name) => {
    if (!channels.has(name)) {
      const ctx = fakeStorage();
      ctx.instance = new HostChannel({ storage: ctx.storage }, env);
      channels.set(name, ctx);
    }
    return channels.get(name);
  };
  const limiter = (name) => {
    if (!limiters.has(name)) {
      const ctx = fakeStorage();
      ctx.instance = new RateLimiter({ storage: ctx.storage });
      ctx.calls = 0;
      limiters.set(name, ctx);
    }
    return limiters.get(name);
  };
  env.HOST_CHANNEL = {
    idFromName: (name) => ({ name, toString: () => name }),
    get: (id) => ({ fetch: (request) => channel(id.name).instance.fetch(request) }),
  };
  env.RATE_LIMITER = {
    idFromName: (name) => ({ name, toString: () => name }),
    get: (id) => ({ fetch: (request) => { const ctx = limiter(id.name); ctx.calls++; return ctx.instance.fetch(request); } }),
  };
  return { env, channel, channels, limiters };
}

// Runs a HostChannel alarm the way the runtime does (the fired alarm is cleared first).
async function runAlarm(ctx) {
  ctx.alarm = null;
  await ctx.instance.alarm();
}

// Global fetch mock; `reset(handler)` per test. OAuth calls are answered by
// default so FCM sends only need a handler for messages:send.
const fetchMock = {
  calls: [],
  handler: null,
  reset(handler) { this.calls = []; this.handler = handler; },
  providerCalls() { return this.calls.filter((c) => c.url !== "https://oauth2.googleapis.com/token"); },
};
const realFetch = globalThis.fetch;
globalThis.fetch = async (input, init = {}) => {
  const url = typeof input === "string" ? input : input.url;
  let body = init.body;
  try { body = JSON.parse(init.body); } catch {}
  const call = { url, method: init.method, headers: { ...(init.headers || {}) }, body, rawBody: init.body };
  fetchMock.calls.push(call);
  if (url === "https://oauth2.googleapis.com/token" && (!fetchMock.handler || !fetchMock.handler.oauth)) {
    return new Response(JSON.stringify({ access_token: `oauth-${fetchMock.calls.length}`, expires_in: 3599 }), { status: 200 });
  }
  if (!fetchMock.handler) throw new Error(`unexpected fetch ${url}`);
  return fetchMock.handler(call, fetchMock.calls.length);
};
after(() => { globalThis.fetch = realFetch; });

function captureLogs() {
  const lines = [];
  const originals = {};
  for (const level of ["log", "info", "warn", "error", "debug"]) {
    originals[level] = console[level];
    console[level] = (...args) => { lines.push(args.map(String).join(" ")); };
  }
  return { lines, restore() { Object.assign(console, originals); } };
}

function hostCanonical(method, pathname, timestamp, nonce, bodyText, aud = AUD) {
  return ["agentbuddy-push-host-v2", aud, method, pathname, String(timestamp), nonce, sha256(bodyText)].join("\n");
}

// Builds a host-signed request. `opts` can override timestamp, nonce, the
// signer, the claimed hostId, the signature, the signed `aud`, the request
// origin, the client IP and extra/omitted headers.
function hostRequest(host, method, pathname, body, opts = {}) {
  const bodyText = body === undefined ? "" : typeof body === "string" ? body : JSON.stringify(body);
  const timestamp = String(opts.timestamp ?? clock.sec);
  const nonce = opts.nonce ?? randHex(16);
  const signature = opts.signature ?? (opts.signer ?? host).sign(hostCanonical(method, pathname, timestamp, nonce, opts.signedBody ?? bodyText, opts.aud ?? AUD));
  const headers = {
    "x-agentbuddy-host": opts.hostId ?? host.id,
    "x-agentbuddy-timestamp": timestamp,
    "x-agentbuddy-nonce": nonce,
    "x-agentbuddy-signature": signature,
    ...(opts.ip ? { "cf-connecting-ip": opts.ip } : {}),
    ...(opts.headers ?? {}),
  };
  for (const name of opts.omit ?? []) delete headers[name];
  return new Request(`${opts.origin ?? AUD}${pathname}`, { method, headers, body: body === undefined ? undefined : bodyText });
}

const X25519_PKCS8_PREFIX = Buffer.from("302e020100300506032b656e04220420", "hex");
const X25519_SPKI_PREFIX = Buffer.from("302a300506032b656e032100", "hex");
const x25519Private = (hex) => nodeCrypto.createPrivateKey({ key: Buffer.concat([X25519_PKCS8_PREFIX, Buffer.from(hex, "hex")]), format: "der", type: "pkcs8" });
const x25519PublicRaw = (key) => nodeCrypto.createPublicKey(key).export({ format: "der", type: "spki" }).subarray(12);

// Seals a push target exactly like the phone (spec §5.5), independently of the
// Worker code. Options override any input: `ephemeralPrivate` / `nonce` (hex)
// make it deterministic, `aad`, `kid`, `version`, `workerPublic`, `plaintext`
// (string or raw Buffer).
function sealTarget(opts) {
  const eph = opts.ephemeralPrivate ? x25519Private(opts.ephemeralPrivate) : nodeCrypto.generateKeyPairSync("x25519").privateKey;
  const epk = x25519PublicRaw(eph);
  const workerPublic = Buffer.from(opts.workerPublic ?? TEST_SEAL_PUBLIC, "hex");
  const shared = nodeCrypto.diffieHellman({
    privateKey: eph,
    publicKey: nodeCrypto.createPublicKey({ key: Buffer.concat([X25519_SPKI_PREFIX, workerPublic]), format: "der", type: "spki" }),
  });
  const key = Buffer.from(nodeCrypto.hkdfSync("sha256", shared, Buffer.concat([epk, workerPublic]), Buffer.from("agentbuddy-push-target-v1"), 32));
  const nonce = opts.nonce ? Buffer.from(opts.nonce, "hex") : nodeCrypto.randomBytes(12);
  const plaintext = opts.plaintext ?? JSON.stringify({
    v: 1, platform: opts.platform, token: opts.token, apnsEnvironment: opts.apnsEnvironment, deviceId: opts.deviceId, hostId: opts.hostId,
  });
  const cipher = nodeCrypto.createCipheriv("aes-256-gcm", key, nonce);
  cipher.setAAD(Buffer.from(opts.aad ?? `${opts.hostId}|${opts.deviceId}`, "utf8"));
  const plaintextBytes = Buffer.isBuffer(plaintext) ? plaintext : Buffer.from(plaintext, "utf8");
  const ciphertext = Buffer.concat([cipher.update(plaintextBytes), cipher.final(), cipher.getAuthTag()]);
  return Buffer.concat([Buffer.from([opts.version ?? 1, opts.kid ?? 1]), epk, nonce, ciphertext]).toString("base64url");
}

// A device grant body for POST /v2/subscriptions. `fields.pushToken` is the
// token sealed into `sealedTarget` (it is not sent in clear). `sign` overrides
// the values placed in the canonical string (to simulate mismatches) and may
// set `signer`; `seal` overrides the sealed target's inputs.
function makeGrant(device, hostId, fields = {}, sign = {}, seal = {}) {
  const { pushToken = IOS_TOKEN, ...rest } = fields;
  const body = {
    deviceId: device.id,
    platform: "ios",
    apnsEnvironment: "production",
    agent: "codex",
    threadId: "thread-1",
    turnId: "turn-1",
    issuedAt: clock.sec,
    expiresAt: clock.sec + 86400,
    grantNonce: randHex(16),
    ...rest,
  };
  if (!("sealedTarget" in rest)) {
    body.sealedTarget = sealTarget({
      platform: body.platform,
      token: pushToken,
      apnsEnvironment: body.platform === "ios" ? body.apnsEnvironment : null,
      deviceId: body.deviceId,
      hostId,
      ...seal,
    });
  }
  const s = {
    aud: AUD,
    host: hostId,
    device: body.deviceId,
    platform: body.platform,
    environment: body.platform === "ios" ? body.apnsEnvironment : "none",
    target: body.sealedTarget,
    agent: body.agent,
    thread: body.threadId,
    turn: body.turnId,
    issued: body.issuedAt,
    expires: body.expiresAt,
    nonce: body.grantNonce,
    ...sign,
  };
  const canonical = [
    "agentbuddy-push-grant-v2",
    `aud=${s.aud}`,
    `host=${s.host}`,
    `device=${s.device}`,
    `platform=${s.platform}`,
    `environment=${s.environment}`,
    `target_sha256=${sha256(String(s.target))}`,
    `agent=${s.agent}`,
    `thread=${s.thread}`,
    `turn=${s.turn}`,
    `issued=${s.issued}`,
    `expires=${s.expires}`,
    `nonce=${s.nonce}`,
  ].join("\n");
  body.grantSignature = (sign.signer ?? device).sign(canonical);
  return body;
}

function makeRevoke(device, hostId, fields = {}, signer = device, aud = AUD) {
  const body = { hostId, deviceId: device.id, scope: "all", timestamp: clock.sec, nonce: randHex(16), ...fields };
  const canonical = [
    "agentbuddy-push-revoke-v2",
    `aud=${aud}`,
    `host=${body.hostId}`,
    `device=${body.deviceId}`,
    `scope=${body.scope}`,
    `timestamp=${body.timestamp}`,
    `nonce=${body.nonce}`,
  ].join("\n");
  body.signature = fields.signature ?? signer.sign(canonical);
  return body;
}

function makeEvent(fields = {}) {
  return {
    eventId: `evt_${randHex(16)}`,
    agent: "codex",
    threadId: "thread-1",
    turnId: "turn-1",
    type: "completed",
    reason: null,
    occurredAt: clock.sec,
    ...fields,
  };
}

function revokeRequest(body, ip = "203.0.113.7") {
  return new Request(`${AUD}/v2/subscriptions/revoke`, {
    method: "POST",
    headers: { "cf-connecting-ip": ip },
    body: typeof body === "string" ? body : JSON.stringify(body),
  });
}

module.exports = {
  load,
  clock,
  sha256,
  randHex,
  AUD,
  TEST_SEAL_PRIVATE,
  TEST_SEAL_PUBLIC,
  TEST_SEAL_KEY,
  sealTarget,
  hostCanonical,
  ed25519FromSeed,
  HOST_A,
  DEVICE_A,
  HOST_B,
  DEVICE_B,
  IOS_TOKEN,
  IOS_TOKEN_2,
  ANDROID_TOKEN,
  VECTOR_TIME,
  fakeStorage,
  makeEnv,
  runAlarm,
  fetchMock,
  captureLogs,
  hostRequest,
  makeGrant,
  makeRevoke,
  makeEvent,
  revokeRequest,
};
