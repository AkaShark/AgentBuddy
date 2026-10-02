"""Play preflight must never upload/commit and must discard its temporary edit."""
import importlib.util
from io import BytesIO
from pathlib import Path
import unittest
from unittest.mock import patch
from urllib.error import HTTPError

spec = importlib.util.spec_from_file_location(
    "play_preflight", Path(__file__).resolve().parents[1] / "play-preflight.py")
preflight = importlib.util.module_from_spec(spec)
spec.loader.exec_module(preflight)


class PlayPreflightTests(unittest.TestCase):
    def test_success_discards_edit_without_committing(self):
        with patch.object(preflight, "api_request", side_effect=[
            {"id": "test-edit"}, {"tracks": [{"track": "internal"}]}, {},
        ]) as request:
            preflight.check_access("test-token", "com.example.app", "internal")
        self.assertEqual([call.args[1] for call in request.call_args_list], ["POST", "GET", "DELETE"])
        self.assertTrue(request.call_args_list[-1].args[2].endswith("/edits/test-edit"))

    def test_track_failure_still_discards_edit(self):
        with patch.object(preflight, "api_request", side_effect=[
            {"id": "test-edit"}, RuntimeError("permission denied"), {},
        ]) as request:
            with self.assertRaisesRegex(RuntimeError, "permission denied"):
                preflight.check_access("test-token", "com.example.app", "internal")
        self.assertEqual(request.call_args.args[1], "DELETE")

    def test_missing_track_fails_and_discards_edit(self):
        with patch.object(preflight, "api_request", side_effect=[
            {"id": "test-edit"}, {"tracks": []}, {},
        ]) as request:
            with self.assertRaisesRegex(RuntimeError, "Create it in Play Console"):
                preflight.check_access("test-token", "com.example.app", "internal")
        self.assertEqual(request.call_args.args[1], "DELETE")

    def test_creation_failure_does_not_delete_other_edits(self):
        with patch.object(preflight, "api_request", side_effect=RuntimeError("missing app")) as request:
            with self.assertRaisesRegex(RuntimeError, "missing app"):
                preflight.check_access("test-token", "com.example.app", "internal")
        self.assertEqual(request.call_count, 1)

    def test_http_errors_give_setup_hint_without_response_body(self):
        for code, hint in [(401, "key"), (403, "app access"), (404, "first signed AAB")]:
            with self.subTest(code=code), patch.object(preflight.urllib.request, "urlopen", side_effect=HTTPError(
                "https://example.com", code, "error", {}, BytesIO(b"sensitive-response"),
            )):
                with self.assertRaisesRegex(RuntimeError, hint) as raised:
                    preflight.api_request("test-token", "POST", "https://example.com")
                self.assertNotIn("sensitive-response", str(raised.exception))
                self.assertNotIn("test-token", str(raised.exception))


if __name__ == "__main__":
    unittest.main()
