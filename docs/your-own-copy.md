# Run your own copy of Holiday Planner

This guide gets you **your own** Holiday Planner. Your trips go in **your** Google Sheet, under your
Google account, and the app uses **your** keys. Nothing links your copy to anyone else's. The only
thing you share with the person who gave you this repo is the code.

The minimum (steps 1 to 3) takes about 20 minutes. Everything, including the map, fast photos and
automatic updates, takes about an hour.

---

## What you are setting up

| Part | Do you need it? | What it gives you | Cost |
|---|---|---|---|
| **Google Sheet + Apps Script** | **Yes** | Where all your data lives. Phones sync through it. | Free |
| **Google Maps key** | Recommended | The map, place search, opening hours, Google photos of places | Free for personal use, but Google wants a billing account |
| **Cloudflare Worker** | Optional | Fast photo uploads, automatic app updates, adding places from a Claude chat | Free tier |
| **Signing key** | Optional (needed for auto-updates) | Release builds that can update themselves | Free |

Without the optional parts the app still works. Photos upload more slowly (to Google Drive), and
you install new versions yourself from Android Studio.

## Before you start

- **Android Studio**, a recent version. It comes with the JDK 21 the build needs.
- **A Google account** to own the sheet.
- **An Android phone** on Android 9 or newer.

All your keys and passwords go in one file, `local.properties`, which git never commits. This
guide fills it in step by step. `local.properties.example` lists every line you will need.

---

## Step 1: Build the code

1. Clone the repo and open the folder in Android Studio. Let the Gradle sync finish.
   Android Studio creates `local.properties` with one line in it (`sdk.dir=...`). **Keep that line.**
2. Open `local.properties.example`, copy everything from `SHEETS_URL=` down, and paste it at the
   end of `local.properties`. Leave the values blank for now.
3. Build once, to make sure everything is in place:

   ```bash
   ./gradlew :app:assembleDebug
   ```

   On Windows use `.\gradlew.bat`. If it complains about Java, point `JAVA_HOME` at Android Studio's
   own JDK, the `jbr` folder inside the Android Studio install. In PowerShell:
   `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"` (use your own path).

**It worked if** the build ends with `BUILD SUCCESSFUL`. The build also runs detekt, a strict code
checker. If you change code later and the build fails on a detekt finding, fix the finding: that
is how the project stays tidy.

> **Optional: your own app id.** The app id is `com.pat.app.holidayplanner` (`applicationId` in
> `app/build.gradle.kts`). You can keep it; it only matters if one phone ever has two copies
> installed. To change it, edit that one line, for example to `com.yourname.holidayplanner`, and
> use the new id when you set up the Maps key in step 4. Leave `namespace` as it is.

---

## Step 2: Make your sheet (the backend)

The app keeps its data in a Google Sheet, through a small script that runs inside the sheet.

