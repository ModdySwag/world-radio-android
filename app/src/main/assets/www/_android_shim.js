/* Android shell shim for the MODDYS World Radio web app.
 *
 * Injected once, after the page's own scripts have run. It does three things and no
 * more:
 *   1. reads authoritative playback state (preferring the page's own window.__dbg API,
 *      falling back to the media element's events) and reports changes to the shell,
 *   2. exposes window.__wr so the notification / lock-screen controls can drive the
 *      page's existing play and stop controls - no playback logic is duplicated here,
 *   3. removes the desktop-only bits (the pop-out opens a second window, which a phone
 *      does not have) and makes tap targets behave on touch.
 *
 * If window.__dbg ever disappears, playback still works: state then comes from the
 * media events, and the notification simply has no station title to show.
 */
(function () {
  "use strict";
  if (window.__wr) return;                       // already injected

  var host = window.WorldRadioJs;

  function report(message) {
    try { if (host && host.log) host.log(String(message)); } catch (e) { /* shell gone */ }
  }

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

  function click(selector) {
    var el = document.querySelector(selector);
    if (el && !el.disabled) { el.click(); return true; }
    return false;
  }

  window.__wr = {
    play: function () {
      // the page's own play control resumes the current station
      if (!click("#bPlay")) report("play: no #bPlay control on this page");
    },
    pause: function () {
      var d = window.__dbg;
      if (d && typeof d.stop === "function") {
        try { d.stop(); return; } catch (e) { /* fall back to the button */ }
      }
      if (!click("#bStop")) report("pause: no stop control on this page");
    },
    toggle: function () { if (info().playing) { window.__wr.pause(); } else { window.__wr.play(); } },
    info: info
  };

  var last = null;
  function push(force) {
    var s = info();
    var key = (s.playing ? "1" : "0") + "|" + (s.cur || "") + "|" + (s.error || "");
    if (!force && key === last) return;
    last = key;
    try {
      if (host && host.state) host.state(!!s.playing, s.cur || "", s.error || "");
    } catch (e) { /* shell gone */ }
  }

  setInterval(push, 1000);                       // cheap poll; only reports on change

  // Media events do not bubble, but they do take the capture path to the document.
  ["play", "playing", "pause", "ended", "stalled", "error", "suspend"].forEach(function (ev) {
    document.addEventListener(ev, function () {
      setTimeout(function () { push(true); }, 250);   // let the page settle first
    }, true);
  });

  window.addEventListener("beforeunload", function () {
    try { if (host && host.state) host.state(false, "", ""); } catch (e) { /* ignore */ }
  });

  // ---- desktop-only affordances and touch behaviour -------------------------
  var style = document.createElement("style");
  style.textContent = [
    "#btnPop{display:none !important}",                 // no second window on a phone
    "*{-webkit-tap-highlight-color:rgba(255,62,165,.22)}",
    "button,.btn,.play,.iconbtn{touch-action:manipulation}",  // kill double-tap zoom lag
    "body{overscroll-behavior:none}"                    // no rubber-band pull-to-refresh
  ].join("");
  (document.head || document.documentElement).appendChild(style);

  push(true);
  report("shim ready | stations=" +
    (window.__dbg && typeof window.__dbg.ALL === "function" ? window.__dbg.ALL() : "?"));
})();
