#!/usr/bin/env python3
"""Loopback-only SSH UI fixture. No remote command is executed on this Mac.

Install asyncssh in an isolated work-directory venv. Start with --key-file in
the work directory, then use `hdc rport tcp:2222 tcp:2222`. Synthetic login:
harmony / AgentBuddyFixtureOnly. Remove the forwarding and key after testing.
"""
import argparse
import asyncio
import os
from pathlib import Path

import asyncssh


class FixtureServer(asyncssh.SSHServer):
    def begin_auth(self, username):
        return True

    def password_auth_supported(self):
        return True

    def validate_password(self, username, password):
        accepted = username == "harmony" and password == "AgentBuddyFixtureOnly"
        print("authentication:", "accepted" if accepted else "rejected", flush=True)
        return accepted


async def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--key-file", type=Path, required=True)
    parser.add_argument("--port", type=int, default=2222)
    args = parser.parse_args()
    if not args.key_file.exists():
        key = asyncssh.generate_private_key("ssh-ed25519")
        descriptor = os.open(args.key_file, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
        with os.fdopen(descriptor, "wb") as output:
            output.write(key.export_private_key())
    key = asyncssh.read_private_key(args.key_file)

    async def process(session):
        command = session.command or ""
        if command == "echo %OS%":
            session.stdout.write("%OS%\n")
        elif command == "echo $env:OS":
            session.stdout.write(":OS\n")
        elif "probe_one codex codex" in command:
            session.stdout.write("claude\t\npi\t\nopencode\t\ncodex\t\n")
            print("agent probe: all CLIs absent (fixture)", flush=True)
        else:
            # Includes wake-MAC probes. Intentionally neither execute nor log
            # received commands, which may include private authentication data.
            print("unsupported probe: returned empty", flush=True)
        session.exit(0)

    server = await asyncssh.create_server(FixtureServer, "127.0.0.1", args.port,
                                         server_host_keys=[key], process_factory=process)
    print("SSH fixture ready:", args.port, key.get_fingerprint(), flush=True)
    await server.wait_closed()


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        pass
