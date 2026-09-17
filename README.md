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
   ```

   `app/build.gradle.kts` reads them into `BuildConfig` (missing keys become empty strings, so a
   fresh checkout still builds). The backend URL and secret can also be overridden at runtime in
   Settings → Advanced.
3. Deploy the backend: `backend/README.md`.

## Build

```bash
.\gradlew.bat :app:compileDebugKotlin
.\gradlew.bat :app:testDebugUnitTest
```

Detekt runs before every compile (`config/detekt/detekt.yml`).

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

