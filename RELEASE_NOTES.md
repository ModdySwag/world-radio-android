# MODDYS World Radio v1.6.3

This one is about keeping up to date, and about the keyboard. The bundled web app also catches
up with the site — five revisions of player, search and pager work that shipped to the website
but not to the app until now.

`world-radio-v1.6.3.apk` installs over v1.6.2 (same signing key), so your saved stations,
favourites and volume survive the upgrade.

## What's new

**The app can now update itself.** On launch it reads a small file from the site
(`updates.json`) and compares it with its own version code. If a newer build exists you get a
notice at the top of the player:

> **Version 1.6.3 is available** — you are on 1.6.2. Update now installs it over this one; your
> saved stations and favourites stay.  [ **Update now** ]  [ Later ]

One tap downloads the new APK and hands it to Android's installer. The download is checked
against the checksum published in the feed before it is offered, so a half-finished download
cannot reach the installer pretending to be an app. The only thing Android insists on is its
own "install this app?" confirmation — that is deliberate and cannot be skipped by any app.

You will need "install unknown apps" allowed for World Radio, which you already granted when
you installed it this way. If the permission is missing, the app says so and opens the right
settings page instead of failing silently.

**The keyboard now goes away when you commit a search.** Pressing the magnifier or return in
the search box used to leave the keyboard sitting over the list. The page asks the shell to take
it down, and the shell really does it — a blur alone is not enough inside a WebView, where the
keyboard belongs to the system.

**The app now carries the website's latest work**, which it was missing:

- the pager's arrows are 44px touch targets, and the page number sits between them as plain
  text (the number box beside them is gone)
- Reset and Filters sit together with Filters on the right, so the toolbar is three rows on a
  phone instead of five
- the player bar steps aside while you search on a phone or tablet, and comes back when you play
- the same visualiser on the main page and in the player sheet, from one shared file

## Verified

- `tools/apk_identity.py` reads the published APK: versionName `1.6.3`, versionCode 11, no
  `.debug` suffix, v2+v3 signatures, and the same signing certificate as v1.6.2
- `tools/shim_harness.py` drives the real shell over both bridges — 70 checks, including the
  keyboard hand-off and the update notice: both routes to committing a search tell the shell to
  put the keyboard away, a newer build raises the notice, and its button hands the native side
  the URL, version and checksum
- the bundled `index.html` is byte-identical to the one `moddys.net` serves

## Install

1. Download `world-radio-v1.6.3.apk` to the phone or tablet.
2. Tap it. Android asks once to allow installs from this source — allow it, then tap Install.
3. From this version on, updates arrive as a notice in the app itself.

Needs Android 8.0 or newer (API 26). Tuned for Android 11+ — the System WebView version is what
decides which streams can play, and the app's Check panel reports yours.

## Cheers Moddy !
