#!/usr/bin/env python3
"""Small HDC UI test driver. Targets only the AgentBuddy window; never logs pair tokens."""
import json
import os
import re
import shlex
import subprocess
import sys
import time

bundle = "com.akashark.agentbuddy.mobile"
hdc = os.environ.get("DEVECO_ROOT", "/Applications/DevEco-Studio.app/Contents") + "/sdk/default/openharmony/toolchains/hdc"
device = os.environ.get("HARMONY_DEVICE_SERIAL")
if not device:
    devices = subprocess.check_output([hdc, "list", "targets"], text=True).splitlines()
    if len(devices) != 1 or devices[0] == "[Empty]":
        raise SystemExit("Set HARMONY_DEVICE_SERIAL to one authorized device.")
    device = devices[0].strip()

def shell(*args):
    return subprocess.check_output([hdc, "-t", device, "shell", shlex.join(args)], text=True)

def nodes():
    layout = "/data/local/tmp/agentbuddy-ui.json"
    shell("uitest", "dumpLayout", "-b", bundle, "-p", layout)
    tree = json.loads(shell("cat", layout))
    shell("rm", "-f", layout)
    result = []
    def visit(node):
        result.append(node.get("attributes", {}))
        for child in node.get("children", []):
            visit(child)
    visit(tree)
    return result

def locate(label, editable=False):
    # RPC-backed navigation can finish after uiInput click returns. Wait for the
    # specific enabled control instead of treating a transient old layout as failure.
    deadline = time.monotonic() + 8
    while True:
        matches = [node for node in nodes() if
                   (node.get("hint") == label if editable else node.get("text") == label)
                   and node.get("clickable") == "true" and node.get("enabled") == "true"]
        if matches or time.monotonic() >= deadline:
            break
        time.sleep(0.15)
    if len(matches) != 1:
        raise SystemExit(f"Expected one enabled control for {label!r}, found {len(matches)}")
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", matches[0]["bounds"]))
    return str((x1 + x2) // 2), str((y1 + y2) // 2)

command = sys.argv[1] if len(sys.argv) > 1 else "dump"
if command == "dump":
    for node in nodes():
        if node.get("text") or node.get("hint"):
            text = node.get("text", "")
            if '"token"' in text or any(word in node.get("hint", "") for word in ["密码", "口令"]):
                text = "<credential redacted>"
            print(json.dumps({"text": text[:180], "hint": node.get("hint"), "type": node.get("type"),
                              "enabled": node.get("enabled"), "bounds": node.get("bounds")}, ensure_ascii=False))
elif command == "tap":
    print(shell("uitest", "uiInput", "click", *locate(sys.argv[2])))
elif command in {"input", "replace", "pair-input"}:
    if command == "pair-input":
        hint = "或粘贴配对内容"
        text = subprocess.check_output(["/Applications/AgentBuddy.app/Contents/MacOS/agentbuddy", "pair"], text=True).strip()
        json.loads(text)
    else:
        hint, text = sys.argv[2:4]
    if command == "replace":
        shell("uitest", "uiInput", "click", *locate(hint, editable=True))
        shell("uitest", "uiInput", "keyEvent", "2072", "2017")  # Ctrl+A
        shell("uitest", "uiInput", "keyEvent", "2055")  # Delete selected text
        if text:
            shell("uitest", "uiInput", "text", text)
    else:
        shell("uitest", "uiInput", "inputText", *locate(hint, editable=True), text)
    print("Input entered.")
elif command == "back":
    print(shell("uitest", "uiInput", "keyEvent", "Back"))
elif command == "wait-gone":
    deadline = time.monotonic() + 8
    while any(node.get("text") == sys.argv[2] for node in nodes()):
        if time.monotonic() >= deadline:
            raise SystemExit(f"Control still present after waiting: {sys.argv[2]!r}")
        time.sleep(0.15)
    print("Control dismissed.")
else:
    raise SystemExit("Usage: device-ui.py [dump|tap LABEL|input HINT TEXT|replace HINT TEXT|pair-input|back|wait-gone LABEL]")
