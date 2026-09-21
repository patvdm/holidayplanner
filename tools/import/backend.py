"""Talks to the Holiday Planner backend the way the app does. Secrets come from local.properties."""
import json
import subprocess
import sys
import tempfile
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
JAR = Path(tempfile.gettempdir()) / "holiday_planner_cookies.txt"


def secrets():
    props = {}
    for line in (ROOT / "local.properties").read_text(encoding="utf-8").splitlines():
        if "=" in line and not line.startswith("#"):
            key, value = line.split("=", 1)
            props[key.strip()] = value.strip()
    return props["SHEETS_URL"], props["SHEETS_SECRET"]


# What an answer to each action must carry, so a reply meant for an earlier call is spotted.
ANSWERS = {"pull": "changes", "push": "accepted", "inbox": "results", "image": "url",
           "deleteImage": "trashed", "ping": "serverTime"}


def call(body, attempts=3):
    """Calls the backend and insists on an answer to the call actually made.

    Apps Script redirects each POST to a single-use URL, and asking twice in quick succession can
    hand back the previous call's body; a pull answered by a push reads as an empty database.
    """
    expected = ANSWERS.get(body.get("action"))
    last = None
    for attempt in range(attempts):
        last = call_once(body)
        stale = (not last.get("ok") and last.get("error") == "not json") or (
            last.get("ok") and expected and expected not in last)
        if not stale:
            return last
        time.sleep(0.4 * (attempt + 1))
    return last


def call_once(body):
    """One request. Apps Script answers with a redirect that needs the cookies it set, so curl
    keeps a jar, and a fresh one each time: a stale jar gets a Google sign-in page instead."""
    JAR.unlink(missing_ok=True)
    url, secret = secrets()
    with tempfile.NamedTemporaryFile("w", suffix=".json", delete=False, encoding="utf-8") as f:
        json.dump(dict(body, secret=secret), f)
        path = f.name
    try:
        out = subprocess.run(
            ["curl", "-sL", "-c", str(JAR), "-b", str(JAR), "-H", "Content-Type: application/json",
             "--data-binary", f"@{path}", url],
            capture_output=True, text=True, encoding="utf-8",
        )
    finally:
        Path(path).unlink(missing_ok=True)
    try:
        return json.loads(out.stdout)
    except json.JSONDecodeError:
        return {"ok": False, "error": "not json", "raw": out.stdout[:400]}


def pull():
    return call({"action": "pull", "since": 0})


def push(rows):
    return call({"action": "push", "rows": rows})


def inbox(rows):
    return call({"action": "inbox", "rows": rows})


if __name__ == "__main__":
    data = pull()
    if not data.get("ok"):
        print("pull failed:", data)
        sys.exit(1)
    live = {t: [r for r in rows if r.get("deleted") != "true"] for t, rows in data["changes"].items()}
    print({t: len(rows) for t, rows in live.items()})
    countries = sorted(live.get("Countries", []), key=lambda r: r.get("sortKey", ""))
    cities = live.get("Cities", [])
    spots = live.get("Spots", [])
    for c in countries:
        print(f"{c['isoCode']} {c['name']}")
        for ci in sorted([x for x in cities if x["countryId"] == c["id"]], key=lambda r: r.get("sortKey", "")):
            names = [s["name"] for s in sorted(spots, key=lambda r: r.get("sortKey", "")) if s["parentCityId"] == ci["id"]]
            print(f"   {ci['name']}: {', '.join(names)}")
