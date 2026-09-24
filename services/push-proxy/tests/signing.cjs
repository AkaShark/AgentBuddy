// Spec §12 v2 vectors: canonical strings, hashes, signatures and the sealed
// target must match the host (alleycat) and mobile implementations byte for byte.
const { test } = require("node:test");
const assert = require("node:assert/strict");
const nodeCrypto = require("node:crypto");
const h = require("./support/harness");

const signing = h.load("signing");
const sealed = h.load("sealed-target");
const worker = h.load("index").default;

const AUD = "https://push.example.test";
const HOST_ID = "8a88e3dd7409f195fd52db2d3cba5d72ca6709bf1d94121bf3748801b40f6f5c";
const DEVICE_ID = "8139770ea87d175f56a35466c34c7ecccb8d8a91b4ee37a25df60f5b8fc9b394";

// Sealed target (test-only Worker key, kid 1).
const SEAL_PRIVATE = "0303030303030303030303030303030303030303030303030303030303030303";
const SEAL_PUBLIC = "5dfedd3b6bd47f6fa28ee15d969d5bb0ea53774d488bdaf9df1c6e0124b3ef22";
const EPH_PRIVATE = "0404040404040404040404040404040404040404040404040404040404040404";
const EPH_PUBLIC = "ac01b2209e86354fb853237b5de0f4fab13c7fcbf433a61c019369617fecf10b";
const GCM_NONCE = "050505050505050505050505";
const SHARED = "40e47a3f525bdcac491d418978d7db5af623ac7afe7623c6d78a5d4fce9d0f63";
const HKDF_KEY = "defc170b9433ff2d066c943ce0cc4d508d0f384468635ee35355a6847269928d";
const PUSH_TOKEN = "a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1";
const PLAINTEXT = '{"v":1,"platform":"ios","token":"a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1","apnsEnvironment":"production","deviceId":"8139770ea87d175f56a35466c34c7ecccb8d8a91b4ee37a25df60f5b8fc9b394","hostId":"8a88e3dd7409f195fd52db2d3cba5d72ca6709bf1d94121bf3748801b40f6f5c"}';
const AAD = "8a88e3dd7409f195fd52db2d3cba5d72ca6709bf1d94121bf3748801b40f6f5c|8139770ea87d175f56a35466c34c7ecccb8d8a91b4ee37a25df60f5b8fc9b394";
const SEALED = "AQGsAbIgnoY1T7hTI3td4PT6sTx_y_QzphwBk2lhf-zxCwUFBQUFBQUFBQUFBZ-odLYTYW6QLwzbORXVqxoBYANUa4EtrUQcvhO-SD6OJtfJpKJt7AB1O9_WrnlX9wxFfUOQylFkMm8ji3OikI9ZvU_wZHaKZN_Mw5iiKA9ThUZYktapcYlLgIb3bFGgQ4_UHttBPnCwqeBa45g0BTJQO33TxFOrTfbK8HkwaDUeG1NPZGxxgDDKI6YdUyR2YDwE4xdNqIM1WIXyg8b0l0ZoFXhpNpBil2Utqj7hSPCUsIFPHGvGJENgkeIM_hWXuwfK_KOP598o6DflDBF60r9C5N-VnaaY1YxF_VkW2-ySG5tjwNtJ2Pyd6UbAwr_wDDh3t9IXNpuWi4W_nUudMfy_znge2419AjQmS42j6jSjXIiiPP9cmbvJi3hx4wyO2TrEifIPigtxn7SG3g";
const SEALED_SHA = "3f40a3fbd4fe15e4a6a69c92c3ea5a264f483efa20e82f0fc4826a2734b58fa1";

