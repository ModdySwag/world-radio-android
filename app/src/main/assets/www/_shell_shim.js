/* Platform shell for MODDYS World Radio.
 *
 * ONE file, shipped byte-identical by the Android app and the iOS app; only the bridge
 * behind it differs (see BRIDGE below). Nothing in here is platform-specific.
 *
 * Adds what the phone needs and removes what only makes sense on a desktop:
 *
 *   1. ONE player, not two. The web app's own bottom bar is kept as a hidden control
 *      plane - all playback still goes through its buttons, so there is exactly one
 *      source of truth and none of its logic is reimplemented here. The shell renders
 *      the player you actually see and drive.
 *   2. That player is a bottom sheet: drag it up for the full view, drag it down (or tap
 *      the handle) to minimise. It carries the station art, name, country/language/
 *      codec/bitrate, transport, volume and the navigation toggles.
 *   3. The pop-out / mini-player is gone: it opens a second browser window, and a phone
 *      has no second window.
 *   4. The "you're viewing the files directly from disk" notice is suppressed - inside
 *      the app it is never true, and on the web it never appears.
 *
 * State comes from the page's own window.__dbg API where available and from the media
 * element's events otherwise, so playback survives that API changing.
 *
 * BRIDGE - the only difference between the two apps:
 *
 *   Android   window.WorldRadioJs      a synchronous @JavascriptInterface object
 *   iOS       window.webkit.messageHandlers.wr   asynchronous postMessage, plus
 *             window.__wrDevice       device facts injected before the page runs, because
 *                                     WKWebView has no way to answer a JS call synchronously
 *
 * native() below hides that difference. Everything else is shared verbatim, so a fix to
 * the player, the theme or the check panel lands on both platforms at once.
 */
