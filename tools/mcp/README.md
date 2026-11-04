# "Add this to my travel plans", from any Claude chat

`worker.js` is a small MCP server that runs as a Cloudflare Worker (free tier is plenty). Added to
claude.ai as a custom connector, it gives Claude these tools in every chat, on the web and in the
phone app.

Places:

- **add_places**: files places into the backend through the sheet's Inbox (see `docs/import.md`),
  so "tell me about Kasteeltuinen Arcen" followed by "add it to my travel plans" just works.
- **update_place**: "mark the Rijksmuseum visited", "make Plitvice high priority", "add a note to
  Hvar", a rename, or "change Belgium's picture to this link". Works on a country, a city or a
  spot, and goes through the sync API like an edit on a phone would. Clearing a picture (an empty
  link) makes the app look for a new one by itself.
- **remove_place**: deletes a spot and its links. Claude is told to ask first.
- **list_places**: what is already saved, so Claude checks for duplicates and can answer "what do
  I have in Croatia?". With `details` it also lists notes and the picture each entry is using,
  which is what to look at before changing one.

Trips and routes, so "plan our Amsterdam weekend" can be done from a chat:

- **list_plans**: every trip and route; or one trip day by day, with each route's stops; or one
  route in full. Claude reads this before changing anything.
- **create_trip** / **update_trip**: name, country, dates and notes.
- **add_to_trip**: a saved spot (with a time, for a booking), a saved route, or a flight or train
  onto one day.
- **update_trip_item**: move something to another day or time, change its notes, or take it off
  the trip (the spot or route itself stays saved).
- **create_route** / **update_route**: a walk through one city from saved spots in order, which
  can put itself on a trip day; later rename it, reorder or replace the stops, add or remove some,
  change the notes, or mark it walked. Stop order uses the same keys as the app.

Spots on a route or trip must be saved first (add_places). Links, and deleting a whole trip or
route, stay in the app.

Nothing here touches the phones directly; they pick the rows up on their next sync.

## One-time setup (about ten minutes)

1. **Backend first.** Paste the current `backend/Code.gs` into the Apps Script editor, run
   `setup()` (it creates the `Inbox` tab), and deploy a new version
   (Deploy → Manage deployments → Edit → New version).
2. **Cloudflare.** Sign up at dash.cloudflare.com (free). *Compute (Workers) → Create → Start with
   Hello World*, name it `holiday-planner`, deploy, then *Edit code*, replace everything with
   `worker.js`, and deploy again.
3. **Secrets.** In the Worker: *Settings → Variables and Secrets → Add*, type **Secret**, three of them:
   - `SHEETS_URL`: the `/exec` URL from `local.properties`
   - `SHEETS_SECRET`: the secret from `local.properties`
   - `ACCESS_TOKEN`: a long random string (a password manager can generate one)
   Deploy once more so the secrets apply.
4. **Claude.** In claude.ai: *Settings → Connectors → Add custom connector*. Name `Holiday Planner`,
   URL `https://holiday-planner.<your-subdomain>.workers.dev/<ACCESS_TOKEN>/mcp`, no OAuth. Enable it
   in a chat's tools menu (it is on by default for new chats).

Test: "What do I have saved in the Netherlands?" then "Add the Rijksmuseum in Amsterdam, high
priority." The Inbox tab shows the row with `done` beside it, and the app shows the spot after
its next sync.

## Photos (optional, makes photo uploads much faster)

The same Worker can store the app's photos in Cloudflare R2 instead of Drive. Uploads skip the
Apps Script cold start, its redirect and the Drive calls, and the picture goes up as plain bytes.
Photos already in Drive keep working; only new ones go to R2. R2's free tier is 10 GB.

1. **Bucket.** In Cloudflare: *Storage & databases → R2 Object Storage → Create bucket*, name it
   `holiday-planner-photos`. (R2 asks for a payment method once, even on the free tier.) Leave
   Public Access disabled: the Worker serves the photos.
2. **Binding.** In the Worker: *Settings → Bindings → Add → R2 bucket*, variable name `PHOTOS`,
   bucket `holiday-planner-photos`.
3. **Secret.** *Settings → Variables and Secrets → Add*, type **Secret**, `PHOTO_TOKEN`: another
   long random string (not the same as `ACCESS_TOKEN`).
4. **Code.** Paste the current `worker.js` over the old one and deploy.
5. **App.** In `local.properties`:
   ```properties
   PHOTOS_URL=https://holiday-planner.<your-subdomain>.workers.dev
   PHOTOS_TOKEN=<the PHOTO_TOKEN value>
   ```
   and rebuild. A phone built without these still uploads to Drive, and either phone shows photos
   from both places.

Check: `curl -i https://holiday-planner.<your-subdomain>.workers.dev/photos/nothing` answers 404.
A 500 saying "No PHOTOS bucket bound" means step 2 is missing.

## Notes

- The token in the URL is the only lock on the door. Anyone with the full URL could add places.
  Keep it private; rotate it by changing the secret in Cloudflare and the URL in claude.ai.
- Claude decides when to call the tools from the descriptions in `worker.js`. If it adds things
  you did not want, or phrases spots badly, tighten those descriptions.
- Apps Script geocodes each row; a run of more than ~150 places at once may hit its time limit.
