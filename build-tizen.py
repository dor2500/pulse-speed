#!/usr/bin/env python3
"""
Pulse Speed Samsung Smart TV (Tizen) WGT Packager
Packages Pulse as a Samsung Tizen .wgt widget package for Samsung Smart TVs.
"""
import os
import shutil
import zipfile

def build_tizen_wgt(src_dir, tizen_dir, dist_dir, version="1.5.0"):
    output_path = os.path.join(dist_dir, f"Pulse-v1.5.0-tizen.wgt")
    print(f"Building Samsung Smart TV (Tizen) package: {output_path}")

    with zipfile.ZipFile(output_path, 'w', zipfile.ZIP_DEFLATED) as zf:
        # 1. Add config.xml
        config_path = os.path.join(tizen_dir, 'config.xml')
        zf.write(config_path, arcname='config.xml')

        # 2. Add all source files
        for root, dirs, files in os.walk(src_dir):
            for file in files:
                abs_p = os.path.join(root, file)
                rel_p = os.path.relpath(abs_p, src_dir)
                zf.write(abs_p, arcname=rel_p)

    print(f"Successfully generated {output_path} ({os.path.getsize(output_path)/1024:.1f} KB)")

if __name__ == '__main__':
    base = os.path.dirname(os.path.abspath(__file__))
    src = os.path.join(base, 'multiplatform', 'src')
    tizen = os.path.join(base, 'multiplatform', 'tizen')
    dist = os.path.join(base, 'dist')
    os.makedirs(dist, exist_ok=True)
    build_tizen_wgt(src, tizen, dist)

