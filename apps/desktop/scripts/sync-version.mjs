#!/usr/bin/env node
import { readFileSync, writeFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, resolve } from "node:path";

export function syncVersion(cargoTomlPath, tauriConfPath) {
  const cargo = readFileSync(cargoTomlPath, "utf8");
  const match = cargo.match(/^version\s*=\s*"([^"]+)"/m);
  if (!match) throw new Error(`version not found in ${cargoTomlPath}`);
  const conf = JSON.parse(readFileSync(tauriConfPath, "utf8"));
  conf.version = match[1];
  writeFileSync(tauriConfPath, JSON.stringify(conf, null, 2) + "\n");
  return match[1];
}

const isMain = process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (isMain) {
  const here = dirname(fileURLToPath(import.meta.url));
  const cargo = resolve(here, "../../../services/kittylitter/Cargo.toml");
  const conf = resolve(here, "../src-tauri/tauri.conf.json");
  const v = syncVersion(cargo, conf);
  console.log(`tauri.conf.json version -> ${v}`);
}
