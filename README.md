# MODDYS World Radio — Android

**SPRAY IT LOUD, in your pocket.** Your existing MODDYS World Radio web app, wrapped in
the Android shell it never had: it keeps playing when you leave the app or lock the
screen, puts the current station on your lock screen with play/pause, and doesn't lose
the ~8,300 stations that stream over plain `http://`.

The web app itself is untouched. If `moddys.net` changes, rebuild and you have the new
version.

**There is an iOS twin**: [world-radio-ios](https://github.com/ModdySwag/world-radio-ios).
Both apps bundle the same site files and ship the **same shell script** — the player, the
theme, the visualiser and the compatibility panel are one file, so a fix lands on both.

## What it does

- **Background playback.** A `mediaPlayback` foreground service keeps the stream alive
  after you leave the app or turn the screen off.
- **Lock-screen controls.** A media notification shows the current station with
  play/pause and stop. Live radio has no seek, so pause and stop both mean
  "disconnect" — which is exactly what the site's stop button does.
- **One player, dragged open.** No pop-out, no second player. The bottom bar is the only
  player: drag it up for the full view (logo, station name, country · language · codec ·
  bitrate, a labelled Play, Details / Stop / Website, volume, and one-tap jumps to Search,
  Light/Dark, Favourites, Random, Playable, Reset, Top and Visual), drag it down or tap a
  header strip to fold it back to a bar.
- **Five visualiser styles, where you can actually see them.** The site's visualiser is
  decorative and hidden below 900px wide — i.e. on every phone. The shell lifts its canvas
  into the expanded player and adds style buttons: Bars, Waves, Mirror, Dots, Blocks.
- **Light mode.** The theme button re-skins the interface and the player together, and
  remembers your choice between launches.
- **Random.** One tap plays a station from whatever is currently listed, so it respects
  your filters, and flashes the one it picked.
- **A static header.** The site's "Header" collapse control is gone in the app: title,
  stats and tagline are always visible.
- **Shares the sound with the rest of the phone — by staying out of the argument.** The radio
  asks for no exclusive audio of its own: the engine that actually plays the stream (the
  device's WebView) handles that, the way any media app does. Earlier versions asked for it
  from the service as well, which made one app look like two — and produced an "another app
  wants the sound" dialog on devices where nothing else was playing. Both the request and the
  dialog are gone. If the stream stops for any reason, the notification stays up with a Play
  button, so there is always a way back.
- **It can be mixed with.** Because it claims no exclusive audio, some devices will play the
  radio alongside another app rather than pausing it. If you hear two things at once, pause
  the radio — it isn't fighting anything, but nothing is ducking for it either.
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
| `MainActivity.java` | hosts the web app in a WebView; loads `_shell_shim.js` after each page load; routes external links to the phone's browser |
| `PlaybackService.java` | foreground service + `MediaSession` + notification; never touches audio itself |
| `Compat.java` | reads `compat.json`, decides whether this device is supported, and shows the one-time notice |
| `assets/www/_shell_shim.js` | the only new front-end code: the player sheet, its visualiser styles and light mode, external-link routing, the banner suppression, and state reporting. **Byte-identical to the iOS app's copy** |
| `assets/www/compat.json` | the version thresholds the notice, the Check panel and the website all read |
| `res/xml/network_security_config.xml` | allows cleartext for the `http://` stations |
| `tools/shim_harness.py` | drives that shell in a real browser on all three bridge paths |

Playback stays in the page — its own single-owner model is not re-implemented. The shell
reads state from the page's `window.__dbg` API and drives the page's existing play/stop
controls; if `window.__dbg` ever goes away, playback still works and only the
notification's station name is lost.

Audio focus is the one thing the service owns that the page cannot: it asks the system for
the output and forwards each verdict to `window.__wr.focus('gain' | 'duck' |
'lossTransient' | 'loss')`. The page decides what to do about it, so "paused" still means
one thing in one place. Ducking goes through the page's own volume slider, because its
audio element is a detached `new Audio()` that nothing outside the page can reach.

### The player sheet is coupled to the page's markup, on purpose

The sheet does not reimplement playback, so it reaches for the page's own controls by id:
`#bPlay`, `#bStop`, `#bName` (opens the station details), `#bMeta`, `#bArt`, `#bState`,
`#vol` and `#btnBarSite`, plus the navigation targets `#q`, `#btnFav`, `#btnPlayable`,
`#btnReset`, `#toTop` and the cards' `[data-act="play"]` buttons that Random drives. The
visualiser is steered through `#btnViz` and its canvas `#viz` is moved into the sheet;
`#hdrHead` is held expanded so the header stays static.

It also hides `#btnPop`, `#dock`, `#bar` and `#btnHdr`, suppresses the disk notice, and
carries the light-mode palette — the page is dark-only, so those overrides live in one
block in the shell, each one measured against the page rather than guessed.

So if the web app ever renames those ids, the shell needs the matching edit — one place,
listed above. That is the deliberate trade for having one source of truth instead of two
players that can disagree.

## Compatibility

| | |
|---|---|
| Android | **8.0 (API 26) and newer**, up to 15/16-era tablets; `targetSdk 34` |
| Tuned for | **Android 11 (API 30)** and newer — below it, the app says so at launch and points you at the Check panel |
| Installing on something older | Android's own installer refuses: the APK declares `minSdkVersion 26`, so Play shows the requirement and a sideload fails with "app not compatible". Nothing is downloaded that cannot run. |
| Playback engine | the device's **Android System WebView** — its version, not the Android version, decides what plays |
| Formats | MP3 and AAC everywhere; **HLS (`.m3u8`) only where the WebView can decode it** (about 3,000 of the stations are HLS) |
| Cleartext | allowed app-wide: ~8,300 stations are plain `http://` |
| No WebView at all | the app says so and tells you what to install, instead of crashing |

**The compatibility notice.** Two levels, on purpose. The **hard** floor is 8.0, and Android
enforces it at install time. The **soft** floor is Android 11, and below it the app shows one
dismissible notice at first launch naming this device's version, the version it is tuned for,
and exactly which part is at risk — the System WebView, which is what decodes the streams and
is not something either you or the app can choose independently of the device. It appears once
per app version, and there is a button straight into the Check panel.

Both numbers live in `assets/www/compat.json`, which is also where the iOS app keeps its own
(`iOS 15` to install, `iOS 18` tuned) and where the website's download section reads them from.
One file per platform, one source of truth per number.

The **Check** panel in the expanded player reports the verdict, the WebView version, which
formats the device can decode, and how many of the 47,994 stations it can actually play — with
a **Copy report** button for sending that on. If stations misbehave on a device, that report
is the answer: it is almost always the WebView version or a missing codec.

The **Playable** filter in the player hides anything this device cannot decode, so nobody
gets stuck tapping stations that were never going to work.

## Known limitations (v1.x)

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
