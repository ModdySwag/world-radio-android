# MODDYS World Radio v1.6.0

**This is the one that tells you when your device is older than the app is tuned for** — and
it is the release where the Android and iOS apps became one product instead of two.

`world-radio-v1.6.0.apk` installs straight over 1.5.0. Favourites are kept.

## What changed

- **A compatibility notice, once, at launch.** Android has always refused to *install* below
  8.0 (`minSdkVersion 26`), and Play has always shown the requirement. What was missing was
  the honest middle ground: a device that *can* install the app but is old enough that things
  will misbehave. If yours is below **Android 11** — the version the app is tuned for — you get
  one dismissible notice naming what you have, what it is tuned for, and which part is at
  risk: playback happens in the device's own **System WebView**, not in the app, and on a
  release that old that component is often years behind what the stations expect. There is a
  button straight to the Check panel, so "what can this device actually play?" is one tap away.
  Shown once per app version. Never a nag, never a dialog you cannot dismiss.
- **`compat.json` is now the single source of truth** for those numbers — the launch notice,
  the in-app Check panel and the website's download section all read the same file, so they
  cannot drift apart. Changing the floors is a one-line edit.
- **The Check panel reports the verdict properly**: the system line now carries the verdict
  (supported / below the tuned-for version / not supported) instead of making you compare
  numbers yourself, and the engine is reported as its own row, because its version — not the
  Android version — is what decides which stations decode.
- **This shim is now the shared shell.** `_android_shim.js` became `_shell_shim.js`, and the
  iOS app ships the identical file: same player sheet, same gestures, same theme, same
  visualiser, same compatibility panel. One fix lands on both platforms. The only difference
  between them is ten lines of bridge (Android's synchronous JS interface vs iOS's message
  handler), and the shim hides that from itself.
- **The player now waits for the page.** If the shell is injected before the page has built
  its own player bar — which is what happens on iOS, where injection is at the end of parsing —
  it retries for ten seconds instead of giving up and reporting a dead player. On Android this
  was never visible; it is what makes one shim work on both.

## Verified

- **38 checks** driving the real shell in a real browser, on all three paths: the Android
  bridge, the iOS message-handler bridge, and no bridge at all (where the Check panel must say
  "not reported by this shell" rather than invent a verdict). That includes the deferred
  startup above, and that an external link still reaches the native side on both bridges.
  It runs in CI on every push.
- **Compat.java parses, and so does the asset it reads**: the APK build now fails if
  `compat.json` is missing or is not valid JSON, and the release workflow asserts both new
  files are inside the shipped APK.
- The published APK is checked after CI as before: identity, version, signature (the same key
  as every release) and the bundled assets.

## Install

Download `world-radio-v1.6.0.apk` below and open it on the phone or tablet. It installs over
any older version — no uninstall, no lost favourites.

**Privacy & safety:** the app talks to one thing only, the radio directory and the streams you
play. No analytics, no accounts, no tracking, nothing collected, nothing sent anywhere.
Cleartext `http://` streams are allowed because 8,330 of the 47,994 stations are still plain
HTTP; certificate checks are never bypassed.

Cheers Moddy !
