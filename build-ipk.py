#!/usr/bin/env python3
"""
Pulse Speed LG webOS IPK Packager
Builds a standard, valid LG webOS .ipk package without requiring webOS CLI.
"""
import io
import os
import tarfile
import time

def create_ar_header(filename, size, mtime=None, mode=0o100644, uid=0, gid=0):
    if mtime is None:
        mtime = int(time.time())
    # Format: filename (16), mtime (12), uid (6), gid (6), mode (8), size (10), magic (2)
    name = (filename.strip() + '/').ljust(16)[:16]
    mtime_str = str(mtime).ljust(12)[:12]
    uid_str = str(uid).ljust(6)[:6]
    gid_str = str(gid).ljust(6)[:6]
    mode_str = oct(mode)[2:].rjust(8)[:8]
    size_str = str(size).ljust(10)[:10]
    header = (name + mtime_str + uid_str + gid_str + mode_str + size_str + '`\n').encode('ascii')
    return header

def build_ipk(src_dir, webos_dir, output_path, version="1.5.0", app_id="com.dorp.pulse"):
    print(f"Building LG webOS IPK package: {output_path}")

    # 1. debian-binary
    debian_binary = b"2.0\n"

    # 2. control.tar.gz
    control_content = f"""Package: {app_id}
Version: {version}
Section: misc
Priority: optional
Architecture: all
Maintainer: Dor <pulse@speed.app>
Description: Pulse internet speed test for LG webOS TV
""".encode('utf-8')

    control_tar_io = io.BytesIO()
    with tarfile.open(fileobj=control_tar_io, mode='w:gz') as tar:
        ti = tarfile.TarInfo('./control')
        ti.size = len(control_content)
        ti.mtime = int(time.time())
        ti.mode = 0o644
        tar.addfile(ti, io.BytesIO(control_content))
    control_tar_gz = control_tar_io.getvalue()

    # 3. data.tar.gz
    data_tar_io = io.BytesIO()
    with tarfile.open(fileobj=data_tar_io, mode='w:gz') as tar:
        base_install_dir = f"./usr/palm/applications/{app_id}"

        # Add appinfo.json
        appinfo_path = os.path.join(webos_dir, 'appinfo.json')
        tar.add(appinfo_path, arcname=f"{base_install_dir}/appinfo.json")

        # Add all files from src_dir
        for root, dirs, files in os.walk(src_dir):
            for file in files:
                abs_path = os.path.join(root, file)
                rel_path = os.path.relpath(abs_path, src_dir).replace('\\', '/')
                tar.add(abs_path, arcname=f"{base_install_dir}/{rel_path}")

    data_tar_gz = data_tar_io.getvalue()

    # Combine into Debian ar package (.ipk)
    with open(output_path, 'wb') as f:
        # Magic
        f.write(b"!<arch>\n")

        # debian-binary
        f.write(create_ar_header('debian-binary', len(debian_binary)))
        f.write(debian_binary)
        if len(debian_binary) % 2 == 1:
            f.write(b"\n")

        # control.tar.gz
        f.write(create_ar_header('control.tar.gz', len(control_tar_gz)))
        f.write(control_tar_gz)
        if len(control_tar_gz) % 2 == 1:
            f.write(b"\n")

        # data.tar.gz
        f.write(create_ar_header('data.tar.gz', len(data_tar_gz)))
        f.write(data_tar_gz)
        if len(data_tar_gz) % 2 == 1:
            f.write(b"\n")

    size_kb = os.path.getsize(output_path) / 1024
    print(f"Successfully generated {output_path} ({size_kb:.1f} KB)")

if __name__ == '__main__':
    base_dir = os.path.dirname(os.path.abspath(__file__))
    src_dir = os.path.join(base_dir, 'multiplatform', 'src')
    webos_dir = os.path.join(base_dir, 'multiplatform', 'webos')
    out_dir = os.path.join(base_dir, 'dist')
    os.makedirs(out_dir, exist_ok=True)
    out_ipk = os.path.join(out_dir, 'Pulse-v1.5.0-webos.ipk')
    build_ipk(src_dir, webos_dir, out_ipk)

