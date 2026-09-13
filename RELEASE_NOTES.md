# MODDYS World Radio v1.6.7

Housekeeping: two lines of dead code out of the page, and the bundled page refreshed.

## What changed

**Two lines nobody was using, gone.** The page carried a channel-message counter and a
recent-stations helper that had been written once and never read. They are out of `index.html`,
and the counter also leaves `popout.html`.

Nothing else moved. The toolbar, the search, the filters, Saved, 🎲 Random, the cover-picture
pop-out and the hand-over all behave exactly as they did in 1.6.6 - which is the point: the same
213-check suite that drives the real page passes on the cleaned page exactly as it passes on what
moddys.net serves.

**The bundled page is the current one**, byte for byte.

## Verified

- the suite that drives the real page, against the cleaned page: 213 checks, 0 failed
- the same suite against what moddys.net serves: 213 checks, 0 failed
- the downloads suite: 84 checks, 0 failed, including the "the new file has not been uploaded
  yet" fallback the site is in the middle of during this release
- the layout sweep over 14 device shapes (Android 320 / 360 / 412, landscape, 800 tablet; iPhone
  SE / 15 / 15 Pro Max; iPad mini / Pro; macOS 1280 / 1440; Windows 1366 / 1920): 14 measured,
  0 broken
- `tools/shim_harness.py` drives the real shell over both bridges: 76 checks, 0 failed - and the
  same harness under WebKit, the engine iOS runs: 76 checks, 0 failed
- the bundled `index.html` is byte-identical to the one this release puts on the site

## Install

Open the app: it offers this itself - **Update now**, one tap. Your stations and favourites stay.

## Cheers Moddy !
