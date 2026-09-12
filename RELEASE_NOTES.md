# MODDYS World Radio v1.6.2

**The player bar now fits your phone.** Reported from an Android device: the Pop-out button was
squashed against the right edge with half of it missing. `world-radio-v1.6.2.apk` installs
straight over 1.6.1 - favourites kept, nothing else changed.

## What was wrong

Three separate faults in one row, all measured across 14 device shapes rather than guessed at:

- **Nothing in the bar could give way.** The volume slider's own minimum width (about 129px) was
  wider than the box it sat in, so it spilled out and painted across the Pop-out button. And
  because the buttons were shrinkable, a tight row squeezed Pop-out down to 53px, which made its
  label wrap onto three lines - a 68px tall button in a 62px row, with the text cut off.
- **A phone rule was hiding an element that does not exist** (it named `#barStop`; the button is
  `#bStop`), so Stop stayed on screen taking room the row did not have.
- **The page reserved no space for the bar at all**, and the station grid's 320px minimum column
  was wider than a 320px phone's 288px of content - which pushed the whole document past the
  edge of the screen and made Android scale the page down.

## What changed

- The bar's controls can no longer be squeezed, and the volume slider now shrinks properly.
- On phones the bar drops Stop, Website, the volume percentage, the badge, the station meta line
  and the visualiser toggle - that last one because below 900px its canvas is already switched
  off, so on a phone it was a button that could not do anything visible.
- The Pop-out button keeps its icon and its accessible name; only the word goes on the narrowest
  screens, which gives the station name room to breathe.
- The bar's height is measured, so the page makes exactly that much room at the bottom and the
  back-to-top button and any toast sit above the bar instead of behind it.
- Buttons are at least 40px on any touch device, and the bar respects the safe areas on the
  sides too, not just the bottom (that is landscape notches).

## Verified

- **The bar, on 14 device profiles** - Android 320/360/412, an Android tablet, a landscape
  phone, iPhone SE/15/Pro Max, iPad mini and Pro, macOS at two sizes and Windows at two sizes:
  nothing clipped, nothing overlapping, the row fits the window, and the page is never wider
  than the device. It was 6 broken profiles before this.
- **46 shell checks** still pass on both bridges, and the page inside the app is byte-identical
  to the one moddys.net serves.

## Install

Download `world-radio-v1.6.2.apk` below and open it on the phone or tablet.

**Privacy & safety:** the app talks to one thing only, the radio directory and the streams you
play. No analytics, no accounts, no tracking, nothing collected, nothing sent anywhere.
Cleartext `http://` streams are allowed because 8,330 of the 47,994 stations are still plain
HTTP; certificate checks are never bypassed.

Cheers Moddy !
