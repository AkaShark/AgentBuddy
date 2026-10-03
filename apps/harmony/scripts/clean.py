#!/usr/bin/env python3
"""Remove only known disposable Harmony build outputs; retain signing and deliverables."""
import os
import shutil
from pathlib import Path

root = Path(os.environ.get("AGENTBUDDY_HARMONY_HOME", Path.home() / ".agentBuddy/harmony")).resolve()
repo = Path(__file__).resolve().parents[3]
if root in {Path('/'), Path.home(), repo, Path.home() / 'Downloads'}:
    raise SystemExit("Refusing an unsafe Harmony work root")
if not (root / "artifacts/native/libcodex_mobile_client.so").is_file():
    raise SystemExit("No completed Harmony native artifact found; not cleaning an unknown directory")

targets = ["build/rust", "build/rust-host", "build/codex-tests", "build/tools",
           "build/bindgen", "build/vendor-tests", "build/project/entry/build",
           "build/project/generated/core/build", "build/project/.hvigor",
           "artifacts/entry-default-unsigned.hap", "artifacts/start-debug.png", "tmp",
           "tools/ssh-test-venv", "tools/codex-python-venv", "tools/python",
           "cache/pip", "cache/pycache", "cache/uv", "cache/ruff"]
for relative in targets:
    candidate = root / relative
    if candidate.is_symlink() or candidate.is_file():
        candidate.unlink()
    elif candidate.is_dir():
        shutil.rmtree(candidate)
    print(f"Cleaned {relative}")
