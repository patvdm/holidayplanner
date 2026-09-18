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
    name: 'update_place',
    description:
      'Changes one saved place in the Holiday Planner travel database: mark it visited, change its priority ' +
      'or type, rename it, or replace or add to its notes. Find it by `spot` (its current name) and, when the ' +
      'same name exists in more than one city, `city`. Only the fields given are changed.',
    inputSchema: {
      type: 'object',
      properties: {
        spot: { type: 'string', description: 'Current name of the place. Required.' },
        city: { type: 'string', description: 'City it is filed under, to tell same-named places apart.' },
        name: { type: 'string', description: 'New name.' },
        status: { type: 'string', enum: ['visited', 'unvisited'] },
        priority: { type: 'string', enum: ['high', 'normal'] },
        type: { type: 'string', enum: ['poi', 'restaurant', 'accommodation'] },
        notes: { type: 'string', description: 'Replaces the notes entirely.' },
        append_notes: { type: 'string', description: 'Added as a new line under the existing notes.' },
      },
      required: ['spot'],
    },
  },
  {
    name: 'remove_place',
    description:
      'Removes one saved place from the Holiday Planner travel database, with its links. Find it by `spot` ' +
      'and, if needed, `city`. Ask the user before calling this unless they clearly asked for a removal.',
    inputSchema: {
      type: 'object',
      properties: {
        spot: { type: 'string', description: 'Current name of the place. Required.' },
        city: { type: 'string', description: 'City it is filed under, to tell same-named places apart.' },
      },
      required: ['spot'],
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
    if (name === 'update_place') return await updatePlace(args, env);
    if (name === 'remove_place') return await removePlace(args, env);
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

/** The saved spot called `spot`, in `city` if given. A string back means it could not be settled. */
async function findSpot({ spot, city }, env) {
  const reply = await backend(env, { action: 'pull', since: 0, tables: ['Countries', 'Cities', 'Spots'] });
  if (!reply.ok) return `Backend error: ${reply.error || JSON.stringify(reply)}`;
  const live = (table) => (reply.changes[table] || []).filter((r) => r.deleted !== 'true');
  const cities = live('Cities');
  const wantedName = text(spot).toLowerCase();
  const wantedCity = text(city).toLowerCase();
  const matches = live('Spots').filter((s) => {
    if (s.name.toLowerCase() !== wantedName) return false;
    if (!wantedCity) return true;
    const home = cities.find((c) => c.id === s.parentCityId);
    return home && home.name.toLowerCase() === wantedCity;
  });
  if (matches.length === 0) return `No saved place called "${spot}"${city ? ` in ${city}` : ''}.`;
  if (matches.length > 1) {
    const where = matches.map((s) => (cities.find((c) => c.id === s.parentCityId) || {}).name || '?');
    return `"${spot}" exists in more than one city (${where.join(', ')}); say which one.`;
  }
  return matches[0];
}

async function updatePlace(args, env) {
  const found = await findSpot(args, env);
  if (typeof found === 'string') return toolText(found, true);
  const changes = {};
  if (text(args.name)) changes.name = text(args.name);
  if (text(args.status)) changes.status = text(args.status).toUpperCase();
  if (text(args.priority)) changes.priority = text(args.priority).toUpperCase();
  if (text(args.type)) changes.type = text(args.type).toUpperCase();
  if (args.notes !== undefined && args.notes !== null) changes.notes = text(args.notes);
  if (text(args.append_notes)) {
    changes.notes = [changes.notes !== undefined ? changes.notes : found.notes, text(args.append_notes)]
      .filter(Boolean)
      .join('\n');
  }
  if (Object.keys(changes).length === 0) return toolText('Nothing to change was given.', true);
  const now = String(Date.now());
  const reply = await backend(env, {
    action: 'push',
    rows: { Spots: [{ id: found.id, updatedAt: now, updatedBy: 'claude', ...changes }] },
  });
  if (!reply.ok) return toolText(`Backend error: ${reply.error || JSON.stringify(reply)}`, true);
  const what = Object.keys(changes).map((k) => `${k} = ${changes[k]}`).join(', ');
  return toolText(`Updated ${found.name}: ${what}.`);
}

async function removePlace(args, env) {
  const found = await findSpot(args, env);
  if (typeof found === 'string') return toolText(found, true);
  const extras = await backend(env, { action: 'pull', since: 0, tables: ['Links', 'Hearts'] });
  if (!extras.ok) return toolText(`Backend error: ${extras.error || JSON.stringify(extras)}`, true);
  const now = String(Date.now());
  const gone = (id) => ({ id, updatedAt: now, updatedBy: 'claude', deleted: 'true' });
  const attached = (table) => (extras.changes[table] || []).filter((r) => r.spotId === found.id && r.deleted !== 'true');
  const rows = {
    Spots: [gone(found.id)],
    Links: attached('Links').map((r) => gone(r.id)),
    Hearts: attached('Hearts').map((r) => gone(r.id)),
  };
  const reply = await backend(env, { action: 'push', rows });
  if (!reply.ok) return toolText(`Backend error: ${reply.error || JSON.stringify(reply)}`, true);
  return toolText(`Removed ${found.name} (and ${rows.Links.length} link(s)).`);
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
  let response = await fetch(env.SHEETS_URL, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ ...body, secret: env.SHEETS_SECRET }),
    redirect: 'manual',
  });
  // A large answer goes through more than one redirect; each hop may set cookies the next needs.
  const cookies = {};
  for (let hop = 0; hop < 5 && response.status >= 300 && response.status < 400; hop++) {
    const location = response.headers.get('Location');
    if (!location) break;
    (response.headers.getSetCookie ? response.headers.getSetCookie() : []).forEach((c) => {
      const [name, ...rest] = c.split(';')[0].split('=');
      cookies[name.trim()] = rest.join('=');
    });
    const jar = Object.keys(cookies).map((k) => `${k}=${cookies[k]}`).join('; ');
    response = await fetch(new URL(location, env.SHEETS_URL), {
      headers: jar ? { Cookie: jar } : {},
      redirect: 'manual',
    });
  }
  const raw = await response.text();
  try {
    const parsed = JSON.parse(raw);
    if (parsed && parsed.ok && body.action === 'pull' && !parsed.changes) parsed.changes = {};
    return parsed;
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
