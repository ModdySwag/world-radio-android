# MODDYS World Radio v1.6.4

A small one: the page inside the app catches up with the site, and this is the first release
that will reach you through the updater rather than by hand.

If you are on v1.6.3, open the app and it will offer this itself — **Update now**, one tap.

## What changed

**The bundled page is current again.** The one visible difference: pressing Pop out no longer
announces that a click is needed. On a phone a pop-out window usually cannot start audio until
it has been touched, and the old code stopped the page to find that out, then asked you for a
tap to get the music back. Now the page keeps playing, the player opens as a remote control,
and the first touch in it takes the stream over without a word.

Nothing native changed in this build — no new permissions, no new behaviour outside the page.

## Verified

- `tools/apk_identity.py` reads the published APK: versionName `1.6.4`, versionCode 12, no
  `.debug` suffix, same signing certificate as every release before it, and `compat.json`
  naming this build
- the bundled `index.html` is byte-identical to the one `moddys.net` serves
- `tools/shim_harness.py` drives the real shell over both bridges

## Install

Nothing to do if you are on v1.6.3 — the app offers the update itself. Otherwise download
`world-radio-v1.6.4.apk` and tap it.

Needs Android 8.0 or newer (API 26); tuned for Android 11+.

## Cheers Moddy !
