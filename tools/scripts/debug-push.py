#!/usr/bin/env python3
"""Send one test push through the Worker's admin-only POST /debug/push.

Secrets never go on the command line:
  * the admin token is read from a 0600 file (default
    ~/.config/agentbuddy/debug-push-admin-token) or from stdin;
  * the device token is read from a 0600 file or from stdin.
Only one of them can come from stdin per run.

Without --send the script only prints the request it would make, with the
device token and Authorization header redacted. A 200 response with
"accepted": true means APNs/FCM accepted the push, not that the phone showed it.

Examples (placeholders, never real tokens):
  # dry run, iOS sandbox alert, device token from a private file
  tools/scripts/debug-push.py --platform ios --env sandbox --mode alert \
      --device-token-file ~/.config/agentbuddy/ios-device-token

  # actually send an Android background (data) push, device token from stdin
  pbpaste | tools/scripts/debug-push.py --platform android --mode background \
      --device-token-stdin --send
"""

from __future__ import annotations

import argparse
import getpass
import hashlib
import json
import os
import stat
import sys
import urllib.error
import urllib.parse
import urllib.request

DEFAULT_WORKER_URL = "https://agentbuddy-push-proxy.aaksharker.workers.dev"
DEFAULT_ADMIN_TOKEN_FILE = "~/.config/agentbuddy/debug-push-admin-token"


def fail(message: str) -> "NoReturn":  # type: ignore[name-defined]
    print(f"error: {message}", file=sys.stderr)
    sys.exit(2)


def read_private_file(path: str, label: str) -> str:
    expanded = os.path.expanduser(path)
    try:
        fd = os.open(expanded, os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0))
    except FileNotFoundError:
        fail(f"{label} file not found: {path}")
    except OSError as error:
        fail(f"cannot open {label} file {path} (symlinks are refused): {error.strerror}")
    with os.fdopen(fd, encoding="utf-8") as handle:
        info = os.fstat(handle.fileno())
        if info.st_uid != os.getuid():
            fail(f"{label} file {path} is not owned by the current user")
        if info.st_mode & (stat.S_IRWXG | stat.S_IRWXO):
            fail(f"{label} file {path} is readable by group/others; run: chmod 600 {path}")
        value = handle.read().strip()
    if not value:
        fail(f"{label} file {path} is empty")
    return value


def read_stdin(label: str) -> str:
    if sys.stdin.isatty():
        value = getpass.getpass(f"{label}: ").strip()
    else:
        value = sys.stdin.read().strip()
    if not value:
        fail(f"no {label} on stdin")
    return value


class _NoRedirect(urllib.request.HTTPRedirectHandler):
    """Never follow redirects: urllib would resend the Authorization header."""

    def redirect_request(self, *args, **kwargs):  # noqa: D401 - urllib hook
        return None


def require_https(url: str) -> None:
    parts = urllib.parse.urlsplit(url)
    if parts.scheme != "https" and parts.hostname not in ("localhost", "127.0.0.1", "::1"):
        fail(f"worker URL must use https (got {parts.scheme}://{parts.hostname})")


def fingerprint(secret: str) -> str:
    return f"sha256:{hashlib.sha256(secret.encode()).hexdigest()[:8]} (len {len(secret)})"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--platform", choices=["ios", "android"], required=True)
    parser.add_argument("--env", choices=["sandbox", "production"], help="APNs environment (required for iOS)")
    parser.add_argument("--mode", choices=["alert", "background"], default="alert")
    parser.add_argument("--title", default="AgentBuddy 推送测试")
    parser.add_argument("--body", default="这是一条测试通知")
    parser.add_argument("--host-id", help="64-hex host id, builds serverId alleycat:<host-id> for tap routing")
    parser.add_argument("--thread-id")
    parser.add_argument("--turn-id")
    token_source = parser.add_mutually_exclusive_group(required=True)
    token_source.add_argument("--device-token-file", help="0600 file containing the device push token")
    token_source.add_argument("--device-token-stdin", action="store_true", help="read the device token from stdin")
    parser.add_argument("--admin-token-file", default=DEFAULT_ADMIN_TOKEN_FILE,
                        help=f"0600 file with DEBUG_PUSH_ADMIN_TOKEN (default {DEFAULT_ADMIN_TOKEN_FILE})")
    parser.add_argument("--admin-token-stdin", action="store_true", help="read the admin token from stdin instead")
    parser.add_argument("--worker-url", default=os.environ.get("AGENTBUDDY_PUSH_WORKER_URL", DEFAULT_WORKER_URL))
    parser.add_argument("--send", action="store_true", help="actually send; without it this is a dry run")
    args = parser.parse_args()

    if args.platform == "ios" and not args.env:
        fail("--env is required for iOS (sandbox for development builds, production for TestFlight/App Store)")
    if args.platform == "android" and args.env:
        fail("--env only applies to iOS")
    if args.device_token_stdin and args.admin_token_stdin:
        fail("only one secret can be read from stdin per run")

    device_token = read_stdin("device push token") if args.device_token_stdin else read_private_file(
        args.device_token_file, "device token")
    admin_token = read_stdin("admin token") if args.admin_token_stdin else read_private_file(
        args.admin_token_file, "admin token")

    payload: dict[str, object] = {"platform": args.platform, "pushToken": device_token, "mode": args.mode}
    if args.env:
        payload["apnsEnvironment"] = args.env
    if args.mode == "alert":
        payload["title"] = args.title
        payload["body"] = args.body
    for key, value in (("hostId", args.host_id), ("threadId", args.thread_id), ("turnId", args.turn_id)):
        if value:
            payload[key] = value

    url = args.worker_url.rstrip("/") + "/debug/push"
    require_https(url)
    redacted = dict(payload, pushToken=fingerprint(device_token))
    print(f"POST {url}")
    print(f"Authorization: Bearer <admin token {fingerprint(admin_token)}>")
    print(json.dumps(redacted, ensure_ascii=False, indent=2))
    if not args.send:
        print("dry run: nothing sent (pass --send to send exactly one push)")
        return 0

    request = urllib.request.Request(
        url,
        data=json.dumps(payload).encode(),
        method="POST",
        # Cloudflare's browser integrity check rejects urllib's default
        # User-Agent with error 1010, so identify the tool explicitly.
        headers={
            "Content-Type": "application/json",
            "Authorization": f"Bearer {admin_token}",
            "User-Agent": "agentbuddy-debug-push/1",
        },
    )
    try:
        with urllib.request.build_opener(_NoRedirect).open(request, timeout=20) as response:
            status, text = response.status, response.read().decode()
    except urllib.error.HTTPError as error:
        status, text = error.code, error.read().decode()
    except urllib.error.URLError as error:
        fail(f"could not reach {url}: {error.reason}")

    print(f"HTTP {status}")
    try:
        body = json.loads(text)
        print(json.dumps(body, ensure_ascii=False, indent=2))
    except ValueError:
        print(text[:500])
        return 1
    if status == 200 and body.get("accepted"):
        print("provider accepted the push (this does not prove the phone displayed it)")
        return 0
    return 1


if __name__ == "__main__":
    sys.exit(main())
