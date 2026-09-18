"""Sends places to the sheet's Inbox and imports them.

    python tools/import/inbox.py places.csv          # import
    python tools/import/inbox.py places.csv --dry    # show what would be sent

The file is CSV with a header row using any of these columns, in any order:

    country, city, spot, type, priority, notes, link

Only `spot` is required (a city or country alone is accepted too). `type` is poi, restaurant or
accommodation; `priority` is high or blank; `link` is a Google Maps link (which becomes the spot's
own link) or any other web address (attached to the spot). Everything is located by Google's
geocoder inside the sheet, which also settles the country when it is left blank.
"""
import csv
import sys
from pathlib import Path

import backend

COLUMNS = ["country", "city", "spot", "type", "priority", "notes", "link"]


def read(path):
    rows = []
    with Path(path).open(encoding="utf-8-sig", newline="") as f:
        for raw in csv.DictReader(f):
            row = {col: (raw.get(col) or "").strip() for col in COLUMNS}
            if any(row[c] for c in ("spot", "city", "country")):
                rows.append(row)
    return rows


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(2)
    rows = read(sys.argv[1])
    for row in rows:
        print(" | ".join(row[c] for c in COLUMNS if row[c]))
    print(f"{len(rows)} rows")
    if "--dry" in sys.argv:
        sys.exit(0)
    result = backend.inbox(rows)
    if not result.get("ok"):
        print("failed:", result)
        sys.exit(1)
    for r in result.get("results", []):
        print(f"{r['result']:>10}  {r['spot']}")
