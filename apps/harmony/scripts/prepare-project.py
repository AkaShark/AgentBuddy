#!/usr/bin/env python3
"""Stage a disposable DevEco project, resources and generated bindings outside Git."""
import json
import os
import shutil
from pathlib import Path

source = Path(__file__).resolve().parents[1]
repo = source.parents[1]
root = Path(os.environ.get("AGENTBUDDY_HARMONY_HOME", Path.home() / ".agentBuddy/harmony")).resolve()
project = root / "build/project"
deveco = Path(os.environ.get("DEVECO_ROOT", "/Applications/DevEco-Studio.app/Contents"))
project.mkdir(parents=True, exist_ok=True)
for item in source.iterdir():
    if item.name in {"scripts", "README.md", "docs", "tests", ".gitignore", "native-Cargo.lock"}:
        continue
    destination = project / item.name
    if item.is_dir():
        shutil.copytree(item, destination, dirs_exist_ok=True)
    else:
        shutil.copy2(item, destination)

generated = root / "build/generated/core"
if not generated.exists():
    raise SystemExit("Generate ArkTS bindings before building the app.")
shutil.copytree(generated, project / "generated/core", dirs_exist_ok=True)

# Brand and semantic colors share the Android/iOS source of truth.
brand = repo / "apps/android/app/src/main/res/drawable-nodpi/brand_logo.png"
for resources in [project / "AppScope/resources", project / "entry/src/main/resources"]:
    media = resources / "base/media"
    media.mkdir(parents=True, exist_ok=True)
    shutil.copy2(brand, media / "brand_logo.png")

roles = {
    "background": "editor.background", "surface": "panel.background", "text": "agentbuddy.textBody",
    "secondary": "agentbuddy.textSystem", "brand": "agentbuddy.brand", "on_brand": "agentbuddy.onBrand",
    "action": "button.background", "on_action": "agentbuddy.onAction", "link": "textLink.foreground", "border": "panel.border",
    "error": "agentbuddy.error", "error_surface": "agentbuddy.errorSurface",
}
for mode, folder in [("light", "base"), ("dark", "dark")]:
    theme = json.loads((repo / f"apps/ios/Sources/AgentBuddy/Resources/Themes/agentbuddy-mint-{mode}.json").read_text())["colors"]
    colors = [{"name": "buddy_" + role, "value": theme[key]} for role, key in roles.items()]
    colors.append({"name": "start_window_background", "value": theme["editor.background"]})
    output = project / f"entry/src/main/resources/{folder}/element/color.json"
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps({"color": colors}, ensure_ascii=False, indent=2) + "\n")

# Reuse installed build tools without installing a second global toolchain.
plugins = project / "node_modules/@ohos"
plugins.mkdir(parents=True, exist_ok=True)
for name in ["hvigor", "hvigor-ohos-plugin"]:
    link = plugins / name
    if not link.exists():
        link.symlink_to(deveco / "tools/hvigor" / name, target_is_directory=True)
(project / "local.properties").write_text(f"sdk.dir={deveco}/sdk\n")
(project / ".ohpmrc").write_text(f"cache={root}/cache/ohpm\n")

# Optional local signing configuration is supplied by DevEco, never stored in Git.
signing = Path(os.environ.get("HARMONY_SIGNING_CONFIG", Path(os.environ.get("HARMONY_SIGNING_HOME", Path.home() / ".agentBuddy/signing/harmony")) / "signing-config.json"))
if signing.exists():
    profile_path = project / "build-profile.json5"
    profile = json.loads(profile_path.read_text())
    profile["app"]["signingConfigs"] = [json.loads(signing.read_text())]
    profile["app"]["products"][0]["signingConfig"] = profile["app"]["signingConfigs"][0]["name"]
    profile_path.write_text(json.dumps(profile, indent=2) + "\n")

libs = project / "entry/libs/arm64-v8a"
libs.mkdir(parents=True, exist_ok=True)
for name in ["libcodex_mobile_client.so", "libagentbuddy_core.so"]:
    library = root / "artifacts/native" / name
    if not library.exists():
        raise SystemExit(f"Missing {name}: run make rust-harmony before packaging.")
    shutil.copy2(library, libs / name)
print(project)
