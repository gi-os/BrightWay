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
