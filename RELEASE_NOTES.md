# MODDYS World Radio v1.2.0

**Your player, your look.** 47,994 stations from 221 countries, in the theme you already
know, and it keeps playing when you leave it.

## What's new in this version

- **The visualiser has a home, and five styles to pick from.** Drag the player up, tap
  **Visual**, and a visualiser opens at the bottom with one-tap styles: **Bars, Waves,
  Mirror, Dots and Blocks** — the one you pick stays picked. On the website the visualiser
  vanishes below 900px wide, which means it never showed on a phone at all; in the app it
  now has a proper strip and a proper size.
- **Light mode.** The theme button (where Filters used to be) flips the whole interface —
  header, list, player and all — between dark and light, and remembers your choice.
- **Random.** One tap plays a station from whatever you're looking at, so it respects
  whatever filters you've set. It flashes the station it picked so you can see where it
  went.
- **The header is static.** The site's "Header" collapse control is gone: the title, the
  stats and the tagline are always there, and the shortcut that hid them no longer applies.
- **A tidier expanded player.** The station no longer appears twice — the compact row makes
  way for the full view, which also freed up the room the visualiser needed — and the
  expanded view now has a proper labelled Play button.

## Install

1. Download **world-radio-v1.2.0.apk** from the Assets below — straight to your phone, or
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

- Another music app may play over the radio instead of pausing it. Audio focus is the next
  thing to fix.
- Some stations in the catalogue are genuinely dead. They fail visibly rather than hang.
- No version yet on the Play Store — this is a direct download.

Cheers Moddy !
