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
      'Changes one country, city or spot already saved in the Holiday Planner travel database: mark a spot ' +
      'visited, change a priority or type, rename something, replace or add to a spot\'s notes, or set the ' +
      'picture shown for it. Say which `kind` it is (default spot) and its current `name`; add `city` or ' +
      '`country` when the same name exists twice. Only the fields given are changed. `status`, `type`, `notes` ' +
      'and `append_notes` apply to spots only.',
    inputSchema: {
      type: 'object',
      properties: {
        kind: { type: 'string', enum: ['spot', 'city', 'country'], description: 'What to change. Default spot.' },
        name: { type: 'string', description: 'Its current name (a country also matches its two-letter code).' },
        city: { type: 'string', description: 'City a spot is filed under, to tell same-named spots apart.' },
        country: { type: 'string', description: 'Country a city is in, to tell same-named cities apart.' },
        new_name: { type: 'string', description: 'Rename it to this.' },
        status: { type: 'string', enum: ['visited', 'unvisited'] },
        priority: { type: 'string', enum: ['high', 'normal'] },
        type: { type: 'string', enum: ['poi', 'restaurant', 'accommodation'] },
        notes: { type: 'string', description: 'Replaces the notes entirely.' },
        append_notes: { type: 'string', description: 'Added as a new line under the existing notes.' },
        image_url: {
          type: 'string',
          description:
            'Direct link to an image file (jpg or png), which becomes the picture shown in the app. Pass an ' +
            'empty string to clear it, which makes the app look for a new picture on its own.',
        },
      },
      required: ['name'],
    },
  },
  {
    name: 'remove_place',
    description:
      'Removes one saved spot from the Holiday Planner travel database, with its links. Find it by `name` ' +
      'and, if needed, `city`. Ask the user before calling this unless they clearly asked for a removal. ' +
      'Countries and cities are not removed this way; they go when the app has nothing left under them.',
    inputSchema: {
      type: 'object',
      properties: {
        name: { type: 'string', description: 'Current name of the spot. Required.' },
        city: { type: 'string', description: 'City it is filed under, to tell same-named spots apart.' },
      },
      required: ['name'],
    },
  },
  {
    name: 'list_places',
    description:
      'Lists what is already in the Holiday Planner travel database: countries, their cities and the spots in ' +
      'each. Use it before adding, to avoid duplicates, or when the user asks what they have saved somewhere. ' +
      'Optional `country` narrows it to one country by name or ISO code; `details` adds each entry\'s notes ' +
      'and picture, which is what to use before changing one.',
    inputSchema: {
      type: 'object',
      properties: {
        country: { type: 'string', description: 'Country name or two-letter code to narrow to. Optional.' },
        details: { type: 'boolean', description: 'Include notes and picture links. Default false.' },
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

const TABLE_OF = { country: 'Countries', city: 'Cities', spot: 'Spots' };

/**
 * The one saved country, city or spot going by `name`, narrowed by `city` or `country` when the
 * name is not unique. A string back is the reason it could not be settled, ready to show.
 */
async function findRecord({ kind, name, city, country }, env) {
  const table = TABLE_OF[kind];
  if (!table) return `Unknown kind "${kind}". Use spot, city or country.`;
  const reply = await backend(env, { action: 'pull', since: 0, tables: ['Countries', 'Cities', 'Spots'] });
  if (!reply.ok) return `Backend error: ${reply.error || JSON.stringify(reply)}`;
  const live = (t) => (reply.changes[t] || []).filter((r) => r.deleted !== 'true');
  const countries = live('Countries');
  const cities = live('Cities');
  const wanted = text(name).toLowerCase();
  const inCountry = text(country).toLowerCase();
  const inCity = text(city).toLowerCase();

  let matches;
  if (kind === 'country') {
    matches = countries.filter((c) => c.name.toLowerCase() === wanted || c.isoCode.toLowerCase() === wanted);
  } else if (kind === 'city') {
    matches = cities.filter((c) => {
      if (c.name.toLowerCase() !== wanted) return false;
      if (!inCountry) return true;
      const home = countries.find((x) => x.id === c.countryId);
      return home && (home.name.toLowerCase() === inCountry || home.isoCode.toLowerCase() === inCountry);
    });
  } else {
    matches = live('Spots').filter((s) => {
      if (s.name.toLowerCase() !== wanted) return false;
      if (!inCity) return true;
      const home = cities.find((c) => c.id === s.parentCityId);
      return home && home.name.toLowerCase() === inCity;
    });
  }

  if (matches.length === 0) return `No saved ${kind} called "${name}".`;
  if (matches.length > 1) {
    const where = matches.map((m) => {
      const home = kind === 'city'
        ? countries.find((x) => x.id === m.countryId)
        : cities.find((c) => c.id === m.parentCityId);
      return (home || {}).name || '?';
    });
    return `More than one ${kind} is called "${name}" (${where.join(', ')}); say which one.`;
  }
  return { table, record: matches[0] };
}

async function updatePlace(args, env) {
  const kind = text(args.kind).toLowerCase() || 'spot';
  const found = await findRecord({ ...args, kind }, env);
  if (typeof found === 'string') return toolText(found, true);
  const { table, record } = found;

  const spotOnly = ['status', 'type', 'notes', 'append_notes'].filter((f) => text(args[f]));
  if (kind !== 'spot' && spotOnly.length) {
    return toolText(`A ${kind} has no ${spotOnly.join(' or ')}; those belong to a spot.`, true);
  }

  const changes = {};
  if (text(args.new_name)) changes.name = text(args.new_name);
  if (text(args.priority)) changes.priority = text(args.priority).toUpperCase();
  // An empty string is a real value here: it clears the picture and starts a fresh search.
  if (args.image_url !== undefined && args.image_url !== null) changes.imageUrl = text(args.image_url);
  if (kind === 'spot') {
    if (text(args.status)) changes.status = text(args.status).toUpperCase();
    if (text(args.type)) changes.type = text(args.type).toUpperCase();
    if (args.notes !== undefined && args.notes !== null) changes.notes = text(args.notes);
    if (text(args.append_notes)) {
      changes.notes = [changes.notes !== undefined ? changes.notes : record.notes, text(args.append_notes)]
        .filter(Boolean)
        .join('\n');
    }
  }
  if (Object.keys(changes).length === 0) return toolText('Nothing to change was given.', true);

  const now = String(Date.now());
  const reply = await backend(env, {
    action: 'push',
    rows: { [table]: [{ id: record.id, updatedAt: now, updatedBy: 'claude', ...changes }] },
  });
  if (!reply.ok) return toolText(`Backend error: ${reply.error || JSON.stringify(reply)}`, true);
  const what = Object.keys(changes)
    .map((k) => `${k} = ${changes[k] === '' ? '(cleared)' : changes[k]}`)
    .join(', ');
  return toolText(`Updated the ${kind} ${record.name}: ${what}.`);
}

async function removePlace(args, env) {
  const found0 = await findRecord({ ...args, kind: 'spot' }, env);
  if (typeof found0 === 'string') return toolText(found0, true);
  const found = found0.record;
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

async function listPlaces({ country, details }, env) {
  const reply = await backend(env, { action: 'pull', since: 0, tables: ['Countries', 'Cities', 'Spots'] });
  if (!reply.ok) return toolText(`Backend error: ${reply.error || JSON.stringify(reply)}`, true);
  const live = (table) => (reply.changes[table] || []).filter((r) => r.deleted !== 'true');
  const wanted = text(country).toLowerCase();
  const countries = live('Countries')
    .filter((c) => !wanted || c.name.toLowerCase() === wanted || c.isoCode.toLowerCase() === wanted)
    .sort((a, b) => (a.sortKey > b.sortKey ? 1 : -1));
  const cities = live('Cities');
  const spots = live('Spots');
  const inOrder = (rows) => rows.sort((a, b) => (a.sortKey > b.sortKey ? 1 : -1));

  const lines = [];
  for (const c of countries) {
    lines.push(`${c.name} (${c.isoCode})${c.priority === 'HIGH' ? ' [high]' : ''}${picture(c, details)}`);
    // Spots that belong to the country itself, with no city, come before its cities.
    const own = inOrder(spots.filter((s) => !s.parentCityId && s.parentCountryId === c.id));
    if (own.length && !details) lines.push(`  (country): ${own.map((s) => s.name + tagsOf(s)).join('; ')}`);
    if (own.length && details) {
      lines.push('  (country)');
      for (const s of own) lines.push(`    ${s.name}${tagsOf(s)}${picture(s, true)}`);
    }
    for (const city of inOrder(cities.filter((x) => x.countryId === c.id))) {
      const here = inOrder(spots.filter((s) => s.parentCityId === city.id));
      if (!details) {
        const names = here.map((s) => s.name + tagsOf(s));
        lines.push(`  ${city.name}: ${names.join('; ') || '(no spots yet)'}`);
        continue;
      }
      lines.push(`  ${city.name}${picture(city, true)}`);
      if (here.length === 0) lines.push('    (no spots yet)');
      for (const s of here) {
        lines.push(`    ${s.name}${tagsOf(s)}${picture(s, true)}`);
        const note = s.notes.split('\n').map((l) => l.trim()).find(Boolean);
        if (note) lines.push(`      ${note}`);
      }
    }
  }
  return toolText(lines.length ? lines.join('\n') : wanted ? `Nothing saved for "${country}".` : 'Nothing saved yet.');
}

const tagsOf = (spot) => {
  const tags = [
    spot.type !== 'POI' ? spot.type.toLowerCase() : '',
    spot.priority === 'HIGH' ? 'high' : '',
    spot.status === 'VISITED' ? 'visited' : '',
  ].filter(Boolean);
  return tags.length ? ` (${tags.join(', ')})` : '';
};

/** "none" is what the app writes once a search found nothing, so it never searches twice. */
const picture = (row, details) => {
  if (!details) return '';
  if (!row.imageUrl) return ' — no picture yet, the app is still looking';
  if (row.imageUrl === 'none') return ' — no picture found';
  return ` — picture: ${row.imageUrl}`;
};

/**
 * Calls the Apps Script API. It answers every POST with a redirect to a one-shot URL that only
 * works with the cookies the redirect set, so the hop is followed by hand with those cookies.
 */
/** What an answer to each action must carry, so a reply meant for an earlier call is spotted. */
const ANSWERS = { pull: 'changes', push: 'accepted', inbox: 'results', ping: 'serverTime' };

/**
 * Calls the backend and insists on an answer to the call actually made.
 *
 * Apps Script redirects each POST to a single-use URL, and asking twice in quick succession can
 * hand back the previous call's body. Unnoticed, a pull answered by a push reads as an empty
 * database, which would look like every saved place had vanished.
 */
async function backend(env, body) {
  const expected = ANSWERS[body.action];
  let last;
  for (let attempt = 0; attempt < 3; attempt++) {
    last = await backendOnce(env, body);
    // A sign-in or error page instead of JSON is the same stale-URL hiccup, and passes on a retry.
    const stale = (!last.ok && String(last.error).startsWith('not JSON')) || (last.ok && expected && !(expected in last));
    if (!stale) return last;
    await new Promise((resolve) => setTimeout(resolve, 400 * (attempt + 1)));
  }
  return { ok: false, error: `the backend answered something other than the ${body.action} that was asked for` };
}

async function backendOnce(env, body) {
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
