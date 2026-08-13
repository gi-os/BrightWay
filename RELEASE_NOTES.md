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
