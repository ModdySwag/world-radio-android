/* ============================================================================
   The decorative visualiser's five looks, in one place.

   Two windows draw this: the main page's canvas sits in its player bar, and the mini player
   draws the same look under its controls once it is the one playing. Each host owns its own
   canvas, its own size and its own idea of how loud things are - only the drawing and the
   motion are shared, so the two can never drift into different looks.

   Radio streams are cross-origin, so this is a look, not a live spectrum. It reads playback
   state, never audio samples.

       Viz.STYLES                                    style names, in cycle order
       Viz.draw(ctx, width, height, levels, style)    paint one frame
       Viz.step(levels, dt, active, reduced, now)     advance the levels, in place

   `levels` is 56 numbers between 0 and 1, and `active` is how alive the animation should be:
   1 while this window is playing, less when something else owns the audio, a whisper when
   nothing is playing at all.
   ============================================================================ */
window.Viz = (function () {
  "use strict";

  var STYLES = ["bars", "waves", "mirror", "dots", "blocks"];

  function draw(ctx, W, H, levels, style) {
    ctx.clearRect(0, 0, W, H);
    var n = levels.length, bw = W / n;

    /* one gradient for every style: the site's pink-to-cyan spray, bottom to top */
    var grad = ctx.createLinearGradient(0, H, 0, 0);
    grad.addColorStop(0, "#ff3ea5");
    grad.addColorStop(.35, "#ff9f1c");
    grad.addColorStop(.6, "#ffe14d");
    grad.addColorStop(.82, "#c9ff3d");
    grad.addColorStop(1, "#1fe3ff");
    ctx.fillStyle = grad;
    ctx.strokeStyle = grad;

    var i, j, h, x, y, lv, cols;

    if (style === 0) {                       /* bars */
      for (i = 0; i < n; i++) {
        h = levels[i] * H * 0.92 + 2;
        ctx.fillRect(i * bw + bw * 0.16, H - h, bw * 0.68, h);
      }
    } else if (style === 1) {                /* waves - a line and its reflection */
      ctx.lineWidth = Math.max(1.5, W * 0.004);
      ctx.shadowColor = "#1fe3ff";
      ctx.shadowBlur = 9;
      ctx.beginPath();
      for (i = 0; i < n; i++) {
        x = i * bw + bw / 2;
        y = H / 2 - (levels[i] - 0.32) * H * 0.62;
        i ? ctx.lineTo(x, y) : ctx.moveTo(x, y);
      }
      ctx.stroke();
      ctx.globalAlpha = .45;
      ctx.beginPath();
      for (i = 0; i < n; i++) {
        x = i * bw + bw / 2;
        y = H / 2 + (levels[i] - 0.32) * H * 0.55;
        i ? ctx.lineTo(x, y) : ctx.moveTo(x, y);
      }
      ctx.stroke();
      ctx.globalAlpha = 1;
      ctx.shadowBlur = 0;
    } else if (style === 2) {                /* mirror - bars above and below the middle */
      for (i = 0; i < n; i++) {
        h = levels[i] * H * 0.45 + 1.5;
        ctx.fillRect(i * bw + bw * 0.16, H / 2 - h, bw * 0.68, h);
        ctx.fillRect(i * bw + bw * 0.16, H / 2, bw * 0.68, h);
      }
    } else if (style === 3) {                /* dots - a stack of six per column */
      cols = n / 2 | 0;
      for (i = 0; i < cols; i++) {
        lv = Math.round(levels[i * 2] * 6);
        for (j = 0; j < lv; j++) {
          ctx.beginPath();
          ctx.arc(i * (W / cols) + (W / cols) / 2, H - 6 - j * ((H - 14) / 6),
                  Math.min((W / cols) * 0.3, 4), 0, 6.283);
          ctx.fill();
        }
      }
    } else {                                 /* blocks - a six-high grid, dim where it is empty */
      cols = n / 2 | 0;
      for (i = 0; i < cols; i++) {
        lv = Math.ceil(levels[i * 2] * 6);
        for (j = 0; j < 6; j++) {
          ctx.globalAlpha = j < lv ? 1 : .15;
          ctx.fillRect(i * (W / cols) + 2, H - 4 - (j + 1) * ((H - 10) / 6),
                       Math.max(2, (W / cols) - 4), Math.max(1, (H - 10) / 6 - 2));
        }
      }
      ctx.globalAlpha = 1;
    }
    ctx.shadowBlur = 0;                      /* never leak glow into the next frame */
  }

  function step(levels, dt, active, reduced, now) {
    var n = levels.length, base, tgt;
    for (var i = 0; i < n; i++) {
      base = 0.5 + 0.5 * Math.sin(now * 1.7 + i * 0.35)
           + 0.35 * Math.sin(now * 3.1 + i * 0.11)
           + 0.25 * Math.sin(now * 5.3 - i * 0.7);
      tgt = Math.max(0, Math.min(1,
        (base * 0.5 + (Math.sin(now * 9 + i) * 0.5 + 0.5) * 0.28) * active
        + (reduced ? 0 : (Math.random() - 0.5) * 0.1 * active)));
      levels[i] += (tgt - levels[i]) * Math.min(1, dt * (4 + 7 * active));
    }
  }

  return { STYLES: STYLES, draw: draw, step: step };
})();
