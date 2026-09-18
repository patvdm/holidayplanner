---
name: import-places
description: Turn a messy list of places (any format: notes, a table, a chat message) into spots in the Holiday Planner backend, via the sheet's Inbox. Use when Pat pastes places to add, or asks to add a list to the app.
---

# Import places into the Holiday Planner backend

The backend is a Google Sheet with an `Inbox` tab. Anything written there in plain words is turned
into countries, cities and spots by `importInbox()` in `backend/Code.gs`, which geocodes each row
with Google, settles the country from the result, files the spot under an existing city or a new
one, and writes `done`, `exists` or a failure reason back into the row's `result` column.

From this repo the Inbox is fed through the API with `tools/import/inbox.py`.

## Steps

1. Read what Pat gave you. Shape every place into one row with these columns:
   `country, city, spot, type, priority, notes, link`.
   - One real place per row. A line naming several things ("Den Haag: Peace Palace, forest walk")
     becomes several rows. A bare country or region gets one obvious anchor spot, noted "suggested".
   - `spot` is the place as Google would find it. `city` is where Pat would look for it in the app,
     even if the place is strictly in a neighbouring town (say so in `notes`).
   - `type`: `poi` (default), `restaurant`, `accommodation`.
   - `priority`: `high` for anything Pat ranks at the top or marks as a must; otherwise blank.
   - `notes`: Pat's own words, plus any fact you looked up that matters (opening season, "it is
     actually in X"). Keep a rank as "#3" if the list was ranked.
   - `link`: a Google Maps link becomes the spot's own link; an Instagram reel, TikTok, YouTube or
     web page is attached to the spot. Truncated links are useless: leave them out.
   - Look things up (web search) when a line is an event, a nickname, or ambiguous. If it still
     cannot be pinned down, keep it with a note saying so rather than dropping it.
2. Write the rows to a CSV in the scratchpad (header row first, UTF-8).
3. Show Pat the rows as a table and run `python tools/import/inbox.py <file> --dry` to check the
   file reads. If he has not seen the shaping yet, stop here and let him look; if he already said
   to go ahead, continue.
4. Run `python tools/import/inbox.py <file>`. It prints one result per row. `exists` means a spot
   of that name is already in that city. A `failed: could not locate` row needs a better `spot`
   or `city` wording; fix and rerun only those rows.
5. Report the counts and anything that failed or was guessed. The phones pick the rows up on their
   next sync; pictures follow automatically.

## Notes

- `python tools/import/backend.py` prints everything currently in the sheet, for checking what
  exists before adding (cities are matched by name within a country, case-insensitively).
- Keep a single run under about 150 rows; Apps Script has a six-minute limit and geocodes each row.
- Trips and routes are not imported this way; they are made in the app.