// Device grant.
const GRANT_CANONICAL = "agentbuddy-push-grant-v2\naud=https://push.example.test\nhost=8a88e3dd7409f195fd52db2d3cba5d72ca6709bf1d94121bf3748801b40f6f5c\ndevice=8139770ea87d175f56a35466c34c7ecccb8d8a91b4ee37a25df60f5b8fc9b394\nplatform=ios\nenvironment=production\ntarget_sha256=3f40a3fbd4fe15e4a6a69c92c3ea5a264f483efa20e82f0fc4826a2734b58fa1\nagent=codex\nthread=thread-1\nturn=turn-1\nissued=1790300000\nexpires=1790386400\nnonce=ffeeddccbbaa99887766554433221100";
const GRANT_SIGNATURE = "40206a5b444e0c4beb95208ceb7c10f6d613ff68f0c9402ccc789c9a71017de9a1beb291f7c4ff488f3d72193fa4731113d6b84693a5c3d4636195277205bf05";

// Host request.
const EVENT_BODY = '{"eventId":"evt_0123456789abcdef0123456789abcdef","agent":"codex","threadId":"thread-1","turnId":"turn-1","type":"completed","reason":null,"occurredAt":1790300000}';
const EVENT_BODY_SHA = "b0afc6ab15f7ae40138b3efcecfd209cb704f96abcb25f14807ed800b72732b3";
const HOST_CANONICAL = "agentbuddy-push-host-v2\nhttps://push.example.test\nPOST\n/v2/events\n1790300000\n00112233445566778899aabbccddeeff\nb0afc6ab15f7ae40138b3efcecfd209cb704f96abcb25f14807ed800b72732b3";
const HOST_SIGNATURE = "d3040154598d4426ae08efa11f69365fa37cf164458b05eb56e219c694f679bca6356a2dd8dd7f2151e3663c20dc7d354b71109fd8ab9d8dc8a693d8af1c2b05";

// Device revoke.
const REVOKE_CANONICAL = "agentbuddy-push-revoke-v2\naud=https://push.example.test\nhost=8a88e3dd7409f195fd52db2d3cba5d72ca6709bf1d94121bf3748801b40f6f5c\ndevice=8139770ea87d175f56a35466c34c7ecccb8d8a91b4ee37a25df60f5b8fc9b394\nscope=all\ntimestamp=1790300000\nnonce=0f0e0d0c0b0a09080706050403020100";
const REVOKE_SIGNATURE = "8ebac8fa199a1349c15d693b27a835f6a26d3aad210fe152f52470a78a1e2391d86d0fd2091059482df3d63236c24a934e5c8e0c3ffdc883ef0f91e6c7f34f0e";

const GRANT_FIELDS = {
  aud: AUD,
  host: HOST_ID,
  device: DEVICE_ID,
  platform: "ios",
  environment: "production",
  targetSha256: SEALED_SHA,
  agent: "codex",
  thread: "thread-1",
  turn: "turn-1",
  issued: 1790300000,
  expires: 1790386400,
  nonce: "ffeeddccbbaa99887766554433221100",
};
const EXPECTED_TARGET = { hostId: HOST_ID, deviceId: DEVICE_ID, platform: "ios", apnsEnvironment: "production" };
const SEAL_ENV = { PUSH_TARGET_SEAL_KEY: `1:${SEAL_PRIVATE}` };

test("seeds derive the spec host and device ids; the harness uses the spec aud and test seal key", () => {
  assert.equal(h.HOST_A.id, HOST_ID);
  assert.equal(h.DEVICE_A.id, DEVICE_ID);
  assert.equal(h.AUD, AUD);
  assert.equal(h.TEST_SEAL_PUBLIC, SEAL_PUBLIC);
  assert.equal(h.TEST_SEAL_KEY, `1:${SEAL_PRIVATE}`);
});

test("host request vector: body hash, v2 canonical string with aud, signature", async () => {
  assert.equal(await signing.sha256Hex(EVENT_BODY), EVENT_BODY_SHA);
  const canonical = signing.hostRequestSigningString(AUD, "POST", "/v2/events", 1790300000, "00112233445566778899aabbccddeeff", EVENT_BODY_SHA);
  assert.equal(canonical, HOST_CANONICAL);
  assert.equal(signing.hostRequestSigningString(AUD, "POST", "/v2/events", "1790300000", "00112233445566778899aabbccddeeff", EVENT_BODY_SHA), HOST_CANONICAL);
  assert.equal(h.hostCanonical("POST", "/v2/events", 1790300000, "00112233445566778899aabbccddeeff", EVENT_BODY), HOST_CANONICAL);
  assert.equal(h.HOST_A.sign(canonical), HOST_SIGNATURE, "Ed25519 is deterministic: same signature as the spec");
  assert.equal(await signing.verifyEd25519(HOST_ID, HOST_SIGNATURE, canonical), true);
});

