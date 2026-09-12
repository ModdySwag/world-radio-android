#!/usr/bin/env python3
"""Read a built APK and report what is actually inside it.

A release is checked as an artifact, never as a build log: this opens the .apk, parses the
binary AndroidManifest.xml, reads the bundled web app out of assets/www, and reads the signing
certificate out of the APK Signing Block. No Android SDK, no Java, no network - it runs on a
plain Python 3 and works on the published file, not on anything a build left behind.

    python3 tools/apk_identity.py path/to/world-radio-v1.6.1.apk
    python3 tools/apk_identity.py <apk> --site "C:/Users/Moddy/radio-browser/deploy"
    python3 tools/apk_identity.py <apk> --expect-fingerprint B2:53:...

Exit code is 0 only when every check passes.
"""
from __future__ import annotations

import argparse
import hashlib
import os
import struct
import sys
import zipfile

# ---------------------------------------------------------------- binary XML (AXML) ----

def _string_pool(data: bytes, off: int):
    count, style_count, flags, strings_start, _styles = struct.unpack_from("<IIIII", data, off + 8)
    utf8 = bool(flags & (1 << 8))
    offsets = struct.unpack_from("<%dI" % count, data, off + 28)
    base = off + strings_start
    out = []
    for o in offsets:
        p = base + o
        if utf8:
            n = data[p]
            if n & 0x80:
                n = ((n & 0x7F) << 8) | data[p + 1]
                p += 2
            else:
                p += 1
            nb = data[p]
            if nb & 0x80:
                nb = ((nb & 0x7F) << 8) | data[p + 1]
                p += 2
            else:
                p += 1
            out.append(data[p:p + nb].decode("utf-8", "replace"))
        else:
            n = struct.unpack_from("<H", data, p)[0]
            if n & 0x8000:
                n = ((n & 0x7FFF) << 16) | struct.unpack_from("<H", data, p + 2)[0]
                p += 4
            else:
                p += 2
            out.append(data[p:p + n * 2].decode("utf-16-le", "replace"))
    return out


def parse_axml(data: bytes) -> dict:
    """Return {element_name: {attribute_name: value}} for the whole document."""
    if struct.unpack_from("<H", data, 0)[0] != 0x0003:
        raise ValueError("not a binary AndroidManifest.xml")
    header_size = struct.unpack_from("<H", data, 2)[0]
    total = struct.unpack_from("<I", data, 4)[0]

    strings: list[str] = []
    elements: dict[str, dict] = {}
    off = header_size
    while off < min(total, len(data)):
        ctype, _chsize, csz = struct.unpack_from("<HHI", data, off)
        if csz == 0:
            break
        if ctype == 0x0001:
            strings = _string_pool(data, off)
        elif ctype == 0x0102:  # start element
            name_idx = struct.unpack_from("<I", data, off + 20)[0]
            # attributeStart (ResXMLTree_attrExt) is a byte offset from the START OF THE
            # ELEMENT HEADER - i.e. chunk start + 16 - not from the chunk start. Getting this
            # wrong reads every attribute name as if it were the attribute's value.
            attr_start, attr_size, attr_count = struct.unpack_from("<HHH", data, off + 24)
            name = strings[name_idx] if name_idx < len(strings) else "?"
            attrs = {}
            for k in range(attr_count):
                p = off + 16 + attr_start + k * (attr_size or 20)
                an = struct.unpack_from("<I", data, p + 4)[0]
                dtype = data[p + 15]
                dval = struct.unpack_from("<I", data, p + 16)[0]
                aname = strings[an] if an < len(strings) else "?"
                if dtype == 0x03:      # string
                    attrs[aname] = strings[dval] if dval < len(strings) else ""
                elif dtype == 0x12:    # boolean
                    attrs[aname] = bool(dval)
                else:                  # int / hex / reference
                    attrs[aname] = dval
            elements[name] = attrs
        off += csz
    return elements


# ------------------------------------------------------------- APK Signing Block ----

