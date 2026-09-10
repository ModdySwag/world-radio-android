# MODDYS World Radio — Android

**SPRAY IT LOUD, in your pocket.** Your existing MODDYS World Radio web app, wrapped in
the Android shell it never had: it keeps playing when you leave the app or lock the
screen, puts the current station on your lock screen with play/pause, and doesn't lose
the ~8,300 stations that stream over plain `http://`.

The web app itself is untouched. If `moddys.net` changes, rebuild and you have the new
version.

## What it does

- **Background playback.** A `mediaPlayback` foreground service keeps the stream alive
  after you leave the app or turn the screen off.
- **Lock-screen controls.** A media notification shows the current station with
  play/pause and stop. Live radio has no seek, so pause and stop both mean
  "disconnect" — which is exactly what the site's stop button does.
- **One player, dragged open.** No pop-out, no second player. The bottom bar is the only
  player: drag it up for the full view (logo, station name, country · language · codec ·
  bitrate, Details / Stop / Website, volume, and one-tap jumps to Search, Filters,
  Favourites, Local, Playable, Reset, Top and Visual), drag it down or tap the handle to
  fold it back to a bar.
- **Cleartext streams kept.** Android blocks `http://` by default; the network security
  config allows it, because 8,330 of the 47,994 stations are cleartext. HTTPS stations
  are unaffected and certificate checks are *not* bypassed.
- **Station links go to your browser.** The site's Stream and Website buttons are
  `target="_blank"` links, which Android's WebView drops on the floor when there is no
  second window. They are handed to the phone's browser instead, so they work and the
  player is never navigated away from.
- **Same theme, same data.** The real `index.html`, `stations.js` and `countries.js` are
  bundled, so the station list works with no network — only the streams need it.
- **Favourites persist** across launches (the site's `localStorage` works normally).
- **Touch-correct.** Double-tap-zoom lag, tap highlights and rubber-band scroll are
  disabled. The `file://` "viewing the files directly from disk" banner is suppressed,
  because inside the app it is never true.

## Quick start

The build runs in GitHub Actions, so you don't need the Android SDK on your machine.

1. Push this repo (or just open **Actions → Build APK → Run workflow**).
2. Download the **world-radio-debug-apk** artifact when it goes green — or, for a real
   install, tag `v*` and take the signed APK from the Release.
3. Copy the `.apk` to your phone and open it, allowing "install unknown apps" for your
   file manager when prompted.

To build locally instead you need JDK 17 and the Android SDK (`platforms;android-34`,
`build-tools;34.0.0`), then:

```bash
gradle :app:assembleDebug        # -> app/build/outputs/apk/debug/app-debug.apk
```

## Refreshing the station data

`stations.js` and `countries.js` are snapshots. After `build.py` refreshes the
catalogue on your machine:

```bash
python3 tools/sync-assets.py              # copy from C:\Users\Moddy\radio-browser\deploy
python3 tools/sync-assets.py --from-url   # or pull whatever is live on moddys.net
```

Then commit the changed files. The CI workflow can also do the pull for you: run
**Build APK** from the Actions tab with `sync_from_site` checked. (The **release**
workflow never re-syncs: it ships exactly what is committed, so a release is
reproducible.)

## How it fits together

| file | role |
|---|---|
| `MainActivity.java` | hosts the web app in a WebView; loads `_android_shim.js` after each page load; routes external links to the phone's browser |
| `PlaybackService.java` | foreground service + `MediaSession` + notification; never touches audio itself |
| `assets/www/_android_shim.js` | the only new front-end code: the player sheet, external-link routing, the banner suppression, and state reporting |
| `res/xml/network_security_config.xml` | allows cleartext for the `http://` stations |

Playback stays in the page — its own single-owner model is not re-implemented. The shell
reads state from the page's `window.__dbg` API and drives the page's existing play/stop
controls; if `window.__dbg` ever goes away, playback still works and only the
notification's station name is lost.

### The player sheet is coupled to the page's markup, on purpose

The sheet does not reimplement playback, so it reaches for the page's own controls by id:
`#bPlay`, `#bStop`, `#bName` (opens the station details), `#bMeta`, `#bArt`, `#vol`,
`#btnBarSite`, `#btnViz`, plus the navigation buttons `#q`, `#btnFacets`, `#btnFav`,
`#btnLocal`, `#btnPlayable`, `#btnReset`, `#toTop`. It also hides `#bar` and `#dock` and
suppresses the disk notice.

So if the web app ever renames those ids, the shell needs the matching edit — one place,
listed above. That is the deliberate trade for having one source of truth instead of two
players that can disagree.

## Known limitations (v1.x)

- **Audio focus isn't requested**, so a phone call or another music app may play over the
  radio instead of pausing it. Worth adding next.
- **Broken streams still fail.** Some stations have expired or self-signed certificates;
  the app reports the error rather than papering over it.
- **The app icon is placeholder art** (a broadcast tower in your palette).

## Privacy & safety

Nothing is collected, nothing is transmitted anywhere except the radio streams you press
play on. There are no analytics, no ads, no third-party SDKs — the app has **zero
external dependencies**, only Android's own WebView, MediaSession and Notification APIs.
Cleartext HTTP is permitted because your catalogue needs it; that permits no certificate
weakening, and the only network traffic is the station stream itself.

Cheers Moddy !