test("grant vector: target hash, v2 canonical string and signature", async () => {
  assert.equal(await signing.sha256Hex(SEALED), SEALED_SHA);
  const canonical = signing.grantSigningString(GRANT_FIELDS);
  assert.equal(canonical, GRANT_CANONICAL);
  assert.equal(h.DEVICE_A.sign(canonical), GRANT_SIGNATURE);
  assert.equal(await signing.verifyEd25519(DEVICE_ID, GRANT_SIGNATURE, canonical), true);
});

test("revoke vector: v2 canonical string and signature", async () => {
  const canonical = signing.revokeSigningString({ aud: AUD, host: HOST_ID, device: DEVICE_ID, scope: "all", timestamp: 1790300000, nonce: "0f0e0d0c0b0a09080706050403020100" });
  assert.equal(canonical, REVOKE_CANONICAL);
  assert.equal(h.DEVICE_A.sign(canonical), REVOKE_SIGNATURE);
  assert.equal(await signing.verifyEd25519(DEVICE_ID, REVOKE_SIGNATURE, canonical), true);
});

test("sealed target vector: X25519 shared secret, HKDF key and the exact sealedTarget", () => {
  const priv = (hex) => nodeCrypto.createPrivateKey({ key: Buffer.concat([Buffer.from("302e020100300506032b656e04220420", "hex"), Buffer.from(hex, "hex")]), format: "der", type: "pkcs8" });
  const pub = (key) => nodeCrypto.createPublicKey(key).export({ format: "der", type: "spki" }).subarray(12);
  assert.equal(pub(priv(SEAL_PRIVATE)).toString("hex"), SEAL_PUBLIC);
  assert.equal(pub(priv(EPH_PRIVATE)).toString("hex"), EPH_PUBLIC);
  const shared = nodeCrypto.diffieHellman({ privateKey: priv(EPH_PRIVATE), publicKey: nodeCrypto.createPublicKey(priv(SEAL_PRIVATE)) });
  assert.equal(shared.toString("hex"), SHARED);
  const key = Buffer.from(nodeCrypto.hkdfSync("sha256", shared, Buffer.from(EPH_PUBLIC + SEAL_PUBLIC, "hex"), Buffer.from("agentbuddy-push-target-v1"), 32));
  assert.equal(key.toString("hex"), HKDF_KEY);
  // The harness sealer (used by every other test) reproduces the vector.
  const resealed = h.sealTarget({
    ephemeralPrivate: EPH_PRIVATE, nonce: GCM_NONCE, platform: "ios", token: PUSH_TOKEN,
    apnsEnvironment: "production", deviceId: DEVICE_ID, hostId: HOST_ID,
  });
  assert.equal(resealed, SEALED);
  const raw = Buffer.from(SEALED, "base64url");
  assert.deepEqual([raw[0], raw[1]], [0x01, 1]);
  assert.equal(raw.subarray(2, 34).toString("hex"), EPH_PUBLIC);
  assert.equal(raw.subarray(34, 46).toString("hex"), GCM_NONCE);
  const decipher = nodeCrypto.createDecipheriv("aes-256-gcm", key, raw.subarray(34, 46));
  decipher.setAAD(Buffer.from(AAD));
  decipher.setAuthTag(raw.subarray(raw.length - 16));
  assert.equal(Buffer.concat([decipher.update(raw.subarray(46, raw.length - 16)), decipher.final()]).toString(), PLAINTEXT);
});

