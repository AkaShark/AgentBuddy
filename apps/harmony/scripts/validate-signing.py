#!/usr/bin/env python3
"""Check signing identity before packaging; never print signing secrets."""
import argparse
import json
import os
from pathlib import Path
import subprocess


def validate(config: Path, bundle: str, release: bool) -> None:
    if not config.is_file():
        raise ValueError(f"Signing configuration is missing: {config}")
    material = json.loads(config.read_text())["material"]
    for field in ("storeFile", "certpath", "profile"):
        if not Path(material[field]).is_file():
            raise ValueError(f"Signing material is missing: {field}")
    # CMS extraction verifies the embedded signature, not Huawei's trust chain.
    # Hvigor and the platform perform the actual package signing validation.
    result = subprocess.run(
        ["openssl", "cms", "-verify", "-noverify", "-inform", "DER", "-in", material["profile"]],
        capture_output=True, check=True,
    )
    profile = json.loads(result.stdout)
    if profile.get("bundle-info", {}).get("bundle-name") != bundle:
        raise ValueError("Profile bundle name differs from AppScope/app.json5; obtain a matching Profile")
    if release and (profile.get("type") != "release" or profile.get("app-distribution-type") != "app_gallery"):
        raise ValueError("Store packaging requires a release Profile with app_gallery distribution")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--release", action="store_true")
    args = parser.parse_args()
    root = Path(os.environ.get("HARMONY_WORK_ROOT", Path.home() / ".agentBuddy/harmony"))
    config = Path(os.environ.get("HARMONY_SIGNING_CONFIG", Path(os.environ.get("HARMONY_SIGNING_HOME", Path.home() / ".agentBuddy/signing/harmony")) / "signing-config.json"))
    bundle = json.loads((Path(__file__).resolve().parents[1] / "AppScope/app.json5").read_text())["app"]["bundleName"]
    try:
        validate(config, bundle, args.release)
    except (ValueError, KeyError, OSError, subprocess.CalledProcessError) as error:
        # Do not include openssl output or the signing configuration in diagnostics.
        message = str(error) if isinstance(error, ValueError) and not isinstance(error, json.JSONDecodeError) else "Unable to validate signing material"
        raise SystemExit(message) from None
    print("Signing Profile matches the requested package identity and distribution mode")
