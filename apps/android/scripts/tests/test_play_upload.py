"""Exercise release dispatch without invoking Gradle or accessing real credentials."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / "play-upload.sh"


class PlayUploadTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        root = Path(self.temp.name)
        android = root / "apps/android"
        (android / "scripts").mkdir(parents=True)
        self.script = android / "scripts/play-upload.sh"
        shutil.copy2(SCRIPT, self.script)
        gradlew = android / "gradlew"
        gradlew.write_text("#!/usr/bin/env python3\nimport json, os, sys\n"
                           "with open(os.environ['CAPTURE'], 'a') as f:\n"
                           "    f.write(json.dumps(sys.argv[1:]) + '\\n')\n")
        gradlew.chmod(0o700)
        self.capture = root / "calls.jsonl"
        self.config = root / "play-upload.env"
        store = root / "test.jks"
        account = root / "account.json"
        store.touch()
        account.touch()
        self.env = {"PATH": os.environ["PATH"], "HOME": str(root),
                    "CAPTURE": str(self.capture), "PLAY_UPLOAD_ENV_FILE": str(self.config),
                    "LITTER_UPLOAD_STORE_FILE": str(store),
                    "LITTER_UPLOAD_STORE_PASSWORD": "test-store-password",
                    "LITTER_UPLOAD_KEY_ALIAS": "test-alias",
                    "LITTER_UPLOAD_KEY_PASSWORD": "test-key-password",
                    "LITTER_PLAY_SERVICE_ACCOUNT_JSON": str(account)}

    def run_script(self, success=True, **values):
        result = subprocess.run(["bash", str(self.script)], env=self.env | values,
                                text=True, capture_output=True)
        self.assertEqual(result.returncode == 0, success, result.stderr)
        return [json.loads(line) for line in self.capture.read_text().splitlines()] if self.capture.exists() else []

    def test_build_only_signed_without_service_account(self):
        calls = self.run_script(UPLOAD="0", LITTER_PLAY_SERVICE_ACCOUNT_JSON="")
        self.assertEqual(len(calls), 1)
        self.assertIn(":app:bundleRelease", calls[0])
        self.assertIn("-PLITTER_UPLOAD_KEY_PASSWORD=test-key-password", calls[0])

    def test_build_only_requires_signing(self):
        self.assertEqual(self.run_script(False, UPLOAD="0", LITTER_UPLOAD_KEY_PASSWORD=""), [])

    def test_draft_stays_draft_without_promotion(self):
        calls = self.run_script(LITTER_PLAY_RELEASE_STATUS="draft")
        self.assertEqual(len(calls), 1)
        self.assertIn("-PLITTER_PLAY_RELEASE_STATUS=draft", calls[0])

    def test_staged_upload_keeps_fraction(self):
        calls = self.run_script(LITTER_PLAY_RELEASE_STATUS="inProgress", LITTER_PLAY_USER_FRACTION="0.2")
        self.assertIn("-PLITTER_PLAY_RELEASE_STATUS=inProgress", calls[0])
        self.assertIn("-PLITTER_PLAY_USER_FRACTION=0.2", calls[0])

    def test_promotion_completes_source_only(self):
        calls = self.run_script(LITTER_PLAY_PROMOTE_TRACK="alpha,beta", LITTER_PLAY_RELEASE_STATUS="draft")
        self.assertEqual(len(calls), 3)
        self.assertIn("-PLITTER_PLAY_RELEASE_STATUS=completed", calls[0])
        for call, destination in zip(calls[1:], ["alpha", "beta"]):
            self.assertIn(":app:promoteReleaseArtifact", call)
            self.assertIn("-PLITTER_PLAY_RELEASE_STATUS=draft", call)
            self.assertIn(f"-PLITTER_PLAY_PROMOTE_TRACK={destination}", call)

    def test_env_file_loaded_before_options(self):
        self.config.write_text('LITTER_PLAY_TRACK=alpha\nLITTER_PLAY_RELEASE_STATUS=draft\nGRADLE_MAX_WORKERS=2\n')
        calls = self.run_script()
        self.assertIn("-PLITTER_PLAY_TRACK=alpha", calls[0])
        self.assertIn("-PLITTER_PLAY_RELEASE_STATUS=draft", calls[0])
        self.assertIn("--max-workers=2", calls[0])

    def test_upload_requires_service_account_file(self):
        self.assertEqual(self.run_script(False, LITTER_PLAY_SERVICE_ACCOUNT_JSON="/nonexistent/test.json"), [])


if __name__ == "__main__":
    unittest.main()
