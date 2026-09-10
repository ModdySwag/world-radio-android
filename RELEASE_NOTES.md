# MODDYS World Radio v1.3.0

**It steps aside when something else needs to be heard.** 47,994 stations from 221
countries, in the theme you already know, and it keeps playing when you leave it.

## What's new in this version

- **The radio shares the sound properly now.** It asks Android for the audio when you
  press play, and behaves when another app wants it:
  - **A phone call** (or another player briefly taking over) pauses the stream. When the
    call ends, the station comes back on its own — the one you were on, not the first in
    the list.
  - **A navigation prompt or a notification sound** ducks the radio to a quarter volume
    for as long as it's needed, then hands your volume back. The stream keeps running, so
    there's no reconnect gap.
  - **Another app taking the output for good** (you started Spotify) pauses the radio and
    keeps it out of the way. It will not grab the sound back when that app stops.
- **The notification no longer disappears mid-call.** When a focus loss pauses the radio,
  the notification changes to a Play button and says why — *"Paused — another app needed
  the sound. Tap play to reconnect."* Swipe it away and it stops; while the radio is
  actually playing, the notification stays put as before.
- **And it takes the output when it starts**, so another app doesn't keep talking over
  the radio. If something else already holds the sound, the app stays quiet rather than
  playing underneath it.

## Install

1. Download **world-radio-v1.3.0.apk** from the Assets below — straight to your phone, or
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

- Some stations in the catalogue are genuinely dead. They fail visibly rather than hang.
- No version yet on the Play Store — this is a direct download.

Cheers Moddy !
