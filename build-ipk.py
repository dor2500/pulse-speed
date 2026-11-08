#!/usr/bin/env python3
"""
Pulse Speed LG webOS IPK Packager
Uses official LG webOS ares-package tool to generate a 100% compliant .ipk package.
"""
import os
import shutil
import subprocess

def build_webos_ipk():
    base_dir = os.path.dirname(os.path.abspath(__file__))
    src_dir = os.path.join(base_dir, 'multiplatform', 'src')
    dist_dir = os.path.join(base_dir, 'dist')
    webos_dir = os.path.join(base_dir, 'multiplatform', 'webos')
    os.makedirs(dist_dir, exist_ok=True)

    # Copy clean appinfo.json into src
    shutil.copy2(os.path.join(webos_dir, 'appinfo.json'), os.path.join(src_dir, 'appinfo.json'))

    # Locate official ares-package
    ares_cmd = shutil.which("ares-package") or r"C:\Users\Dorp\AppData\Roaming\npm\ares-package.cmd"

    print(f"Building LG webOS IPK using official ares-package: {ares_cmd}")
    subprocess.run([ares_cmd, src_dir, "-o", dist_dir, "--no-minify"], check=True)

    # Standardize output file name
    official_ipk = os.path.join(dist_dir, "com.dorp.pulse_1.5.2_all.ipk")
    target_ipk = os.path.join(dist_dir, "Pulse-v1.5.2-webos.ipk")
    if os.path.exists(official_ipk):
        shutil.copy2(official_ipk, target_ipk)
        print(f"Successfully generated: {target_ipk} ({os.path.getsize(target_ipk)/1024:.1f} KB)")

if __name__ == '__main__':
    build_webos_ipk()
