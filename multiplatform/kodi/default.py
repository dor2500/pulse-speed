# -*- coding: utf-8 -*-
"""
Pulse Speed Kodi Addon
Runs speed tests natively in Kodi with live progress and dialogs.
"""
import sys
import time
import urllib.request
import urllib.error
import json

try:
    import xbmc
    import xbmcgui
    import xbmcaddon
    IN_KODI = True
except ImportError:
    IN_KODI = False

def notify(msg, title="Pulse Speed"):
    if IN_KODI:
        xbmcgui.Dialog().notification(title, msg, xbmcgui.NOTIFICATION_INFO, 5000)
    else:
        print(f"[{title}] {msg}")

def run_test():
    pDialog = None
    if IN_KODI:
        pDialog = xbmcgui.DialogProgress()
        pDialog.create("Pulse Speed Test", "Connecting to server...")
    else:
        print("Starting Pulse Speed Test...")

    # 1. Ping
    samples = []
    if pDialog: pDialog.update(10, "Measuring Ping & Jitter...")
    for i in range(10):
        t0 = time.time()
        try:
            req = urllib.request.Request("https://speed.cloudflare.com/__down?bytes=0&r=" + str(time.time()), headers={"User-Agent": "PulseKodi/1.5.0"})
            with urllib.request.urlopen(req, timeout=3) as resp:
                resp.read()
            samples.append((time.time() - t0) * 1000)
        except Exception:
            pass
        if pDialog and pDialog.iscanceled(): return
        time.sleep(0.05)

    if not samples: samples = [35.0]
    samples.sort()
    ping_ms = samples[len(samples)//2]
    jitter_ms = sum(abs(samples[i+1]-samples[i]) for i in range(len(samples)-1)) / max(1, len(samples)-1)

    # 2. Download
    if pDialog: pDialog.update(35, "Testing Download speed...")
    dl_start = time.time()
    dl_bytes = 0
    dl_url = "https://speed.cloudflare.com/__down?bytes=25000000&r=" + str(time.time())
    try:
        req = urllib.request.Request(dl_url, headers={"User-Agent": "PulseKodi/1.5.0"})
        with urllib.request.urlopen(req, timeout=10) as resp:
            while time.time() - dl_start < 6.0:
                chunk = resp.read(64 * 1024)
                if not chunk: break
                dl_bytes += len(chunk)
                if pDialog and pDialog.iscanceled(): return
    except Exception:
        pass

    dl_time = max(0.5, time.time() - dl_start)
    dl_mbps = (dl_bytes * 8) / (dl_time * 1e6)

    # 3. Upload
    if pDialog: pDialog.update(70, "Testing Upload speed...")
    up_start = time.time()
    up_bytes = 0
    dummy_data = b"0" * (128 * 1024)
    while time.time() - up_start < 4.0:
        try:
            req = urllib.request.Request("https://speed.cloudflare.com/__up?r=" + str(time.time()), data=dummy_data, headers={"User-Agent": "PulseKodi/1.5.0"})
            with urllib.request.urlopen(req, timeout=5) as resp:
                resp.read()
            up_bytes += len(dummy_data)
        except Exception:
            break
        if pDialog and pDialog.iscanceled(): return

    up_time = max(0.5, time.time() - up_start)
    up_mbps = (up_bytes * 8) / (up_time * 1e6)

    # 4. Result Dialog
    if pDialog:
        pDialog.update(100, "Complete!")
        pDialog.close()

    result_text = (
        f"Download: {dl_mbps:.1f} Mbps\n"
        f"Upload: {up_mbps:.1f} Mbps\n"
        f"Ping: {ping_ms:.1f} ms (Jitter: {jitter_ms:.1f} ms)\n"
        f"Server: Cloudflare Edge"
    )

    if IN_KODI:
        xbmcgui.Dialog().ok("Pulse Speed Results", result_text)
    else:
        print("\n--- RESULTS ---")
        print(result_text)

if __name__ == "__main__":
    run_test()
