import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[3]
ARM64 = "aarch64-apple-ios-macabi"
INTEL = "x86_64-apple-ios-macabi"


class CatalystBuildTests(unittest.TestCase):
    def run_build(self, flag, *, host="arm64", profile="mobile-release", fail=False):
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
            generated = root / "apps/ios/GeneratedRust/ios-macabi"
            generated.mkdir(parents=True)
            (generated / "libghostty.a").touch()
            bin_dir = root / "bin"
            bin_dir.mkdir()
            (bin_dir / "uname").write_text(f"#!/bin/sh\necho {host}\n")
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
            (bin_dir / "lipo").write_text('''#!/usr/bin/env python3
from pathlib import Path
import sys
args = sys.argv[1:]
assert args[0] == "-create"
sources = args[1:args.index("-output")]
Path(args[-1]).write_text("\\n".join(Path(p).read_text() for p in sources))
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
                ["bash", str(script), flag, "--skip-bindings"],
                env=env, capture_output=True, text=True,
            )
            calls = [json.loads(line) for line in (root / "calls.jsonl").read_text().splitlines()]
            library = generated / "libcodex_mobile_client.a"
            return result, calls, library.read_text() if library.exists() else None

    def test_arm64_release_preserves_profile_even_on_intel_host(self):
        for profile in ("release", "mobile-release"):
            with self.subTest(profile=profile):
                result, calls, library = self.run_build(
                    "--macabi-arm64-only", host="x86_64", profile=profile,
                )
                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertEqual(calls, [{"target": ARM64, "profile": profile}])
                self.assertEqual(library, ARM64)

    def test_default_catalyst_release_keeps_both_architectures(self):
        result, calls, library = self.run_build("--macabi-only")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertCountEqual(calls, [
            {"target": ARM64, "profile": "mobile-release"},
            {"target": INTEL, "profile": "mobile-release"},
        ])
        self.assertEqual(library, f"{ARM64}\n{INTEL}")

    def test_fast_catalyst_keeps_host_arch_and_dev_profile(self):
        for host, target in (("arm64", ARM64), ("x86_64", INTEL)):
            with self.subTest(host=host):
                result, calls, library = self.run_build("--fast-macabi", host=host)
                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertEqual(calls, [{"target": target, "profile": "ios-dev"}])
                self.assertEqual(library, target)

    def test_compiler_failure_does_not_publish_an_artifact(self):
        result, _, library = self.run_build("--macabi-arm64-only", fail=True)
        self.assertEqual(result.returncode, 42)
        self.assertIsNone(library)


if __name__ == "__main__":
    unittest.main()