test("the Worker derives the test public key and decrypts the exact vector sealedTarget", async () => {
  const keys = await sealed.sealKeys(SEAL_ENV);
  assert.deepEqual([...keys.keys()], [1]);
  assert.equal(Buffer.from(keys.get(1).publicKey).toString("hex"), SEAL_PUBLIC);
  assert.equal(await sealed.openSealedTarget(SEAL_ENV, SEALED, EXPECTED_TARGET), PUSH_TOKEN);
  // Uppercase hex and whitespace in the secret are accepted.
  assert.equal(await sealed.openSealedTarget({ PUSH_TARGET_SEAL_KEY: ` 1:${SEAL_PRIVATE.toUpperCase()} ` }, SEALED, EXPECTED_TARGET), PUSH_TOKEN);

  // AAD and plaintext are bound to host, device, platform and environment.
  const mismatches = [
    { ...EXPECTED_TARGET, hostId: h.HOST_B.id },
    { ...EXPECTED_TARGET, deviceId: h.DEVICE_B.id },
    { ...EXPECTED_TARGET, platform: "android", apnsEnvironment: null },
    { ...EXPECTED_TARGET, apnsEnvironment: "sandbox" },
  ];
  for (const expected of mismatches) {
    assert.equal(await sealed.openSealedTarget(SEAL_ENV, SEALED, expected), null, JSON.stringify(expected));
  }
  // Another Worker key (same kid) cannot open it.
  assert.equal(await sealed.openSealedTarget({ PUSH_TARGET_SEAL_KEY: `1:${"07".repeat(32)}` }, SEALED, EXPECTED_TARGET), null);
});

test("verification rejects tampering, wrong keys, v1 strings and malformed input", async () => {
  assert.equal(await signing.verifyEd25519(HOST_ID, HOST_SIGNATURE, HOST_CANONICAL + "\n"), false);
  assert.equal(await signing.verifyEd25519(HOST_ID, HOST_SIGNATURE, HOST_CANONICAL.replace("POST", "PUT")), false);
  assert.equal(await signing.verifyEd25519(HOST_ID, HOST_SIGNATURE, HOST_CANONICAL.replace(AUD, "https://other.example.test")), false);
  assert.equal(await signing.verifyEd25519(HOST_ID, HOST_SIGNATURE, HOST_CANONICAL.replace("-v2\n" + AUD + "\n", "-v1\n")), false);
  assert.equal(await signing.verifyEd25519(DEVICE_ID, HOST_SIGNATURE, HOST_CANONICAL), false);
  assert.equal(await signing.verifyEd25519(HOST_ID, GRANT_SIGNATURE, HOST_CANONICAL), false);
  assert.equal(await signing.verifyEd25519(HOST_ID.toUpperCase(), HOST_SIGNATURE, HOST_CANONICAL), false);
  assert.equal(await signing.verifyEd25519(HOST_ID, HOST_SIGNATURE.slice(2), HOST_CANONICAL), false);
  assert.equal(await signing.verifyEd25519("zz".repeat(32), HOST_SIGNATURE, HOST_CANONICAL), false);
  const flipped = (HOST_SIGNATURE[0] === "d" ? "e" : "d") + HOST_SIGNATURE.slice(1);
  assert.equal(await signing.verifyEd25519(HOST_ID, flipped, HOST_CANONICAL), false);
});

test("timestamp window is 300 seconds either way", () => {
  assert.equal(signing.withinSignatureWindow(1000, 1300), true);
  assert.equal(signing.withinSignatureWindow(1000, 700), true);
  assert.equal(signing.withinSignatureWindow(1000, 1301), false);
  assert.equal(signing.withinSignatureWindow(1000, 699), false);
});

test("audience: request origin by default, PUSH_AUDIENCE overrides it", () => {
  const request = new Request("https://push.example.test/v2/events?x=1");
  assert.equal(signing.workerAudience(request, {}), AUD);
  assert.equal(signing.workerAudience(new Request("http://127.0.0.1:8787/v2/events"), {}), "http://127.0.0.1:8787");
  assert.equal(signing.workerAudience(request, { PUSH_AUDIENCE: " https://Push.Custom.example/ " }), "https://push.custom.example");
  assert.equal(signing.workerAudience(request, { PUSH_AUDIENCE: "" }), AUD);
  assert.equal(signing.workerAudience(request, { PUSH_AUDIENCE: "not a url" }), "not a url", "unparsable override matches no signer");
});