1. Go to [sheets.new](https://sheets.new) to make a new, empty Google Sheet. Call it anything, for
   example *Holiday Planner*.
2. In the sheet, open **Extensions → Apps Script**. Delete what is in the editor, paste in the whole
   of `backend/Code.gs` from this repo, and press **Save**.
3. In the toolbar, choose the function **`setup`** and press **Run**. Google asks for permission
   ("Google hasn't verified this app": choose *Advanced → Go to ... (unsafe)*. It is your own
   script). Afterwards the sheet has tabs called `Users`, `Countries`, `Cities`, `Spots` and so on.
4. **Make a secret.** This is the password the app uses to talk to your sheet. Any long random
   string will do. A password manager can make one, or run this in PowerShell:

   ```bash
   -join ((48..57) + (65..90) + (97..122) | Get-Random -Count 40 | % {[char]$_})
   ```

5. In Apps Script, open **Project Settings** (the gear) → **Script properties** →
   **Add script property**. Name: `SECRET`. Value: your secret. Save.
6. **Deploy → New deployment.** Click the gear next to *Select type* and choose **Web app**:
   - Execute as: **Me**
   - Who has access: **Anyone**

   Press **Deploy** and copy the **Web app URL** (it ends in `/exec`).

   *"Anyone" sounds alarming, but it only means the app can reach the script without signing in
   to Google. The script refuses every request that does not carry your secret.*
7. In `local.properties`:

   ```properties
   SHEETS_URL=https://script.google.com/macros/s/....../exec
   SHEETS_SECRET=your-secret
   ```

**Keep these two values private.** Anyone with both can read and change your trips.

---

## Step 3: Put it on your phone

1. Turn on USB debugging on the phone and plug it in (or pair it over Wi-Fi in Android Studio).
2. Press **Run** in Android Studio, or:

   ```bash
   ./gradlew :app:installDebug
   ```

3. The app opens on **Who is planning?** Tap **I'm new**, type your name, and continue.
4. Check the connection: **Settings** (from the menu on any tab) → **Advanced** → **Ping backend**.

**It worked if** the ping shows `OK` and a time. Add a country in the app; within a minute it shows
up in the `Countries` tab of your sheet.

> **Planning with someone else?** Install the same build on their phone. On first launch they tap
> **I'm new** and type their own name. You both then see the same trips, and each change shows who
> made it. Only give your build to people you want in your sheet: the sheet's address and secret
> are inside it.

The settings in `local.properties` are built into the app. **After changing any of them, rebuild
and reinstall.**

---

## Step 4: The map and place search (recommended)

Without a Maps key the app works, but there is no map, and you type place names instead of picking
them from Google's suggestions.

1. Go to [console.cloud.google.com](https://console.cloud.google.com) and create a project (any
   name).
2. **APIs & Services → Library**: enable **Places API (New)** (not the plain "Places API") and
   **Maps SDK for Android**.
3. **Billing**: attach a billing account. Google requires one even though personal use stays
   inside the free allowance. A free trial cannot charge you unless you press *Activate*.
4. **APIs & Services → Credentials → Create credentials → API key.** Then edit the key:
   - **Application restrictions:** *Android apps*. Add the app id (`com.pat.app.holidayplanner`,
     or yours from step 1) with your debug SHA-1. To get the SHA-1, run
     `./gradlew :app:signingReport` and copy the `SHA1` line under `Variant: debug`.
   - **API restrictions:** *Restrict key*, and tick only the two APIs from point 2.
5. In `local.properties`: `MAPS_API_KEY=your-key`. Rebuild and reinstall.

**It worked if** the map tab shows a map and adding a spot suggests places as you type.

Worth doing as well, so a bug can never run up a bill:

- Once billing is no longer a trial: **APIs & Services → Places API (New) → Quotas**. Set
  `AutocompletePlacesRequest per day` and `GetPlaceRequest per day` to about 300 each.
- **Billing → Budgets & alerts**: a small budget, say 5, so you get an email if anything is ever
  charged.

---

## Step 5: Cloudflare Worker (optional)

One small free Cloudflare Worker does three jobs:

- **Photos.** Your own photos upload in a second instead of going slowly through Google Drive.
- **Updates.** Your phones update the app by themselves (needs step 6 too).
- **Claude.** If you use claude.ai, you can say "add this to my travel plans" in any chat.

Full details are in `tools/mcp/README.md`. In short:

1. Sign up at [dash.cloudflare.com](https://dash.cloudflare.com) (free).
2. **Compute (Workers) → Create → Start with Hello World.** Name it `holiday-planner` and deploy.
   Then **Edit code**, replace everything with `tools/mcp/worker.js`, and deploy again.
3. **R2 Object Storage → Create bucket**, named `holiday-planner-photos`. Cloudflare asks for a
   payment method once, even on the free tier. Leave public access off.
4. In the Worker, **Settings → Bindings → Add → R2 bucket**: variable name `PHOTOS`, bucket
   `holiday-planner-photos`.
5. In the Worker, **Settings → Variables and Secrets**, add these, each as type **Secret**:

   | Name | Value |
   |---|---|
   | `SHEETS_URL` | the same as in `local.properties` |
   | `SHEETS_SECRET` | the same as in `local.properties` |
   | `PHOTO_TOKEN` | a new long random string |
   | `RELEASE_TOKEN` | another new long random string |
   | `ACCESS_TOKEN` | another one, only needed for the Claude connector |

   Deploy once more so the secrets apply.
6. In `local.properties`:

   ```properties
   PHOTOS_URL=https://holiday-planner.<your-subdomain>.workers.dev
   PHOTOS_TOKEN=the PHOTO_TOKEN value
   RELEASE_TOKEN=the RELEASE_TOKEN value
   ```

   Rebuild and reinstall.

**It worked if** opening `https://holiday-planner.<your-subdomain>.workers.dev/photos/nothing` in a
browser says *Not found*. If it says "No PHOTOS bucket bound", point 4 is missing.

For Claude: on claude.ai go to **Settings → Connectors → Add custom connector**, with the URL
`https://holiday-planner.<your-subdomain>.workers.dev/<ACCESS_TOKEN>/mcp`.

---

## Step 6: Release builds and automatic updates (optional)

Debug builds from Android Studio are fine for one phone. To update every phone without a cable,
build signed releases and publish them to your Worker. Full details are in
`tools/release/README.md`.

1. **Make a signing key, once.** Keep it **outside** this folder and back it up. If you lose it,
   no phone can take another update without uninstalling first.

   ```bash
   keytool -genkeypair -v -keystore holidayplanner.jks -alias holidayplanner -keyalg RSA -keysize 4096 -validity 10000
   ```

2. Fill in the `RELEASE_*` lines in `local.properties` (the path with forward slashes).
3. Run `./gradlew :app:signingReport` again. Add the SHA-1 under `Variant: release` to your Maps
   key's Android restriction, next to the debug one. Without it the map works in debug builds but
   not in releases.
4. Commit your work, then publish (needs Python 3):

   ```bash
   python tools/release/publish.py
   ```

   It builds, checks and uploads the release. The version number is the git commit count, which is
   why it refuses to run with uncommitted changes.
5. **Each phone, once:** let the app sync, uninstall the debug build, then install
   `app/build/outputs/apk/release/app-release.apk` (by adb, or send the file to the phone and open
   it). Your data comes back from the sheet.

From then on, phones check for updates when the app starts and every hour on Wi-Fi.
**Settings → Check for updates** checks straight away.

---

## Taking updates from the original repo

- `git pull` brings in new code. Your `local.properties` is untouched.
- If `backend/Code.gs` changed: paste it into Apps Script again, save, then **Deploy → Manage
  deployments → pencil → Version: New version → Deploy**. Use *Manage deployments*: a *new*
  deployment gets a new URL, and every phone would need rebuilding. If new columns arrived, run
  `setup` again (it only adds what is missing).
- If `tools/mcp/worker.js` changed: paste it into the Worker and deploy.
- Then rebuild, or publish a release.

If you send changes back, check `git status` first. `local.properties` and your keystore must
never appear in it.

---

## When something goes wrong

| What you see | What to check |
|---|---|
| Settings says the backend is **not configured** | `SHEETS_URL` is empty, or you did not rebuild after filling it in. |
| Ping says **unauthorized** | `SHEETS_SECRET` does not match the `SECRET` script property exactly. |
| Ping fails with an HTML page or a sign-in page | The deployment's access is not **Anyone**, or the URL is from an old deployment. |
| The map is blank and place search does nothing | The APIs are not enabled, billing is missing, or the key's app id or SHA-1 does not match the build. |
| The map works in debug but not in a release | The release SHA-1 is not on the Maps key (step 6, point 3). |
| The release APK will not install | No `RELEASE_KEYSTORE` (the release is unsigned), or a debug build is still installed: uninstall it. |
| Photos take a long time to upload | No Worker set up, so they go through Google Drive. Step 5 fixes it. |
| Nothing appears in the sheet | Settings shows how many changes are waiting to sync. Tap **Sync now** and check the ping. |
| The build fails on Java | Point `JAVA_HOME` at Android Studio's `jbr` folder (step 1). |

More on how the sheet works, and on testing it from the command line: `backend/README.md`.
