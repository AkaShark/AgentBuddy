#!/usr/bin/env python3
"""Check Play API access without uploading or committing a release (Python + openssl)."""

import argparse
import importlib.util
import json
import os
from pathlib import Path
import sys
import urllib.error
import urllib.parse
import urllib.request


def issue_token(path):
    # Share the existing Google service-account signer with store triage.
    source = Path(__file__).resolve().parents[3] / "tools/scripts/fetch-mobile-store-artifacts.py"
    spec = importlib.util.spec_from_file_location("store_artifacts", source)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module.issue_token(path, module.PLAY_PUBLISHER_SCOPE)


def api_request(token, method, url):
    request = urllib.request.Request(
        url, method=method, data=b"{}" if method == "POST" else None,
        headers={"Authorization": f"Bearer {token}", "Content-Type": "application/json"},
    )
    try:
        with urllib.request.urlopen(request, timeout=60) as response:
            body = response.read()
            return json.loads(body) if body else {}
    except urllib.error.HTTPError as exc:
        hints = {
            401: "Check that the service-account key is valid and enabled.",
            403: "Enable Google Play Android Developer API and grant this service account app access in Play Console (view app information and release to testing tracks).",
            404: "Create the app and manually upload its first signed AAB in Play Console; also check the package name and service-account app access.",
        }
        # Do not include credentials, bearer tokens, or the raw response in logs.
        raise RuntimeError(f"Play API {method} failed (HTTP {exc.code}). "
                           + hints.get(exc.code, "Check Play Console and retry.")) from None


def check_access(token, package, track):
    base = ("https://androidpublisher.googleapis.com/androidpublisher/v3/applications/"
            + urllib.parse.quote(package, safe="") + "/edits")
    edit = api_request(token, "POST", base)
    url = base + "/" + urllib.parse.quote(edit["id"], safe="")
    try:
        tracks = api_request(token, "GET", url + "/tracks").get("tracks", [])
        if track not in {item["track"] for item in tracks}:
            raise RuntimeError(f"Track '{track}' is missing. Create it in Play Console first.")
    finally:
        # Discard only the temporary edit created above; never commit it.
        api_request(token, "DELETE", url)
    print(f"Play API access OK: {package}, track={track}. No release uploaded or committed.")
    print("This checks edit/track access; publishing permissions and app declarations are checked by Play on upload.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--service-account", default=os.environ.get("LITTER_PLAY_SERVICE_ACCOUNT_JSON"))
    parser.add_argument("--package", default="com.akashark.agentbuddy.android")
    parser.add_argument("--track", default=os.environ.get("LITTER_PLAY_TRACK", "internal"))
    args = parser.parse_args()
    if not args.service_account or not Path(args.service_account).is_file():
        parser.error("Set LITTER_PLAY_SERVICE_ACCOUNT_JSON or --service-account to an existing JSON file.")
    try:
        token = issue_token(Path(args.service_account))
    except Exception:
        # Token exchange errors can contain credential material; keep CI logs safe.
        print("Google authentication failed. Check the service-account JSON/key and network access.", file=sys.stderr)
        return 1
    try:
        check_access(token, args.package, args.track)
    except (RuntimeError, urllib.error.URLError, KeyError, ValueError) as exc:
        print(f"Play preflight failed: {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
