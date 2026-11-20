<a href="https://github.com/sponsors/gi-os"><img src="https://raw.githubusercontent.com/gi-os/gi-os/main/images/bright-sponsors.svg" width="100%" alt="Thank you to my sponsors"></a>

<img src="docs/icon.png" alt="" width="72" align="left" />

# BrightWay

[**⬇ Download the latest APK**](https://github.com/gi-os/BrightWay/releases/latest) · free, open source.

Walking and subway directions for the Light Phone III. Text-first: one big instruction,
the wheel scrolls the rest of the trip. No map tiles, no Play Services, no account —
you bring your own Google Maps Platform key.

## What it does

- **GO** — search anywhere (Places), get one walking route and up to three subway/bus
  itineraries (Routes API). Pick one; the nav screen shows the current turn huge, the live
  GPS distance to it, and auto-advances as you walk. Transit steps show the bullet, the
  headsign, stop count and exit stop. The trip survives the screen turning off — pocket the
  phone between turns and the step keeps advancing — and while navigating, the current turn
  is published at `content://com.gios.brightway.nav/current` for BrightControl's lock face
  to draw.
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

Location permission is asked on first launch — GPS only, plain `LocationManager`.
Nothing runs in the background after you leave the app, with one scoped exception:
while a trip is running, a foreground service keeps navigation alive so the screen can
turn off, and it stops itself on arrival, on END, or after four hours flat. Its
notification is a single silent "Navigating · tap to return" line.

## Build and rotate the release signer

`./gradlew :app:assembleRelease` builds an unsigned APK. Debug builds use the standard
Android debug key and cannot update a production install. **Check** tests the release
signing path with disposable keys and never publishes.

1. **Disable publishing before merging.** Disable [Build & Release APK](https://github.com/gi-os/BrightWay/actions/workflows/build.yml)
   and leave it disabled until the rotation and BrightMarket handover are ready. A push
   to `main` can publish. The original key was committed and remains compromised;
   use it offline only to authorize its replacement.
2. **Generate the key and lineage offline.** In a private directory outside the checkout,
   restore the original key from Git history (`d16ef54^:keystore/brightway.jks`), use its
   existing passwords only at the offline prompts, and choose fresh passwords for the
   new key. Use Android Build Tools 35.0.0:

   ```sh
   umask 077
   git -C /path/to/BrightWay show 'd16ef54^:keystore/brightway.jks' > brightway-original.jks
   keytool -genkeypair -storetype JKS -keystore brightway-new.jks -alias brightway-release \
     -keyalg RSA -keysize 3072 -validity 10000

   apksigner rotate --out brightway.lineage \
     --old-signer --ks brightway-original.jks --ks-key-alias brightway \
     --set-installed-data true --set-rollback false \
     --new-signer --ks brightway-new.jks --ks-key-alias brightway-release
   apksigner lineage --in brightway.lineage --print-certs
   ```

3. **Set the public pins.** Match signer #1's SHA-256 digest to
   `signing-lineage-root-fingerprint.txt` (also the signer of BrightWay v1.12.18
   and the current BrightMarket pin). Leave it unchanged. Commit signer #2's
   SHA-256 digest to `signing-fingerprint.txt` on the PR. CI refuses to publish
   while both pins still name the original signer.
4. **Move signing secrets to the protected environment.** In
   [Settings → Environments](https://github.com/gi-os/BrightWay/settings/environments),
   create `release`; under **Deployment branches and tags**, choose **Selected branches
   and tags** and add exactly one **Branch** rule for `main`, with no tag rules.
   Remove the legacy `KEYSTORE_B64` and `KEYSTORE_PASSWORD`, and any repository-level
   copies of the new signing secrets, from
   [repository Actions secrets](https://github.com/gi-os/BrightWay/settings/secrets/actions).
   Set the five values only in `release` (Linux commands below; enter the new passwords
   at the prompts). Never upload the original key or its passwords.

   ```sh
   base64 -w 0 brightway-new.jks | gh secret set RELEASE_KEYSTORE_BASE64 --env release --repo gi-os/BrightWay
   base64 -w 0 brightway.lineage | gh secret set SIGNING_LINEAGE_BASE64 --env release --repo gi-os/BrightWay
   gh secret set KEYSTORE_PASSWORD --env release --repo gi-os/BrightWay
   gh secret set KEY_ALIAS --env release --repo gi-os/BrightWay --body brightway-release
   gh secret set KEY_PASSWORD --env release --repo gi-os/BrightWay
   ```

5. **Verify the upgrade and coordinate BrightMarket before publishing.** With
   publishing still disabled, test an in-place update from the latest shipped APK
   to a locally signed test APK with a higher `versionCode` on a device with no real
   user data. Add the exact `("com.gios.brightway", old_sha256, new_sha256, reason)`
   entry to [BrightMarket's index handovers](https://github.com/gi-os/brightmarket-index/blob/HEAD/scripts/build_index.py)
   using the two pins (lowercase, no colons), and make sure
   [the BrightMarket updater](https://github.com/gi-os/BrightMarket) accepts the
   installed predecessor for this transition. The index otherwise keeps the old
   release, and changing its pin alone makes the current updater classify old
   installs as foreign. Re-enable publishing only when you intend to release.

The signing step supplies only the new private key to `scripts/sign-release.sh`.
It signs with v3 (no v1/v2 are needed on API 29+) and verifies the APK's pinned lineage,
including installed-data access and no signer rollback.

<!-- bright-footer:begin -->
---

## Bright\*

**It's not Light, it's Bright.**

26 open-source apps for the **Light Phone III** — camera, music, maps, messages,
reading, transit, games. The phone has no app store, so they install by sideload: scan one
code from **[brightmarket.gzl.dev](https://brightmarket.gzl.dev)** and BrightMarket keeps them updated.

[Roll](https://github.com/gi-os/Roll) · [BrightNotebook](https://github.com/gi-os/BrightNotebook) · [BrightControl](https://github.com/gi-os/BrightControl) · **BrightWay** (you are here) · [BrightChat](https://github.com/gi-os/BrightChat) · [browse all 26 →](https://brightmarket.gzl.dev)

The Light Phone does not sponsor or endorse any of these. Built by
[Giovanni Lupo](https://github.com/gi-os) — if this one is useful to you, a ⭐ helps the next
person find it.
<!-- bright-footer:end -->

## Support

These apps are free, open, and built on my own time. Sponsorship pays the bills that don't go away: build servers, test hardware, and the crash reporter that keeps them shipping. Donation or not my code is always free for the world to use.

[Sponsor on GitHub](https://github.com/sponsors/gi-os)
