# MODDYS World Radio v1.7.1

The page buttons, redone: the arrows now take you to the page, one tap is always one page, and the
row itself is laid out properly on every screen size.

## What changed

**The arrows take you to the page.** They always did turn it - but every turn jumped you to the very
top of the page. On a phone that is the header and the search field, with the freshly loaded stations
sitting below the fold, which looks exactly like "the button did nothing". A turn now lands on the
results, and turning from the bottom pager (240 cards down) comes back up to the first card of the new
page.

**One tap, one page - never two, never none.** Two things could turn a page: the browser's click, and
a fallback for the WebViews that swallow the click after a touch. Which of them had already acted was
decided by a stopwatch, so a tap landing just inside the window was swallowed (nothing happened) and
one just outside it was counted twice (two pages a tap). They now share a single flag per touch: the
click claims its own touch, the fallback takes it only if no click ever arrived.

**The row is laid out across all formats.** On a phone and a tablet the back arrow owns the left end,
the forward arrow the right end, and the page numbers sit together in the middle - it used to spread
four items with a 136px hole between the back arrow and the first number. Every control is a 44px
target, the page you are on is the cyan number between the arrows, and tapping a number is a jump:
page 1 and the last page are always one tap away. The same row is used above and below the list.

**No more listener pile-up.** Every keystroke in the search field re-bound the pager and added another
touch listener without removing the old one - twelve on a pager after six letters, and climbing for
the rest of the session. The pager is bound once now.

**Screen readers hear the page change** ("Page 2 of 174"). Nothing new is printed on screen and the
stations line above still carries only the count.

**The bundled page is the current one**, byte for byte.

## Verified

- the suite that drives the real page, against this page: 251 checks, 0 failed - including the new
  section checks: one tap advances exactly one page, a turn from the bottom pager brings the reader to
  the results, the two arrows own the two ends of the row, every pager control is a 44px target, and
  tapping a page number jumps to it
- the same suite against what moddys.net serves: 251 checks, 0 failed
- `tools/shim_harness.py` drives the real shell over both bridges: 76 checks, 0 failed - including the
  three pager checks inside the app (the arrow turns the page, a touch that never became a click still
  turns it, and a real tap advances exactly one page)
- the layout sweep over 14 device shapes: 14 measured, 0 broken
- the pager measured directly on a desktop, a 390x844 phone and an 844x390 landscape phone: mouse
  clicks, real touch taps and bare synthetic taps each turn the page exactly once, the arrows sit at
  the two ends of the row, and nothing covers them
- the bundled `index.html` is byte-identical to the one this release puts on the site, and it is read
  back out of the published APK rather than off a build log

## Install

Open the app: it offers this itself - **Update now**, one tap. Your stations and favourites stay.

## Cheers Moddy !
