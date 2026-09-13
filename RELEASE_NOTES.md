# MODDYS World Radio v1.6.9

The mini player is a window now: the site gets out of its way, and you can drag it, size it, and put it
anywhere on the screen.

## What changed

**The mini player takes the screen.** Pressing the cover picture on a phone or a tablet used to leave the
station list sitting behind the player - two things competing for one screen. While the player is up the
page now stands down: header, toolbar, station list and the bottom bar all go, and the mini player is what
you have. Close it with ✕, with a tap on the backdrop, or with Escape, and the page comes straight back.

**It moves, and it resizes.** Drag its title bar to put it anywhere; drag the grip in its corner for any
size in between. The box you leave it in is remembered for next time.

**S / M / L mean what they say.** S is a small player - genuinely small, 320x250 at most instead of the old
430x330 - M is half the screen, L is the whole screen. The size in use is lit up, and nothing else changes
between them.

**A touch screen always gets the docked player.** A tablet switched to "desktop site" reports a desktop
user agent, and with that it was still being sent to a full-screen tab where there was nothing to minimise.
Any finger-only screen now docks as well as any mobile browser, and it opens as the small player - never
full screen.

**The player inside fits whatever size the window is.** The docked page reflows at every size from the
corner grip, and its controls stay reachable at the smallest one.

**The bundled page is the current one**, byte for byte.

## Verified

- the suite that drives the real page, against this page: 236 checks, 0 failed - including a twenty-check
  section that runs the player as a tablet at 800x1280 and 1280x800: it docks, opens no tab, the site
  stands down behind it, it opens as the small player and not full screen, M is half the screen, L is all
  of it, the title bar drags it, the corner resizes it, and the docked page is a player rather than a
  minimised page
- the same suite against what moddys.net serves: 236 checks, 0 failed
- the layout sweep over 14 device shapes (Android 320 / 360 / 412, landscape, 800 tablet; iPhone
  SE / 15 / 15 Pro Max; iPad mini / Pro; macOS 1280 / 1440; Windows 1366 / 1920): 14 measured,
  0 broken, locally and against the live site
- `tools/shim_harness.py` drives the real shell over both bridges: 76 checks, 0 failed
- the downloads suite: 84 checks, 0 failed, and 28 more against the live feed, the file it points
  at, and the sha256 of what the host really serves
- the bundled `index.html` is byte-identical to the one this release puts on the site
  (sha256 `38b1b8b41ba29259…`, read out of the published APK, not off a build log)
- `tools/apk_identity.py` on the published APK: versionName `1.6.9`, versionCode 17, no `.debug`
  suffix, the same signing certificate as every release before it: 27 checks, 0 failed

## Install

Open the app: it offers this itself - **Update now**, one tap. Your stations and favourites stay.

## Cheers Moddy !
