import { test } from "node:test";
import assert from "node:assert/strict";
import { mkdtempSync, writeFileSync, readFileSync } from "node:fs";
import { join } from "node:path";
import { tmpdir } from "node:os";
import { syncVersion } from "./sync-version.mjs";

test("copies the Cargo.toml version into tauri.conf.json", () => {
  const dir = mkdtempSync(join(tmpdir(), "syncver-"));
  const cargo = join(dir, "Cargo.toml");
  const conf = join(dir, "tauri.conf.json");
  writeFileSync(cargo, '[package]\nname = "agentbuddycli"\nversion = "1.2.3"\n');
  writeFileSync(conf, JSON.stringify({ productName: "AgentBuddy", version: "0.0.0" }, null, 2));
  const v = syncVersion(cargo, conf);
  assert.equal(v, "1.2.3");
  assert.equal(JSON.parse(readFileSync(conf, "utf8")).version, "1.2.3");
});

test("throws when Cargo.toml has no version", () => {
  const dir = mkdtempSync(join(tmpdir(), "syncver-"));
  const cargo = join(dir, "Cargo.toml");
  writeFileSync(cargo, "[package]\nname = \"x\"\n");
  assert.throws(() => syncVersion(cargo, join(dir, "c.json")), /version not found/);
});
