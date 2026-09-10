# MODDYS World Radio v1.0.0

**SPRAY IT LOUD — in your pocket.** 47,994 stations from 221 countries, in the theme you
already know, and it keeps playing when you leave it.

## What it does

- **Keeps playing.** Lock the screen or switch apps and the station carries on. Play/pause
  and the station name sit on your lock screen and in the notification shade.
- **47,994 stations, 221 countries.** Filter by country, genre, codec and language, search
  the lot, and keep favourites — they survive closing the app.
- **Browse with no signal.** The whole station list is inside the app, so the list, the
  filters and your favourites work offline. Only the streams need a connection.
- **No ads, no accounts, no tracking.**

## Install

1. Download **world-radio-v1.0.0.apk** from the Assets below — straight to your phone, or
   on a computer and copy it across.
2. Open it. Android will ask you to allow installing from this source — the normal prompt
   for anything that isn't on the Play Store. Play Protect may say "unknown developer" for
   the same reason.
3. That's it. Needs **Android 8.0 or newer**.

Future versions install straight over the top and keep your favourites.

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
