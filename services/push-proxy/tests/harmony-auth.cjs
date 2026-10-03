const { test, beforeEach } = require("node:test");
const assert = require("node:assert/strict");
const { generateKeyPairSync, verify, constants } = require("node:crypto");
const h = require("./support/harness");
const { harmonyAuthorization, clearHarmonyJWTCache } = h.load("harmony-auth");
const pair = generateKeyPairSync("rsa", { modulusLength: 2048 });
const account = {
  project_id: "123456789", key_id: "test-key", sub_account: "test-service-account",
  private_key: pair.privateKey.export({ format: "pem", type: "pkcs8" }),
};
const environment = value => ({ HARMONY_SERVICE_ACCOUNT: JSON.stringify(value) });
beforeEach(() => { clearHarmonyJWTCache(); h.clock.set(h.VECTOR_TIME * 1000); });

test("Huawei v3 bearer JWT has required claims and a verifiable PS256 signature", async () => {
  const auth = await harmonyAuthorization(environment(account));
  assert.equal(auth.projectId, account.project_id);
  const [header, body, signature] = auth.token.split(".");
  assert.deepEqual(JSON.parse(Buffer.from(header, "base64url")), { alg: "PS256", typ: "JWT", kid: account.key_id });
  assert.deepEqual(JSON.parse(Buffer.from(body, "base64url")), {
    aud: "https://oauth-login.cloud.huawei.com/oauth2/v3/token", iss: account.sub_account,
    iat: h.VECTOR_TIME, exp: h.VECTOR_TIME + 3600,
  });
  assert.ok(verify("sha256", Buffer.from(`${header}.${body}`), {
    key: pair.publicKey, padding: constants.RSA_PKCS1_PSS_PADDING, saltLength: 32,
  }, Buffer.from(signature, "base64url")));
});

test("cached JWT refreshes before expiry and when credentials rotate", async () => {
  const env = environment(account);
  const first = await harmonyAuthorization(env);
  assert.deepEqual(await harmonyAuthorization(env), first);
  h.clock.advance(3300 * 1000);
  const refreshed = await harmonyAuthorization(env);
  assert.notEqual(refreshed.token, first.token);
  const rotated = await harmonyAuthorization(environment({ ...account, key_id: "rotated" }));
  assert.notEqual(rotated.token, refreshed.token);
  clearHarmonyJWTCache();
  assert.notEqual((await harmonyAuthorization(env)).token, refreshed.token);
});

test("invalid or absent service credentials fail without exposing secrets", async () => {
  await assert.rejects(harmonyAuthorization({}), { message: "harmony_credentials_missing" });
  for (const value of [null, {}, { ...account, project_id: "../bad" }, { ...account, private_key: "secret-invalid-key" }]) {
    await assert.rejects(harmonyAuthorization(environment(value)), { message: "harmony_credentials_invalid" });
  }
  await assert.rejects(harmonyAuthorization({ HARMONY_SERVICE_ACCOUNT: "{secret-invalid-json" }), { message: "harmony_credentials_invalid" });
});
