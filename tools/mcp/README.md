# "Add this to my travel plans", from any Claude chat

`worker.js` is a small MCP server that runs as a Cloudflare Worker (free tier is plenty). Added to
claude.ai as a custom connector, it gives Claude two tools in every chat, on the web and in the
phone app:

- **add_places**: files places into the backend through the sheet's Inbox (see `docs/import.md`),
  so "tell me about Kasteeltuinen Arcen" followed by "add it to my travel plans" just works.
- **list_places**: what is already saved, so Claude checks for duplicates and can answer "what do
  I have in Croatia?".

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

## Notes

- The token in the URL is the only lock on the door. Anyone with the full URL could add places.
  Keep it private; rotate it by changing the secret in Cloudflare and the URL in claude.ai.
- Claude decides when to call the tools from the descriptions in `worker.js`. If it adds things
  you did not want, or phrases spots badly, tighten those descriptions.
- Apps Script geocodes each row; a run of more than ~150 places at once may hit its time limit.
