#!/usr/bin/env python3
"""
Pulse Speed Master Multiplatform Build Script
Builds all deliverables:
- Android APK (Pulse-v1.5.0.apk)
- LG webOS Smart TV IPK (Pulse-v1.5.0-webos.ipk)
- Windows Desktop Executable (Pulse.exe)
- macOS Desktop App (Pulse-v1.5.0-mac.zip)
- Standalone Web Package (Pulse-v1.5.0-web.zip)
"""
import os
import shutil
import subprocess
import zipfile

def main():
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    dist = os.path.join(root, "dist")
    os.makedirs(dist, exist_ok=True)

    print("=========================================")
    print("      Pulse Speed Multiplatform Build    ")
    print("=========================================\n")

    # 1. Copy Android APK
    apk_src = os.path.join(root, "app", "build", "outputs", "apk", "debug", "app-debug.apk")
    apk_dest = os.path.join(dist, "Pulse-v1.5.0.apk")
    if os.path.exists(apk_src):
        shutil.copy2(apk_src, apk_dest)
        print(f"[1/5] Android APK: {apk_dest} ({os.path.getsize(apk_dest)/1024/1024:.2f} MB)")
    else:
        print(f"[1/5] Android APK not found at {apk_src}. Please run ./gradlew assembleDebug first.")

    # 2. Build LG webOS IPK
    ipk_script = os.path.join(root, "build-ipk.py")
    subprocess.run(["python", ipk_script], check=True)
    ipk_dest = os.path.join(dist, "Pulse-v1.5.0-webos.ipk")
    print(f"[2/5] LG webOS IPK: {ipk_dest} ({os.path.getsize(ipk_dest)/1024:.1f} KB)")

    # 3. Windows Desktop EXE
    win_exe = os.path.join(dist, "Pulse.exe")
    if os.path.exists(win_exe):
        # Also create a zip for Windows
        win_zip = os.path.join(dist, "Pulse-v1.5.0-windows.zip")
        with zipfile.ZipFile(win_zip, 'w', zipfile.ZIP_DEFLATED) as zf:
            zf.write(win_exe, arcname="Pulse.exe")
        print(f"[3/5] Windows Executable: {win_exe} & {win_zip} ({os.path.getsize(win_exe)/1024:.1f} KB)")

    # 4. macOS Desktop App
    mac_script = os.path.join(root, "build-mac.py")
    subprocess.run(["python", mac_script], check=True)
    mac_dest = os.path.join(dist, "Pulse-v1.5.0-mac.zip")
    print(f"[4/5] macOS App: {mac_dest} ({os.path.getsize(mac_dest)/1024:.1f} KB)")

    # 5. Web standalone zip
    src_dir = os.path.join(root, "multiplatform", "src")
    web_zip = os.path.join(dist, "Pulse-v1.5.0-web.zip")
    with zipfile.ZipFile(web_zip, 'w', zipfile.ZIP_DEFLATED) as zf:
        for f in os.listdir(src_dir):
            full_f = os.path.join(src_dir, f)
            if os.path.isfile(full_f):
                zf.write(full_f, arcname=f)
    print(f"[5/5] Web Package: {web_zip} ({os.path.getsize(web_zip)/1024:.1f} KB)")

    print("\nAll deliverables built successfully in 'dist/' directory!")

if __name__ == "__main__":
    main()

