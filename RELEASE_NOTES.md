## BrightWay v1.11 — arrival that actually ends the trip

**Arriving no longer gets missed when the last step is short.** One GPS fix can finish the
second-to-last step and land within 20 m of the destination at the same time. The old code only
checked for arrival when the step *didn't* advance, so that fix moved you onto the last step and
then waited for the next fix to declare you arrived — and a phone lying still at the destination
never sends one, because the GPS loop only reports after 2 m of movement. The service kept the
GPS running in your pocket until its four-hour safety cap. Arrival is now decided on the same fix
that advances the step, and there is a unit test standing on exactly that case.

**Routes can't start from where the phone was yesterday.** The location loop warms up from the
system's last known fix, which is fine for drawing the map and useless as a route origin — it can
be hours old. Routing now rejects any fix older than two minutes and says "Waiting for GPS fix"
instead of quietly planning a trip from wherever the phone last had sky.

**A rare race can no longer hold the GPS with nothing driving it.** A start request queued behind
a shutdown used to land on a service whose coroutines were already cancelled: it took the GPS
lease, and the loops that would have used it — and the cap timer that would have released it —
silently never ran. A finished service instance now refuses to begin and just stops. A route that
parses to zero steps (origin on top of destination) shuts down immediately too, instead of
running a trip that can never arrive.

**Coarse fixes can't fake an arrival.** A network fix with 100 m of slop can land "within 20 m"
of a step endpoint it is nowhere near, skipping steps or ending the trip early. Fixes vaguer than
40 m no longer make advance or arrival decisions.

**The wheel updates the distance immediately.** Changing the step by wheel used to keep showing
the old step's meters — on the big number and on the lock face — until the next fix, which a
stationary phone never gets. The distance is now recomputed against the new step on the spot.

**Smaller things.**

- The map cache now budgets by memory (16 MB) instead of counting pictures; eight zoom levels of
  full-size map could sit on ~52 MB of a very small heap.
- A malformed route line degrades to "no map" with a reason instead of crashing the app.
- The Settings key row and the home screen's empty state update the moment you scan a key,
  instead of saying "No key" until something else redrew the screen.
- Reopening the app after the system reclaimed it mid-trip no longer risks a crash on the way
  back to the nav screen.
