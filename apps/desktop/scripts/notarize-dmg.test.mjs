import { test } from "node:test";
import assert from "node:assert/strict";
import { mkdtempSync, mkdirSync, writeFileSync, readFileSync, rmSync } from "node:fs";
import { join } from "node:path";
import { tmpdir } from "node:os";
import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";

const script = fileURLToPath(new URL("./notarize-dmg.sh", import.meta.url));

for (const status of ["Accepted", "Invalid", "In Progress"]) {
  test(`notarization handles ${status} without losing the submission ID`, () => {
    const dir = mkdtempSync(join(tmpdir(), "notary-test-"));
    try {
      const bundle = join(dir, "bundle with spaces");
      const diagnostics = join(dir, "diagnostics");
      mkdirSync(bundle);
      writeFileSync(join(bundle, "AgentBuddy.dmg"), "test");
      writeFileSync(join(dir, "xcrun"), `#!/usr/bin/env bash
set -eu
echo "$1 $2" >> "$TEST_CALLS"
case "$1 $2" in
  "notarytool submit") echo '{"id":"test-submission"}' ;;
  "notarytool wait")
    test -f "$TEST_DIAGNOSTICS/submission.json"
    echo 'Finished waiting'
    if [[ "$TEST_STATUS" == "In Progress" ]]; then exit 1; fi ;;
  "notarytool info") printf '{"status":"%s"}\n' "$TEST_STATUS" ;;
  "notarytool log")
    for LAST_ARG in "$@"; do :; done
    echo '{}' > "$LAST_ARG" ;;
  "stapler staple"|"stapler validate") test "$TEST_STATUS" = Accepted ;;
  *) exit 2 ;;
esac
`, { mode: 0o755 });
      const result = spawnSync("bash", [script, bundle, diagnostics], {
        encoding: "utf8",
        timeout: 5000,
        env: {
          ...process.env,
          PATH: `${dir}:${process.env.PATH}`,
          APPLE_API_KEY_PATH: join(dir, "test-key.p8"),
          APPLE_API_KEY: "test-key-id",
          APPLE_API_ISSUER: "test-issuer",
          GITHUB_STEP_SUMMARY: join(dir, "summary.md"),
          TEST_STATUS: status,
          TEST_CALLS: join(dir, "calls"),
          TEST_DIAGNOSTICS: diagnostics,
        },
      });
      assert.equal(result.status, status === "Accepted" ? 0 : 1, result.stderr);
      assert.equal(JSON.parse(readFileSync(join(diagnostics, "submission.json"))).id, "test-submission");
      assert.equal(JSON.parse(readFileSync(join(diagnostics, "status.json"))).status, status);
      const calls = readFileSync(join(dir, "calls"), "utf8");
      assert.equal(calls.includes("stapler staple"), status === "Accepted");
      assert.equal(calls.includes("stapler validate"), status === "Accepted");
      assert.equal(calls.includes("notarytool log"), status !== "In Progress");
      assert.match(readFileSync(join(dir, "summary.md"), "utf8"), /test-submission/);
    } finally {
      rmSync(dir, { recursive: true, force: true });
    }
  });
}
