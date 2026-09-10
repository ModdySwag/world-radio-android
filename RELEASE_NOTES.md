# MODDYS World Radio v1.0.0

**SPRAY IT LOUD — now in your pocket.** 47,994 stations from 221 countries, in the same
theme you already know, in an app that keeps playing when you leave it.

## What it does

- **Keeps playing in the background.** Lock the screen, switch apps, check a message —
  the station carries on. Play/pause and the station name appear on your lock screen and
  in the notification shade.
- **47,994 stations, 221 countries.** Filter by country, genre, codec and language,
  search the lot, and keep favourites — they survive closing the app.
- **Browse with no signal.** The whole station list ships inside the app, so the list and
  the filters work offline. Only the streams need a connection.
- **No ads, no accounts, no tracking.** The only thing the app talks to is the stream you
  press play on.

## Install

1. Download **world-radio-v1.0.0.apk** from the Assets below — either straight to your
   phone, or on a computer then copy it across.
2. Open the file. Android will ask you to allow installing from this source; that's the
   normal prompt for any app that isn't on the Play Store.
3. Play Protect may mention an unknown developer — same reason, it isn't on the Store.

Requires **Android 8.0 or newer**.

**Upgrading:** this is the first release, so just install it. Future versions install
straight over the top and keep your favourites.

## What it asks for

- **Internet** — to play the stations.
- **Notifications** — for the playback controls (Android 13+ will ask you to allow this).
- That's all. No location, no contacts, no storage, no camera.

## Privacy & safety

Nothing is collected and nothing is sent anywhere except the radio stream you choose.
There are no analytics, no ads and no third-party SDKs — the app is built on Android's own
WebView, media session and notification APIs and nothing else. About 8,300 stations in the
catalogue stream over plain HTTP, so the app allows cleartext for them; HTTPS stations are
unaffected and certificate checking is **not** weakened — a station with a broken
certificate fails, and the app says so instead of pretending.

## Known limitations

- A phone call or another music app may play over the radio rather than pausing it.
  Audio focus is the next thing to fix.
- Some stations in the catalogue are genuinely dead, or have expired certificates. Those
  fail visibly rather than hanging.
- If you had an older **debug** build installed, you can uninstall it — this release is a
  separate, properly signed app.

Cheers Moddy !
