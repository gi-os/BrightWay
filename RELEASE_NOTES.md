## BrightWay v1.8 — it remembers where you went

**A journey log, and a provider for it.** This app knew every trip and forgot each one immediately.
`recents` looks like a history and is not: twelve destinations, deduplicated, with no times on them,
so a second walk to the same place overwrote the first and a day's travel was gone within the hour.

Now a trip is opened when a route is chosen and closed when navigation ends. Both halves are needed
and neither is enough on its own — the choice knows where, how, how far and how long it *should*
take; only the ending knows whether you got there and what it actually took.

**Arrival is recorded, not assumed.** Ending navigation and giving up on it are the same gesture
here — there is one END row — so arriving is inferred from having reached the final step and stored
as its own fact. "Walked to Union Square" and "set off towards Union Square" are different days.

**`content://com.gios.brightway.trips/trips/2026-08-25`** answers with a row per trip started that
day: when it began and ended, walk or transit, the place, the planned duration, the distance, and
whether you got there. Read-only, no permission, the same shape as BrightRecorder's clips bridge —
and BrightNotebook is why it exists: "Walked to Union Square, 18 minutes" was the most obviously
missing line on a day it builds out of every other app on the phone.

Still `SharedPreferences`, as the file's own argument says: a database for a list this size is
machinery this app does not need. Capped at 120 trips, which is a few weeks of ordinary use.
## BrightWay v1.7 — the new key is withdrawn; this installs over what you have

**No uninstall. This is an ordinary update.** v1.6 was signed with a brand-new certificate, which
meant it could only be installed by removing the app first and losing the API key, saved places and recents.
That cost was not worth what it bought, so it has been withdrawn. v1.7 is signed with the same
certificate every release before v1.6 used, and it installs straight over the copy on your phone.

If you already uninstalled and installed v1.6, this one will not go over it — uninstall once more
and install v1.7, and that is the end of it.

**What this does and does not fix.** The signing key is no longer committed to this repository and
the file is gitignored, so a fresh clone does not hand it out. But it is still in this repository's
git history and always will be, so treat it as public: anyone determined enough can still build an
APK this phone would accept as an update. Closing that for real needs an APK Signature Scheme v3
rotation — signing with a new key while carrying a proof-of-rotation signed by the old one, which
Android accepts as a normal update — and that is a separate change, done carefully, not bundled in
behind an uninstall.

Everything else in v1.6 stands and is still here.

## BrightWay v1.6 — a new signing key, and one reinstall to take it

**Withdrawn.** The key change described below was reverted in v1.7; see the top of this file. The rest of this release stands.
**You have to uninstall BrightWay and install it again.** Not an update — a full uninstall
first. Android identifies an app by its package name *and* the certificate it was signed
with, so a build signed with a different key is a different app as far as the phone is
concerned. Installing this one over the old one fails with a bare `Failure: Invalid` and no
explanation. Uninstall, then install; it is a one-time cost and nothing after this release
asks for it again.

Uninstalling clears the app's data, which for BrightWay is the API key, saved places and
recents. Have the key QR to hand before you start.

**Why.** The release key was committed to this repository with its password written three
lines under it in `app/build.gradle.kts`. Anyone who cloned it could build an APK that
Android would accept as an update to the one on your phone — which is the whole of the
protection Android offers, handed out with the source. The old key is now retired and the
new one is a CI secret: the workflow decodes it at build time, `keystore/*.jks` is
gitignored so a checkout cannot commit it back, and the certificate the release actually
carries is checked against `signing-fingerprint.txt` before anything is published.

A build without the secret — a branch check, a local clone — still compiles and still
produces an APK. It just is not signed with the release key and will not install over one.
That is the right way for it to fail.

**Also in this build.** Every GitHub Action the workflows use is pinned to a commit SHA
rather than a moving tag, so a compromised or retagged action cannot quietly change what
builds your APK. And a commit that touches `signing-fingerprint.txt` no longer skips the
release workflow — it used to be on the ignore list, which meant the one commit that
rotates a key was also the one commit that shipped nothing.

## BrightWay v1.5 — somewhere to hand a place to

**Another app can now say "go here".** BrightWay had no intent surface at all: one activity, one
MAIN/LAUNCHER filter, and no way for anything on the phone to hand it a destination. So a calendar
entry with an address on it — the thing every other phone lets you tap to start navigating — had
nothing to tap *to*.

Three shapes are understood, all of them landing in the search box:

```
brightway://go?q=Regal+Union+Square    ours, used by BrightNotebook
geo:0,0?q=350+5th+Ave                  the standard one, from anything on the phone
geo:40.748,-73.985                     coordinates, searched as text
```

**Searched, never routed.** A calendar's location is a string somebody typed — "Regal Union
Square", "moms", half an address — and starting a route off it would mean walking somebody
somewhere on the strength of a guess. The results list, with what it matched and the address it
matched to, is one extra press and no wrong turns.

Two details worth knowing. `geo:` is an *opaque* URI, so `getQueryParameter` returns nothing and
the query has to be split by hand — and decoded by hand, because an address with a comma in it
arrives percent-encoded and "350 5th Ave%2C New York" on screen looks like this app mangled it.
And the activity is `singleTask` now, so a second handover reuses the app that is already open
instead of stacking another copy of it behind the first.

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
