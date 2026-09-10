# MODDYS World Radio v1.5.0

**This is the one that fixes the "another app wants the sound" loop.** If the radio stopped a
fraction of a second after you pressed play, and pressing Continue here just brought the same
dialog back, install this. It goes straight over the top — favourites are kept.

## What was really wrong

Nothing else on the device was playing. **The app was fighting itself.**

The radio's audio is played by the WebView engine, and that engine asks Android for audio
focus on its own — as any media app does. The playback service asked for audio focus as well.
Two requests from one app means one app that looks like two, and the framework reported the
resulting shuffle back to us as a loss or a refusal — *about this app's own audio*. So:

- the stream started, the framework reported a loss about the app's own player, and the radio
  stopped a fraction of a second later — the "blip";
- the shell saw a loss while playing and put up the dialog, naming an app that was never there;
- choosing **Continue here** asked for focus again, the same shuffle happened again, and the
  dialog came back — forever.

v1.3.0 had the same requests but only *paused* on a loss, which is why it felt fine on this
device; v1.4.0 turned that reaction into a dialog, which is what made it impossible to escape.

## What changed

- **The app no longer asks for audio focus at all.** The player handles that, because it is
  the thing making the sound. No competing request, no verdict about our own audio, nothing to
  fight.
- **What it does instead is watch** — and it filters other apps by UID, so this app's own
  playback can never be mistaken for a competitor. Only a real, different app can trigger the
  dialog now.
- **Nothing pauses the radio except a real other app starting to play.** No verdicts, no
  refusals, no speculation. When the radio is already stopped, another app starting asks you
  nothing.
- **Nothing auto-resumes, and nothing is retried without you.** The radio no longer tries to
  reconnect itself after anything.
- **Stop means stop.** Choosing Stop now takes the notification down too, instead of leaving it
  offering to reconnect.
- Everything from 1.4.x is still here: the Check panel (device, WebView version, codecs, how
  much of the catalogue this device can play, copy report), the failure-name handling (a
  tap-required device is never retried without one, an undecodable stream is never retried at
  all), renderer recovery after a low-memory kill, and the no-WebView message.

## What you give up

The earlier version ducked the radio under a navigation prompt by lowering its own volume. That
needed the focus request that caused all of this, so it is gone: on some devices the radio may
now be mixed with other sound instead of stepping aside. If you hear two things at once, pause
the radio.

## Verified

- **125 checks** driving the real player in a real browser, including: another app starting
  pauses the radio and asks once; a second take-over right after answering does not ask again;
  a fresh one later does; nothing is ever played automatically; and the stale focus verdicts
  the app no longer produces are ignored outright.
- **66 project checks**, including that the service contains **no** focus request at all, that
  the observation is filtered by UID, and that the shell has no focus-verdict path left.
- The published APK is checked after CI: identity, version, signature (same key as every
  release) and the new code present in `classes.dex`.

## Install

Download `world-radio-v1.5.0.apk` below and open it on the phone or tablet. It installs over
any older version — no uninstall, no lost favourites.

**Privacy & safety:** the app talks to one thing only, the radio directory and the streams you
play. No analytics, no accounts, no tracking, nothing collected, nothing sent anywhere.
Cleartext `http://` streams are allowed because 8,330 of the 47,994 stations are still plain
HTTP; certificate checks are never bypassed.

Cheers Moddy !
