# MODDYS World Radio v1.6.5

Three fixes to how a long list behaves: searching again, scrolling through it, and the arrows at
the top of it.

## What changed

**Searching again starts the new list at its top.** Before, searching from halfway down the
previous results left you halfway down the new ones, reading whatever happened to be under your
thumb. It now puts the first result just under the toolbar — and only when there is something to
gain, so it never fights you while you are reading the top of a list. Same for the filter
selects and Reset, which produce new lists the same way.

**The toolbar and the filter panel step out of the way while you scroll.** Scrolling is reading
time: the chrome slides away so more results fit, and comes straight back when the scrolling
stops. Scroll back up and it returns at once. It stays put while the search field has focus —
that is the keyboard's doing, not yours. Desktop keeps its toolbar throughout; there is nothing
to win there.

**The page arrows.** Reported twice, and this time I think I have it: some WebViews never
deliver the click that should follow a touch, which is why the arrows worked everywhere I
tested them and nowhere on a real phone. They now also listen for a touch that never became a
click, with a guard so a real tap still counts once and not twice.

## Verified

- the suite that drives the real page: 129 checks including all of the above on a phone and on a
  desktop — that a new search lands at the top of the new list, that the first result sits just
  under the toolbar, that the chrome hides on a phone and never on a desktop, and that a touch
  alone turns the page
- `tools/shim_harness.py` drives the real shell over both bridges: 76 checks, including a touch
  that never became a click turning the page, and a real tap advancing exactly one page
- `tools/apk_identity.py` on the published APK: versionName `1.6.5`, versionCode 13, no `.debug`
  suffix, same signing certificate as every release before it
- the bundled `index.html` is byte-identical to the one `moddys.net` serves

## Install

Open the app: it offers this itself — **Update now**, one tap. Your stations and favourites stay.

## Cheers Moddy !
