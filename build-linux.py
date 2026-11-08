#!/usr/bin/env python3
"""
Pulse Speed Linux Packager
Builds:
1. pulse_1.5.0_all.deb (Debian/Ubuntu/Raspberry Pi package)
2. Pulse-v1.5.0-linux.tar.gz (Universal portable archive)
"""
import io
import os
import tarfile
import time

def create_ar_header(filename, size, mtime=None, mode=0o100644, uid=0, gid=0):
    if mtime is None:
        mtime = int(time.time())
    name = (filename.strip() + '/').ljust(16)[:16]
    mtime_str = str(mtime).ljust(12)[:12]
    uid_str = str(uid).ljust(6)[:6]
    gid_str = str(gid).ljust(6)[:6]
    mode_str = oct(mode)[2:].rjust(8)[:8]
    size_str = str(size).ljust(10)[:10]
    return (name + mtime_str + uid_str + gid_str + mode_str + size_str + '`\n').encode('ascii')

def build_linux_packages(src_dir, dist_dir, version="1.5.1"):
    # 1. Debian Package (.deb)
    deb_path = os.path.join(dist_dir, f"pulse_{version}_all.deb")
    print(f"Building Debian (.deb) package: {deb_path}")

    debian_binary = b"2.0\n"

    control_content = f"""Package: pulse-speed
Version: {version}
Section: net
Priority: optional
Architecture: all
Maintainer: Dor <pulse@speed.app>
Depends: python3 | nodejs | xdg-utils
Description: Pulse internet speed test with bufferbloat analysis
 Fast and beautiful internet speed test client measuring ping, jitter,
 download, upload and bufferbloat across Cloudflare, Ookla and Netflix.
""".encode('utf-8')

    control_tar_io = io.BytesIO()
    with tarfile.open(fileobj=control_tar_io, mode='w:gz') as tar:
        ti = tarfile.TarInfo('./control')
        ti.size = len(control_content)
        ti.mtime = int(time.time())
        ti.mode = 0o644
        tar.addfile(ti, io.BytesIO(control_content))
    control_tar_gz = control_tar_io.getvalue()

    desktop_entry = """[Desktop Entry]
Name=Pulse Speed
Comment=Internet speed test with bufferbloat analysis
Exec=/opt/pulse/pulse
Icon=/opt/pulse/icon-256.png
Terminal=false
Type=Application
Categories=Network;Utility;
""".encode('utf-8')

    launcher_script = """#!/bin/sh
DIR="/opt/pulse"
if which xdg-open > /dev/null; then
    xdg-open "$DIR/index.html"
elif which sensible-browser > /dev/null; then
    sensible-browser "$DIR/index.html"
else
    python3 -m http.server 7890 --directory "$DIR" &
    sleep 1
    xdg-open "http://localhost:7890" || sensible-browser "http://localhost:7890"
fi
""".encode('utf-8')

    data_tar_io = io.BytesIO()
    with tarfile.open(fileobj=data_tar_io, mode='w:gz') as tar:
        # Add app files to /opt/pulse/
        for root, dirs, files in os.walk(src_dir):
            for file in files:
                abs_p = os.path.join(root, file)
                rel_p = os.path.relpath(abs_p, src_dir).replace('\\', '/')
                tar.add(abs_p, arcname=f"./opt/pulse/{rel_p}")

        # Desktop entry
        ti_desk = tarfile.TarInfo('./usr/share/applications/pulse.desktop')
        ti_desk.size = len(desktop_entry)
        ti_desk.mtime = int(time.time())
        ti_desk.mode = 0o644
        tar.addfile(ti_desk, io.BytesIO(desktop_entry))

        # Launcher executable
        ti_launch = tarfile.TarInfo('./opt/pulse/pulse')
        ti_launch.size = len(launcher_script)
        ti_launch.mtime = int(time.time())
        ti_launch.mode = 0o755
        tar.addfile(ti_launch, io.BytesIO(launcher_script))

        # Symlink in /usr/bin/pulse
        ti_sym = tarfile.TarInfo('./usr/bin/pulse')
        ti_sym.type = tarfile.SYMTYPE
        ti_sym.linkname = '/opt/pulse/pulse'
        tar.addfile(ti_sym)

    data_tar_gz = data_tar_io.getvalue()

    with open(deb_path, 'wb') as f:
        f.write(b"!<arch>\n")
        f.write(create_ar_header('debian-binary', len(debian_binary)))
        f.write(debian_binary)
        if len(debian_binary) % 2 == 1: f.write(b"\n")
        f.write(create_ar_header('control.tar.gz', len(control_tar_gz)))
        f.write(control_tar_gz)
        if len(control_tar_gz) % 2 == 1: f.write(b"\n")
        f.write(create_ar_header('data.tar.gz', len(data_tar_gz)))
        f.write(data_tar_gz)
        if len(data_tar_gz) % 2 == 1: f.write(b"\n")

    print(f"Successfully generated {deb_path} ({os.path.getsize(deb_path)/1024:.1f} KB)")

    # 2. Universal Linux tar.gz
    targz_path = os.path.join(dist_dir, f"Pulse-v{version}-linux.tar.gz")
    print(f"Building Linux tar.gz package: {targz_path}")
    with tarfile.open(targz_path, 'w:gz') as tar:
        for root, dirs, files in os.walk(src_dir):
            for file in files:
                abs_p = os.path.join(root, file)
                rel_p = os.path.relpath(abs_p, src_dir).replace('\\', '/')
                tar.add(abs_p, arcname=f"pulse-linux/{rel_p}")
        ti_run = tarfile.TarInfo('pulse-linux/pulse.sh')
        ti_run.size = len(launcher_script)
        ti_run.mtime = int(time.time())
        ti_run.mode = 0o755
        tar.addfile(ti_run, io.BytesIO(launcher_script))
    print(f"Successfully generated {targz_path} ({os.path.getsize(targz_path)/1024:.1f} KB)")

if __name__ == '__main__':
    base = os.path.dirname(os.path.abspath(__file__))
    src = os.path.join(base, 'multiplatform', 'src')
    dist = os.path.join(base, 'dist')
    os.makedirs(dist, exist_ok=True)
    build_linux_packages(src, dist)

