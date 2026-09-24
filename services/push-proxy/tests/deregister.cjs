const { test, after } = require("node:test");
const assert = require("node:assert/strict");
const { execFileSync } = require("node:child_process");
const { mkdtempSync, rmSync } = require("node:fs");
const { tmpdir } = require("node:os");
const path = require("node:path");
const output = mkdtempSync(path.join(tmpdir(), "agentbuddy-push-test-"));
execFileSync(process.execPath, [require.resolve("typescript/bin/tsc"), "--noEmit", "false", "--module", "commonjs", "--moduleResolution", "node", "--outDir", output], { cwd: path.join(__dirname, "..") });
after(() => rmSync(output, { recursive: true, force: true }));
const { PushRegistration } = require(path.join(output, "durable-object.js"));
const worker = require(path.join(output, "index.js")).default;

test("deregister clears registration and returns JSON that Android can decode", async () => {
  let cleared = false;
  const registration = new PushRegistration({ storage: { deleteAll: async () => { cleared = true; } } }, {});
  const env = { PUSH_REGISTRATION: {
    idFromString: id => id,
    get: () => ({ fetch: request => registration.fetch(request) }),
  } };
  const response = await worker.fetch(new Request("https://proxy/test-id/deregister", { method: "POST" }), env);
  assert.equal(response.status, 200);
  assert.equal(cleared, true);
  assert.deepEqual(await response.json(), { ok: true });
});
