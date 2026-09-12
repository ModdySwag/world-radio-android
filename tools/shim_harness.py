#!/usr/bin/env python3
"""Drive the real shell shim in a real browser, on both bridges.

The shim is the whole front end (player sheet, theme, visualiser, check panel) and it is
injected at runtime by the native shell, so it can be tested without a phone - and without
a Mac, which is the only way the iOS bridge path gets exercised at all.

What this covers, per bridge:

  android   window.WorldRadioJs      synchronous device facts, state/log/url calls
  ios       window.webkit.messageHandlers.wr   asynchronous postMessage, plus the
            window.__wrDevice facts injected before the page runs
  web       neither bridge: the check panel must degrade to "not reported", not crash

Usage:
    python3 tools/shim_harness.py                 # uses ../radio-browser/deploy/index.html
    python3 tools/shim_harness.py --page PATH
    python3 tools/shim_harness.py --headed        # watch it
"""
import argparse
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
PROJECT = os.path.dirname(HERE)
SHIM_CANDIDATES = [
    # the Android project
    os.path.join(PROJECT, "app", "src", "main", "assets", "www", "_shell_shim.js"),
    # the iOS project
    os.path.join(PROJECT, "Resources", "www", "_shell_shim.js"),
]
SHIM = next((p for p in SHIM_CANDIDATES if os.path.isfile(p)), SHIM_CANDIDATES[0])

# The bundled copies are byte-identical to what the site serves, so either will do - prefer the
# live site folder when it is on this machine, else the copy inside whichever project this is
# (the Android repo keeps it under app/src/main/assets, the iOS repo under Resources/www).
PAGE_CANDIDATES = [
    r"C:\Users\Moddy\radio-browser\deploy\index.html",
    os.path.join(PROJECT, "app", "src", "main", "assets", "www", "index.html"),
    os.path.join(PROJECT, "Resources", "www", "index.html"),
]
DEFAULT_PAGE = next((p for p in PAGE_CANDIDATES if os.path.isfile(p)), PAGE_CANDIDATES[0])

ANDROID_DEVICE = {
    "manufacturer": "samsung", "model": "SM-T580", "api": 28, "release": "9",
    "app": "1.6.0", "webview": "com.google.android.webview 95.0.4638.74",
    "engine": "com.google.android.webview 95.0.4638.74", "engineLabel": "WebView",
    "platform": "android", "osName": "Android", "notifications": False,
    "system": "Android 9 (API 28)",
    "compat": {
        "supported": True, "recommended": False, "system": "Android 9 (API 28)",
        "hardLabel": "Android 8.0 (API 26)", "softLabel": "Android 11 (API 30)",
        "engineNote": "playback happens in the device's Android System WebView",
    },
}

IOS_DEVICE = {
    "manufacturer": "Apple", "model": "iPad7,5", "platform": "ios", "osName": "iOS",
    "osVersion": "16.6", "app": "1.0.0", "engine": "WebKit 605.1.15 (iOS 16.6)",
    "engineLabel": "WebKit", "notifications": None, "system": "iOS 16.6 (iPad7,5)",
    "compat": {
        "supported": True, "recommended": False, "system": "iOS 16.6 (iPad7,5)",
        "hardLabel": "iOS 15", "softLabel": "iOS 18",
        "engineNote": "playback happens in Apple's WebKit engine",
    },
}

# The bridge stub for each platform. Android's device() answers synchronously, so it is a
# plain function on a global; iOS has no such thing, hence __wrDevice.
STUB = """
window.__wrTest = { calls: [], posted: [] };
%(setup)s
"""

ANDROID_SETUP = """
window.WorldRadioJs = {
  log: function (m) { window.__wrTest.calls.push(['log', String(m)]); },
  state: function (p, s, e) { window.__wrTest.calls.push(['state', !!p, String(s), String(e || '')]); },
  url: function (u) { window.__wrTest.calls.push(['url', String(u)]); },
  dismissKeyboard: function () { window.__wrTest.calls.push(['dismissKeyboard']); },
  updateApp: function (u, v, s) { window.__wrTest.calls.push(['updateApp', String(u), String(v), String(s)]); },
  checkUpdate: function () { window.__wrTest.calls.push(['checkUpdate']); },
  device: function () { return %(device)s; }
};
"""

