#!/usr/bin/env python3
"""
Pulse Speed Kodi Addon Packager
Creates Pulse-v1.5.0-kodi-addon.zip with plugin.program.pulsespeed root folder structure.
"""
import os
import shutil
import zipfile

def build_kodi_addon(kodi_dir, src_dir, dist_dir, version="1.5.1"):
    output_path = os.path.join(dist_dir, f"Pulse-v1.5.1-kodi-addon.zip")
    print(f"Building Kodi Addon package: {output_path}")

    addon_folder = "plugin.program.pulsespeed"

    with zipfile.ZipFile(output_path, 'w', zipfile.ZIP_DEFLATED) as zf:
        # addon.xml
        zf.write(os.path.join(kodi_dir, 'addon.xml'), arcname=f"{addon_folder}/addon.xml")
        # default.py
        zf.write(os.path.join(kodi_dir, 'default.py'), arcname=f"{addon_folder}/default.py")
        # icon.png
        icon_path = os.path.join(src_dir, 'icon-256.png')
        if os.path.exists(icon_path):
            zf.write(icon_path, arcname=f"{addon_folder}/icon.png")

    print(f"Successfully generated {output_path} ({os.path.getsize(output_path)/1024:.1f} KB)")

if __name__ == '__main__':
    base = os.path.dirname(os.path.abspath(__file__))
    kodi = os.path.join(base, 'multiplatform', 'kodi')
    src = os.path.join(base, 'multiplatform', 'src')
    dist = os.path.join(base, 'dist')
    os.makedirs(dist, exist_ok=True)
    build_kodi_addon(kodi, src, dist)

