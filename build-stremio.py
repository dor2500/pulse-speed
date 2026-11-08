#!/usr/bin/env python3
"""
Pulse Speed Stremio Addon Packager
Creates Pulse-v1.5.0-stremio-addon.zip ready to run locally or deploy to Beamup / Render / Vercel.
"""
import os
import zipfile

def build_stremio_addon(stremio_dir, src_dir, dist_dir, version="1.5.1"):
    output_path = os.path.join(dist_dir, f"Pulse-v1.5.1-stremio-addon.zip")
    print(f"Building Stremio Addon package: {output_path}")

    with zipfile.ZipFile(output_path, 'w', zipfile.ZIP_DEFLATED) as zf:
        # Add stremio files
        for f in os.listdir(stremio_dir):
            p = os.path.join(stremio_dir, f)
            if os.path.isfile(p):
                zf.write(p, arcname=f)

        # Add web app assets into public/
        for root, dirs, files in os.walk(src_dir):
            for file in files:
                abs_p = os.path.join(root, file)
                rel_p = os.path.relpath(abs_p, src_dir)
                zf.write(abs_p, arcname=f"public/{rel_p}")

    print(f"Successfully generated {output_path} ({os.path.getsize(output_path)/1024:.1f} KB)")

if __name__ == '__main__':
    base = os.path.dirname(os.path.abspath(__file__))
    stremio = os.path.join(base, 'multiplatform', 'stremio')
    src = os.path.join(base, 'multiplatform', 'src')
    dist = os.path.join(base, 'dist')
    os.makedirs(dist, exist_ok=True)
    build_stremio_addon(stremio, src, dist)

