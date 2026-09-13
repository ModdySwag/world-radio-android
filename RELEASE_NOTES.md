# MODDYS World Radio v1.6.6

The toolbar finally looks like a toolbar, and the mini player stops asking to be clicked.

## What changed

**Four tidy rows, all the same width.** The header, then the search field, then the stations line
(the count and the Verified switch), then Format · Bitrate · Country, then Saved and Reset. Every
row is cut to the shape of the search field, which is what makes it read as one thing instead of a
pile of buttons.

**The genre panel is gone.** It was too complicated and it cluttered the screen in landscape, and
the search field already did the job: type `dub`, `news` or `spanish` and the list narrows to that.
The station line is now just the count — no page number, no "1–240", no "10,965 hidden" — and the
Verified switch at its right-hand end decides whether that number is the stations this browser can
play or all of them.

**🎲 Random** plays a station out of the list you are looking at. Your search, your filters and the
Verified switch all still apply, and it prefers one this browser can actually play.

**The mini player takes the stream over by itself.** This was the real bug: a freshly opened window
has no user activation of its own, and the mini player took that to mean the browser would refuse
to play anything — so it sat there as a remote control until you clicked inside it. It does not
refuse any more. It connects and buffers the same stream while the page is still playing, takes
over the moment it can make a sound, and the page lets go only then. Handing it back is the same
story in reverse: ⤴, or simply closing the window, starts the page again in a moment and the mini
player keeps playing until the page really has the sound. The music moves; it never stops.

The cover picture is the pop-out control at every width now — and the **MINI** label does the same
job while nothing is playing — which is where the old Pop-out button used to be.

**On a phone the page arrows take a line of their own**, filling it, so a hundred-odd pages of
results are easier to move through.

## Verified

- the suite that drives the real page: 213 checks, 0 failed, against the local files and against
  what moddys.net actually serves
- a probe that drives the real page in a real browser with Chrome's desktop autoplay policy in
  force: the mini player takes the stream over with nothing clicked inside it (the page goes quiet
  and the mini player sounds in the same millisecond, and none of the three rounds needed a tap),
  ⤴ leaves 125 ms between the mini player's last sound and the page's first, and closing the window
  hands the stream back in 84 ms
- `tools/shim_harness.py` drives the real shell over both bridges: 76 checks
- `tools/apk_identity.py` on the published APK: versionName `1.6.6`, versionCode 14, no `.debug`
  suffix, same signing certificate as every release before it
- the bundled `index.html` is byte-identical to the one `moddys.net` serves

## Install

Open the app: it offers this itself — **Update now**, one tap. Your stations and favourites stay.

## Cheers Moddy !
