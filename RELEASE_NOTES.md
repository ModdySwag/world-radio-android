# MODDYS World Radio v1.1.0

**One player, and it's yours to push around.** 47,994 stations from 221 countries, in the
theme you already know, and it keeps playing when you leave it.

## What's new in this version

- **One player, drag to open.** The pop-out mini player is gone — the app has a single
  player now, living in a sheet at the bottom. Drag it up for the full player, drag it down
  (or just tap the handle) to fold it back to a bar. You never have to wonder where
  playback went.
- **The full player shows what you're listening to.** Big logo and station name, plus the
  country, language, codec and bitrate. Alongside it: Details, Stop, Website, a volume
  slider, and a grid of one-tap jumps — Search, Filters, Favourites, Local, Playable,
  Reset, Top and Visual.
- **Station links work now.** The Website and Stream buttons used to do nothing at all in
  the app: Android blocks the way the site opens them, and with no second window there was
  nowhere for them to go. They now open in your phone's browser — where they belong — and
  the app itself is never navigated away from the radio.
- **The "you're viewing these files directly from disk" banner is gone.** It's true when
  you open the site from a folder on a computer; in the app it was never true.
- **Tidier underneath.** The player drives the site's own controls instead of duplicating
  them, so there's a single source of truth for playback and nothing to drift apart.

## Install

1. Download **world-radio-v1.1.0.apk** from the Assets below — straight to your phone, or
   on a computer and copy it across.
2. Open it. Android will ask you to allow installing from this source — the normal prompt
   for anything that isn't on the Play Store. Play Protect may say "unknown developer" for
   the same reason.
3. That's it. Needs **Android 8.0 or newer**.

Already on v1.0.0? Install straight over the top — your favourites survive.

## Privacy & safety

Nothing is collected, and nothing leaves your phone except the stream you press play on.
No analytics, no ads, no third-party libraries at all — the app is Android's own WebView,
media session and notification APIs and nothing else. About 8,300 stations in the
catalogue stream over plain HTTP, so the app allows cleartext for them; HTTPS stations are
untouched and certificate checking is **not** weakened — a station with a broken
certificate fails, and the app says so instead of pretending.

## Known limitations

- Another music app may play over the radio instead of pausing it. Audio focus is the next
  thing to fix.
- Some stations in the catalogue are genuinely dead. They fail visibly rather than hang.
- No version yet on the Play Store — this is a direct download.

Cheers Moddy !
