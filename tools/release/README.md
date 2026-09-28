# Keeping both phones on the latest version

The app updates itself from its own channel: the Cloudflare Worker in `tools/mcp`, which keeps
the newest release in the same R2 bucket as the photos.

- **Publishing** (this PC): commit, then `python tools/release/publish.py`. It builds a signed
  release, checks it, and uploads it. Add `--install` to also put it on the phone plugged in by adb.
- **Receiving** (the phones): the app asks the channel on every start and every hour, on Wi-Fi
  only, since the APK is large (about 66 MB). A newer version is downloaded, its checksum and
  package checked, and then offered with **Install**. *Settings → Check for updates* does the
  same at once, on mobile data too.
- **Without a tap:** Android lets an app update itself silently only once it is its own
  "installer of record", which it becomes after the first update it installs. From then on, a
  version found while the app is closed is installed without asking.

The version number is the commit count (`app/build.gradle.kts`), which is why `publish.py` refuses
to run with uncommitted changes, and why the Worker refuses anything not newer than what it has.

## One-time setup

1. **Signing key.** Lives in `D:\Git Projects\holidayplanner-signing\holidayplanner.jks`, its
   passwords in `local.properties` (`RELEASE_*`). **Back up the folder and those lines together.**
   Losing the key means neither phone can take another update without uninstalling first.
2. **Worker.** Paste the current `tools/mcp/worker.js` and deploy. Add a Secret `RELEASE_TOKEN`
   with the value of `RELEASE_TOKEN` in `local.properties` (the PC's publishing key; the phones
   never see it).
3. **Maps key.** In Google Cloud, add the release key's SHA-1 to the API key's Android
   restriction (package `com.pat.app.holidayplanner`), next to the debug one, or the map and place
   search fail in release builds. Print it with `.\gradlew.bat :app:signingReport`.
4. **Each phone, once.** A phone running a debug build (signed with a different key) must
   uninstall it before the release can go on; the data comes back from the sheet on first sync,
   but let a sync finish first so nothing unsynced is lost. Then install the release APK by adb or
   by sending the file (`app/build/outputs/apk/release/app-release.apk`) and opening it, allowing
   "install unknown apps" when Android asks. From then on updates arrive by themselves.

## Checking the channel

```bash
curl -s -H "Authorization: Bearer <PHOTOS_TOKEN>" https://holiday-planner.<subdomain>.workers.dev/app/latest
```

answers with the published `versionCode` and `versionName`.
