# BrightWay

Walking and subway directions for the Light Phone III. Text-first: one big instruction,
the wheel scrolls the rest of the trip. No map tiles, no Play Services, no account —
you bring your own Google Maps Platform key.

## What it does

- **GO** — search anywhere (Places), get one walking route and up to three subway/bus
  itineraries (Routes API). Pick one; the nav screen shows the current turn huge, the live
  GPS distance to it, and auto-advances as you walk. Transit steps show the bullet, the
  headsign, stop count and exit stop.
- **PLACE MAP** — search results open pinned on a map first: wheel to zoom, GO to route.
- **COMPASS** — bearing + distance to your destination, as the crow flies (true-north
  corrected). During nav, toggle it between the next turn and the destination. Works with no
  key and no signal.
- **SETTINGS** — scan your API key in by QR, save places (Home, Work) from where you're
  standing, toggle colour.

## Bring your own key

1. In [Google Cloud Console](https://console.cloud.google.com/google/maps-apis), create an
   API key with **Routes API**, **Places API (New)** and **Maps Static API** enabled. One phone stays inside
   the monthly free tier.
2. Open <https://gi-os.github.io/BrightWay/> on your computer, paste the key, scan the QR
   from Settings → SCAN. The page is client-side only; the key lives in the app's private
   prefs and never in this repository or the APK.

## Colour (optional)

LightOS's greyscale is a colour matrix, not the panel. One adb grant lets BrightWay lift
it during navigation so subway bullets render in their real MTA colours, restoring
greyscale when you're done:

```
adb shell pm grant com.gios.brightway android.permission.WRITE_SECURE_SETTINGS
```

Skip the grant and everything works in greyscale.

## Install

Grab the APK from the latest release (or point Obtainium at this repo). Every push to
`main` cuts a signed release; the certificate is pinned in `signing-fingerprint.txt`.

Location permission is asked on first launch — GPS only, plain `LocationManager`,
nothing runs in the background after you leave the app.

<!-- bright-footer:begin -->
---

## Bright\*

26 open-source apps for the **Light Phone III** — camera, music, maps, messages,
reading, transit, games. The phone has no app store, so they install by sideload: scan one
code from **[brightmarket.gzl.dev](https://brightmarket.gzl.dev)** and BrightMarket keeps them updated.

[Roll](https://github.com/gi-os/Roll) · [BrightMusic](https://github.com/gi-os/BrightMusic) · **BrightWay** (you are here) · [BrightChat](https://github.com/gi-os/BrightChat) · [BrightControl](https://github.com/gi-os/BrightControl) · [BrightRemote](https://github.com/gi-os/BrightRemote) · [browse all 26 →](https://brightmarket.gzl.dev)

The Light Phone does not sponsor or endorse any of these. Built by
[Giovanni Lupo](https://github.com/gi-os) — if this one is useful to you, a ⭐ helps the next
person find it.
<!-- bright-footer:end -->