IOS_SETUP = """
window.webkit = { messageHandlers: { wr: { postMessage: function (msg) {
  window.__wrTest.posted.push(msg);
  if (msg && msg.m === 'log') window.__wrTest.calls.push(['log', String(msg.a[0])]);
  if (msg && msg.m === 'state') window.__wrTest.calls.push(['state', !!msg.a[0], String(msg.a[1]), String(msg.a[2] || '')]);
  if (msg && msg.m === 'url') window.__wrTest.calls.push(['url', String(msg.a[0])]);
  if (msg && msg.m === 'dismissKeyboard') window.__wrTest.calls.push(['dismissKeyboard']);
  if (msg && msg.m === 'updateApp') window.__wrTest.calls.push(['updateApp', String(msg.a[0]), String(msg.a[1]), String(msg.a[2])]);
  if (msg && msg.m === 'checkUpdate') window.__wrTest.calls.push(['checkUpdate']);
} } } };
window.__wrDevice = %(device)s;
"""

WEB_SETUP = "/* no bridge at all */"

failures = []
checks = 0


def check(label, cond, detail=""):
    global checks
    checks += 1
    if cond:
        print("  ok   " + label)
    else:
        print("  FAIL " + label + ((" :: " + str(detail)) if detail else ""))
        failures.append(label)


def row(rows, label):
    for r in rows:
        if r["l"] == label:
            return r
    return None


def inject(page):
    """Wait for the page's own player bar, then inject the shim the way the shell does."""
    try:
        page.wait_for_function("() => !!document.querySelector('#bar')", timeout=30000)
    except Exception as e:
        print("  note: #bar never appeared (%s)" % type(e).__name__)
    page.evaluate(open(SHIM, encoding="utf-8").read())
    page.wait_for_timeout(400)


