# Backend: Google Sheet + Apps Script

The backend is one Google Sheet with a tab per table, exposed through the Apps Script web app in
`Code.gs`. The app talks to it with JSON POSTs. There is no other server.

## First deploy

1. Create a Google Sheet (any name). Open **Extensions → Apps Script**.
2. Replace the editor contents with `Code.gs` and save.
3. Select the `setup` function in the toolbar and **Run** it. Approve the permissions prompt.
   The sheet now has the tabs `Users`, `Countries`, `Cities`, `Spots`, `Links`, `Hearts`, `Trips`,
   `TripItems` and `RouteStops`, each with a frozen header row and plain-text formatting.
4. **Project Settings (gear) → Script properties → Add script property**: name `SECRET`, value a long
   random string. Put the same value in `local.properties` as `SHEETS_SECRET`.
5. **Deploy → New deployment**. Type: **Web app**. Execute as: **Me**. Who has access: **Anyone**.
   Copy the URL ending in `/exec` into `local.properties` as `SHEETS_URL`.
6. Rebuild the app. In Settings → Advanced, tap **Ping backend**. It should show `OK` and the server time.

## Updating `Code.gs`

Paste the new code, save, then **Deploy → Manage deployments → Edit (pencil) → Version: New version → Deploy**.
Creating a *new* deployment instead changes the URL, and every phone would need it.

If the schema gained columns, run `setup()` again. It only appends missing columns.

## Manual edits in the sheet

You can edit data rows by hand. The `onEdit` trigger stamps `updatedAt`, `syncedAt` and
`updatedBy = sheet`, so the edit reaches the phones on their next pull. Do not edit the header row,
and do not add tabs with the same names for other purposes. Extra tabs with other names are ignored.

## Manual tests

Replace `URL` and `S` with your deployment URL and secret. `-L` follows the 302 redirect Apps Script
returns for every request; the redirected request is a GET, which is expected.

Use a cookie jar (`-c`/`-b`). Apps Script redirects to a one-shot `user_content_key` URL, and
fetching it without the cookies the 302 set returns a Drive "Cannot open the file at the moment"
HTML page instead of the JSON. The GET health check fails this way every time without it.

**An HTML error page does not mean the call failed.** The script runs and commits its writes before
the response is fetched, so a push that returns the Drive error page has still written its rows.
Re-pushing the same rows is safe: last-write-wins skips anything not newer.

```bash
URL="https://script.google.com/macros/s/XXX/exec"
S="your-secret"
C="$(mktemp)"   # cookie jar, see above

# health check (GET, no secret needed)
curl -sL -c "$C" -b "$C" "$URL"

# ping
curl -sL -c "$C" -b "$C" -H "Content-Type: application/json" -d "{\"secret\":\"$S\",\"action\":\"ping\"}" "$URL"

# push a user
curl -sL -c "$C" -b "$C" -H "Content-Type: application/json" -d "{\"secret\":\"$S\",\"action\":\"push\",\"rows\":{\"Users\":[{\"id\":\"u1\",\"name\":\"Test\",\"createdAt\":\"1\",\"createdBy\":\"u1\",\"updatedAt\":\"100\",\"updatedBy\":\"u1\",\"deleted\":\"false\"}]}}" "$URL"

# older updatedAt must be skipped: expect "skipped":["u1"]
curl -sL -c "$C" -b "$C" -H "Content-Type: application/json" -d "{\"secret\":\"$S\",\"action\":\"push\",\"rows\":{\"Users\":[{\"id\":\"u1\",\"name\":\"Older\",\"updatedAt\":\"50\"}]}}" "$URL"

# formula-injection guard: the name must come back as "=1+1", not "2" and not "'=1+1"
curl -sL -c "$C" -b "$C" -H "Content-Type: application/json" -d "{\"secret\":\"$S\",\"action\":\"push\",\"rows\":{\"Users\":[{\"id\":\"u2\",\"name\":\"=1+1\",\"createdAt\":\"1\",\"createdBy\":\"u1\",\"updatedAt\":\"100\",\"updatedBy\":\"u1\",\"deleted\":\"false\"}]}}" "$URL"

# pull everything
curl -sL -c "$C" -b "$C" -H "Content-Type: application/json" -d "{\"secret\":\"$S\",\"action\":\"pull\",\"since\":0}" "$URL"

# wrong secret: expect "unauthorized"
curl -sL -c "$C" -b "$C" -H "Content-Type: application/json" -d "{\"secret\":\"wrong\",\"action\":\"ping\"}" "$URL"
```

Then check `onEdit`: change the `name` of `u1` directly in the `Users` tab, and confirm its `updatedAt`,
`syncedAt` and `updatedBy` cells were stamped. A `pull` with `since` set to just before the edit must
return that row.

## Behaviour notes

- Every response is HTTP 200. Check `ok` in the body.
- Requests are serialised with a script lock (30 s wait). A timeout returns `error: "busy"`.
- `pull` filters on the server-side `syncedAt`, with `>=` so nothing is ever missed.
- `push` is last-write-wins on `updatedAt` and always sets `syncedAt` to the server clock.
- Columns are matched by header name, never by position.
- Cold starts take a few seconds. The app uses a 30 s timeout.