test("the Worker accepts the exact vector grant (sealed target), event request and revoke", async () => {
  h.clock.set(h.VECTOR_TIME * 1000);
  const { env, channel } = h.makeEnv();
  h.fetchMock.reset(() => new Response(null, { status: 200 }));

  // Grant vector with the vector sealed target, submitted by host A (the
  // vector host) with a fresh host signature.
  const grant = {
    deviceId: DEVICE_ID, platform: "ios", apnsEnvironment: "production", sealedTarget: SEALED,
    agent: "codex", threadId: "thread-1", turnId: "turn-1", issuedAt: 1790300000, expiresAt: 1790386400,
    grantNonce: "ffeeddccbbaa99887766554433221100", grantSignature: GRANT_SIGNATURE,
  };
  const sub = await worker.fetch(h.hostRequest(h.HOST_A, "POST", "/v2/subscriptions", grant), env);
  assert.equal(sub.status, 201);
  const { subscriptionId, expiresAt } = await sub.json();
  assert.match(subscriptionId, /^sub_[0-9a-f]{32}$/);
  assert.equal(expiresAt, 1790386400);
  assert.equal(channel(HOST_ID).map.get(`sub:${subscriptionId}`).pushToken, PUSH_TOKEN, "the decrypted token is stored");

  // Host request vector verbatim: origin, body, timestamp, nonce and signature.
  const request = new Request(`${AUD}/v2/events`, {
    method: "POST",
    headers: {
      "X-AgentBuddy-Host": HOST_ID,
      "X-AgentBuddy-Timestamp": "1790300000",
      "X-AgentBuddy-Nonce": "00112233445566778899aabbccddeeff",
      "X-AgentBuddy-Signature": HOST_SIGNATURE,
    },
    body: EVENT_BODY,
  });
  const event = await worker.fetch(request, env);
  assert.equal(event.status, 202);
  assert.deepEqual(await event.json(), {
    eventId: "evt_0123456789abcdef0123456789abcdef", matched: 1, results: [{ subscriptionId, state: "sent" }],
  });
  assert.equal(h.fetchMock.providerCalls().length, 1);
  assert.equal(h.fetchMock.calls[0].url, `https://api.push.apple.com/3/device/${PUSH_TOKEN}`);

  // Revoke vector (device-signed, no host signature).
  const revoke = await worker.fetch(h.revokeRequest({
    hostId: HOST_ID, deviceId: DEVICE_ID, scope: "all", timestamp: 1790300000,
    nonce: "0f0e0d0c0b0a09080706050403020100", signature: REVOKE_SIGNATURE,
  }), env);
  assert.equal(revoke.status, 200);
  assert.deepEqual(await revoke.json(), { revoked: 0 });
  assert.deepEqual(channel(HOST_ID).map.get(`revoked:${DEVICE_ID}`).at, 1790300000);
});

test("the same vector requests are rejected at another origin (aud mismatch)", async () => {
  h.clock.set(h.VECTOR_TIME * 1000);
  const { env, channels } = h.makeEnv();
  h.fetchMock.reset(() => new Response(null, { status: 200 }));
  const event = await worker.fetch(new Request("https://other.example.test/v2/events", {
    method: "POST",
    headers: {
      "X-AgentBuddy-Host": HOST_ID,
      "X-AgentBuddy-Timestamp": "1790300000",
      "X-AgentBuddy-Nonce": "00112233445566778899aabbccddeeff",
      "X-AgentBuddy-Signature": HOST_SIGNATURE,
    },
    body: EVENT_BODY,
  }), env);
  assert.equal(event.status, 401);
  const revoke = await worker.fetch(new Request("https://other.example.test/v2/subscriptions/revoke", {
    method: "POST",
    body: JSON.stringify({
      hostId: HOST_ID, deviceId: DEVICE_ID, scope: "all", timestamp: 1790300000,
      nonce: "0f0e0d0c0b0a09080706050403020100", signature: REVOKE_SIGNATURE,
    }),
  }), env);
  assert.equal(revoke.status, 401);
  assert.equal(channels.size, 0);
});
