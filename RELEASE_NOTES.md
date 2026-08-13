## BrightWay v1.4 — see where it is before you go

- **Search results open on a map.** Tapping a result now shows the place pinned on a
  map — name, address, how far away it is, your own position as the tiny marker. Wheel
  zooms (11–19). GO hands it to the same routes screen as always; BACK returns to the
  results.
- Saved places and recents still route in one tap — you know where home is.
- Same say-why-it-failed rule as the nav map: fetch errors show the reason and TAP TO
  RETRY instead of loading forever.

## BrightWay v1.3 — believe the GPS, not the magnetometer

- **Heading from GPS while walking.** The magnetometer reports "high confidence" on stale
  calibration until you shake the phone in a figure 8 — so while you're actually moving
  (fresh fix, > ~2 mph), the needle now follows your GPS course over ground instead:
  true north by construction, immune to magnets, rails, and radiators. The label reads
  "heading from GPS". Standing still falls back to the sensor.
- **Interference detection.** A raw magnetometer watch runs alongside: field strength
  outside Earth's plausible 25–65 µT band now says "magnetic interference — move from
  metal" instead of pretending the compass is fine.
- **Stale-course guard.** Fixes only arrive after ~2 m of movement, so a 1 s ticker
  expires the GPS course ~3.5 s after you stop — no frozen needle.
- Guards against 5-element rotation vectors (some HALs ship them; the matrix call throws).

## BrightWay v1.2 — the compass points the right way

- **Compass bearing fixed.** The needle was referenced to magnetic north while GPS
  bearings are true north — in NYC that's ~13° of constant error, a whole avenue over a
  few blocks. Declination from the GPS fix now corrects it, and holding the phone
  upright (instead of flat) no longer scrambles the heading.
- **Next turn / destination toggle.** The in-nav compass now has a NEXT TURN /
  DESTINATION switch: point at the current step's endpoint, or at the destination as the
  crow flies. Your choice is remembered.
- **New compass face.** Rotating tick ring with upright N/E/S/W (N tracks true north), a
  smoothly animated needle that takes the short way round, distance under the face.
  Shared between the standalone compass and the in-nav one.
- **Map view actually tells you what's wrong.** Fetch failures used to be swallowed —
  "loading map…" forever. Errors now surface with the real reason (including a hint when
  Maps Static API isn't enabled on your key) and a TAP TO RETRY.
- **Long routes fit on the map.** Cross-borough transit polylines could blow the Static
  Maps URL limit and kill the request. Routes are now thinned to 150 points before the
  GET — invisible at panel resolution, always under the cap.

## BrightWay v1.1 — the map view

- **Route on a map**: tap MAP on the nav screen. The whole route drawn on a real map
  (Maps Static API — enable it on your key alongside Routes and Places), destination
  marked, your position marked.
- **Wheel zoom**: turn the wheel to drop from the whole-route overview into street level
  centred on you; the map follows as you walk. Zoom past the bottom to get the overview
  back.
- Map images are cached, and follow mode only re-fetches after ~50 m of movement, so a
  trip costs a handful of Static Maps calls out of your 10k/month free tier.
- The step list keeps the wheel when the map is hidden; the map takes it when shown —
  never both.

### v1.0 — first release

Walking navigation (Places search, saved places, turn-by-turn, auto-advancing steps),
subway + bus itineraries with real line colours, compass crow-flies mode, BYO Google key
by QR, optional colour during nav, shake to report.
