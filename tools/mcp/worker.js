/**
 * Holiday Planner connector: a tiny MCP server for claude.ai, as a Cloudflare Worker.
 *
 * It gives Claude two tools in any chat: `add_places` (what "add this to my travel plans" calls)
 * and `list_places` (so it can check what is already saved). Both talk to the backend sheet's
 * Apps Script API, which does the geocoding and the filing through the Inbox.
 *
 * Secrets (set in the Worker's settings, never in this file):
 *   SHEETS_URL     the Apps Script /exec URL, same as local.properties
 *   SHEETS_SECRET  the script's SECRET, same as local.properties
 *   ACCESS_TOKEN   a long random string; the connector URL is https://<worker>/<ACCESS_TOKEN>/mcp
 *
 * Speaks MCP over Streamable HTTP (JSON responses, no session state), which is what claude.ai's
 * custom connectors use. See tools/mcp/README.md for the setup.
 */

const PROTOCOL_VERSIONS = ['2025-06-18', '2025-03-26', '2024-11-05'];

const TOOLS = [
  {
    name: 'add_places',
    description:
      'Adds places to the Holiday Planner travel database (the app the user and their partner plan trips in). ' +
      'Use when the user asks to add, save or remember a place, restaurant or hotel for their travels. ' +
      'One entry per real place; a line naming several things becomes several entries. Word `spot` the way ' +
      'Google Maps would find it. `city` is the town the user would look under in the app. Put the reason it is ' +
      'worth going, seasons, and anything looked up into `notes`. Returns one result per place: done, exists, ' +
      'or why it failed.',
    inputSchema: {
      type: 'object',
      properties: {
        places: {
          type: 'array',
          minItems: 1,
          items: {
            type: 'object',
            properties: {
              spot: { type: 'string', description: 'The place, as Google Maps would find it. Required.' },
              city: { type: 'string', description: 'Town to file it under. Optional; geocoded when blank.' },
              country: { type: 'string', description: 'Country name. Optional; geocoded when blank.' },
              type: { type: 'string', enum: ['poi', 'restaurant', 'accommodation'], description: 'Default poi.' },
              priority: { type: 'string', enum: ['high', 'normal'], description: 'high only for a must-see.' },
              notes: { type: 'string', description: 'Why it is worth it, seasons, tips. First line shows in lists.' },
              link: {
                type: 'string',
                description: 'One URL: a Google Maps link becomes the spot\'s own link; anything else is attached.',
              },
            },
            required: ['spot'],
          },
        },
      },
      required: ['places'],
    },
  },
  {
    name: 'list_places',
    description:
      'Lists what is already in the Holiday Planner travel database: countries, their cities and the spots in ' +
      'each. Use it before adding, to avoid duplicates, or when the user asks what they have saved somewhere. ' +
      'Optional `country` narrows it to one country by name or ISO code.',
    inputSchema: {
      type: 'object',
      properties: {
        country: { type: 'string', description: 'Country name or two-letter code to narrow to. Optional.' },
      },
    },
  },
];

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (!env.ACCESS_TOKEN || url.pathname !== `/${env.ACCESS_TOKEN}/mcp`) {
      return new Response('Not found', { status: 404 });
    }
    if (request.method === 'GET') {
      // No server-initiated stream; clients take a 405 as "post only".
      return new Response('Method Not Allowed', { status: 405, headers: { Allow: 'POST' } });
    }
    if (request.method === 'DELETE') return new Response(null, { status: 200 });
    if (request.method !== 'POST') return new Response('Method Not Allowed', { status: 405 });

    let message;
    try {
      message = await request.json();
    } catch (err) {
      return rpcError(null, -32700, 'Parse error');
    }
    if (Array.isArray(message)) {
      const answers = [];
      for (const one of message) {
        const answer = await handle(one, env);
        if (answer) answers.push(answer);
      }
      return answers.length ? jsonResponse(answers) : new Response(null, { status: 202 });
    }
    const answer = await handle(message, env);
    return answer ? jsonResponse(answer) : new Response(null, { status: 202 });
  },
};

/** One JSON-RPC message in, one result out (or null for a notification). */
async function handle(message, env) {
  const { id, method, params } = message || {};
  if (id === undefined || id === null) return null; // a notification: nothing to say back
  switch (method) {
    case 'initialize': {
      const asked = params && params.protocolVersion;
      return rpcResult(id, {
        protocolVersion: PROTOCOL_VERSIONS.includes(asked) ? asked : PROTOCOL_VERSIONS[0],
        capabilities: { tools: {} },
        serverInfo: { name: 'holiday-planner', version: '1.0.0' },
      });
    }
    case 'ping':
      return rpcResult(id, {});
    case 'tools/list':
      return rpcResult(id, { tools: TOOLS });
    case 'tools/call':
      return rpcResult(id, await callTool(params || {}, env));
    default:
      return rpcErrorObject(id, -32601, `Method not found: ${method}`);
  }
}

