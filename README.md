# Holiday Planner

Private Android app for collecting places to see, capturing shared links, and planning trips.
Backend is a Google Sheet behind an Apps Script web app (see `backend/README.md`).

## Setup

1. Open the project in Android Studio (AGP 9.3, Kotlin 2.2, JDK 21).
2. Add these keys to `local.properties` (never committed):

   ```properties
   SHEETS_URL=https://script.google.com/macros/s/XXX/exec
   SHEETS_SECRET=the-script-property-SECRET
   MAPS_API_KEY=
   PHOTOS_URL=https://holiday-planner.<your-subdomain>.workers.dev
   PHOTOS_TOKEN=the-worker's-PHOTO_TOKEN
   ```

   `PHOTOS_URL` and `PHOTOS_TOKEN` are optional: with them, photos are stored in Cloudflare R2
   through the Worker in `tools/mcp` (quick); without them, in Drive through Apps Script (slow).
   Either way a photo uploads in the background, so adding one never waits.

   `app/build.gradle.kts` reads them into `BuildConfig` (missing keys become empty strings, so a
   fresh checkout still builds). The backend URL and secret can also be overridden at runtime in
   Settings → Advanced.
3. Deploy the backend: `backend/README.md`.
4. Set up Google Cloud for autocomplete and the map: see **Google Cloud** below.

## Google Cloud

Only needed for place autocomplete and the map. Without `MAPS_API_KEY` the app still works: the
place sheet opens straight into typing a name, and the map toggle shows nothing to map.

- Enable **Places API (New)**, not the legacy Places API. The code calls the new-API initialiser.
- Enable **Maps SDK for Android**. Map loads on mobile are unlimited free.
- Attach a billing account. A free trial never auto-charges and never auto-upgrades, so nothing can
  be billed until Activate is clicked. The trial expires after 90 days and the project is deleted
  30 days later, taking the key with it.
- Restrict the key to Android apps (package `com.pat.app.holidayplanner` plus the debug and release
  SHA-1 fingerprints) and to those two APIs. An Android-restricted key only works from an app
  signed with that fingerprint, which is why shipping it inside the APK is acceptable.
- Once the account is paid, cap `AutocompletePlacesRequest per day` and `GetPlaceRequest per day` at
  300 each. The free allowance is 10,000 a month per API; the defaults are 175,000 and 125,000 a
  **day**. Quotas cannot be changed while on the free trial.
- Add a budget alert at a small amount as a canary. Alerts only email; quotas are what stop usage.

Get the debug fingerprint with:

```bash
.\gradlew.bat :app:signingReport
```

## Releasing

There is no CI and no git remote: releases are built here and handed to the phones.

1. Create a keystore once, and keep it somewhere safe. Losing it means a new application identity
   and a manual reinstall on both phones.

   ```bash
   keytool -genkeypair -v -keystore holidayplanner.jks -alias holidayplanner -keyalg RSA -keysize 4096 -validity 10000
   ```

2. Point `local.properties` at it:

   ```properties
   RELEASE_KEYSTORE=D:/path/to/holidayplanner.jks
   RELEASE_STORE_PASSWORD=...
   RELEASE_KEY_ALIAS=holidayplanner
   RELEASE_KEY_PASSWORD=...
   ```

3. Add that keystore's SHA-1 to the API key restriction, or autocomplete and the map will fail in
   release builds while working in debug. `.\gradlew.bat :app:signingReport` prints it.
4. Bump `versionCode` and `versionName` in `app/build.gradle.kts`.
5. Build and install:

   ```bash
   .\gradlew.bat :app:assembleRelease
   ```

   The APK lands in `app/build/outputs/apk/release/`. Install it by USB with
   `adb install -r <apk>`, or send the file to the other phone and open it.

Without a keystore the release build is unsigned and will not install; debug builds are unaffected.

### Auto-updating

Both phones update themselves from the app's own channel on the Cloudflare Worker, instead of the
Firebase App Distribution the plan named: no extra accounts, and the Worker was already there for
photos. Publishing is `python tools/release/publish.py`; setup and how updates arrive are in
`tools/release/README.md`. The version number is the commit count, so steps 4 and 5 above are
replaced by committing and running that script.

## Build

```bash
.\gradlew.bat :app:compileDebugKotlin
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:installDebug
```

Detekt runs before every compile (`config/detekt/detekt.yml`) and the build fails on any finding.
Fix them rather than suppressing; where a suppression is genuinely right, scope it to the narrowest
declaration.

`JAVA_HOME` must point at a JDK 21. In PowerShell:

```bash
$env:JAVA_HOME = "D:\Program Files\Android Studio\jbr"
```

## Layout

Single `:app` module, package-by-feature under `com.pat.app.holidayplanner`:

- `data/` local DB, remote API, sync, repositories, preferences
- `domain/` pure logic (sort keys, Maps URLs, parsing)
- `ui/` one package per screen family, plus `navigation/` and `components/`
- `backend/` the Apps Script source and deploy notes

## How data moves

Room is the single source of truth and the screens observe it. Only the sync worker talks to the
sheet. Every local write stamps the row with the current time and user, marks it dirty, and asks for
a sync a few seconds later; a burst of edits collapses into one run.

A sync run pushes every dirty row in batches, then pulls everything the server has stamped since the
last successful pull. Local tables are never cleared. When a row comes back that was edited locally
and is newer, the local copy wins and goes out on the next push.

The server clock of the last pull lives in the `sync_state` table rather than in DataStore, so
dropping the database also drops it. Otherwise a rebuilt database would never ask for the rows it
just lost. A schema change drops and refills rather than migrating, which is why that matters.

## Ordering

Lists are ordered by a fractional sort key, so dragging a row to a new position rewrites that one
row instead of renumbering the list. Two people reordering different items therefore never conflict.
The sort mode itself is a local preference, and dragging is only offered when the list is in manual
order, since that is the only order a person chose.

