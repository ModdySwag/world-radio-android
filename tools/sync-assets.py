#!/usr/bin/env python3
"""Copy the live web app into the Android project's assets.

The station catalogue is ~18 MB, so it is not kept in the repo's history by hand -
run this whenever build.py refreshes the data, then commit the result (or let the CI
workflow pull it from the live site instead, see .github/workflows/android.yml).

Usage:
    python tools/sync-assets.py                 # from the local site folder
    python tools/sync-assets.py --from-url      # from https://moddys.net
    python tools/sync-assets.py --site DIR      # from somewhere else
"""
import argparse
import hashlib
import os
import shutil
import sys
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
PROJECT = os.path.dirname(HERE)
DEST = os.path.join(PROJECT, "app", "src", "main", "assets", "www")

# popout.html is deliberately not shipped: it opens a second browser window for the
# single-owner handover, and a phone has no second window.
FILES = ["index.html", "stations.js", "countries.js", "viz.js"]

DEFAULT_SITES = [
    r"C:\Users\Moddy\radio-browser\deploy",
    r"C:\Users\Moddy\radio-browser",
]
LIVE = "https://moddys.net/"


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()[:16]


def find_site(explicit):
    if explicit:
        return explicit if os.path.isdir(explicit) else None
    for cand in DEFAULT_SITES:
        if os.path.isdir(cand) and os.path.isfile(os.path.join(cand, "index.html")):
            return cand
    return None


def from_url(dest):
    os.makedirs(dest, exist_ok=True)
    for name in FILES:
        url = LIVE + name
        out = os.path.join(dest, name)
        print(f"  fetching {url}")
        with urllib.request.urlopen(url, timeout=120) as r, open(out, "wb") as f:
            shutil.copyfileobj(r, f)
    return True


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--from-url", action="store_true", help="pull from https://moddys.net")
    ap.add_argument("--site", default=None, help="local site folder to copy from")
    ap.add_argument("--check", action="store_true", help="report only, change nothing")
    a = ap.parse_args()

    if a.from_url:
        if a.check:
            print("--check with --from-url: nothing to verify locally")
            return 0
        print(f"syncing from {LIVE}")
        from_url(DEST)
    else:
        site = find_site(a.site)
        if not site:
            print("Could not find the site folder. Pass --site DIR or --from-url.", file=sys.stderr)
            return 1
        print(f"syncing from {site}")
        missing = [f for f in FILES if not os.path.isfile(os.path.join(site, f))]
        if missing:
            print(f"  missing in source: {', '.join(missing)}", file=sys.stderr)
            return 1
        if not a.check:
            os.makedirs(DEST, exist_ok=True)
            for name in FILES:
                shutil.copyfile(os.path.join(site, name), os.path.join(DEST, name))
                print(f"  copied {name}")

    print("\nassets in the project:")
    total = 0
    for name in FILES:
        p = os.path.join(DEST, name)
        if os.path.isfile(p):
            size = os.path.getsize(p)
            total += size
            print(f"  {name:14s} {size:>10,} B  sha256:{sha256(p)}")
        else:
            print(f"  {name:14s} MISSING")
    print(f"  {'total':14s} {total:>10,} B")
    print("\nSanity check: does index.html load its two data files?")
    idx = os.path.join(DEST, "index.html")
    if os.path.isfile(idx):
        html = open(idx, encoding="utf-8", errors="replace").read()
        for name in ("countries.js", "stations.js"):
            print(f"  references {name}: {'yes' if name in html else 'NO'}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
