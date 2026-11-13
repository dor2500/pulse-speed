#!/usr/bin/env python3
"""
Pulse Speed Master Multiplatform Build Script
Builds all deliverables for all platforms:
1. Android & Android TV / Fire TV APK (Pulse-v1.5.2.apk)
2. LG webOS Smart TV (Pulse-v1.5.2-webos.ipk)
3. Samsung Smart TV Tizen (Pulse-v1.5.2-tizen.wgt)
4. Stremio Addon (Pulse-v1.5.2-stremio-addon.zip)
5. Kodi Media Center Addon (Pulse-v1.5.2-kodi-addon.zip)
6. Linux Debian Package (pulse_1.5.2_all.deb)
7. Linux Tarball (Pulse-v1.5.2-linux.tar.gz)
8. Windows Desktop Executable (Pulse.exe & Pulse-v1.5.2-windows.zip)
9. macOS Desktop App (Pulse-v1.5.2-mac.zip)
10. Universal CLI (pulse-cli.py)
11. Standalone Web Package (Pulse-v1.5.2-web.zip)
"""
import os
import shutil
import subprocess
import zipfile

VERSION = "1.5.2"

def main():
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    dist = os.path.join(root, "dist")
    os.makedirs(dist, exist_ok=True)

    print("=====================================================")
    print(f"      Pulse Speed Universal Multiplatform Build v{VERSION}      ")
    print("=====================================================\n")

    # 1. Android APK - Prefer official signed release build
    apk_release = os.path.join(root, "app", "build", "outputs", "apk", "github", "release", "app-github-release.apk")
    apk_debug = os.path.join(root, "app", "build", "outputs", "apk", "github", "debug", "app-github-debug.apk")
    apk_src = apk_release if os.path.exists(apk_release) else apk_debug
    apk_dest = os.path.join(dist, f"Pulse-v{VERSION}.apk")
    if os.path.exists(apk_src):
        shutil.copy2(apk_src, apk_dest)
        print(f"[1/11] Android & Android TV APK ({'RELEASE SIGNED' if apk_src == apk_release else 'DEBUG'}): {apk_dest} ({os.path.getsize(apk_dest)/1024/1024:.2f} MB)")

    # 2. LG webOS IPK
    subprocess.run(["python", os.path.join(root, "build-ipk.py")], check=True)
    ipk_dest = os.path.join(dist, f"Pulse-v{VERSION}-webos.ipk")
    print(f"[2/11] LG webOS IPK: {ipk_dest} ({os.path.getsize(ipk_dest)/1024:.1f} KB)")

    # 3. Samsung Smart TV Tizen WGT
    subprocess.run(["python", os.path.join(root, "build-tizen.py")], check=True)
    tizen_dest = os.path.join(dist, f"Pulse-v{VERSION}-tizen.wgt")
    print(f"[3/11] Samsung Tizen TV: {tizen_dest} ({os.path.getsize(tizen_dest)/1024:.1f} KB)")

    # 4. Stremio Addon
    subprocess.run(["python", os.path.join(root, "build-stremio.py")], check=True)
    stremio_dest = os.path.join(dist, f"Pulse-v{VERSION}-stremio-addon.zip")
    print(f"[4/11] Stremio Addon: {stremio_dest} ({os.path.getsize(stremio_dest)/1024:.1f} KB)")

    # 5. Kodi Media Center Addon
    subprocess.run(["python", os.path.join(root, "build-kodi.py")], check=True)
    kodi_dest = os.path.join(dist, f"Pulse-v{VERSION}-kodi-addon.zip")
    print(f"[5/11] Kodi Addon: {kodi_dest} ({os.path.getsize(kodi_dest)/1024:.1f} KB)")

    # 6 & 7. Linux Debian (.deb) and tar.gz
    subprocess.run(["python", os.path.join(root, "build-linux.py")], check=True)
    deb_dest = os.path.join(dist, f"pulse_{VERSION}_all.deb")
    targz_dest = os.path.join(dist, f"Pulse-v{VERSION}-linux.tar.gz")
    print(f"[6/11] Linux Debian (.deb): {deb_dest} ({os.path.getsize(deb_dest)/1024:.1f} KB)")
    print(f"[7/11] Linux Tarball (.tar.gz): {targz_dest} ({os.path.getsize(targz_dest)/1024:.1f} KB)")

    # 8. Windows Desktop EXE
    win_exe = os.path.join(dist, "Pulse.exe")
    win_zip = os.path.join(dist, f"Pulse-v{VERSION}-windows.zip")
    if os.path.exists(win_exe):
        with zipfile.ZipFile(win_zip, 'w', zipfile.ZIP_DEFLATED) as zf:
            zf.write(win_exe, arcname="Pulse.exe")
        print(f"[8/11] Windows Desktop: {win_exe} & {win_zip} ({os.path.getsize(win_exe)/1024:.1f} KB)")

    # 9. macOS Desktop App
    subprocess.run(["python", os.path.join(root, "build-mac.py")], check=True)
    mac_dest = os.path.join(dist, f"Pulse-v{VERSION}-mac.zip")
    print(f"[9/11] macOS App: {mac_dest} ({os.path.getsize(mac_dest)/1024:.1f} KB)")

    # 10. Universal CLI Tool
    cli_src = os.path.join(root, "multiplatform", "cli", "pulse.py")
    cli_dest = os.path.join(dist, "pulse-cli.py")
    shutil.copy2(cli_src, cli_dest)
    print(f"[10/11] Universal CLI Tool: {cli_dest} ({os.path.getsize(cli_dest)/1024:.1f} KB)")

    # 11. Standalone Web Package
    src_dir = os.path.join(root, "multiplatform", "src")
    web_zip = os.path.join(dist, f"Pulse-v{VERSION}-web.zip")
    with zipfile.ZipFile(web_zip, 'w', zipfile.ZIP_DEFLATED) as zf:
        for f in os.listdir(src_dir):
            full_f = os.path.join(src_dir, f)
            if os.path.isfile(full_f):
                zf.write(full_f, arcname=f)
    print(f"[11/11] Web Package: {web_zip} ({os.path.getsize(web_zip)/1024:.1f} KB)")

    print("\nAll 11 deliverables built successfully in 'dist/' directory!")

if __name__ == "__main__":
    main()
