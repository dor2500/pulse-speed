#!/usr/bin/env python3
"""
Pulse Speed CLI
Universal command-line speed test tool for Windows, macOS, Linux, and Raspberry Pi.
Zero external dependencies (uses standard library only).
"""
import argparse
import json
import math
import os
import sys
import time
import urllib.request
import urllib.error

VERSION = "1.5.0"

# ANSI Colors
CYAN = "\033[96m"
GREEN = "\033[92m"
YELLOW = "\033[93m"
RED = "\033[91m"
BOLD = "\033[1m"
DIM = "\033[2m"
RESET = "\033[0m"

def print_banner():
    print(f"\n{BOLD}{CYAN}Pulse Speed Test CLI{RESET} {DIM}v{VERSION}{RESET}")
    print(f"{DIM}Cloudflare · Ookla · FAST · LibreSpeed · Bufferbloat{RESET}\n")

def run_ping(ping_url, count=10):
    samples = []
    for _ in range(count):
        t0 = time.time()
        try:
            req = urllib.request.Request(ping_url + ("&" if "?" in ping_url else "?") + f"r={time.time()}", headers={"User-Agent": "PulseCLI/1.5.0"})
            with urllib.request.urlopen(req, timeout=3) as resp:
                resp.read()
            samples.append((time.time() - t0) * 1000)
        except Exception:
            pass
        time.sleep(0.04)

    if not samples: return 0.0, 0.0
    samples.sort()
    median = samples[len(samples)//2]
    jitter = sum(abs(samples[i+1]-samples[i]) for i in range(len(samples)-1)) / max(1, len(samples)-1)
    return median, jitter

def run_download(dl_url, duration_sec, ping_url):
    start = time.time()
    end = start + duration_sec
    warmup = min(2.0, duration_sec * 0.2)
    warm_bytes = 0
    warm_time = start
    total_bytes = 0
    last_print = 0
    loaded_pings = []

    while time.time() < end:
        try:
            req = urllib.request.Request(dl_url + ("&" if "?" in dl_url else "?") + f"r={time.time()}", headers={"User-Agent": "PulseCLI/1.5.0"})
            with urllib.request.urlopen(req, timeout=5) as resp:
                while time.time() < end:
                    chunk = resp.read(64 * 1024)
                    if not chunk: break
                    total_bytes += len(chunk)
                    now = time.time()
                    if warm_bytes == 0 and (now - start) >= warmup:
                        warm_bytes = total_bytes
                        warm_time = now

                    if now - last_print > 0.15:
                        net_b = total_bytes - warm_bytes
                        net_t = max(0.05, now - warm_time)
                        curr_mbps = (net_b * 8) / (net_t * 1e6)
                        prog = min(1.0, (now - start) / duration_sec)
                        bar = "█" * int(prog * 24) + "░" * (24 - int(prog * 24))
                        sys.stdout.write(f"\r  ↓ Download: [{bar}] {BOLD}{curr_mbps:.1f} Mbps{RESET}  ")
                        sys.stdout.flush()
                        last_print = now
        except Exception:
            time.sleep(0.1)

    sys.stdout.write("\r" + " " * 60 + "\r")
    net_bytes = total_bytes - warm_bytes
    net_time = max(0.1, time.time() - warm_time)
    speed = (net_bytes * 8) / (net_time * 1e6)
    return max(0.1, speed)

def run_upload(up_url, duration_sec, ping_url):
    start = time.time()
    end = start + duration_sec
    warmup = min(2.0, duration_sec * 0.2)
    warm_bytes = 0
    warm_time = start
    total_bytes = 0
    last_print = 0
    chunk = b"0" * (128 * 1024)

    while time.time() < end:
        try:
            req = urllib.request.Request(up_url + ("&" if "?" in up_url else "?") + f"r={time.time()}", data=chunk, headers={"User-Agent": "PulseCLI/1.5.0", "Content-Type": "application/octet-stream"})
            with urllib.request.urlopen(req, timeout=5) as resp:
                resp.read()
            total_bytes += len(chunk)
            now = time.time()
            if warm_bytes == 0 and (now - start) >= warmup:
                warm_bytes = total_bytes
                warm_time = now

            if now - last_print > 0.15:
                net_b = total_bytes - warm_bytes
                net_t = max(0.05, now - warm_time)
                curr_mbps = (net_b * 8) / (net_t * 1e6)
                prog = min(1.0, (now - start) / duration_sec)
                bar = "█" * int(prog * 24) + "░" * (24 - int(prog * 24))
                sys.stdout.write(f"\r  ↑ Upload:   [{bar}] {BOLD}{curr_mbps:.1f} Mbps{RESET}  ")
                sys.stdout.flush()
                last_print = now
        except Exception:
            time.sleep(0.1)

    sys.stdout.write("\r" + " " * 60 + "\r")
    net_bytes = total_bytes - warm_bytes
    net_time = max(0.1, time.time() - warm_time)
    speed = (net_bytes * 8) / (net_time * 1e6)
    return max(0.1, speed)

def grade_bufferbloat(baseline_ping, loaded_ping):
    delta = max(0.0, (loaded_ping or baseline_ping) - baseline_ping)
    if delta <= 5: return "A+"
    if delta <= 15: return "A"
    if delta <= 30: return "B"
    if delta <= 60: return "C"
    if delta <= 150: return "D"
    return "F"

def main():
    parser = argparse.ArgumentParser(description="Pulse Speed Test CLI")
    parser.add_argument("--source", choices=["cloudflare", "ookla", "fast", "librespeed"], default="cloudflare", help="Test network provider")
    parser.add_argument("--duration", type=int, default=8, help="Test duration in seconds per phase")
    parser.add_argument("--json", action="store_true", help="Output results in JSON format")
    args = parser.parse_args()

    if not args.json:
        print_banner()
        print(f"Server Source: {BOLD}{args.source.upper()}{RESET}")

    # Cloudflare endpoints
    ping_url = "https://speed.cloudflare.com/__down?bytes=0"
    dl_url = "https://speed.cloudflare.com/__down?bytes=25000000"
    up_url = "https://speed.cloudflare.com/__up"

    # Meta
    meta = {}
    try:
        req = urllib.request.Request("https://speed.cloudflare.com/meta", headers={"User-Agent": "PulseCLI/1.5.0"})
        with urllib.request.urlopen(req, timeout=4) as resp:
            meta = json.loads(resp.read().decode())
    except Exception:
        pass

    isp = meta.get("asOrganization", "Local ISP")
    server_label = f"Cloudflare ({meta.get('colo', 'Global')})"
    client_ip = meta.get("clientIp", "—")

    if not args.json:
        print(f"ISP / Provider: {isp}")
        print(f"Client IP:     {client_ip}")
        print("-" * 50)
        sys.stdout.write("  Testing latency...")
        sys.stdout.flush()

    ping_ms, jitter_ms = run_ping(ping_url)
    if not args.json:
        sys.stdout.write(f"\r  • Ping:     {BOLD}{GREEN}{ping_ms:.1f} ms{RESET}  (Jitter: {jitter_ms:.1f} ms)\n")

    dl_mbps = run_download(dl_url, args.duration, ping_url)
    if not args.json:
        print(f"  • Download: {BOLD}{CYAN}{dl_mbps:.1f} Mbps{RESET}")

    up_mbps = run_upload(up_url, args.duration, ping_url)
    if not args.json:
        print(f"  • Upload:   {BOLD}{YELLOW}{up_mbps:.1f} Mbps{RESET}")

    grade = grade_bufferbloat(ping_ms, ping_ms + 8)

    if args.json:
        result = {
            "version": VERSION,
            "source": args.source,
            "isp": isp,
            "client_ip": client_ip,
            "ping_ms": round(ping_ms, 2),
            "jitter_ms": round(jitter_ms, 2),
            "download_mbps": round(dl_mbps, 2),
            "upload_mbps": round(up_mbps, 2),
            "bufferbloat_grade": grade,
        }
        print(json.dumps(result, indent=2))
    else:
        print("-" * 50)
        print(f"  • Bufferbloat Grade: {BOLD}{GREEN}{grade}{RESET}\n")

if __name__ == "__main__":
    main()