def run_platform(page, name, global_name, setup, device, headed):
    print("\n== %s bridge ==" % name)
    page.add_init_script(STUB % {"setup": setup})
    page.goto(page_url)
    inject(page)

    built = page.evaluate("!!window.__wr")
    check("%s: shim installs" % name, built is True)
    if not built:
        return

    sheet = page.evaluate("!!document.getElementById('wrSheet')")
    check("%s: player sheet built" % name, sheet is True)

    check("%s: marks the page as running inside the app" % name,
          page.evaluate("document.documentElement.classList.contains('wr-app')") is True)

    res = page.evaluate("window.__wr.check()")
    rows = res["rows"]
    check("%s: check panel renders rows" % name, len(rows) >= 8, len(rows))

    system = row(rows, "System")
    check("%s: System row present" % name, system is not None)
    if system:
        check("%s: System row reports the device" % name,
              device.get("system", "") in system["d"], system["d"])
        check("%s: System row warns when below the tuned-for version" % name,
              system["s"] == "warn", system["s"])

    needs = row(rows, "App needs")
    check("%s: 'App needs' row present" % name, needs is not None)
    if needs:
        check("%s: hard floor named" % name,
              device["compat"]["hardLabel"] in needs["d"], needs["d"])
        check("%s: tuned-for version named" % name,
              device["compat"]["softLabel"] in needs["d"], needs["d"])
        check("%s: the requirement row is informational, so the verdict is counted once" % name,
              needs["s"] == "info", needs["s"])

    engine = row(rows, device["engineLabel"])
    check("%s: engine row uses the platform label (%s)" % (name, device["engineLabel"]),
          engine is not None and device["engine"] in engine["d"],
          engine["d"] if engine else None)

    report = page.evaluate("window.__wr.checkReport()")
    check("%s: report carries the verdict" % name, "[!]" in report and "App needs" in report)
    check("%s: report names the platform" % name, device["platform"] in report)

    # The launch notice's button: expand the player AND show the check panel.
    page.evaluate("window.__wr.compat()")
    page.wait_for_timeout(300)
    cls = page.evaluate("document.getElementById('wrSheet').className")
    check("%s: __wr.compat() opens the sheet and the check panel" % name,
          "wr-open" in cls and "wr-check" in cls, cls)

    # The visualiser. The shell moves the page's canvas into its own panel and drives the page's
    # own toggle, and the page now draws through the shared viz.js - so this is also the check
    # that the file shipped INSIDE the app, which no other test would notice was missing.
    page.evaluate("""(() => {
      const b = document.querySelector('#wrVizOpts button');
      if (b) b.click();
    })()""")
    page.wait_for_timeout(800)
    viz = page.evaluate("""(() => {
      const c = document.getElementById('viz'), wrap = document.getElementById('wrVizWrap');
      if (!c) return {missing: 'no canvas'};
      let painted = false;
      try {
        const d = c.getContext('2d').getImageData(0, 0, c.width, c.height).data;
        for (let i = 3; i < d.length; i += 4) if (d[i] > 0) { painted = true; break; }
      } catch (e) { painted = 'unreadable: ' + e.message; }
      return {loaded: !!window.Viz, styles: window.Viz ? window.Viz.STYLES.length : 0,
              inPanel: !!wrap && wrap.contains(c), shown: getComputedStyle(c).display !== 'none',
              size: c.width + 'x' + c.height, painted: painted};
    })()""")
    check("%s: the shared visualiser file is inside the app" % name,
          viz.get("loaded") and viz.get("styles") == 5, viz)
    check("%s: the visualiser draws in the shell's own panel" % name,
          viz.get("inPanel") and viz.get("shown"), viz)
    check("%s: and it is actually painting pixels" % name, viz.get("painted") is True, viz)

    # External links must reach the native side on both bridges (Android sync, iOS post).
    page.evaluate("""(() => {
      const a = document.createElement('a');
      a.href = 'https://example.org/station';
      a.id = 'wrProbeLink';
      a.textContent = 'probe';
      document.body.appendChild(a);
      a.click();
    })()""")
    page.wait_for_timeout(250)
    calls = page.evaluate("window.__wrTest.calls")
    urls = [c for c in calls if c[0] == "url"]
    check("%s: an external link reaches the native side" % name, len(urls) == 1,
          calls)

    # A relative same-site page (the site's downloads.html and friends) must NOT navigate the
    # WebView away from the player: it is handed to the shell like an external link.
    before = page.evaluate("location.pathname")
    page.evaluate("""(() => {
      const a = document.createElement('a');
      a.href = 'downloads.html';
      a.id = 'wrProbePage';
      a.textContent = 'page';
      document.body.appendChild(a);
      a.click();
    })()""")
    page.wait_for_timeout(250)
    after = page.evaluate("location.pathname")
    calls = page.evaluate("window.__wrTest.calls")
    urls = [c for c in calls if c[0] == "url"]
    check("%s: a same-site page link does not navigate the player away" % name,
          before == after, "%s -> %s" % (before, after))
    check("%s: a same-site page link is handed to the native side" % name, len(urls) == 2, calls)
    check("%s: it is handed over as an absolute URL" % name,
          len(urls) == 2 and urls[1][1].startswith("file://") and
          urls[1][1].endswith("downloads.html"), urls[1][1] if len(urls) == 2 else None)

    # The pager inside the app. Reported from a phone: the arrows did nothing at all. The page
    # turns them on a click, and a WebView can drop the click that should follow a touch - so the
    # arrows also listen for a touch that never became one. Both routes are exercised here, and
    # the guard that stops a real tap counting twice.
    def page_number():
        return page.evaluate("() => { const m = document.querySelector('#cntLine').textContent"
                             ".match(/page\\s+(\\d+)/); return m ? +m[1] : -1; }")

    page.evaluate("() => window.scrollTo({ top: 0 })")
    start = page_number()
    page.evaluate("""() => { const b = document.querySelector('#pagerTop button:last-of-type');
        b.dispatchEvent(new Event('touchend', { bubbles: true })); }""")
    page.wait_for_timeout(600)
    after_touch = page_number()
    check("%s: a touch that never became a click still turns the page" % name,
          after_touch == start + 1, "%s -> %s" % (start, after_touch))

    # and a normal tap must not be counted twice (touchend plus the click it did produce)
    page.evaluate("""() => { const b = document.querySelector('#pagerTop button:last-of-type');
        b.dispatchEvent(new Event('touchend', { bubbles: true })); b.click(); }""")
    page.wait_for_timeout(700)
    after_both = page_number()
    check("%s: a real tap advances exactly one page, not two" % name,
          after_both == after_touch + 1, "%s -> %s" % (after_touch, after_both))

    # the back arrow returns
    page.evaluate("""() => { const b = document.querySelector('#pagerTop button:first-of-type');
        b.click(); }""")
    page.wait_for_timeout(600)
    check("%s: and the back arrow returns" % name, page_number() == after_both - 1,
          "%s -> %s" % (after_both, page_number()))

    logs = [c for c in calls if c[0] == "log"]
    check("%s: the shim reports itself to the native side" % name,
          any("shim ready" in c[1] for c in logs), logs[:3])

    # Committing a search must take the keyboard down. Headless Chromium has no soft keyboard,
    # so the proxy is the bridge call the shell makes: if the shell is told, the IME goes.
    page.evaluate("window.__wrTest.calls.length = 0")
    page.evaluate("""(() => {
      const q = document.querySelector('#q');
      q.value = 'jazz';
      q.focus();
      q.dispatchEvent(new KeyboardEvent('keydown', {key: 'Enter', bubbles: true}));
    })()""")
    page.wait_for_timeout(300)
    calls = page.evaluate("window.__wrTest.calls")
    check("%s: committing a search tells the shell to put the keyboard away" % name,
          any(c[0] == "dismissKeyboard" for c in calls), calls)
    focus = page.evaluate("() => document.activeElement && document.activeElement.id")
    check("%s: and the field has given up focus" % name, focus != "q", focus)

    # The magnifier route: a `search` event, which is what a soft keyboard sends.
    page.evaluate("window.__wrTest.calls.length = 0")
    page.evaluate("""(() => {
      const q = document.querySelector('#q');
      q.focus();
      q.dispatchEvent(new Event('search', {bubbles: true}));
    })()""")
    page.wait_for_timeout(300)
    check("%s: the keyboard's magnifier does the same" % name,
          any(c[0] == "dismissKeyboard" for c in page.evaluate("window.__wrTest.calls")))

    # A newer build: the shell must show a notice with a working Update button, and the button
    # must hand the native side everything it needs to fetch and install.
    info = {"version": "9.9.9", "url": "https://moddys.net/downloads/world-radio-v9.9.9.apk",
            "sha256": "abc123", "notes": "fixes", "code": 99, "platform": name}
    page.evaluate("(i) => window.__wrUpdate.available(i)", info)
    page.wait_for_timeout(400)
    banner = page.evaluate("""() => {
      const el = document.querySelector('#wrUpd');
      if (!el) return {shown: false};
      const go = el.querySelector('[data-wr-upd=\"go\"]');
      const r = go ? go.getBoundingClientRect() : null;
      return {shown: getComputedStyle(el).display !== 'none', text: el.textContent,
              goLabel: go ? go.textContent : null, goH: r ? Math.round(r.height) : 0,
              mentions: el.textContent.indexOf('9.9.9') >= 0,
              mentionsMine: el.textContent.indexOf('1.6.') >= 0 || el.textContent.indexOf('1.0.') >= 0};
    }""")
    check("%s: a newer build raises a notice" % name, banner["shown"] and banner["mentions"], banner)
    check("%s: the notice names the version you are on" % name, banner["mentionsMine"], banner)
    check("%s: its button is a real touch target" % name, banner["goH"] >= 40, banner)
    page.evaluate("window.__wrTest.calls.length = 0")
    page.click("#wrUpd [data-wr-upd=\"go\"]")
    page.wait_for_timeout(300)
    upd = [c for c in page.evaluate("window.__wrTest.calls") if c[0] == "updateApp"]
    check("%s: Update now hands the native side the URL, version and checksum" % name,
          len(upd) == 1 and upd[0][1] == info["url"] and upd[0][2] == "9.9.9" and upd[0][3] == "abc123",
          upd)

    # Later dismisses it; being up to date shows nothing at all.
    page.evaluate("window.__wrTest.calls.length = 0")
    page.click("#wrUpd [data-wr-upd=\"later\"]")
    page.wait_for_timeout(200)
    check("%s: Later dismisses the notice" % name,
          page.evaluate("() => getComputedStyle(document.querySelector('#wrUpd')).display === 'none'"))
    page.evaluate("() => window.__wrUpdate.none('9.9.9')")
    page.wait_for_timeout(200)
    check("%s: nothing is shown when this build is current" % name,
          page.evaluate("() => { const u = document.querySelector('#wrUpd');"
                        " return !u || getComputedStyle(u).display === 'none'; }"))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--page", default=DEFAULT_PAGE)
    ap.add_argument("--headed", action="store_true")
    ap.add_argument("--only", default=None,
                    help="run one bridge only: android, ios or web")
    a = ap.parse_args()

    from playwright.sync_api import sync_playwright

    global page_url
    page_url = "file:///" + a.page.replace("\\", "/")
    if not os.path.isfile(a.page):
        print("page not found: " + a.page, file=sys.stderr)
        print("tried:", file=sys.stderr)
        for candidate in PAGE_CANDIDATES:
            print("  " + candidate, file=sys.stderr)
        return 2
    print("page: " + page_url)
    print("shim: " + SHIM)

    with sync_playwright() as p:
        browser = p.chromium.launch(headless=not a.headed)
        for name, gname, setup, device in (
            ("android", "WorldRadioJs", ANDROID_SETUP, ANDROID_DEVICE),
            ("ios", "", IOS_SETUP, IOS_DEVICE),
            ("web", "", WEB_SETUP, None),
        ):
            if a.only and a.only != name:
                continue
            page = browser.new_page(viewport={"width": 412, "height": 915},
                                    device_scale_factor=2)
            page.set_default_timeout(15000)
            try:
                if device is None:
                    print("\n== web (no bridge) ==")
                    page.add_init_script(STUB % {"setup": setup})
                    page.goto(page_url)
                    inject(page)
                    res = page.evaluate("window.__wr.check()")
                    rows = res["rows"]
                    check("web: shim still installs",
                          page.evaluate("!!window.__wr") is True)
                    fallback = row(rows, "App check")
                    check("web: no native verdict -> says so instead of guessing",
                          fallback is not None and "not reported" in fallback["d"],
                          fallback["d"] if fallback else None)
                    check("web: does not claim a platform verdict",
                          row(rows, "App needs") is None)

                    # iOS injects the shim at the end of parsing, which can be before the page
                    # has built its own bar. The shim must wait rather than give up.
                    page.goto("about:blank")
                    page.set_content("<html><body><div id='host'></div></body></html>")
                    page.evaluate(open(SHIM, encoding="utf-8").read())
                    check("deferred: shell installs with no bar present",
                          page.evaluate("!!window.__wr") is True)
                    check("deferred: no sheet before the bar exists",
                          page.evaluate("!!document.getElementById('wrSheet')") is False)
                    page.evaluate("""(() => {
                      const b = document.createElement('div');
                      b.id = 'bar';
                      b.style.display = 'none';
                      document.body.appendChild(b);
                    })()""")
                    page.wait_for_timeout(1200)
                    check("deferred: sheet is built once the page's bar appears",
                          page.evaluate("!!document.getElementById('wrSheet')") is True)
                else:
                    run_platform(page, name, gname, setup % {"device": json.dumps(device)},
                                 device, a.headed)
            finally:
                page.close()
        browser.close()

    print("\n%d checks, %d failed" % (checks, len(failures)))
    for f in failures:
        print("  FAILED: " + f)
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