async function callTool({ name, arguments: args = {} }, env) {
  try {
    if (name === 'add_places') return await addPlaces(args, env);
    if (name === 'list_places') return await listPlaces(args, env);
    return toolText(`Unknown tool: ${name}`, true);
  } catch (err) {
    return toolText(`The backend could not be reached: ${err && err.message ? err.message : err}`, true);
  }
}

async function addPlaces({ places }, env) {
  if (!Array.isArray(places) || places.length === 0) return toolText('No places given.', true);
  const rows = places.map((p) => ({
    country: text(p.country),
    city: text(p.city),
    spot: text(p.spot),
    type: text(p.type),
    priority: text(p.priority),
    notes: text(p.notes),
    link: text(p.link),
  }));
  const reply = await backend(env, { action: 'inbox', rows });
  if (!reply.ok) return toolText(`Backend error: ${reply.error || JSON.stringify(reply)}`, true);
  const lines = (reply.results || []).map((r) => `${r.result}: ${r.spot}`);
  return toolText(lines.length ? lines.join('\n') : 'Nothing was imported.');
}

async function listPlaces({ country }, env) {
  const reply = await backend(env, { action: 'pull', since: 0 });
  if (!reply.ok) return toolText(`Backend error: ${reply.error || JSON.stringify(reply)}`, true);
  const live = (table) => (reply.changes[table] || []).filter((r) => r.deleted !== 'true');
  const wanted = text(country).toLowerCase();
  const countries = live('Countries')
    .filter((c) => !wanted || c.name.toLowerCase() === wanted || c.isoCode.toLowerCase() === wanted)
    .sort((a, b) => (a.sortKey > b.sortKey ? 1 : -1));
  const cities = live('Cities');
  const spots = live('Spots');
  const lines = [];
  for (const c of countries) {
    lines.push(`${c.name} (${c.isoCode})${c.priority === 'HIGH' ? ' [high]' : ''}`);
    for (const city of cities.filter((x) => x.countryId === c.id).sort((a, b) => (a.sortKey > b.sortKey ? 1 : -1))) {
      const here = spots.filter((s) => s.parentCityId === city.id).sort((a, b) => (a.sortKey > b.sortKey ? 1 : -1));
      const names = here.map((s) => {
        const tags = [s.type !== 'POI' ? s.type.toLowerCase() : '', s.priority === 'HIGH' ? 'high' : '', s.status === 'VISITED' ? 'visited' : '']
          .filter(Boolean);
        return s.name + (tags.length ? ` (${tags.join(', ')})` : '');
      });
      lines.push(`  ${city.name}: ${names.join('; ') || '(no spots yet)'}`);
    }
  }
  return toolText(lines.length ? lines.join('\n') : wanted ? `Nothing saved for "${country}".` : 'Nothing saved yet.');
}

/**
 * Calls the Apps Script API. It answers every POST with a redirect to a one-shot URL that only
 * works with the cookies the redirect set, so the hop is followed by hand with those cookies.
 */
async function backend(env, body) {
  const first = await fetch(env.SHEETS_URL, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ ...body, secret: env.SHEETS_SECRET }),
    redirect: 'manual',
  });
  let response = first;
  if (first.status >= 300 && first.status < 400) {
    const location = first.headers.get('Location');
    const cookies = (first.headers.getSetCookie ? first.headers.getSetCookie() : [])
      .map((c) => c.split(';')[0])
      .join('; ');
    response = await fetch(location, { headers: cookies ? { Cookie: cookies } : {} });
  }
  const raw = await response.text();
  try {
    return JSON.parse(raw);
  } catch (err) {
    return { ok: false, error: `not JSON (HTTP ${response.status}): ${raw.slice(0, 200)}` };
  }
}

const text = (v) => (v === undefined || v === null ? '' : String(v).trim());
const toolText = (message, isError = false) => ({ content: [{ type: 'text', text: message }], isError });
const rpcResult = (id, result) => ({ jsonrpc: '2.0', id, result });
const rpcErrorObject = (id, code, message) => ({ jsonrpc: '2.0', id, error: { code, message } });
const rpcError = (id, code, message) => jsonResponse(rpcErrorObject(id, code, message));
const jsonResponse = (payload) =>
  new Response(JSON.stringify(payload), { status: 200, headers: { 'Content-Type': 'application/json' } });