def signing_block(apk: bytes) -> dict:
    """{block id: value bytes} from the APK Signing Block (v2/v3), plus whether it is there.

    Layout: u64 size | pairs | u64 size (again) | "APK Sig Block 42". The size field counts
    everything AFTER it, so the block starts at (magic + 16) - (8 + size).
    """
    magic = apk.rfind(b"APK Sig Block 42")
    if magic == -1:
        return {}
    size = struct.unpack_from("<Q", apk, magic - 8)[0]
    start = magic + 8 - size
    off, end = start + 8, magic - 8
    out = {}
    while off < end:
        ln = struct.unpack_from("<Q", apk, off)[0]
        if ln < 4 or off + 8 + ln > end + 8:
            break
        pid = struct.unpack_from("<I", apk, off + 8)[0]
        out[pid] = apk[off + 12:off + 8 + ln]
        off += 8 + ln
    return out


def _lp(buf: bytes, off: int):
    n = struct.unpack_from("<I", buf, off)[0]
    return buf[off + 4:off + 4 + n], off + 4 + n


def v2_certificates(block: bytes) -> list[bytes]:
    """Walk the v2/v3 block down to the signer certificates (list of DER bytes).

    block -> signers -> signer -> signed_data -> {digests, certificates, attributes}
    """
    signers, _ = _lp(block, 0)
    signer, _ = _lp(signers, 0)
    signed_data, _ = _lp(signer, 0)
    _, o = _lp(signed_data, 0)          # skip the digests sequence
    certs, _ = _lp(signed_data, o)      # the certificates sequence
    out = []
    o = 0
    while o < len(certs):
        der, o = _lp(certs, o)
        out.append(der)
    return out


def fingerprint_of(der: bytes) -> str:
    from cryptography import x509
    from cryptography.hazmat.primitives import hashes
    cert = x509.load_der_x509_certificate(der)
    fp = cert.fingerprint(hashes.SHA256())
    return ":".join("%02X" % b for b in fp)


# ---------------------------------------------------------------------- checks ----

