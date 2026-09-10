# MODDYS World Radio v1.5.0

**This is the one that ends the "another app wants the sound" loop.** If the radio stopped a
fraction of a second after you pressed play, and pressing Continue here only brought the same
dialog back, install this. It goes straight over whatever you have — favourites are kept.

## What was really wrong

Nothing else on the device was playing. **The app was fighting itself.**

The radio's audio is played by the WebView engine, and that engine asks Android for audio
focus on its own — as any media app does. The playback service asked for audio focus as well.
Two requests from one app means one app that looks like two, and the framework reported the
resulting shuffle back to us as a loss or a refusal — *about this app's own audio*:

- the stream started, the framework reported a loss about the app's own player, and the radio
  stopped a fraction of a second later — the "blip";
- the shell saw that as another app taking over and put up a dialog naming an app that was
  never there;
- choosing **Continue here** asked for focus again, the same shuffle happened again, and the
  dialog came straight back — forever.

v1.3.0 made the same requests but only paused on a loss, which is why it felt fine; v1.4.0
turned that reaction into a dialog, which is what made it impossible to escape.

## What changed

- **The app no longer asks Android for audio focus at all.** The player handles that, because
  it is the thing making the sound. No competing request, no verdict about our own audio,
  nothing to fight.
- **It no longer guesses who else is playing either.** Working that out would mean reading
  Android's playback list and asking whose audio each entry is — and those methods
  (`isActive`, `getClientUid`) are hidden from the public SDK. Every answer this app could
  give would be a guess, and a wrong guess about another app is exactly what you saw. So the
  dialog is gone: this app will never again tell you another app wants the sound.
- **A stop is just a stop.** When the page stops for any reason, the notification stays up
  with a **Play** button instead of vanishing, so there is always a way back — with no invented
  reason printed on it.
- **Nothing is played, paused or retried on its own.** Not on a timer, not after an
  interruption, not for a device that wants a tap.
- Everything from 1.4.x is still here: the Check panel (device, WebView version, codecs, how
  much of the catalogue this device can play, copy report), the failure-name handling (a
  tap-required device is never retried without one, an undecodable stream is never retried),
  renderer recovery after a low-memory kill, and the no-WebView message.

## What you give up

Ducking. The old version lowered its own volume under a navigation prompt, which needed the
focus request that caused all of this. With no focus request the radio may now simply be mixed
with other sound on some devices. If you hear two things at once, pause the radio.

## Verified

- **106 checks** driving the real player in a real browser, including that no dialog element
  exists, that the shell has no audio-focus code path left, and that a playing stream is never
  stopped or restarted by the app itself.
- **58 project checks**, including that the service contains no focus request, no playback
  observation, and no hidden-API call — and that the notification text never claims another
  app is involved.
- The published APK is checked after CI: identity, version, signature (the same key as every
  release) and the absence of the focus code in `classes.dex`.

## Install

Download `world-radio-v1.5.0.apk` below and open it on the phone or tablet. It installs over
any older version — no uninstall, no lost favourites.

**Privacy & safety:** the app talks to one thing only, the radio directory and the streams you
play. No analytics, no accounts, no tracking, nothing collected, nothing sent anywhere.
Cleartext `http://` streams are allowed because 8,330 of the 47,994 stations are still plain
HTTP; certificate checks are never bypassed.

Cheers Moddy !
