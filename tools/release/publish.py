"""Builds a signed release of the app and publishes it to the update channel.

Both phones check the channel on app start and every hour on Wi-Fi, and offer (or, once the app
has updated itself once, silently install) whatever is newest. See tools/release/README.md.

    python tools/release/publish.py            build, check, upload
    python tools/release/publish.py --install  and also install it on the phone plugged in by adb

Refuses to publish from a working tree with uncommitted changes: the version number is the commit
count, so an uncommitted build would carry the number of the commit before it.
"""

import argparse
import hashlib
import json
import os
import subprocess
import sys
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
JAVA_HOME = r"D:\Program Files\Android Studio\jbr"
ADB = r"D:\Work\AndroidSDK\platform-tools\adb.exe"
USER_AGENT = "HolidayPlanner-publish/1.0"


def local_properties():
    props = {}
    for line in (ROOT / "local.properties").read_text(encoding="utf-8").splitlines():
        if "=" in line and not line.lstrip().startswith("#"):
            key, value = line.split("=", 1)
            props[key.strip()] = value.strip()
    return props


def fail(message):
    print(f"publish: {message}", file=sys.stderr)
    sys.exit(1)


def run(args, **kwargs):
    return subprocess.run(args, cwd=ROOT, check=True, **kwargs)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--install", action="store_true", help="also adb install it on the attached phone")
    options = parser.parse_args()

    props = local_properties()
    base = props.get("PHOTOS_URL", "").rstrip("/")
    token = props.get("RELEASE_TOKEN", "")
    if not base or not token:
        fail("PHOTOS_URL and RELEASE_TOKEN must both be set in local.properties")
    if not props.get("RELEASE_KEYSTORE"):
        fail("no RELEASE_KEYSTORE in local.properties; an unsigned build cannot be installed")

    dirty = run(["git", "status", "--porcelain"], capture_output=True, text=True).stdout.strip()
    if dirty:
        fail("commit first; the version number comes from the commit count\n" + dirty)

    env = dict(os.environ, JAVA_HOME=JAVA_HOME)
    gradlew = str(ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew"))
    run([gradlew, ":app:assembleRelease", "--console=plain", "-q"], env=env)

    out = ROOT / "app/build/outputs/apk/release"
    element = json.loads((out / "output-metadata.json").read_text(encoding="utf-8"))["elements"][0]
    apk = out / element["outputFile"]
    if "unsigned" in apk.name:
        fail(f"{apk.name} is unsigned")
    code, name = element["versionCode"], element["versionName"]
    data = apk.read_bytes()
    digest = hashlib.sha256(data).hexdigest()
    print(f"built {apk.name}: version {name} (code {code}), {len(data) / 1_000_000:.1f} MB")

    request = urllib.request.Request(
        f"{base}/app/release",
        data=data,
        method="PUT",
        headers={
            "Authorization": f"Bearer {token}",
            "Content-Type": "application/vnd.android.package-archive",
            "User-Agent": USER_AGENT,
            "X-Version-Code": str(code),
            "X-Version-Name": name,
            "X-Sha256": digest,
        },
    )
    try:
        with urllib.request.urlopen(request, timeout=600) as response:
            print("published:", json.loads(response.read()).get("apk"))
    except urllib.error.HTTPError as error:
        body = error.read().decode(errors="replace")[:300]
        if error.code == 409:
            print(f"not published, the channel already has this version or newer: {body}")
        else:
            fail(f"HTTP {error.code}: {body}")

    if options.install:
        run([ADB, "install", "-r", str(apk)])


if __name__ == "__main__":
    main()
