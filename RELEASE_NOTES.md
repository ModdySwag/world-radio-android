# MODDYS World Radio v1.6.1

**The same app as 1.6.0 - carrying the cleaned-up page.** Nothing about playback, the station
list or your favourites has changed. `world-radio-v1.6.1.apk` installs straight over it.

## What changed

- **The header no longer collapses.** It used to have a small control in the top right, and the
  app's shell had to hide it and fight any collapsed state that came back from storage. The
  control is gone from the page, so the header is simply always the full-size one - and the
  shell's guard went with it, because it was guarding against something that can no longer
  happen.
- **The filter panel starts closed.** Opening the site used to show the filter row expanded;
  now the station list is the first thing you see and "Filters" opens the rest when you want it.
- **The download button has a pointing finger above it** - an inline SVG in the same cyan, violet
  and pink as the rest of the site. Decoration, so it is hidden from screen readers, it takes no
  clicks, and it stops bobbing if your system asks for reduced motion.
- **The mini-player window's scroll wheel now changes transparency instead of opacity.** Before,
  the wheel faded *everything* including the words, which is unreadable by design. Now it fades
  only the background - the page, its decorative layers, the control bar and the list rows - and
  the text, artwork and buttons stay fully opaque. The gesture is unchanged (wheel up is more
  solid), it stops at 75%, and it is remembered between opens.

## Verified

- **34 new checks** for those four changes, run against the built page and again against the
  live site, including the returning-visitor cases: a stored "header collapsed" flag and a
  stored "filters open" state both come up correct on load.
- **46 checks** driving the real shell in a real browser on both bridges, so the app's own
  player, gestures and Check panel are unaffected by any of it.
- **84 existing site checks** still pass, and the page the app bundles is byte-identical to the
  one moddys.net serves (same file, verified by hash on all three copies).

## Install

Download `world-radio-v1.6.1.apk` below and open it on the phone or tablet. It installs over any
older version - no uninstall, no lost favourites.

**Privacy & safety:** the app talks to one thing only, the radio directory and the streams you
play. No analytics, no accounts, no tracking, nothing collected, nothing sent anywhere.
Cleartext `http://` streams are allowed because 8,330 of the 47,994 stations are still plain
HTTP; certificate checks are never bypassed.

Cheers Moddy !