def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("apk")
    ap.add_argument("--site", help="the live site's deploy folder, to compare the bundled page")
    ap.add_argument("--expect-fingerprint", help="signing certificate SHA-256, colon separated")
    ap.add_argument("--expect-version")
    a = ap.parse_args()

    fails, checks = [], 0

    def check(label, ok, detail=""):
        nonlocal checks
        checks += 1
        if not ok:
            fails.append(label)
        print("  %-4s %s%s" % ("ok" if ok else "FAIL", label,
                               ("  [%s]" % detail) if detail else ""))

    apk_bytes = open(a.apk, "rb").read()
    sha = hashlib.sha256(apk_bytes).hexdigest()
    print("APK      %s" % os.path.basename(a.apk))
    print("         %d bytes, sha256 %s" % (len(apk_bytes), sha))

    z = zipfile.ZipFile(a.apk)
    names = set(z.namelist())

    print("\n== the manifest, as the device reads it ==")
    if "AndroidManifest.xml" not in names:
        print("  FAIL no AndroidManifest.xml")
        return 1
    m = parse_axml(z.read("AndroidManifest.xml"))
    manifest = m.get("manifest", {})
    uses_sdk = m.get("uses-sdk", {})
    application = m.get("application", {})

    pkg = manifest.get("package", "?")
    check("package is com.moddys.worldradio", pkg == "com.moddys.worldradio", pkg)
    check("it is NOT the debug build (no .debug id)", ".debug" not in str(pkg), pkg)
    check("the app is not marked debuggable", not application.get("debuggable"), application.get("debuggable"))
    check("application has a label", bool(application.get("label")), application.get("label"))

    vcode = manifest.get("versionCode")
    vname = manifest.get("versionName")
    check("versionCode is a whole number", isinstance(vcode, int), vcode)
    check("versionName looks like a version", isinstance(vname, str) and vname.count(".") >= 1, vname)
    if a.expect_version:
        check("versionName is %s" % a.expect_version, vname == a.expect_version, vname)

    min_sdk = uses_sdk.get("minSdkVersion")
    target = uses_sdk.get("targetSdkVersion")
    check("minSdkVersion is the hard floor, API 26", min_sdk == 26, min_sdk)
    check("targetSdkVersion is set", isinstance(target, int), target)

    print("\n== the web app it carries ==")
    for asset in ("assets/www/index.html", "assets/www/_shell_shim.js",
                  "assets/www/compat.json", "assets/www/stations.js",
                  "assets/www/countries.js"):
        check("bundles %s" % asset, asset in names)

    if "assets/www/compat.json" in names:
        import json
        cj = json.loads(z.read("assets/www/compat.json").decode("utf-8"))
        check("compat.json is for android", cj.get("platform") == "android", cj.get("platform"))
        check("compat.json appVersion matches the build", cj.get("appVersion") == vname,
              "%s vs %s" % (cj.get("appVersion"), vname))
        os_block = cj.get("os", {})
        hard, soft = os_block.get("hard", {}), os_block.get("soft", {})
        check("compat.json's hard floor is the same API as minSdkVersion",
              hard.get("api") == min_sdk, "%s vs minSdk %s" % (hard.get("api"), min_sdk))
        check("compat.json carries a soft (tuned-for) floor too",
              isinstance(soft.get("api"), int) and soft["api"] >= hard.get("api", 0),
              soft.get("api"))
        check("compat.json says the hard floor blocks installation",
              os_block.get("hardBlocksInstall") is True)
        check("compat.json names the real playback engine",
              "WebView" in str(cj.get("engine", {}).get("name")),
              cj.get("engine", {}).get("name"))

    if a.site and "assets/www/index.html" in names:
        site_page = os.path.join(a.site, "index.html")
        if os.path.exists(site_page):
            here = hashlib.sha256(z.read("assets/www/index.html")).hexdigest()
            there = hashlib.sha256(open(site_page, "rb").read()).hexdigest()
            check("the bundled page is byte-identical to the site's",
                  here == there, "%s vs %s" % (here[:16], there[:16]))

    if "assets/www/_shell_shim.js" in names:
        shim = z.read("assets/www/_shell_shim.js").decode("utf-8", "replace")
        check("the shim is the shared shell (native() bridge, not host.*)",
              "function native(" in shim and "host.state" not in shim)
        check("the shim still carries the compat panel", "compat" in shim.lower())

    print("\n== the signature ==")
    blocks = signing_block(apk_bytes)
    schemes = {0x7109871A: "v2", 0xF05368C0: "v3", 0x42726577: "verity padding",
               0x504B4453: "SDK dependency info"}
    present = [schemes.get(b, hex(b)) for b in blocks]
    check("APK Signing Block present", bool(blocks), ", ".join(present))
    # v2 covers Android 7+ and is what a minSdk-26 app needs; v3 adds key rotation, which this
    # app does not use, so its absence is worth reporting but is not a fault.
    check("signed with an APK signature scheme (v2 or v3)",
          0x7109871A in blocks or 0xF05368C0 in blocks)
    if 0xF05368C0 not in blocks:
        print("         note: no v3 block - not needed here (v3 is for key rotation)")

    der = None
    for bid in (0xF05368C0, 0x7109871A):
        if bid in blocks:
            try:
                der = v2_certificates(blocks[bid])[0]
                break
            except Exception as e:  # noqa: BLE001
                print("         (could not read certificates from %s: %s)" % (hex(bid), e))
    if der:
        fp = fingerprint_of(der)
        print("         signing certificate sha256: %s" % fp)
        check("a signing certificate is present", True)
        if a.expect_fingerprint:
            check("it is the same key as previous releases",
                  fp.upper() == a.expect_fingerprint.upper(), fp)
    else:
        check("a signing certificate was found", False)

    check("no debug Certificate/keystore leaked into the archive",
          not any(n.startswith("META-INF/") and n.upper().endswith((".RSA", ".DSA", ".EC"))
                  for n in names))
    check("exactly one dex (nothing unexpected gets shipped)",
          len([n for n in names if n.endswith(".dex")]) == 1)

    print("\n%d checks, %d failed" % (checks, len(fails)))
    return 1 if fails else 0


if __name__ == "__main__":
    sys.exit(main())
