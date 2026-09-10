# MODDYS World Radio v1.4.0

**Plays on the tablets it wouldn't.** 47,994 stations from 221 countries, in the theme you
already know, and it keeps playing when you leave it.

## What's new in this version

- **"Your browser blocked playback" is fixed — and it was never one problem.** That single
  message covered four different failures: a browser that wants a real *tap*, our own stop
  interrupting a start, a stream this device cannot decode, and a device that failed to
  read one. Each now gets the right response:
  - an interrupted start is quietly retried once;
  - a stream this device cannot decode is reported instead of retried forever;
  - and a device that wants a tap **is never restarted without one**.
- **The radio no longer reconnects itself on devices that won't allow it.** After a phone
  call the stream used to come back on its own — the one moment a browser can refuse,
  because no finger was involved. On a device that needs a tap it now stays paused with the
  Play button and the reason on it, instead of failing with an error.
- **Another app wants the sound? You choose.** When something else takes over playback, the
  radio stops rather than talking over it and asks: **Continue here**, **Pause**, or
  **Stop**. If you're not looking at the app, the notification carries the same choice
  instead of interrupting you.
- **A system check, built in.** Tap **Check** in the expanded player for this device's
  real story: model, Android version, **WebView version**, which formats it can decode
  (MP3 / AAC / HLS), **how many of the 47,994 stations this device can actually play**,
  whether it needs a tap to start, whether favourites will persist, notifications, sound
  sharing and network. **Copy report** puts it on the clipboard to paste into a message.
- **Hardened for real-world tablets:**
  - a device with no usable WebView now says so, instead of crashing on launch;
  - a WebView renderer killed by low memory (common on tablets) rebuilds the player
    instead of leaving a blank screen with the audio gone;
  - the "no tap needed" setting is re-applied on every page load, because some devices
    lose it;
  - failed page loads are logged, so a device report has something to go on.

## If a station still won't play

Open the player, tap **Check**, then **Copy report** and send it on. It says exactly which
formats that device can decode and how many stations it can play, which is almost always
the answer. The **Playable** button in the player filters the list to just the stations
this device can actually play.

## Install

1. Download **world-radio-v1.4.0.apk** from the Assets below — straight to your phone, or
   on a computer and copy it across.
2. Open it. Android will ask you to allow installing from this source — the normal prompt
   for anything that isn't on the Play Store. Play Protect may say "unknown developer" for
   the same reason.
3. That's it. Needs **Android 8.0 or newer**.

Already on an earlier version? Install straight over the top — your favourites survive.

## Privacy & safety

Nothing is collected, and nothing leaves your phone except the stream you press play on.
No analytics, no ads, no third-party libraries at all — the app is Android's own WebView,
media session and notification APIs and nothing else. About 8,300 stations in the
catalogue stream over plain HTTP, so the app allows cleartext for them; HTTPS stations are
untouched and certificate checking is **not** weakened — a station with a broken
certificate fails, and the app says so instead of pretending.

## Known limitations

- Playback runs in the device's Android System WebView, so what plays depends on that
  **WebView version**, not the Android version. Updating "Android System WebView" (or
  Chrome, which supplies it on many devices) in the Play Store fixes most of it — and the
  built-in Check panel shows what any device can do.
- Some stations in the catalogue are genuinely dead. They fail visibly rather than hang.
- No version yet on the Play Store — this is a direct download.

Cheers Moddy !
