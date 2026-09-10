# MODDYS World Radio v1.4.1

**Fixes a bug in v1.4.0.** If the radio told you another app wanted the sound and then
refused to play no matter how many times you pressed the button, this is that fixed.
Install straight over the top — your favourites are kept.

## What was wrong

v1.4.0 treated a refused audio-focus request as permission to play. On some devices —
Samsung, Xiaomi and a few tablets — that request comes back refused even when **nothing else
is playing**, so the radio stopped, asked you what to do, and then asked again the moment you
answered. A loop with no way out.

Two other things went in with it, both of which made that worse:

- A silent probe at startup trying to work out whether your device needed a real tap. It ran
  with no gesture and no user intent, which on some tablets means it fails, marking a device
  that plays perfectly as one that "needs a tap".
- The refusal path stopped playback instead of simply saying the sound was shared.

## What changed

- **Focus is advice, not a gate.** If the system refuses the request, the radio plays anyway
  — Android does not enforce audio focus, and refusing you the handle is not the same as
  telling you not to play. You get one quiet note that the sound is shared, and that's all.
- **The dialog means one thing.** Another app taking the output *while you were listening*
  brings up Continue here / Pause / Stop, as before. A take-over straight after you answered
  is reported quietly rather than asked again, so it can never loop — and a fresh take-over
  later still asks properly.
- **Nothing is touched at startup.** The tap probe now runs from the Check panel, on demand,
  from a real press, where the answer is honest. Until then the panel says "not tested yet"
  rather than guessing.
- **Everything else from v1.4.0 is unchanged:** the Check panel (device, WebView version,
  codecs, how much of the catalogue this device can play, copy report), the failure
  classification, renderer recovery, the no-WebView message.

## Verified

- 128 checks driving the real player in a real browser, including the reported loop: answer
  "Continue here", have the sound taken straight back, and confirm the dialog does **not**
  return.
- 68 project checks, including that the service can no longer stop playback over a failed
  focus request, and that the shell's refusal path contains no pause and no dialog.
- The published APK is checked after CI: identity, signature (same key as every release),
  and the new code present in `classes.dex`.

## Install

Download `world-radio-v1.4.1.apk` below and open it on the phone or tablet. If you have any
version installed, this installs over it — no uninstall, no lost favourites.

**Privacy & safety:** the app talks to one thing only, the radio directory and the streams
you play. No analytics, no accounts, no tracking, nothing collected and nothing sent
anywhere. Cleartext `http://` streams are allowed because 8,330 of the 47,994 stations are
still plain HTTP; certificate checks are never bypassed.

Cheers Moddy !
