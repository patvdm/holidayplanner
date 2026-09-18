# Adding places without the app

The backend sheet has an **Inbox** tab. Type or paste places there in plain words and they become
countries, cities and spots in the app. No ids, no timestamps, no PC needed: it works in the Google
Sheets app on a phone.

## The Inbox tab

| column | what goes in it |
|---|---|
| `country` | Optional. Left blank, the country comes from the geocoder. |
| `city` | Where you want to find it in the app. Left blank, the geocoder's town is used. |
| `spot` | The place, worded so Google finds it: "Vredespaleis", "Kasteeltuinen Arcen". |
| `type` | `poi` (default), `restaurant` or `accommodation`. |
| `priority` | `high`, or blank. |
| `notes` | Anything. The first line shows on the spot's row in the app. |
| `link` | A Google Maps link becomes the spot's own link. Any other link is attached to the spot. |
| `result` | Written by the import: `done`, `exists`, or why it failed. Leave blank; clear it to retry a row. |

Rows with something in `result` are left alone, so the tab doubles as a log. Delete old rows
whenever you like.

## Running the import

- **In a browser**: menu **Holiday Planner → Import inbox rows**.
- **Automatically**: run `installInboxTrigger()` once from the Apps Script editor and the Inbox is
  imported every ten minutes, which is what makes it work from a phone.
- **From this repo**: `python tools/import/inbox.py places.csv` sends a CSV with the same columns
  through the API and imports it at once. `/import-places` in Claude Code does the shaping for you.

Each row is geocoded once with Google (Apps Script's Maps service, free within its daily quota),
which gives the coordinates and settles the country. A spot with a name that already exists in
that city is reported as `exists` and not added twice.

## Using Claude (chat, no Claude Code) to shape a messy list

Paste this into a claude.ai Project's instructions, or at the top of a chat, then paste your list:

> Turn the list I give you into rows for a spreadsheet with exactly these columns, tab-separated,
> header row first: country, city, spot, type, priority, notes, link. One real place per row; a
> line that names several things becomes several rows. spot is the place as Google Maps would find
> it. city is the town I would look under, even if the place is strictly in a neighbouring town
> (say so in notes). type is poi, restaurant or accommodation. priority is "high" only for things I
> ranked at the top or called a must, else blank. notes keeps my own words plus anything you looked
> up that matters, such as opening seasons. link is a single URL or blank; drop truncated links.
> For a bare country or region, add one obvious anchor place and write "suggested" in notes.
> Output only the table.

Copy the table, paste it into the Inbox tab under the header (Sheets splits tab-separated text
into columns), and the next import picks it up.
