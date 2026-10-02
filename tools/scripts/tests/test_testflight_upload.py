import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[3]
REVIEW_CONFLICT = (
    "Error: testflight review submit: failed to submit: Another build is in review.: "
    "Another build in the same train is already in beta review. "
    "Please submit it again once it gets completed."
)


class TestFlightUploadTests(unittest.TestCase):
    def run_upload(self, *, review_error="", review_exit=0, validation_exit=0,
                   submit_review="1", groups="Internal Testers,Beta Testers"):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            scripts = root / "apps/ios/scripts"
            scripts.mkdir(parents=True)
            for name in ("testflight-upload.sh", "release-common.sh"):
                shutil.copy2(ROOT / "apps/ios/scripts" / name, scripts / name)
            build_dir = root / "build"
            build_dir.mkdir()
            (build_dir / "AgentBuddy.ipa").touch()
            (build_dir / "testflight-build.env").write_text(
                "MARKETING_VERSION=1.5.1\nBUILD_NUMBER=123\n"
                "APP_STORE_APP_ID=test-app\nTEAM_ID=test-team\n"
            )
            bin_dir = root / "bin"
            bin_dir.mkdir()
            for name in ("xcodebuild", "xcodegen"):
                # Only command discovery is expected; no native build is needed.
                (bin_dir / name).write_text("#!/bin/sh\nexit 99\n")
            (bin_dir / "asc").write_text('''#!/usr/bin/env python3
import json
import os
from pathlib import Path
import sys

args = sys.argv[1:]
with Path(os.environ["TEST_ASC_CALLS"]).open("a") as log:
    log.write(json.dumps(args) + "\\n")
if args[:2] == ["builds", "upload"]:
    print(json.dumps({"data": {"id": "uploaded-build"}}))
elif args[:3] == ["builds", "build-beta-detail", "view"]:
    print(json.dumps({"data": {"attributes": {"internalBuildState": "IN_BETA_TESTING"}}}))
elif args[:3] == ["testflight", "groups", "list"]:
    print(json.dumps({"data": [
        {"id": "internal-group", "attributes": {"name": "Internal Testers"}},
        {"id": "external-group", "attributes": {"name": "Beta Testers"}},
    ]}))
elif args[:3] == ["testflight", "review", "submit"]:
    if os.environ["TEST_REVIEW_ERROR"]:
        print(os.environ["TEST_REVIEW_ERROR"], file=sys.stderr)
    sys.exit(int(os.environ["TEST_REVIEW_EXIT"]))
elif args[:2] == ["validate", "testflight"]:
    sys.exit(int(os.environ["TEST_VALIDATION_EXIT"]))
elif args[:3] != ["builds", "test-notes", "update"] and args[:2] != ["builds", "add-groups"]:
    print("Unexpected ASC command: " + repr(args), file=sys.stderr)
    sys.exit(99)
''')
            for executable in bin_dir.iterdir():
                executable.chmod(0o755)
            summary = root / "summary.md"
            calls_path = root / "calls.jsonl"
            env = {
                **os.environ,
                "PATH": f'{bin_dir}:{os.environ["PATH"]}',
                "BUILD_DIR": str(build_dir),
                "BUILD_METADATA_PATH": str(build_dir / "testflight-build.env"),
                "SCHEME": "AgentBuddy",
                "TESTFLIGHT_SKIP_BUILD": "1",
                "TESTFLIGHT_SKIP_UPLOAD": "0",
                "TESTFLIGHT_AUTO_BUMP_VERSION": "0",
                "PROJECT_VERSION_BUMP_REQUIRED": "0",
                "ASSIGN_BETA_GROUP": "1",
                "AUTO_ASSIGN_ENCRYPTION_DECLARATION": "1",
                "EXPORT_SIGNING_STYLE": "automatic",
                "WHAT_TO_TEST": "Test release changes",
                "INTERNAL_BETA_GROUP_NAME": "Internal Testers",
                "BETA_GROUP_NAMES": groups,
                "SUBMIT_BETA_REVIEW": submit_review,
                "GITHUB_ACTIONS": "true",
                "GITHUB_STEP_SUMMARY": str(summary),
                "TEST_ASC_CALLS": str(calls_path),
                "TEST_REVIEW_ERROR": review_error,
                "TEST_REVIEW_EXIT": str(review_exit),
                "TEST_VALIDATION_EXIT": str(validation_exit),
            }
            result = subprocess.run(
                ["bash", str(scripts / "testflight-upload.sh")],
                env=env, capture_output=True, text=True,
            )
            calls = [json.loads(line) for line in calls_path.read_text().splitlines()]
            return result, calls, summary.read_text() if summary.exists() else ""

    def test_successful_review_submission_completes_validation(self):
        result, calls, summary = self.run_upload()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("Beta review:  submitted", result.stdout)
        self.assertEqual(calls[-1][:2], ["validate", "testflight"])
        self.assertEqual(summary, "")

    def test_known_review_conflict_reports_deferred_after_successful_upload(self):
        result, calls, summary = self.run_upload(review_error=REVIEW_CONFLICT, review_exit=32)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(calls[0][:2], ["builds", "upload"])
        self.assertEqual(calls[-1][:2], ["validate", "testflight"])
        self.assertIn("--strict", calls[-1])
        self.assertIn("::warning title=TestFlight review deferred::", result.stdout)
        self.assertIn("Beta review:  deferred", result.stdout)
        self.assertIn("has not been submitted for Beta App Review", result.stdout)
        self.assertIn("external testing is pending", summary)
        self.assertIn("asc testflight review submit --build-id uploaded-build --confirm", summary)

    def test_other_review_errors_keep_the_original_exit_code(self):
        for exit_code in (7, 32):
            with self.subTest(exit_code=exit_code):
                message = "Error: testflight review submit: missing required review information"
                result, calls, summary = self.run_upload(review_error=message, review_exit=exit_code)
                self.assertEqual(result.returncode, exit_code)
                self.assertIn(message, result.stderr)
                self.assertEqual(calls[-1][:3], ["testflight", "review", "submit"])
                self.assertNotIn("TestFlight upload complete", result.stdout)
                self.assertEqual(summary, "")

    def test_readiness_error_still_fails_after_review_conflict(self):
        result, calls, summary = self.run_upload(
            review_error=REVIEW_CONFLICT, review_exit=32, validation_exit=6,
        )
        self.assertEqual(result.returncode, 6)
        self.assertEqual(calls[-1][:2], ["validate", "testflight"])
        self.assertNotIn("TestFlight upload complete", result.stdout)
        self.assertEqual(summary, "")

    def test_internal_only_or_disabled_review_is_not_submitted(self):
        for options in ({"groups": "Internal Testers"}, {"submit_review": "0"}):
            with self.subTest(options=options):
                result, calls, summary = self.run_upload(**options)
                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertFalse(any(call[:3] == ["testflight", "review", "submit"] for call in calls))
                self.assertIn("Beta review:  not requested", result.stdout)
                self.assertEqual(summary, "")


if __name__ == "__main__":
    unittest.main()