(function () {
  "use strict";
  if (window.__wr) return;                       // already injected

  var host = window.WorldRadioJs;                // Android, if we are on Android
  var wk = null, ios = false;                    // iOS message handler, if we are on iOS
  try {
    wk = (window.webkit && window.webkit.messageHandlers && window.webkit.messageHandlers.wr) || null;
  } catch (e) { wk = null; }
  ios = !!wk && !host;

  /* Call the native side by name on whichever bridge exists. Android answers synchronously
     and iOS does not, so the return value is only meaningful on Android - which is why the
     one call that needs an answer (device facts) reads window.__wrDevice on iOS. */
  function native(name, args) {
    try { if (host && typeof host[name] === "function") return host[name].apply(host, args || []); }
    catch (e) { /* the shell is gone; the page must keep working without it */ }
    try { if (wk) wk.postMessage({ m: name, a: args || [] }); }
    catch (e) { /* ditto */ }
    return null;
  }

  var doc = document;
  var $ = function (sel) { return doc.querySelector(sel); };

  function report(msg) {
    native("log", [String(msg)]);
  }

  /* ---------------------------------------------------------------- state ---- */

  function info() {
    var cur = null, playing = false, error = null;
    var d = window.__dbg;
    if (d) {
      try {
        var p = d.player ? d.player() : null;
        if (p) { cur = p.cur || null; playing = !!p.playing; }
      } catch (e) { /* fall through to element state */ }
      try {
        var a = d.audioState ? d.audioState() : null;
        if (a) {
          if (a.err) error = String(a.err);
          playing = !!(a.src && a.paused === false);
        }
      } catch (e) { /* ignore */ }
    }
    return { cur: cur, playing: playing, error: error };
  }

  var lastKey = null;
  function pushState(force) {
    var s = info();
    var key = (s.playing ? "1" : "0") + "|" + (s.cur || "") + "|" + (s.error || "");
    if (!force && key === lastKey) return;
    lastKey = key;
    native("state", [!!s.playing, s.cur || "", s.error || ""]);
  }

  /* What the lock screen shows. The station, the country · language · codec · bitrate line and
     the artwork URL - all of it read from the page's own bar, none of it invented here.
     Android's shell ignores this message today; iOS builds its Now Playing card from it. */
  var lastNow = null;
  function pushNowPlaying() {
    var nameNode = $("#bName"), metaNode = $("#bMeta"), artNode = $("#bArt");
    var name = nameNode ? (nameNode.textContent || "").trim() : "";
    if (name === "Pick a station") name = "";               // nothing chosen yet
    var meta = metaNode ? (metaNode.textContent || "").trim() : "";
    var art = artNode ? (artNode.getAttribute("src") || "") : "";
    var key = name + "|" + meta + "|" + art;
    if (key === lastNow) return;
    lastNow = key;
    native("nowplaying", [name, meta, art]);
  }

  /* ------------------------------------------------- controls on the page ---- */
  /* Every action below drives a control the page already owns. Nothing is duplicated. */

  /* Station streams and homepages belong in the phone's browser. Left alone they are
     either silently blocked (target="_blank" with no second window) or they replace the
     player with a web page - both wrong. */
  function openExternal(url) {
    if (!url) return;
    try {
      if (native("url", [String(url)]) !== null) return;   // native browser
    } catch (e) { /* fall through to the web behaviour */ }
    var w = window.__wrRealOpen ? window.__wrRealOpen(url, "_blank") : null;
    if (!w) { try { location.href = url; } catch (e2) { } }
  }

  function press(sel) {
    var el = $(sel);
    if (el && !el.disabled) { el.click(); return true; }
    return false;
  }

  function playPlayback() { press("#bPlay"); }

  /* Stop means disconnect: live radio has no seek, and the page's own stop is the honest
     version of that (it tells the pop-out too, if one is ever open). */
  function pausePlayback() {
    var d = window.__dbg;
    if (d && typeof d.stop === "function") { try { d.stop(); return; } catch (e) { } }
    press("#bStop");
  }

  function focusSearch() {
    var q = $("#q");
    if (!q) return;
    q.scrollIntoView({ behavior: "smooth", block: "center" });
    q.focus({ preventScroll: true });
  }

  function setVolume(v) {
    var el = $("#vol");
    if (!el) return;
    el.value = String(v);
    el.dispatchEvent(new Event("input", { bubbles: true }));
  }

  /* ------------------------------------------------------- pop-out removal ---- */

  function killPopOut() {
    var css = doc.createElement("style");
    css.textContent =
      "#btnPop,#dock{display:none !important}" +
      /* the page's own bar stays in the layout as a hidden control plane */
      "#bar{display:none !important}" +
      /* keep room for the sheet so the list is never trapped behind it */
      "body{padding-bottom:0 !important}";
    (doc.head || doc.documentElement).appendChild(css);

    var realOpen = window.open;
    window.__wrRealOpen = realOpen;
    window.open = function (url) {
      if (String(url || "").indexOf("popout") !== -1) return null;   // never a 2nd window
      if (/^https?:/i.test(String(url || ""))) { openExternal(url); return null; }
      return realOpen.apply(window, arguments);
    };
  }

  /* The page's own buttons for a station's stream and homepage are plain target="_blank"
     links, which a WebView with no second window drops on the floor. Catch them here. */
  function externalLinks() {
    doc.addEventListener("click", function (ev) {
      var a = ev.target && ev.target.closest ? ev.target.closest('a[href]') : null;
      if (!a) return;
      var href = a.getAttribute("href") || "";
      if (!/^https?:/i.test(href)) return;          // leave #anchors and mailto alone
      ev.preventDefault();
      ev.stopPropagation();
      openExternal(href);
    }, true);
  }

  /* ---------------------------------------------- the disk-notice suppressor ---- */

  var DISK_NOTICE = /viewing the files directly from disk/i;

  function suppressDiskNotice() {
    var notice = $("#notice");
    if (!notice) return;
    var css = doc.createElement("style");
    css.textContent = "#notice.wr-muted{display:none !important}";
    (doc.head || doc.documentElement).appendChild(css);

    var check = function () {
      var hit = DISK_NOTICE.test(notice.textContent || "");
      notice.classList.toggle("wr-muted", hit);
    };
    check();
    /* the page can raise or replace this notice at any time, so watch it rather than
       guessing when it fires. Other notices (unplayable station etc.) still show. */
    new MutationObserver(check).observe(notice, {
      childList: true, characterData: true, subtree: true, attributes: true
    });
  }

  /* ------------------------------------------------------------ the sheet ---- */

  var sheetEl = null;        // the sheet element, once built
  var sheetCtl = null;       // its gesture controller
  var SHEET = { collapsed: true, open: false };

  function buildSheet() {
    var bar = $("#bar");
    if (!bar) return null;

    var css = doc.createElement("style");
    css.textContent = [
      "html{-webkit-text-size-adjust:100%}",
      "#wrSheet{position:fixed;left:0;right:0;bottom:0;z-index:70;",
      "  background:var(--wr-sheet,#12131e);",
      "  border-top:3px solid var(--cyan);box-shadow:0 -10px 40px rgba(0,0,0,.6);",
      "  transform:translateY(var(--wr-y,0px));will-change:transform;",
      "  padding-bottom:env(safe-area-inset-bottom,0px);touch-action:none;}",
      "#wrSheet.wr-anim{transition:transform .26s cubic-bezier(.22,.61,.36,1)}",
      "#wrSheet.wr-drag{transition:none}",
      /* handle */
      ".wr-grab{display:flex;align-items:center;justify-content:center;height:22px;",
      "  cursor:grab;touch-action:none}",
      ".wr-grab i{width:44px;height:4px;border-radius:3px;background:var(--line);display:block}",
      ".wr-grab b{position:absolute;right:14px;font-size:13px;color:var(--mut);",
      "  transition:transform .26s ease;transform:rotate(180deg)}",
      "#wrSheet.wr-open .wr-grab b{transform:rotate(0)}",
      /* collapsed row */
      ".wr-row{display:flex;align-items:center;gap:11px;padding:6px 12px 12px;touch-action:none}",
      /* expanded, the hero below already shows the station: keeping the compact row too
         just repeated it and stole the room the visualiser needs */
      "#wrSheet.wr-open .wr-row{display:none}",
      ".wr-art{width:46px;height:46px;border-radius:10px 4px 10px 4px;object-fit:cover;",
      "  border:1px solid var(--line);background:#0b0b12;flex:0 0 auto}",
      ".wr-txt{min-width:0;flex:1}",
      ".wr-name{font-weight:800;font-size:14.5px;white-space:nowrap;overflow:hidden;",
      "  text-overflow:ellipsis}",
      ".wr-meta{font-size:11px;color:var(--mut);white-space:nowrap;overflow:hidden;",
      "  text-overflow:ellipsis;margin-top:2px}",
      ".wr-chip{font-size:10px;font-weight:800;letter-spacing:.5px;text-transform:uppercase;",
      "  border:1px solid var(--line);border-radius:999px;padding:2px 7px;color:var(--mut);flex:0 0 auto}",
      ".wr-chip.wr-live{color:var(--cyan);border-color:var(--cyan)}",
      /* the round transport button */
      ".wr-round{flex:0 0 auto;width:46px;height:46px;border-radius:50%;border:2px solid var(--pink);",
      "  background:var(--pink);color:#1a0312;font-size:17px;font-weight:900;cursor:pointer;",
      "  display:flex;align-items:center;justify-content:center;padding:0}",
      ".wr-round:active{transform:scale(.94)}",
      /* expanded view */
      ".wr-full{max-height:0;overflow:hidden;transition:max-height .26s cubic-bezier(.22,.61,.36,1)}",
      "#wrSheet.wr-open .wr-full{max-height:var(--wr-full,60vh);overflow-y:auto;touch-action:pan-y}",
      ".wr-hero{display:flex;gap:14px;align-items:center;padding:4px 14px 12px}",
      ".wr-hero img{width:88px;height:88px;border-radius:14px 6px 14px 6px;object-fit:cover;",
      "  border:2px solid var(--cyan);background:#0b0b12;flex:0 0 auto}",
      ".wr-hero .wr-h1{font-weight:900;font-size:19px;line-height:1.15;cursor:pointer}",
      ".wr-hero .wr-sub{font-size:12px;color:var(--mut);margin-top:5px;line-height:1.45}",
      ".wr-transport{display:flex;align-items:center;gap:10px;padding:0 14px 10px}",
      ".wr-tbtn{flex:1;border:2px solid var(--line);background:var(--wr-btn,rgba(255,255,255,.04));color:inherit;",
      "  border-radius:12px;padding:11px 8px;font-size:13px;font-weight:800;cursor:pointer}",
      ".wr-tbtn:active{transform:scale(.97)}",
      ".wr-tbtn.wr-primary{background:var(--pink);border-color:var(--pink);color:#1a0312;flex:1.25}",
      ".wr-vol{display:flex;align-items:center;gap:10px;padding:0 14px 12px}",
      ".wr-vol input{flex:1;accent-color:var(--pink);height:22px}",
      ".wr-vol span{font-size:11px;color:var(--mut);min-width:38px;text-align:right}",
      ".wr-nav{display:grid;grid-template-columns:repeat(3,1fr);gap:9px;padding:2px 14px 16px}",
      ".wr-nav button{border:2px solid var(--line);background:var(--wr-btn,rgba(255,255,255,.03));color:inherit;",
      "  border-radius:12px;padding:11px 4px 9px;font-size:10.5px;font-weight:800;cursor:pointer;",
      "  display:flex;flex-direction:column;align-items:center;gap:5px;line-height:1}",
      ".wr-nav button i{font-style:normal;font-size:17px}",
      ".wr-nav button:active{transform:scale(.96);border-color:var(--cyan)}",
      ".wr-nav button.wr-on{border-color:var(--cyan);background:var(--wr-btn,transparent)}",
      ".wr-sep{height:1px;background:var(--line);margin:0 14px 12px;opacity:.5}",
      /* the visualiser, lifted out of the page's bar and into the sheet */
      ".wr-vizsec{display:none;padding:0 14px calc(20px + env(safe-area-inset-bottom,0px))}",
      "#wrSheet.wr-viz .wr-vizsec{display:block}",
      ".wr-vizhead{display:flex;justify-content:space-between;align-items:center;font-size:10.5px;",
      "  font-weight:800;letter-spacing:.5px;text-transform:uppercase;color:var(--mut);padding:0 2px 7px}",
      ".wr-vizhead b{color:var(--cyan)}",
      ".wr-vizwrap{border:2px solid var(--line);border-radius:12px;overflow:hidden;background:#0b0c12}",
      /* the page hides this canvas below 900px and the canvas is sized to its own box,
         so the shell gives it a real box to measure - hence !important on both counts */
      ".wr-vizwrap #viz{display:block !important;width:100% !important;max-width:none !important;",
      "  height:64px !important;border:0;border-radius:0;flex:none}",
      ".wr-vizopts{display:grid;grid-template-columns:repeat(5,1fr);gap:7px;padding:9px 0 0}",
      ".wr-vizopts button{border:2px solid var(--line);background:var(--wr-btn,rgba(255,255,255,.03));",
      "  color:var(--mut);border-radius:10px;padding:8px 2px 6px;font-size:9.5px;font-weight:800;",
      "  cursor:pointer;display:flex;flex-direction:column;align-items:center;gap:4px;line-height:1}",
      ".wr-vizopts button i{font-style:normal;font-size:14px}",
      ".wr-vizopts button.wr-on{border-color:var(--cyan);color:var(--cyan)}",
      ".wr-vizopts button:active{transform:scale(.96)}",
      /* the per-device system check */
      ".wr-checksec{display:none;padding:0 14px calc(20px + env(safe-area-inset-bottom,0px))}",
      "#wrSheet.wr-check .wr-checksec{display:block}",
      ".wr-checkrows{display:flex;flex-direction:column;gap:1px;border:2px solid var(--line);",
      "  border-radius:12px;overflow:hidden}",
      ".wr-checkrows .wr-crow{display:flex;gap:9px;align-items:baseline;padding:8px 10px;",
      "  font-size:11.5px;background:var(--wr-btn,rgba(255,255,255,.03))}",
      ".wr-checkrows .wr-crow b{flex:0 0 16px;font-size:13px;text-align:center}",
      ".wr-checkrows .wr-crow.ok b{color:var(--ok,#7dffa8)}",
      ".wr-checkrows .wr-crow.warn b{color:var(--warn,#ffb454)}",
      ".wr-checkrows .wr-crow span{color:var(--mut)}",
      ".wr-checkrows .wr-crow i{font-style:normal;font-weight:800;flex:0 0 108px}",
      /* the station a random jump landed on, briefly lit */
      ".card.wr-picked{outline:3px solid var(--cyan);outline-offset:2px;border-radius:14px 6px 14px 6px}"
    ].join("");
    (doc.head || doc.documentElement).appendChild(css);

    var sheet = doc.createElement("div");
    sheet.id = "wrSheet";
    sheet.innerHTML =
      '<div class="wr-grab" data-wr="grab"><i></i><b>⌄</b></div>' +
      '<div class="wr-row" data-wr="row">' +
      '  <img class="wr-art" id="wrArt" alt="">' +
      '  <div class="wr-txt">' +
      '    <div class="wr-name" id="wrName">Pick a station</div>' +
      '    <div class="wr-meta" id="wrMeta"></div>' +
      '  </div>' +
      '  <span class="wr-chip" id="wrChip"></span>' +
      '  <button class="wr-round" data-wr="play" title="Play or pause">▶</button>' +
      '</div>' +
      '<div class="wr-full">' +
      '  <div class="wr-hero">' +
      '    <img id="wrBigArt" alt="">' +
      '    <div style="min-width:0">' +
      '      <div class="wr-h1" id="wrBigName">Pick a station</div>' +
      '      <div class="wr-sub" id="wrBigMeta"></div>' +
      '    </div>' +
      '  </div>' +
      '  <div class="wr-transport">' +
      '    <button class="wr-tbtn wr-primary" data-wr="play2">▶ Play</button>' +
      '    <button class="wr-tbtn" data-wr="details">ℹ Details</button>' +
      '    <button class="wr-tbtn" data-wr="stop">⏹ Stop</button>' +
      '    <button class="wr-tbtn" data-wr="site">🌐 Website</button>' +
      '  </div>' +
      '  <div class="wr-vol"><input type="range" id="wrVol" min="0" max="1" step="0.01"><span id="wrVolPct">—</span></div>' +
      '  <div class="wr-sep"></div>' +
      '  <div class="wr-nav">' +
      '    <button data-wr="search"><i>🔎</i>Search</button>' +
      '    <button data-wr="theme"><i>🌙</i>Dark</button>' +
      '    <button data-wr="fav"><i>★</i>Favourites</button>' +
      '    <button data-wr="random"><i>🎲</i>Random</button>' +
      '    <button data-wr="playable"><i>✓</i>Playable</button>' +
      '    <button data-wr="reset"><i>↺</i>Reset</button>' +
      '    <button data-wr="top"><i>▲</i>Top</button>' +
      '    <button data-wr="viz"><i>🎛</i>Visual</button>' +
      '    <button data-wr="check"><i>🩺</i>Check</button>' +
      '  </div>' +
      '  <div class="wr-checksec" id="wrCheckSec">' +
      '    <div class="wr-vizhead"><span>System check</span><b id="wrCheckHead"></b></div>' +
      '    <div class="wr-checkrows" id="wrCheckRows"></div>' +
      '    <div class="wr-vizopts" style="grid-template-columns:1fr 1fr">' +
      '      <button data-check="rerun"><i>↻</i>Run again</button>' +
      '      <button data-check="copy"><i>⧉</i>Copy report</button>' +
      '    </div>' +
      '  </div>' +
      '  <div class="wr-vizsec" id="wrVizSec">' +
      '    <div class="wr-vizhead"><span>Visualiser</span><b id="wrVizName">off</b></div>' +
      '    <div class="wr-vizwrap" id="wrVizWrap"></div>' +
      '    <div class="wr-vizopts" id="wrVizOpts"></div>' +
      '  </div>' +
      '</div>';
    doc.body.appendChild(sheet);
    return sheet;
  }

  /* ------------------------------------------------------- sheet behaviour ---- */

  function sheetController(sheet) {
    var MAX = 0;                                  // px the sheet travels when collapsed
    var y = -1, dragging = false, startY = 0, startOffset = 0;

    var collapseH = 0;                            // measured while collapsed
    function measure() {
      /* The sheet is laid out as a full-height panel pinned to the bottom, then translated
         down so that only the grabber + collapsed row remain on screen. Without an explicit
         height the collapsed state would slide the whole sheet off the bottom edge - which
         is exactly the bug this replaces. The row is hidden once expanded, so its height is
         only ever measured in the collapsed state. */
      if (!sheet.classList.contains("wr-open")) {
        var grab = sheet.querySelector(".wr-grab");
        var row = sheet.querySelector(".wr-row");
        collapseH = (grab ? grab.offsetHeight : 22) + (row ? row.offsetHeight : 66);
      }
      var collapsedH = collapseH || 88;
      var pct = sheet.classList.contains("wr-viz") ? 0.72 : 0.62;   // taller with the visualiser
      var expandedH = Math.min(Math.round(window.innerHeight * pct), pct > 0.65 ? 720 : 640);
      sheet.style.height = expandedH + "px";
      sheet.style.setProperty("--wr-full", Math.max(0, expandedH - collapsedH) + "px");
      MAX = Math.max(0, expandedH - collapsedH);
      apply(SHEET.open ? 0 : MAX, false);
    }

    function apply(offset, animate) {
      sheet.style.setProperty("--wr-y", offset + "px");
      y = offset;
    }

    function setOpen(open, animate) {
      SHEET.open = open;
      sheet.classList.toggle("wr-open", open);
      if (animate !== false) {
        sheet.classList.add("wr-anim");
        window.setTimeout(function () { sheet.classList.remove("wr-anim"); }, 300);
      }
      apply(open ? 0 : MAX, animate);
    }

    function onDown(ev) {
      if (!ev.isPrimary) return;
      if (ev.target.closest("button,input")) return;      // never steal a tap
      dragging = true;
      startY = ev.clientY; startOffset = y < 0 ? (SHEET.open ? 0 : MAX) : y;
      sheet.classList.remove("wr-anim");
      sheet.classList.add("wr-drag");
      try { ev.target.closest("[data-wr]").setPointerCapture(ev.pointerId); } catch (e) { }
    }

    function onMove(ev) {
      if (!dragging) return;
      var dy = ev.clientY - startY;
      var next = Math.min(MAX, Math.max(0, startOffset + dy));
      apply(next, false);
      ev.preventDefault();
    }

    function onUp(ev) {
      if (!dragging) return;
      dragging = false;
      sheet.classList.remove("wr-drag");
      var dy = ev.clientY - startY;
      /* A tap toggles; a real drag is decided by its direction. Keep it this simple: the
         sheet must never feel like it argued with you. */
      if (Math.abs(dy) < 24) { setOpen(!SHEET.open); return; }
      setOpen(dy < 0);
    }

    /* Where it makes sense to grab: the handle, the compact row, and the two inert header
       strips of the expanded view. Buttons and the volume slider are never stolen. */
    [sheet.querySelector('[data-wr="grab"]'), sheet.querySelector('[data-wr="row"]'),
     sheet.querySelector(".wr-hero"), sheet.querySelector(".wr-vizhead")].forEach(function (el) {
      if (el) el.addEventListener("pointerdown", onDown, { passive: true });
    });
    doc.addEventListener("pointermove", onMove, { passive: false });
    doc.addEventListener("pointerup", onUp, { passive: true });
    doc.addEventListener("pointercancel", onUp, { passive: true });

    window.addEventListener("resize", measure);
    // keyboard/AT access to the same states
    sheet.querySelector('[data-wr="grab"]').addEventListener("keydown", function (ev) {
      if (ev.key === "Enter" || ev.key === " ") { ev.preventDefault(); setOpen(!SHEET.open); }
    });
    sheet.querySelector('[data-wr="grab"]').tabIndex = 0;

    sheet.addEventListener("click", function (ev) {
      var c = ev.target.closest("[data-check]");
      if (c) {
        if (c.getAttribute("data-check") === "copy") {
          renderCheck();
          var ok = copyText(checkReport());
          c.innerHTML = "<i>" + (ok ? "\u2713" : "\u2715") + "</i>" + (ok ? "Copied" : "Copy failed");
          window.setTimeout(function () { c.innerHTML = "<i>\u29c9</i>Copy report"; }, 2000);
        } else {
          /* Run again runs from a real press, so the tap probe tells the truth here. */
          renderCheck();
          probeAutoplay(function () { renderCheck(); });
        }
        return;
      }
      var b = ev.target.closest("[data-wr]");
      if (!b) return;
      var act = b.getAttribute("data-wr");
      /* Taps on the handle / row are handled by the gesture code on pointerup - acting on
         the follow-up click as well made every drag undo itself. */
      if (act === "grab" || act === "row") return;
      if (act === "play" || act === "play2") { press("#bPlay"); pushState(true); return; }
      if (act === "stop") { press("#bStop"); pushState(true); return; }
      if (act === "details") { press("#bName"); return; }
      if (act === "site") { press("#btnBarSite"); return; }
      if (act === "search") { focusSearch(); setOpen(false); return; }
      if (act === "theme") { toggleTheme(); return; }
      if (act === "fav") { press("#btnFav"); setOpen(false); return; }
      if (act === "random") { randomStation(); return; }
      if (act === "playable") { press("#btnPlayable"); setOpen(false); return; }
      if (act === "reset") { press("#btnReset"); setOpen(false); return; }
      if (act === "top") { press("#toTop"); setOpen(false); return; }
      if (act === "check") { toggleCheck(); return; }
      if (act === "viz") { toggleViz(); return; }
    });

    var vol = sheet.querySelector("#wrVol");
    vol.addEventListener("input", function () {
      setVolume(vol.value);
      sheet.querySelector("#wrVolPct").textContent = Math.round(vol.value * 100) + "%";
    });

    measure();
    return { measure: measure, setOpen: setOpen };
  }

  /* ------------------------------------------------------ mirror page state ---- */

  function mirror(ctl) {
    var srcName = $("#bName"), srcMeta = $("#bMeta"), srcArt = $("#bArt"),
        srcChip = $("#bState"), srcVol = $("#vol");
    var out = {
      name: $("#wrName"), meta: $("#wrMeta"), art: $("#wrArt"), chip: $("#wrChip"),
      bigName: $("#wrBigName"), bigMeta: $("#wrBigMeta"), bigArt: $("#wrBigArt"),
      vol: $("#wrVol"), volPct: $("#wrVolPct")
    };
    var seen = {};

    function text(node) { return node ? (node.textContent || "").trim() : ""; }

    function sync() {
      var name = text(srcName) || "Pick a station";
      var meta = text(srcMeta);
      var art = srcArt ? srcArt.getAttribute("src") : "";
      var chip = text(srcChip);
      var vol = srcVol ? srcVol.value : "";

      if (seen.name !== name) {
        seen.name = name;
        out.name.textContent = name;
        out.bigName.textContent = name;
        doc.title = name === "Pick a station" ? doc.title : name + " — MODDYS World Radio";
      }
      if (seen.meta !== meta) {
        seen.meta = meta;
        out.meta.textContent = meta;
        out.bigMeta.textContent = meta;
      }
      if (seen.art !== art && art) {
        seen.art = art;
        out.art.src = art;
        out.bigArt.src = art;
      }
      if (seen.chip !== chip) {
        seen.chip = chip;
        out.chip.textContent = chip;
      }
      if (seen.vol !== vol && vol !== "") {
        seen.vol = vol;
        if (doc.activeElement !== out.vol) out.vol.value = vol;
        out.volPct.textContent = Math.round(parseFloat(vol) * 100) + "%";
      }
    }

    // the media element fires these on the document's capture path
    ["play", "playing", "pause", "ended", "error", "volumechange"].forEach(function (ev) {
      doc.addEventListener(ev, function () { setTimeout(sync, 120); }, true);
    });

    var obs = new MutationObserver(function () { sync(); });
    [srcName, srcMeta, srcChip].forEach(function (n) {
      if (n) obs.observe(n, { childList: true, characterData: true, subtree: true });
    });
    if (srcArt) obs.observe(srcArt, { attributes: true, attributeFilter: ["src"] });

    sync();
    return sync;
  }

  /* ---------------------------------------------------------- light mode ---- */

  var THEME_KEY = "rbg-wr-theme";

  function themeNow() {
    try { return localStorage.getItem(THEME_KEY) === "light" ? "light" : "dark"; }
    catch (e) { return "dark"; }
  }

  /* The site is a dark design with no light mode of its own, so light is the shell's own
     re-skin: the page's palette is overridden wholesale and the shell follows it. */
  function injectThemeCss() {
    var css = doc.createElement("style");
    css.textContent = [
      "html.wr-light{",
      "  --bg:#eef1f8;--bg2:#ffffff;--panel:#ffffff;--panel2:#e6eaf5;--line:#c7cee2;",
      "  --tx:#141728;--mut:#5b6383;--pink:#c9166f;--cyan:#0a7f9c;--lime:#5c8a00;",
      "  --orange:#b76100;--purp:#6b31cf;--yel:#8f6f00;--ok:#0d7043;--warn:#9a5200;",
      "  --wr-sheet:#ffffff;--wr-btn:rgba(20,23,40,.05)}",
      "html.wr-light,html.wr-light body{background:var(--bg);color:var(--tx)}",
      "html.wr-light body::before{opacity:.10}",
      /* The page hard-codes these dark, so the palette override alone is not enough
         (and its #id rules outrank a class). Each one below was measured, not guessed. */
      "html.wr-light .btn,html.wr-light .bctrl{background:#fff !important;color:var(--tx) !important;",
      "  border-color:var(--line) !important}",
      "html.wr-light .btn.pink{color:var(--pink) !important;border-color:var(--pink) !important}",
      "html.wr-light .tag{background:#e9edf6 !important;color:#3c4258 !important}",
      "html.wr-light .chip{background:#e9edf6 !important;color:var(--mut) !important}",
      "html.wr-light .ftag{background:#fff !important;color:var(--tx) !important;",
      "  border-color:rgba(20,23,40,.22) !important}",
      "html.wr-light .ftag.on{background:var(--pink) !important;color:#fff !important;border-color:var(--pink) !important}",
      "html.wr-light #q,html.wr-light #pgJump,html.wr-light #dynLang,html.wr-light select{",
      "  background:#fff !important;color:var(--tx) !important}",
      "html.wr-light img.fav{background:#e9edf6 !important}",
      "html.wr-light #dock .dwin{background:#fff !important;color:var(--tx) !important}",
      /* the panel behind the active-filter readout, and the big title, are hardcoded */
      "html.wr-light .facets,html.wr-light .frow,html.wr-light .fbar,html.wr-light #fActive{",
      "  background:#fff !important;color:var(--tx) !important}",
      "html.wr-light header h1,html.wr-light header h1 .brand,html.wr-light header h1 *{",
      "  color:var(--tx) !important}",
      "html.wr-light header h1,html.wr-light header h1 .brand{text-shadow:2px 2px 0 rgba(201,22,111,.28)}",
      /* the accent badge keeps its punch in both themes - it is a gradient, so a plain
         background override would have deleted it */
      "html.wr-light header .spraytag{background:linear-gradient(90deg,#c9ff3d,#ffe14d) !important;",
      "  color:#141728 !important}",
      "html.wr-light .wr-art,html.wr-light .wr-hero img{background:#e9edf6}",
      /* the visualiser stays a dark little screen on purpose: its bars are bright */
      "html.wr-light .wr-vizwrap{border-color:#c7cee2}"
    ].join("");
    (doc.head || doc.documentElement).appendChild(css);
  }

  function applyTheme(mode, remember) {
    var light = mode === "light";
    doc.documentElement.classList.toggle("wr-light", light);
    if (remember !== false) {
      try { localStorage.setItem(THEME_KEY, light ? "light" : "dark"); } catch (e) { }
    }
    var b = sheetEl && sheetEl.querySelector('[data-wr="theme"]');
    if (b) {
      b.querySelector("i").textContent = light ? "☀" : "🌙";
      b.lastChild.textContent = light ? "Light" : "Dark";
      b.classList.toggle("wr-on", light);
      b.title = light ? "Light mode is on - tap for dark" : "Dark mode is on - tap for light";
    }
    return light;
  }

  function toggleTheme() { return applyTheme(themeNow() === "light" ? "dark" : "light"); }

  /* --------------------------------------------------- header stays put ---- */

  /* The page can collapse its own header (its button, or the "h" shortcut, remembered in
     storage). The app wants a static header, so the control goes away and a collapsed
     state is never allowed to stick - however it got set. */
  function keepHeaderStatic() {
    var css = doc.createElement("style");
    css.textContent = "#btnHdr{display:none !important}";
    (doc.head || doc.documentElement).appendChild(css);
    try { localStorage.setItem("rbg-hdr-compact", "false"); } catch (e) { }
    var head = $("#hdrHead");
    if (!head) return;
    var expand = function () {
      if (head.classList.contains("compact")) head.classList.remove("compact");
    };
    expand();
    new MutationObserver(expand).observe(head, { attributes: true, attributeFilter: ["class"] });
  }

  /* ------------------------------------------------------ random station ---- */

  /* "Surprise me": play one of the stations actually on screen. The page owns selection,
     so this drives a card rather than reaching behind it - which also means it respects
     whatever filters are currently applied. */
  function randomStation() {
    var buttons = doc.querySelectorAll('[data-act="play"]');
    if (!buttons.length) return press("#bPlay");
    var pick = buttons[Math.floor(Math.random() * buttons.length)];
    var card = pick.closest(".card") || pick;
    try { card.scrollIntoView({ behavior: "smooth", block: "center" }); } catch (e) { }
    card.classList.add("wr-picked");
    window.setTimeout(function () { card.classList.remove("wr-picked"); }, 1800);
    pick.click();
    return true;
  }

  /* ---------------------------------------------------------- visualiser ---- */

  var VIZNAMES = ["Bars", "Waves", "Mirror", "Dots", "Blocks"];
  var VIZICONS = ["▮", "～", "◧", "⠿", "▦"];
  var VIZ = { style: 0 };

  /* The page cycles its five styles off one button, so a style is picked by driving that
     cycle, then read back to confirm it landed. No page change, nothing duplicated. */
  function vizState() {
    var btn = $("#btnViz");
    var m = /Visualiser:\s*([A-Za-z]+)/.exec(btn ? (btn.getAttribute("title") || "") : "");
    var name = m ? m[1].toLowerCase() : "off";
    if (name === "off") return { on: false, style: -1 };
    var hit = -1;
    for (var n = 0; n < VIZNAMES.length; n++) {
      if (VIZNAMES[n].toLowerCase() === name) hit = n;
    }
    return { on: true, style: hit < 0 ? 0 : hit };
  }

  function vizCycleTo(pos) {                  // 0 = off, 1..5 = styles (index + 1)
    var len = VIZNAMES.length + 1;
    var cur = vizState();
    var steps = (pos - (cur.on ? cur.style + 1 : 0) + len) % len;
    for (var n = 0; n < steps; n++) press("#btnViz");
    return vizState();
  }

  function syncViz() {
    var s = vizState();
    if (!sheetEl) return s;
    if (s.on) VIZ.style = s.style;
    sheetEl.classList.toggle("wr-viz", s.on);
    var name = sheetEl.querySelector("#wrVizName");
    if (name) name.textContent = s.on ? VIZNAMES[VIZ.style] : "off";
    var nav = sheetEl.querySelector('[data-wr="viz"]');
    if (nav) nav.classList.toggle("wr-on", s.on);
    var opts = sheetEl.querySelectorAll("[data-viz]");
    for (var i = 0; i < opts.length; i++) {
      opts[i].classList.toggle("wr-on", s.on && i === VIZ.style);
    }
    return s;
  }

  function buildViz() {
    var wrap = $("#wrVizWrap"), opts = $("#wrVizOpts"), canvas = $("#viz");
    if (!wrap || !opts) return;
    if (canvas) wrap.appendChild(canvas);       // moved, not copied: the page keeps drawing
    VIZNAMES.forEach(function (n, i) {
      var b = doc.createElement("button");
      b.setAttribute("data-viz", String(i));
      b.innerHTML = "<i>" + VIZICONS[i] + "</i>" + n;
      b.title = n + " visualiser";
      opts.appendChild(b);
    });
    opts.addEventListener("click", function (ev) {
      var b = ev.target.closest("[data-viz]");
      if (!b) return;
      vizCycleTo(parseInt(b.getAttribute("data-viz"), 10) + 1);
      syncViz();
      if (sheetCtl) sheetCtl.measure();
    });
    syncViz();
  }

  /* The Visual button in the grid shows or hides the whole section. */
  function toggleViz() {
    if (vizState().on) { vizCycleTo(0); } else { vizCycleTo(VIZ.style + 1); }
    syncViz();
    /* the two panels are exclusive, whichever one was opened last */
    if (sheetEl && vizState().on && sheetEl.classList.contains("wr-check")) {
      sheetEl.classList.remove("wr-check");
      var nav = sheetEl.querySelector('[data-wr="check"]');
      if (nav) nav.classList.remove("wr-on");
    }
    if (sheetCtl) sheetCtl.measure();
  }

  /* ------------------------------------------------- who gets the sound ----

     There is nothing here, and that is the point. The app does not ask Android for audio
     focus, and it does not try to work out who else is playing either.

     The WebView's engine plays the stream and requests focus itself, as any media app does;
     an earlier version had the service request focus too, so one app looked like two and the
     framework reported a loss or a refusal about this app's own audio. That is what stopped
     playback a fraction of a second after it started and blamed an app that was never there.

     Working out "who else is playing" instead would mean reading the playback configuration
     list - and the methods that say whose audio it is (isActive, getClientUid) are hidden
     from the public SDK. Any answer this app gave would be a guess, and a wrong guess about
     another app is precisely what the user saw. So it stays out of it: the radio plays when
     the page plays, and when the page stops for any reason the notification stays up with
     Play rather than inventing a reason. */

  /* ------------------------------------------------ how playback failed ---- */

  /* The page reports a play() rejection by NAME through __dbg.audioState().err and shows
     the same "your browser blocked playback" toast for every one of them. They are not
     the same problem and they do not want the same answer:
       NotAllowedError    the browser wants a real tap - never retry without one
       AbortError         our own stop interrupted the start: a race, retry once
       NotSupportedError  this device cannot decode that stream - do not retry
       NotReadableError   the device failed to read or decode it - do not retry
     Device WebViews differ in which of these they raise, which is how "some tablets fail"
     happens with no fault in the app. */
  var PLAYFAIL = { last: "", retriedAt: 0, gestureNeeded: false, unsupported: false, probed: false };

  function playError() {
    var d = window.__dbg;
    try { return d && d.audioState ? String(d.audioState().err || "") : ""; }
    catch (e) { return ""; }
  }

  function notePlayError() {
    var err = playError();
    if (!err || err === PLAYFAIL.last) return;
    PLAYFAIL.last = err;
    var now = Date.now();

    if (err === "AbortError") {
      /* a start we interrupted ourselves: settle, then retry once, quietly */
      if (now - PLAYFAIL.retriedAt > 2500) {
        PLAYFAIL.retriedAt = now;
        window.setTimeout(function () {
          if (!info().playing && playError() === "AbortError") playPlayback();
        }, 700);
      }
      return;
    }
    if (err === "NotAllowedError") { PLAYFAIL.gestureNeeded = true; return; }
    if (err === "NotSupportedError" || err === "NotReadableError") { PLAYFAIL.unsupported = true; }
  }

  /* 10 ms of silence, built in place so nothing has to be fetched. */
  function silentWav() {
    var n = 160, buf = new ArrayBuffer(44 + n), v = new DataView(buf);
    function tag(off, s) { for (var i = 0; i < s.length; i++) v.setUint8(off + i, s.charCodeAt(i)); }
    tag(0, "RIFF"); v.setUint32(4, 36 + n, true); tag(8, "WAVEfmt ");
    v.setUint32(16, 16, true); v.setUint16(20, 1, true); v.setUint16(22, 1, true);
    v.setUint32(24, 8000, true); v.setUint32(28, 8000, true);
    v.setUint16(32, 1, true); v.setUint16(34, 8, true);
    tag(36, "data"); v.setUint32(40, n, true);
    for (var i = 0; i < n; i++) v.setUint8(44 + i, 128);
    var bytes = new Uint8Array(buf), s = "";
    for (var j = 0; j < bytes.length; j++) s += String.fromCharCode(bytes[j]);
    try { return "data:audio/wav;base64," + btoa(s); } catch (e) { return null; }
  }

  /* Whether a stream can start without a tap is a property of the device, so it is probed
     rather than assumed - and the answer changes the resume policy. */
  function probeAutoplay(cb) {
    var url = silentWav(), done = false, a = null;
    function finish(needed) {
      if (done) return;
      done = true;
      PLAYFAIL.gestureNeeded = !!needed;
      PLAYFAIL.probed = true;
      try { if (a) { a.pause(); a.removeAttribute("src"); } } catch (e) { }
      if (cb) cb(PLAYFAIL.gestureNeeded);
    }
    if (!url) { finish(PLAYFAIL.gestureNeeded); return; }
    try {
      a = new Audio(url);
      a.volume = 0;
      var pr = a.play();
      if (pr && pr.then) {
        pr.then(function () { finish(false); })
          .catch(function (e) { finish((e && e.name) === "NotAllowedError"); });
      } else {
        finish(false);
      }
      window.setTimeout(function () { finish(PLAYFAIL.gestureNeeded); }, 1500);
    } catch (e) {
      finish(true);
    }
  }

  /* --------------------------------------------------- who gets the sound ---- */

  /* --------------------------------------------------------- system check ---- */

  function canPlay(mime) {
    try { return !!doc.createElement("audio").canPlayType(mime); } catch (e) { return false; }
  }

  function deviceFacts() {
    var raw = null, d = {};
    try {
      if (host && typeof host.device === "function") {
        raw = host.device();                            // Android: a synchronous JSON string
      } else if (window.__wrDevice) {
        raw = window.__wrDevice;                        // iOS: injected before the page ran
      }
    } catch (e) { raw = null; }
    /* the Android bridge hands back a JSON string; a browser test or iOS hands back an object */
    if (typeof raw === "string") {
      try { d = JSON.parse(raw) || {}; } catch (e) { d = {}; }
    } else if (raw && typeof raw === "object") {
      d = raw;
    }
    d.platform = d.platform || (ios ? "ios" : (host ? "android" : "web"));
    d.viewport = window.innerWidth + "x" + window.innerHeight;
    d.dpr = String(window.devicePixelRatio || 1);
    return d;
  }

  function checkRows() {
    var rows = [], dev = deviceFacts();
    function row(s, l, d) { rows.push({ s: s, l: l, d: d }); }

    row("ok", "Device", [dev.manufacturer, dev.model].filter(Boolean).join(" ") || "unknown");

    /* The compatibility verdict is decided by the native side, which reads the same
       compat.json the install-time gate uses - so this panel and the launch notice can
       never disagree about whether this device is supported. */
    var c = dev.compat || null;
    if (c) {
      var sysLine = c.system || "unknown";
      if (!c.supported) {
        row("warn", "System", sysLine + " \u2014 not supported: this app needs " +
            (c.hardLabel || "a newer release") + " or newer");
      } else if (!c.recommended) {
        row("warn", "System", sysLine + " \u2014 below the " + (c.softLabel || "newer release") +
            " this app is tuned for; it runs, with rough edges");
      } else {
        row("ok", "System", sysLine + " \u2014 supported");
      }
      row("info", "App needs", (c.hardLabel || "?") + " or newer \u00b7 tuned for " +
          (c.softLabel || "?") + " or newer");
    } else {
      /* No native verdict (the page opened in a plain browser, or an older shell):
         report what the page itself can see instead of inventing an answer. */
      row("info", "System", (dev.platform || "web") + " " +
          (dev.release || dev.osVersion || dev.api || "n/a"));
      row("info", "App check", "not reported by this shell \u2014 open the app for the full check");
    }

    /* The engine, not the OS version, is what decides which streams can decode. */
    var engineLabel = dev.engineLabel || (dev.platform === "ios" ? "WebKit" : "WebView");
    var engineValue = dev.engine || dev.webview || "n/a";
    row(engineValue === "n/a" ? "info" : "ok", engineLabel, engineValue);

    row("ok", "Screen", dev.viewport + " @ " + dev.dpr + "x");

    var mp3 = canPlay("audio/mpeg"), aac = canPlay('audio/mp4; codecs="mp4a.40.2"'),
        hls = canPlay("application/vnd.apple.mpegurl") || canPlay("application/x-mpegURL");
    row(mp3 ? "ok" : "warn", "MP3", mp3 ? "supported" : "not supported \u2014 MP3 stations will fail here");
    row(aac ? "ok" : "warn", "AAC", aac ? "supported" : "not supported \u2014 AAC stations will fail here");
    row(hls ? "ok" : "warn", "HLS streams", hls ? "supported" : "not supported \u2014 HLS stations are hidden by the Playable filter");

    var total = 0, playable = 0;
    try { total = window.__dbg.ALL(); playable = window.__dbg.playableCount(); } catch (e) { }
    row(total ? "ok" : "warn", "Stations", total
      ? (playable.toLocaleString() + " of " + total.toLocaleString() + " playable on this device")
      : "catalogue not loaded yet");

    row(PLAYFAIL.probed ? (PLAYFAIL.gestureNeeded ? "warn" : "ok") : "info", "Start without a tap",
        !PLAYFAIL.probed ? "not tested yet \u2014 Run again to find out (it plays nothing)"
        : (PLAYFAIL.gestureNeeded ? "this device needs play to be pressed" : "allowed"));

    var st = false;
    try {
      localStorage.setItem("rbg-wr-probe", "1");
      st = localStorage.getItem("rbg-wr-probe") === "1";
      localStorage.removeItem("rbg-wr-probe");
    } catch (e) { }
    row(st ? "ok" : "warn", "Saving", st ? "favourites and volume persist" : "blocked \u2014 favourites won't survive a restart");

    var nf = dev.notifications;
    row(nf === false ? "warn" : "ok", "Notifications",
        nf === false ? "not granted \u2014 no lock-screen controls" : (nf === true ? "granted" : "not reported"));

    row("ok", "Sound sharing", "the app asks for no exclusive audio, so the radio mixes normally");

    row(navigator.onLine ? "ok" : "warn", "Network",
        navigator.onLine ? "online" : "offline \u2014 the list still works, streams won't");

    return rows;
  }

  function renderCheck() {
    var box = $("#wrCheckRows");
    if (!box) return null;
    var rows = checkRows(), bad = 0;
    box.innerHTML = rows.map(function (r) {
      if (r.s === "warn") bad++;
      return '<div class="wr-crow ' + r.s + '"><b>' +
             (r.s === "ok" ? "\u2713" : (r.s === "info" ? "\u00b7" : "!")) +
             "</b><i>" + r.l + "</i><span>" + r.d + "</span></div>";
    }).join("");
    var head = $("#wrCheckHead");
    if (head) head.textContent = bad ? (bad + (bad === 1 ? " to note" : " to note")) : "all good";
    return { rows: rows, bad: bad };
  }

  function checkReport() {
    var rows = checkRows();
    var dev = deviceFacts();
    var L = ["MODDYS World Radio \u2014 system check",
             (dev.platform || "?") + " \u00b7 app " + (dev.app || "?"), ""];
    rows.forEach(function (r) { L.push((r.s === "ok" ? "[ok]  " : (r.s === "info" ? "[--]  " : "[!]   ")) + r.l + ": " + r.d); });
    L.push("", "UA: " + navigator.userAgent);
    return L.join("\n");
  }

  function copyText(text) {
    var ta = doc.createElement("textarea");
    ta.value = text;
    ta.setAttribute("readonly", "");
    ta.style.cssText = "position:fixed;top:-1000px;opacity:0";
    doc.body.appendChild(ta);
    var ok = false;
    try { ta.select(); ok = doc.execCommand("copy"); } catch (e) { ok = false; }
    doc.body.removeChild(ta);
    if (!ok && navigator.clipboard) {
      try { navigator.clipboard.writeText(text); ok = true; } catch (e) { ok = false; }
    }
    return ok;
  }

  function toggleCheck() {
    var on = sheetEl && !sheetEl.classList.contains("wr-check");
    if (sheetEl) {
      if (on) {
        /* One panel at a time, and switching the visualiser off is the only honest way to
           say so: leaving it running would have the tick put it straight back. */
        vizCycleTo(0);
        syncViz();
      }
      sheetEl.classList.toggle("wr-check", on);
    }
    if (on) renderCheck();
    if (sheetCtl) sheetCtl.measure();
    var nav = sheetEl && sheetEl.querySelector('[data-wr="check"]');
    if (nav) nav.classList.toggle("wr-on", !!on);
    return !!on;
  }

  /* Mark the page as being inside the app. The site's own "Download the App" block (if it has
     one - see the snippet that ships with the site) hides itself off this class, because a
     download button inside the app is nonsense. Nothing else depends on it. */
  function markAsApp() {
    try { doc.documentElement.classList.add("wr-app"); } catch (e) { }
  }

  /* -------------------------------------------------------------- assemble ---- */

  markAsApp();
  killPopOut();
  externalLinks();
  suppressDiskNotice();
  keepHeaderStatic();
  injectThemeCss();
  applyTheme(themeNow(), false);

  /* The shell is injected by the native side, and on iOS that happens at the end of parsing -
     which can be before the page has built its own bar. Waiting is what makes the player appear
     on both platforms whichever came first; on Android the page is always loaded by the time
     the shell injects, so the first attempt succeeds and nothing is deferred. */
  var bootTries = 0;

  function boot() {
    var sheet = buildSheet();
    sheetEl = sheet;
    if (!sheet) {
      if (++bootTries <= 40) { window.setTimeout(boot, 250); return; }     // up to 10 seconds
      report("shim: no #bar found after 10s - player sheet not built");
      return;
    }
    /* No audio is touched at startup. v1.4.0 probed whether a tap is needed by playing
       silence on launch - with no user gesture the WebView can refuse that probe, which
       falsely marked the device as needing a tap AND poked the device's audio focus at
       exactly the wrong moment. The answer now comes from a real failure, or from the
       on-demand check (which runs with a gesture, so it tells the truth). */
    var ctl = sheetController(sheet);
    sheetCtl = ctl;
    var syncNow = mirror(ctl);
    buildViz();

    // one cheap ticker drives both the sheet mirror and the shell's own state report
    var tick = function () {
      syncNow();
      syncViz();
      notePlayError();
      var s = info();
      var playing = s.playing;
      var chip = $("#wrChip");
      if (chip) {
        chip.textContent = playing ? "LIVE" : (s.error ? "ERROR" : "IDLE");
        chip.className = "wr-chip" + (playing ? " wr-live" : "");
      }
      pressLabel(playing);
      pushState(false);
      pushNowPlaying();
    };
    var playBtn = sheet.querySelector('[data-wr="play"]');
    var playBig = sheet.querySelector('[data-wr="play2"]');
    function pressLabel(playing) {
      var short = playing ? "⏸" : "▶";
      if (playBtn && playBtn.textContent !== short) playBtn.textContent = short;
      var full = playing ? "⏸ Pause" : "▶ Play";
      if (playBig && playBig.textContent !== full) playBig.textContent = full;
    }
    setInterval(tick, 1000);
    tick();

    // the page sets the mini-player's bar visible when a station is chosen
    var visTimer = setInterval(function () {
      var bar = $("#bar");
      if (!bar) return;
      var shown = bar.style.display && bar.style.display !== "none";
      if (shown !== SHEET.visible) {
        SHEET.visible = shown;
        sheet.style.display = shown ? "block" : "none";
        ctl.measure();
      }
    }, 700);
    SHEET.visible = true;

    /* coming back to the app: the state may have moved on while we were away */
    doc.addEventListener("visibilitychange", function () {
      if (!doc.hidden && sheetCtl) sheetCtl.measure();
    });

    window.addEventListener("beforeunload", function () {
      clearInterval(tick);
      clearInterval(visTimer);
      native("state", [false, "", ""]);
    });

    report("shim ready | station=" + (info().cur || "none"));
  }

  boot();

  /* The shell drives playback through these; keep the names stable. */
  window.__wr = {
    play: playPlayback,
    pause: pausePlayback,
    toggle: function () { if (info().playing) { pausePlayback(); } else { playPlayback(); } },
    info: info,
    sheet: function () { return SHEET; },
    /* the per-device check */
    check: renderCheck,
    checkToggle: toggleCheck,
    checkReport: checkReport,
    /* Opened from the launch compatibility notice: expand the player AND show the check
       panel, so "what can this device actually play?" is one tap from the warning. */
    compat: function () {
      if (sheetEl) {
        if (!sheetEl.classList.contains("wr-check")) {
          if (sheetEl.classList.contains("wr-viz")) { vizCycleTo(0); syncViz(); }
          sheetEl.classList.add("wr-check");
          var nav = sheetEl.querySelector('[data-wr="check"]');
          if (nav) nav.classList.add("wr-on");
        }
        if (sheetCtl) { sheetCtl.measure(); sheetCtl.setOpen(true); }
      }
      return renderCheck();
    },
    probe: probeAutoplay,
    playfail: function () {
      return { last: PLAYFAIL.last, gestureNeeded: PLAYFAIL.gestureNeeded,
               unsupported: PLAYFAIL.unsupported, probed: PLAYFAIL.probed };
    },
    theme: function (mode) { return applyTheme(mode || (themeNow() === "light" ? "dark" : "light")); },
    random: randomStation,
    viz: function (i) {
      if (i == null) { return syncViz(); }
      vizCycleTo(i + 1);
      syncViz();
      if (sheetCtl) sheetCtl.measure();
      return vizState();
    },
    vizOff: function () {
      vizCycleTo(0);
      syncViz();
      if (sheetCtl) sheetCtl.measure();
      return vizState();
    }
  };
})();
