import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[3]
SHA = 'a' * 40


class RefreshTests(unittest.TestCase):
    def test_opt_in_updates_manifest_pins_before_cargo(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            script = root / 'tools/scripts/update-alleycat-main.sh'
            script.parent.mkdir(parents=True)
            shutil.copy2(ROOT / script.relative_to(root), script)
            manifests = ['shared/rust-bridge/Cargo.toml', 'services/kittylitter/Cargo.toml']
            for name in manifests:
                target = root / name
                target.parent.mkdir(parents=True)
                shutil.copy2(ROOT / name, target)
            bin_dir = root / 'bin'
            bin_dir.mkdir()
            (bin_dir / 'git').write_text(f'#!/bin/sh\nprintf "{SHA}\\trefs/heads/main\\n"\n')
            (bin_dir / 'cargo').write_text('''#!/usr/bin/env python3
import sys
from pathlib import Path
args = sys.argv
manifest = Path(args[args.index('--manifest-path') + 1])
sha = args[args.index('--precise') + 1]
lines = [line for line in manifest.read_text().splitlines() if 'alleycat.git' in line]
assert lines and all('rev = "' + sha + '"' in line for line in lines), 'manifest is still pinned to old revision'
''')
            for executable in bin_dir.iterdir():
                executable.chmod(0o755)
            env = {**os.environ, 'PATH': f'{bin_dir}:{os.environ["PATH"]}', 'AGENTBUDDY_REFRESH_ALLEYCAT': '1', 'LITTER_SKIP_ALLEYCAT_UPDATE': '0'}
            result = subprocess.run(['bash', str(script)], env=env, capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stderr)
            for name in manifests:
                self.assertIn(f'rev = "{SHA}"', (root / name).read_text())


if __name__ == '__main__':
    unittest.main()
