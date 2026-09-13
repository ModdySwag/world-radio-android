# MODDYS World Radio v1.6.8

A tablet gets a mini player, not a full page with four buttons on it.

## What changed

**The mini player on a tablet.** Pressing the cover picture on a phone or a tablet used to send the
player to a new browser tab, where it came up in its minimised state - so a tablet showed a mostly
empty page with a play button and a "click anywhere to expand" note on it, and what it promised to
expand into was three empty lists. A mobile browser has no pop-up *window* to put the player in
(Chrome on Android and Safari on iOS both turn the request into a whole tab), so on a mobile
browser the player now opens **docked in the page** instead: the same compact player a phone gets
when a pop-up is blocked, with its transport, artwork, volume and lists in one place. Desktop
browsers keep the real window and its click-to-expand, untouched.

**A wide tab is a mini player too.** If the player page ever is opened as a tab, it is held to a
centred column instead of being stretched across the whole screen, and it is never minimised:
the small-window start belongs to a real pop-up window, which only a desktop has.

**The docked player sits above the bar, not behind it.** The bar is fixed to the bottom edge and is
taller than the dock's own inset, so the bottom of the docked player - the volume row and the
footer - was painted over by the bar.

**The bundled page is the current one**, byte for byte.

## Verified

- the suite that drives the real page, against this page: 226 checks, 0 failed - including a new
  eight-check section that runs the player at 800x1280 and 1280x800 as a tablet: the player docks,
  opens no tab, sits clear of the bar, and is a player rather than a minimised page
- the same suite against what moddys.net serves: 226 checks, 0 failed
- the layout sweep over 14 device shapes (Android 320 / 360 / 412, landscape, 800 tablet; iPhone
  SE / 15 / 15 Pro Max; iPad mini / Pro; macOS 1280 / 1440; Windows 1366 / 1920): 14 measured,
  0 broken, locally and against the live site
- `tools/shim_harness.py` drives the real shell over both bridges: 76 checks, 0 failed
- the downloads suite: 84 checks, 0 failed, and 28 more against the live feed, the file it points
  at, and the sha256 of what the host really serves
- the bundled `index.html` is byte-identical to the one this release puts on the site
  (sha256 `3c170425cc7e26eb…`, read out of the published APK, not off a build log)
- `tools/apk_identity.py` on the published APK: versionName `1.6.8`, versionCode 16, no `.debug`
  suffix, the same signing certificate as every release before it: 27 checks, 0 failed

## Install

Open the app: it offers this itself - **Update now**, one tap. Your stations and favourites stay.

## Cheers Moddy !
