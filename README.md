# MODDYS World Radio — Android

**SPRAY IT LOUD, in your pocket.** Your existing MODDYS World Radio web app, wrapped in
the Android shell it never had: it keeps playing when you leave the app or lock the
screen, puts the current station on your lock screen with play/pause, and doesn't lose
the ~8,300 stations that stream over plain `http://`.

The web app is untouched. If `moddys.net` changes, rebuild and you have the new version.

## What it does

- **Background playback.** A `mediaPlayback` foreground service keeps the stream alive
  after you leave the app or turn the screen off.
- **Lock-screen controls.** A media notification shows the current station with
  play/pause and stop. Live radio has no seek, so pause and stop both mean
  "disconnect" — which is exactly what the site's stop button does.
- **Cleartext streams kept.** Android blocks `http://` by default; the network security
  config allows it, because 8,330 of the 47,994 stations are cleartext. HTTPS stations
  are unaffected and certificate checks are *not* bypassed.
- **Same theme, same data.** The real `index.html`, `stations.js` and `countries.js` are
  bundled, so the station list works with no network — only the streams need it.
- **Favourites persist** across launches (the site's `localStorage` works normally).
- **Touch-correct.** Double-tap-zoom lag, tap highlights and rubber-band scroll are
  disabled; the desktop-only pop-out button is hidden (a phone has no second window).

## Quick start

The build runs in GitHub Actions, so you don't need the Android SDK on your machine.

1. Push this repo (or just open **Actions → Build APK → Run workflow**).
2. Download the **world-radio-debug-apk** artifact when it goes green.
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
**Build APK** from the Actions tab with `sync_from_site` checked.

## How it fits together

| file | role |
|---|---|
| `MainActivity.java` | hosts the web app in a WebView; loads `_android_shim.js` after each page load |
| `PlaybackService.java` | foreground service + `MediaSession` + notification; never touches audio itself |
| `assets/www/_android_shim.js` | the only new front-end code: reads playback state, exposes `window.__wr`, hides the pop-out |
| `res/xml/network_security_config.xml` | allows cleartext for the `http://` stations |

Playback stays in the page — its own single-owner model is not re-implemented. The shell
reads state from the page's `window.__dbg` API and drives the page's existing play/stop
controls; if `window.__dbg` ever goes away, playback still works and only the
notification's station name is lost.

## Known limitations (v1)

- **Audio focus isn't requested**, so a phone call or another music app may play over the
  radio instead of pausing it. Worth adding next.
- **No sign-in-free release signing yet** — v1 ships debug builds. A release keystore is
  needed before uploading to Google Play.
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
