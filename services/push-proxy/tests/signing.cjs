// Spec §12 signing vectors: canonical strings, hashes and signatures must match
// the host (alleycat) and mobile implementations byte for byte.
const { test } = require("node:test");
const assert = require("node:assert/strict");
const h = require("./support/harness");

const signing = h.load("signing");
const worker = h.load("index").default;

const HOST_ID = "8a88e3dd7409f195fd52db2d3cba5d72ca6709bf1d94121bf3748801b40f6f5c";
const DEVICE_ID = "8139770ea87d175f56a35466c34c7ecccb8d8a91b4ee37a25df60f5b8fc9b394";
const EVENT_BODY = '{"eventId":"evt_0123456789abcdef0123456789abcdef","agent":"codex","threadId":"thread-1","turnId":"turn-1","type":"completed","reason":null,"occurredAt":1790300000}';
const EVENT_BODY_SHA = "b0afc6ab15f7ae40138b3efcecfd209cb704f96abcb25f14807ed800b72732b3";
const HOST_CANONICAL = "agentbuddy-push-host-v1\nPOST\n/v2/events\n1790300000\n00112233445566778899aabbccddeeff\nb0afc6ab15f7ae40138b3efcecfd209cb704f96abcb25f14807ed800b72732b3";
const HOST_SIGNATURE = "d5ce9808b8bc3c1a19fefa8da077fd42b53180b2e9eb1e7d4672beb0cefeb169af22c08cc88a46ac9d57df17ca8b21a359390598ea96bd9d1f6fd1aad9077f03";
const PUSH_TOKEN = "a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1";
const TOKEN_SHA = "3490f80401886d12f3861ec190f5c16419b26345497a92bc4530e22f5d8b4295";
const GRANT_CANONICAL = "agentbuddy-push-grant-v1\nhost=8a88e3dd7409f195fd52db2d3cba5d72ca6709bf1d94121bf3748801b40f6f5c\ndevice=8139770ea87d175f56a35466c34c7ecccb8d8a91b4ee37a25df60f5b8fc9b394\nplatform=ios\nenvironment=production\ntoken_sha256=3490f80401886d12f3861ec190f5c16419b26345497a92bc4530e22f5d8b4295\nagent=codex\nthread=thread-1\nturn=turn-1\nissued=1790300000\nexpires=1790386400\nnonce=ffeeddccbbaa99887766554433221100";
const GRANT_SIGNATURE = "c0bb8f6643304b84ad574ee0797e698f53a6b3eb9abcb4a08d98f5bc691530f705e36f138f28dcf2f8df271c59372d475d8d17171ec03dda91b0e62cd721200e";
const REVOKE_CANONICAL = "agentbuddy-push-revoke-v1\nhost=8a88e3dd7409f195fd52db2d3cba5d72ca6709bf1d94121bf3748801b40f6f5c\ndevice=8139770ea87d175f56a35466c34c7ecccb8d8a91b4ee37a25df60f5b8fc9b394\nscope=all\ntimestamp=1790300000\nnonce=0f0e0d0c0b0a09080706050403020100";
const REVOKE_SIGNATURE = "8a989a6ca6e9d3cd37bdbfd3ff3b4aa94482e27c0613a03d51a27e51a13f072e1ae39c460e1651601846b43d4a4c6d1cc6923fddaac36ad776892e598b12bf03";

const GRANT_FIELDS = {
  host: HOST_ID,
  device: DEVICE_ID,
  platform: "ios",
  environment: "production",
  tokenSha256: TOKEN_SHA,
  agent: "codex",
  thread: "thread-1",
  turn: "turn-1",
  issued: 1790300000,
  expires: 1790386400,
  nonce: "ffeeddccbbaa99887766554433221100",
};

test("seeds derive the spec host and device ids", () => {
  assert.equal(h.HOST_A.id, HOST_ID);
  assert.equal(h.DEVICE_A.id, DEVICE_ID);
});

test("host request vector: body hash, canonical string and signature", async () => {
  assert.equal(await signing.sha256Hex(EVENT_BODY), EVENT_BODY_SHA);
  const canonical = signing.hostRequestSigningString("POST", "/v2/events", 1790300000, "00112233445566778899aabbccddeeff", EVENT_BODY_SHA);
  assert.equal(canonical, HOST_CANONICAL);
  assert.equal(signing.hostRequestSigningString("POST", "/v2/events", "1790300000", "00112233445566778899aabbccddeeff", EVENT_BODY_SHA), HOST_CANONICAL);
  assert.equal(h.HOST_A.sign(canonical), HOST_SIGNATURE, "Ed25519 is deterministic: same signature as the spec");
  assert.equal(await signing.verifyEd25519(HOST_ID, HOST_SIGNATURE, canonical), true);
});

test("grant vector: token hash, canonical string and signature", async () => {
  assert.equal(await signing.sha256Hex(PUSH_TOKEN), TOKEN_SHA);
  const canonical = signing.grantSigningString(GRANT_FIELDS);
  assert.equal(canonical, GRANT_CANONICAL);
  assert.equal(h.DEVICE_A.sign(canonical), GRANT_SIGNATURE);
  assert.equal(await signing.verifyEd25519(DEVICE_ID, GRANT_SIGNATURE, canonical), true);
});

test("revoke vector: canonical string and signature", async () => {
  const canonical = signing.revokeSigningString({ host: HOST_ID, device: DEVICE_ID, scope: "all", timestamp: 1790300000, nonce: "0f0e0d0c0b0a09080706050403020100" });
  assert.equal(canonical, REVOKE_CANONICAL);
  assert.equal(h.DEVICE_A.sign(canonical), REVOKE_SIGNATURE);
  assert.equal(await signing.verifyEd25519(DEVICE_ID, REVOKE_SIGNATURE, canonical), true);
});

test("verification rejects tampering, wrong keys and malformed input", async () => {
  assert.equal(await signing.verifyEd25519(HOST_ID, HOST_SIGNATURE, HOST_CANONICAL + "\n"), false);
  assert.equal(await signing.verifyEd25519(HOST_ID, HOST_SIGNATURE, HOST_CANONICAL.replace("POST", "PUT")), false);
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

test("the Worker accepts the exact vector event request, grant and revoke", async () => {
  h.clock.set(h.VECTOR_TIME * 1000);
  const { env, channel } = h.makeEnv();
  h.fetchMock.reset(() => new Response(null, { status: 200 }));

  // Grant vector, submitted by host A (the vector host) with a fresh host signature.
  const grant = {
    deviceId: DEVICE_ID, platform: "ios", pushToken: PUSH_TOKEN, apnsEnvironment: "production",
    agent: "codex", threadId: "thread-1", turnId: "turn-1", issuedAt: 1790300000, expiresAt: 1790386400,
    grantNonce: "ffeeddccbbaa99887766554433221100", grantSignature: GRANT_SIGNATURE,
  };
  const sub = await worker.fetch(h.hostRequest(h.HOST_A, "POST", "/v2/subscriptions", grant), env);
  assert.equal(sub.status, 201);
  const { subscriptionId, expiresAt } = await sub.json();
  assert.match(subscriptionId, /^sub_[0-9a-f]{32}$/);
  assert.equal(expiresAt, 1790386400);

  // Host request vector verbatim: body, timestamp, nonce and signature.
  const request = new Request("https://proxy/v2/events", {
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
