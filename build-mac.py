#!/usr/bin/env python3
"""
Pulse Speed macOS App Bundle Packager
Creates Pulse.app and packages it as Pulse-v1.5.0-mac.zip
"""
import os
import shutil
import zipfile

def build_mac_app(src_dir, dist_dir, version="1.5.1"):
    app_name = "Pulse.app"
    app_dir = os.path.join(dist_dir, app_name)
    contents_dir = os.path.join(app_dir, "Contents")
    macos_dir = os.path.join(contents_dir, "MacOS")
    resources_dir = os.path.join(contents_dir, "Resources")
    web_dir = os.path.join(resources_dir, "app")

    if os.path.exists(app_dir):
        shutil.rmtree(app_dir)

    os.makedirs(macos_dir, exist_ok=True)
    os.makedirs(web_dir, exist_ok=True)

    # 1. Info.plist
    info_plist = f"""<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>CFBundleExecutable</key>
    <string>Pulse</string>
    <key>CFBundleIconFile</key>
    <string>icon.png</string>
    <key>CFBundleIdentifier</key>
    <string>com.dorp.pulse</string>
    <key>CFBundleName</key>
    <string>Pulse</string>
    <key>CFBundlePackageType</key>
    <string>APPL</string>
    <key>CFBundleShortVersionString</key>
    <string>{version}</string>
    <key>CFBundleVersion</key>
    <string>{version}</string>
    <key>LSMinimumSystemVersion</key>
    <string>10.13</string>
    <key>NSHighResolutionCapable</key>
    <true/>
</dict>
</plist>
"""
    with open(os.path.join(contents_dir, "Info.plist"), "w", encoding="utf-8") as f:
        f.write(info_plist)

    # 2. Executable launcher script
    launcher_script = """#!/bin/bash
DIR="$(cd "$(dirname "$0")/../Resources/app" && pwd)"
open "$DIR/index.html"
"""
    launcher_path = os.path.join(macos_dir, "Pulse")
    with open(launcher_path, "w", encoding="utf-8", newline="\n") as f:
        f.write(launcher_script)

    # 3. Copy web assets to Resources/app and Resources/icon.png
    for item in os.listdir(src_dir):
        s = os.path.join(src_dir, item)
        d = os.path.join(web_dir, item)
        if os.path.isfile(s):
            shutil.copy2(s, d)

    icon_src = os.path.join(src_dir, "icon.png")
    if os.path.exists(icon_src):
        shutil.copy2(icon_src, os.path.join(resources_dir, "icon.png"))

    # 4. Zip the .app for macOS distribution
    zip_path = os.path.join(dist_dir, f"Pulse-v1.5.1-mac.zip")
    with zipfile.ZipFile(zip_path, 'w', zipfile.ZIP_DEFLATED) as zipf:
        for root, dirs, files in os.walk(app_dir):
            for file in files:
                abs_f = os.path.join(root, file)
                rel_f = os.path.relpath(abs_f, dist_dir)
                zipinfo = zipfile.ZipInfo(rel_f)
                # Give executable permissions to MacOS/Pulse
                if rel_f.endswith("Pulse"):
                    zipinfo.external_attr = 0o755 << 16
                else:
                    zipinfo.external_attr = 0o644 << 16
                with open(abs_f, 'rb') as f_in:
                    zipf.writestr(zipinfo, f_in.read())

    print(f"Successfully generated macOS bundle: {zip_path}")

if __name__ == "__main__":
    base_dir = os.path.dirname(os.path.abspath(__file__))
    src_dir = os.path.join(base_dir, "multiplatform", "src")
    dist_dir = os.path.join(base_dir, "dist")
    build_mac_app(src_dir, dist_dir)

