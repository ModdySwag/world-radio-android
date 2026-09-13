# MODDYS World Radio v1.7.0

You can add your own stations now: a quiet ➕ Add button, a short form, and anything that streams
audio ends up in your list next to the 47,000 already there.

## What changed

**Add a station.** ➕ Add - the last button in the toolbar, or just press `a` - opens a small form:
name, stream URL, format, bitrate, country, city, language, genres and website. The country field
suggests all 221 of them, the format is worked out from the address (with an override if you know
better than the URL does), and there is a short how-to under the fields for the first time you use it.

**It tells you when an address will not play, instead of adding it and leaving you with a dead card.**
A `.pls`, `.m3u` or `.xspf` playlist is refused on the spot - that is a file for a desktop player, not
a browser - and so is a DASH manifest, a web page, a missing name, and the same stream twice. Two
things are added but flagged rather than refused: an HLS address (Safari and Android Chrome only) and
a plain `http://` address on this secure page.

**Test stream, before you save.** It asks the station's own server what it is serving *and* lets the
browser try to decode it - muted, so nothing announces itself out of your phone - then says what it
found. One stream server that answers HEAD requests with an error is handled too (it is asked with GET,
and the body is dropped straight away). When it cannot confirm an address it says exactly that; it
never calls something playable on a guess.

**Your stations are yours.** They live in this app's own storage, are searchable and filterable along
with everything else, can be starred, played and removed (✕ on the card, or Remove in its details),
and they are never uploaded anywhere. No account, same as the rest of the app.

**A station you just added is never lost in the list.** It goes to the top, the search box is put back
to empty for it, and if a filter - or the Verified switch - would still hide it, the app says so and
offers the one tap that shows it.

**Nothing else moved.** The mini player, the docking and resizing, the hand-over, the toolbar's four
bars, the count and the pager are exactly as they were.

**The bundled page is the current one**, byte for byte - the same file the site serves.

## Verified

- the suite that drives the real page, against this page: 239 checks, 0 failed
- the same suite against what moddys.net serves: 239 checks, 0 failed
- the add-a-station suite (new with this release): 60 checks, 0 failed in Chromium and 60 in WebKit,
  and 59 against the live site - the one check that needs a same-origin audio file is skipped there
  and says so
- the layout sweep over 14 device shapes (Android 320 / 360 / 412, landscape, 800 tablet; iPhone
  SE / 15 / 15 Pro Max; iPad mini / Pro; macOS 1280 / 1440; Windows 1366 / 1920): 14 measured,
  0 broken
- `tools/shim_harness.py` drives the real shell over both bridges: 76 checks, 0 failed
- the downloads suite: 84 checks, 0 failed, and the live feed checks against the file and its sha256
- the bundled `index.html` is byte-identical to the one this release puts on the site
  (sha256 `8b842489ac5d8141ac3927718057e525`), and it is read back out of the published APK rather
  than off a build log
- `tools/apk_identity.py` on the published APK: versionName `1.7.0`, versionCode 18, no `.debug`
  suffix, the same signing certificate as every release before it

## Install

Open the app: it offers this itself - **Update now**, one tap. Your stations and favourites stay -
including the ones you added by hand.

## Cheers Moddy !
