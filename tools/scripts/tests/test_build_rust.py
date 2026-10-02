import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[3]
DEVICE = "aarch64-apple-ios"
SIMULATOR = "aarch64-apple-ios-sim"


class IOSBuildTests(unittest.TestCase):
    def run_build(self, flag=None, *, profile="mobile-release", fail=False):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            script = root / "apps/ios/scripts/build-rust.sh"
            script.parent.mkdir(parents=True)
            shutil.copy2(ROOT / script.relative_to(root), script)
            for name in (
                "tools/scripts/load-sccache-aws-creds.sh",
                "tools/scripts/update-alleycat-main.sh",
                "apps/ios/scripts/sync-codex.sh",
            ):
                stub = root / name
                stub.parent.mkdir(parents=True, exist_ok=True)
                stub.write_text("#!/bin/sh\ntrue\n")
                stub.chmod(0o755)
            generated = root / "apps/ios/GeneratedRust/ios-device"
            generated.mkdir(parents=True)
            (generated / "libghostty.a").touch()
            sim = generated.parent / "ios-sim"
            sim.mkdir()
            (sim / "libghostty.a").touch()
            bin_dir = root / "bin"
            bin_dir.mkdir()
            (bin_dir / "xcrun").write_text("#!/bin/sh\nexit 0\n")
            (bin_dir / "rustup").write_text("#!/bin/sh\nexit 0\n")
            (bin_dir / "cargo").write_text('''#!/usr/bin/env python3
import json
import os
from pathlib import Path
import sys
args = sys.argv[1:]
target = args[args.index("--target") + 1]
profile = args[args.index("--profile") + 1] if "--profile" in args else "release"
root = Path(os.environ["TEST_BUILD_ROOT"])
with (root / "calls.jsonl").open("a") as log:
    log.write(json.dumps({"target": target, "profile": profile}) + "\\n")
if os.environ["TEST_BUILD_FAIL"] == "1":
    sys.exit(42)
library = root / "target" / target / profile / "libcodex_mobile_client.a"
library.parent.mkdir(parents=True, exist_ok=True)
library.write_text(target)
''')
            (bin_dir / "xcodebuild").write_text('''#!/usr/bin/env python3
from pathlib import Path
import json, os, sys
Path(os.environ["TEST_BUILD_ROOT"], "packaging.json").write_text(json.dumps(sys.argv[1:]))
''')
            for executable in bin_dir.iterdir():
                executable.chmod(0o755)
            env = {
                **os.environ,
                "PATH": f'{bin_dir}:{os.environ["PATH"]}',
                "IOS_RUST_PROFILE": profile,
                "CARGO_TARGET_DIR": str(root / "target"),
                "CARGO_INCREMENTAL": "1",
                "TEST_BUILD_ROOT": str(root),
                "TEST_BUILD_FAIL": "1" if fail else "0",
            }
            result = subprocess.run(
                ["bash", str(script), *([flag] if flag else []), "--skip-bindings"],
                env=env, capture_output=True, text=True,
            )
            log = root / "calls.jsonl"
            calls = [json.loads(line) for line in log.read_text().splitlines()] if log.exists() else []
            packaging = root / "packaging.json"
            package_args = json.loads(packaging.read_text()) if packaging.exists() else None
            library = generated / "libcodex_mobile_client.a"
            return result, calls, library.read_text() if library.exists() else None, package_args

    def test_device_release_preserves_profile_and_packages_only_device(self):
        for profile in ("release", "mobile-release"):
            with self.subTest(profile=profile):
                result, calls, library, package = self.run_build("--device-only", profile=profile)
                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertEqual(calls, [{"target": DEVICE, "profile": profile}])
                self.assertEqual(library, DEVICE)
                self.assertEqual(package.count("-library"), 1)

    def test_package_has_only_ios_device_and_simulator(self):
        # All compilers are stubs: this test does not build or run a simulator.
        result, calls, library, package = self.run_build()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertCountEqual(calls, [
            {"target": DEVICE, "profile": "mobile-release"},
            {"target": SIMULATOR, "profile": "mobile-release"},
        ])
        self.assertEqual(library, DEVICE)
        self.assertEqual(package.count("-library"), 2)
        self.assertFalse(any("macabi" in arg for arg in package))

    def test_fast_device_skips_packaging(self):
        result, calls, library, package = self.run_build("--fast-device")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(calls, [{"target": DEVICE, "profile": "ios-dev"}])
        self.assertEqual(library, DEVICE)
        self.assertIsNone(package)

    def test_compiler_failure_does_not_publish_an_artifact(self):
        result, _, library, package = self.run_build("--device-only", fail=True)
        self.assertEqual(result.returncode, 42)
        self.assertIsNone(library)
        self.assertIsNone(package)

    def test_retired_catalyst_flags_fail_before_building(self):
        for flag in ("--macabi-only", "--macabi-arm64-only", "--fast-macabi"):
            with self.subTest(flag=flag):
                result, calls, library, package = self.run_build(flag)
                self.assertNotEqual(result.returncode, 0)
                self.assertEqual(calls, [])
                self.assertIsNone(library)
                self.assertIsNone(package)


if __name__ == "__main__":
    unittest.main()
