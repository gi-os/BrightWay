## BrightWay v1.12 — color that survives a crash, and a hint when you've walked the wrong way

**A crash mid-navigation no longer leaves the whole phone in color.** "Color while navigating"
used to write the daltonizer setting on the way into the nav screen and write it back on the way
out — and a crash, a process kill, or arriving with the phone in your pocket meant the way out
never ran. The whole phone stayed in color, permanently, with nothing on screen to explain why.
The nav screen now asks BrightControl for color instead, over a bound service from light-common
1.7.0: the request is held only while the screen is up and the trip is live, and the release is
the binder connection dying — which a crash does for free. On a phone without BrightControl the
library still writes the setting directly, so the app also puts greyscale back from state, not
from transitions: at every launch and at the end of every trip, if nothing is navigating, the
phone is made grey. However color got stuck on, the next launch unsticks it.

**The app now says "off route?" when the distance keeps growing.** Walk past a turn and the big
number just got bigger, with nothing to say so. Now, after five good fixes in a row that each
grew the distance to the next turn — and more than 30 m of total growth, so GPS wander in a
street canyon can't ask the question — a quiet "OFF ROUTE?" appears under the number, and the
lock face's instruction gets " · off route?" appended. It is a hint, not a reroute. It comes down
the moment the number shrinks, the step changes (wheel or auto-advance), or a new trip starts,
and only fixes that pass the existing 40 m accuracy gate ever feed it. The rule is pure math with
unit tests standing on its boundaries: four increases are not enough, 29 m of growth is not
enough, one decrease forgives everything.

**Smaller things.**

- Arriving while the app is in the background now restores greyscale immediately, instead of
  waiting for you to come back to the nav screen.
- light-common 1.2.2 → 1.7.0.
