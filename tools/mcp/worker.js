/**
 * Holiday Planner connector: a tiny MCP server for claude.ai, as a Cloudflare Worker.
 *
 * It gives Claude tools in any chat for the saved places (`add_places`, which is what "add this to
 * my travel plans" calls, plus list, update and remove) and for trips and routes (`list_plans`,
 * create and update a trip, put things on a trip day, create and update a route). All of them
 * talk to the backend sheet's Apps Script API: new places go through the Inbox, which geocodes
 * and files them, and everything else is pushed as rows the way a phone would push an edit.
 *
 * Secrets (set in the Worker's settings, never in this file):
 *   SHEETS_URL     the Apps Script /exec URL, same as local.properties
 *   SHEETS_SECRET  the script's SECRET, same as local.properties
 *   ACCESS_TOKEN   a long random string; the connector URL is https://<worker>/<ACCESS_TOKEN>/mcp
 *   PHOTO_TOKEN    another long random string, which the app sends to store and delete photos,
 *                  and to fetch app updates
 *   RELEASE_TOKEN  a third one, known only to the PC that publishes new versions of the app
 *
 * Binding (Settings, Bindings, R2 bucket):
 *   PHOTOS         the R2 bucket photos are kept in. Served back at https://<worker>/photos/<key>.
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
  {
    name: 'list_plans',
    description:
      'Lists the trips and walking routes in the Holiday Planner. With no arguments: every trip (country, dates, ' +
      'how much is planned) and every route (city, stops, which trip day it is on). With `trip`: that trip day by ' +
      'day, with each route\'s stops in order. With `route`: one route\'s stops, notes and the trip days it is on. ' +
      'Use it before changing a trip or route, to get the exact names.',
    inputSchema: {
      type: 'object',
      properties: {
        trip: { type: 'string', description: 'Name of a trip to show day by day. Optional.' },
        route: { type: 'string', description: 'Name of a route to show in full. Optional.' },
        city: { type: 'string', description: 'City the route goes through, when two routes share a name.' },
      },
    },
  },
  {
    name: 'create_trip',
    description:
      'Creates a trip: a name, the country it is in, and its first and last day. The country must already have ' +
      'places saved; if it has none, add one with add_places first. Fill the days afterwards with add_to_trip ' +
      'and create_route.',
    inputSchema: {
      type: 'object',
      properties: {
        name: { type: 'string', description: 'What to call the trip, usually the main city or region.' },
        country: { type: 'string', description: 'Country name or two-letter code.' },
        start_date: { type: 'string', description: 'First day, yyyy-MM-dd.' },
        end_date: { type: 'string', description: 'Last day, yyyy-MM-dd.' },
        notes: { type: 'string', description: 'The overall plan in a few lines: arrival, departure, bookings.' },
      },
      required: ['name', 'country', 'start_date', 'end_date'],
    },
  },
  {
    name: 'update_trip',
    description:
      'Changes a trip\'s name, dates or notes. Only the fields given change. Things already on a day keep their ' +
      'dates; move them with update_trip_item.',
    inputSchema: {
      type: 'object',
      properties: {
        trip: { type: 'string', description: 'Current name of the trip.' },
        new_name: { type: 'string' },
        start_date: { type: 'string', description: 'yyyy-MM-dd' },
        end_date: { type: 'string', description: 'yyyy-MM-dd' },
        notes: { type: 'string', description: 'Replaces the notes entirely.' },
        append_notes: { type: 'string', description: 'Added as a new line under the existing notes.' },
      },
      required: ['trip'],
    },
  },
  {
    name: 'add_to_trip',
    description:
      'Puts one thing on one day of a trip: a saved spot (a dinner booking, a museum, a show), a saved route, ' +
      'or a flight or train. The spot or route must exist already: add a new place with add_places, and make a ' +
      'walk with create_route, which can put itself on the day. In the app a time shows beside spots and ' +
      'flights; on a route it only orders the day. Timed things come first on a day in time order, then the ' +
      'rest in the order they were added. When several places make up one outing, a route is usually better ' +
      'than several separate spots.',
    inputSchema: {
      type: 'object',
      properties: {
        trip: { type: 'string', description: 'Name of the trip.' },
        date: { type: 'string', description: 'The day, yyyy-MM-dd.' },
        kind: {
          type: 'string',
          enum: ['spot', 'route', 'flight'],
          description: 'Default: route when `route` is given, flight when `origin` is, otherwise spot.',
        },
        spot: { type: 'string', description: 'Name of a saved spot, as list_places shows it.' },
        city: { type: 'string', description: 'City the spot or route is saved under, when the name is not unique.' },
        route: { type: 'string', description: 'Name of a saved route, as list_plans shows it.' },
        time: { type: 'string', description: 'HH:mm, local time. For a flight, the departure.' },
        origin: { type: 'string', description: 'Flight or train: where it leaves from (airport code or station).' },
        destination: { type: 'string', description: 'Flight or train: where it arrives.' },
        arrive: {
          type: 'string',
          description: 'Flight or train arrival: HH:mm on the same day, or yyyy-MM-ddTHH:mm when it lands on a later day.',
        },
        depart_zone: {
          type: 'string',
          description: 'IANA time zone of the departure, e.g. Europe/Amsterdam. Default: the zone used elsewhere ' +
            'on the trip, else Europe/Amsterdam.',
        },
        arrive_zone: { type: 'string', description: 'IANA time zone of the arrival. Same default.' },
        number: { type: 'string', description: 'Flight or train number, shown under the legs.' },
        booking_ref: { type: 'string', description: 'Booking reference.' },
        notes: { type: 'string', description: 'Booking details, what to remember.' },
      },
      required: ['trip', 'date'],
    },
  },
  {
    name: 'update_trip_item',
    description:
      'Changes or removes one thing on a trip: move it to another day or time, replace its notes, or take it off ' +
      'the trip with `remove`. Taking it off only clears it from the day; the spot or route itself stays saved. ' +
      'Find it by `item`, the name list_plans shows for it, plus `date` when it is on more than one day.',
    inputSchema: {
      type: 'object',
      properties: {
        trip: { type: 'string', description: 'Name of the trip.' },
        item: { type: 'string', description: 'The spot, route or flight (e.g. "AMS → HEL") as list_plans shows it.' },
        date: { type: 'string', description: 'yyyy-MM-dd of the day it is on now, to tell repeats apart.' },
        new_date: { type: 'string', description: 'Move it to this day, yyyy-MM-dd.' },
        time: { type: 'string', description: 'New time, HH:mm (a flight\'s departure). An empty string clears it.' },
        arrive: {
          type: 'string',
          description: 'Flight or train only: new arrival, HH:mm on its day or yyyy-MM-ddTHH:mm.',
        },
        notes: { type: 'string', description: 'Replaces its notes.' },
        remove: { type: 'boolean', description: 'Take it off the trip.' },
      },
      required: ['trip', 'item'],
    },
  },
  {
    name: 'create_route',
    description:
      'Creates a walking route through one city: saved spots in the order to visit them, which the app opens as ' +
      'Google Maps walking directions. `stops` are spot names as list_places shows them, saved under that city ' +
      '(or elsewhere in the same country). Places that are not saved yet need add_places first. Give `trip` and ' +
      '`date` to put the route on that day of a trip too. Put the practical plan in `notes`: opening times, ' +
      'distances, where to take a tram.',
    inputSchema: {
      type: 'object',
      properties: {
        city: { type: 'string', description: 'The city the route goes through.' },
        country: { type: 'string', description: 'Country of the city, when two cities share a name.' },
        name: { type: 'string', description: 'What to call the route, e.g. "Saturday: museums and the market".' },
        stops: { type: 'array', items: { type: 'string' }, minItems: 1, description: 'Spot names, in walking order.' },
        notes: { type: 'string' },
        trip: { type: 'string', description: 'Trip to put the route on. Optional.' },
        date: { type: 'string', description: 'Day of that trip, yyyy-MM-dd. Needed with `trip`.' },
        time: { type: 'string', description: 'HH:mm it starts; only orders it among the day\'s other things.' },
      },
      required: ['city', 'name', 'stops'],
    },
  },
  {
    name: 'update_route',
    description:
      'Changes a saved route: rename it, give the whole list of stops in a new order (`stops`), add stops at the ' +
      'end (`add_stops`), take stops off (`remove_stops`), change its notes, or mark it walked. Taking a stop off ' +
      'never deletes the spot. Only the fields given change. Use `stops` to insert or reorder; `add_stops` only ' +
      'appends.',
    inputSchema: {
      type: 'object',
      properties: {
        route: { type: 'string', description: 'Current name of the route.' },
        city: { type: 'string', description: 'Its city, when two routes share a name.' },
        new_name: { type: 'string' },
        stops: { type: 'array', items: { type: 'string' }, description: 'The complete new list, in order.' },
        add_stops: { type: 'array', items: { type: 'string' }, description: 'Spots to add at the end.' },
        remove_stops: { type: 'array', items: { type: 'string' }, description: 'Stops to take off.' },
        notes: { type: 'string', description: 'Replaces the notes entirely.' },
        append_notes: { type: 'string', description: 'Added as a new line under the existing notes.' },
        walked: { type: 'boolean', description: 'Mark the route walked (true) or not yet (false).' },
      },
      required: ['route'],
    },
  },
];

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (url.pathname === '/photos' || url.pathname.startsWith('/photos/')) return photos(request, env, url);
    if (url.pathname.startsWith('/app/')) return releases(request, env, url);
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

/**
 * The app's photo store, far quicker than Drive through Apps Script: no cold start, no redirect,
 * and the picture travels as plain bytes rather than as text inside JSON.
 *
 *   PUT    /photos        (Bearer PHOTO_TOKEN) body is the image; answers {url, fileId}
 *   GET    /photos/<key>  public, so both phones can show it without signing in
 *   DELETE /photos/<key>  (Bearer PHOTO_TOKEN)
 *
 * Keys are random UUIDs, which is what keeps the public GET from being a listing of everything.
 */
async function photos(request, env, url) {
  if (!env.PHOTOS) return new Response('No PHOTOS bucket bound', { status: 500 });
  const key = decodeURIComponent(url.pathname.slice('/photos/'.length));
  const authorised = env.PHOTO_TOKEN && request.headers.get('Authorization') === `Bearer ${env.PHOTO_TOKEN}`;

  if (request.method === 'GET' || request.method === 'HEAD') {
    if (!key) return new Response('Not found', { status: 404 });
    const object = await env.PHOTOS.get(key);
    if (!object) return new Response('Not found', { status: 404 });
    const headers = new Headers();
    object.writeHttpMetadata(headers);
    headers.set('ETag', object.httpEtag);
    // A key never points at a different picture, so it can be cached for good.
    headers.set('Cache-Control', 'public, max-age=31536000, immutable');
    return new Response(request.method === 'HEAD' ? null : object.body, { headers });
  }
  if (!authorised) return new Response('Unauthorized', { status: 401 });

  if (request.method === 'PUT' && url.pathname === '/photos') {
    const type = request.headers.get('Content-Type') || 'image/jpeg';
    if (!type.startsWith('image/')) return new Response('Images only', { status: 415 });
    const extension = type === 'image/png' ? 'png' : type === 'image/webp' ? 'webp' : 'jpg';
    const newKey = `${crypto.randomUUID()}.${extension}`;
    // Read whole rather than streamed: R2 wants a known length, and a scaled photo is small.
    const bytes = await request.arrayBuffer();
    if (bytes.byteLength === 0) return new Response('Empty body', { status: 400 });
    await env.PHOTOS.put(newKey, bytes, { httpMetadata: { contentType: type } });
    return jsonResponse({ ok: true, url: `${url.origin}/photos/${newKey}`, fileId: newKey });
  }
  if (request.method === 'DELETE' && key) {
    await env.PHOTOS.delete(key);
    return jsonResponse({ ok: true });
  }
  return new Response('Method Not Allowed', { status: 405 });
}

/**
 * The app's own update channel, kept in the same bucket as the photos under `app/`.
 *
 *   PUT /app/release   (Bearer RELEASE_TOKEN) body is the APK; headers X-Version-Code,
 *                      X-Version-Name and X-Sha256. Refused unless newer than what is there.
 *   GET /app/latest    (Bearer PHOTO_TOKEN) {versionCode, versionName, sha256, size, apk}
 *   GET /app/apk/<key> (Bearer PHOTO_TOKEN) the APK itself
 *
 * Reading needs the token the app already carries, because an APK holds the app's own secrets and
 * must not be downloadable by anyone who finds the address.
 */
const LATEST_KEY = 'app/latest.json';

async function releases(request, env, url) {
  if (!env.PHOTOS) return new Response('No PHOTOS bucket bound', { status: 500 });
  const bearer = (request.headers.get('Authorization') || '').trim();
  // Trimmed, because a secret pasted into the dashboard easily brings a space or line break along.
  const photoToken = (env.PHOTO_TOKEN || '').trim();
  const releaseToken = (env.RELEASE_TOKEN || '').trim();
  const reader = photoToken !== '' && bearer === `Bearer ${photoToken}`;
  const publisher = releaseToken !== '' && bearer === `Bearer ${releaseToken}`;
  const latest = async () => {
    const object = await env.PHOTOS.get(LATEST_KEY);
    return object ? JSON.parse(await object.text()) : null;
  };

  if (request.method === 'GET' && url.pathname === '/app/latest') {
    if (!reader) return new Response('Unauthorized', { status: 401 });
    const current = await latest();
    return current ? jsonResponse(current) : new Response('Nothing published', { status: 404 });
  }
  if (request.method === 'GET' && url.pathname.startsWith('/app/apk/')) {
    if (!reader) return new Response('Unauthorized', { status: 401 });
    const key = 'app/' + decodeURIComponent(url.pathname.slice('/app/apk/'.length));
    const object = await env.PHOTOS.get(key);
    if (!object) return new Response('Not found', { status: 404 });
    return new Response(object.body, {
      headers: { 'Content-Type': 'application/vnd.android.package-archive', 'Content-Length': String(object.size) },
    });
  }
  if (request.method === 'PUT' && url.pathname === '/app/release') {
    // Says which half is wrong, and never the secret itself: only whether it is set and its length.
    if (!releaseToken) return new Response('RELEASE_TOKEN is not set on this Worker', { status: 500 });
    if (!publisher) {
      return new Response(`Wrong release token (the Worker's is ${releaseToken.length} characters)`, { status: 401 });
    }
    const versionCode = Number(request.headers.get('X-Version-Code'));
    const versionName = request.headers.get('X-Version-Name') || String(versionCode);
    const sha256 = (request.headers.get('X-Sha256') || '').toLowerCase();
    if (!Number.isInteger(versionCode) || versionCode <= 0 || !/^[0-9a-f]{64}$/.test(sha256)) {
      return new Response('Bad version headers', { status: 400 });
    }
    const previous = await latest();
    if (previous && previous.versionCode >= versionCode) {
      return new Response(`Already at ${previous.versionCode}`, { status: 409 });
    }
    const bytes = await request.arrayBuffer();
    const digest = [...new Uint8Array(await crypto.subtle.digest('SHA-256', bytes))]
      .map((b) => b.toString(16).padStart(2, '0')).join('');
    if (digest !== sha256) return new Response('Checksum does not match the upload', { status: 400 });
    const name = `holiday-planner-${versionCode}-${crypto.randomUUID()}.apk`;
    await env.PHOTOS.put('app/' + name, bytes, {
      httpMetadata: { contentType: 'application/vnd.android.package-archive' },
    });
    const release = {
      versionCode, versionName, sha256, size: bytes.byteLength, apk: `/app/apk/${name}`,
      publishedAt: new Date().toISOString(),
    };
    await env.PHOTOS.put(LATEST_KEY, JSON.stringify(release), { httpMetadata: { contentType: 'application/json' } });
    // Only the newest is ever offered, so the one before it is just taking up space.
    if (previous && previous.apk) await env.PHOTOS.delete('app/' + previous.apk.slice('/app/apk/'.length));
    return jsonResponse({ ok: true, ...release });
  }
  return new Response('Not found', { status: 404 });
}

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
    if (name === 'list_plans') return await listPlans(args, env);
    if (name === 'create_trip') return await createTrip(args, env);
    if (name === 'update_trip') return await updateTrip(args, env);
    if (name === 'add_to_trip') return await addToTrip(args, env);
    if (name === 'update_trip_item') return await updateTripItem(args, env);
    if (name === 'create_route') return await createRoute(args, env);
    if (name === 'update_route') return await updateRoute(args, env);
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
  const extras = await backend(env, { action: 'pull', since: 0, tables: ['Links'] });
  if (!extras.ok) return toolText(`Backend error: ${extras.error || JSON.stringify(extras)}`, true);
  const now = String(Date.now());
  const gone = (id) => ({ id, updatedAt: now, updatedBy: 'claude', deleted: 'true' });
  const attached = (table) => (extras.changes[table] || []).filter((r) => r.spotId === found.id && r.deleted !== 'true');
  const rows = {
    Spots: [gone(found.id)],
    Links: attached('Links').map((r) => gone(r.id)),
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

// ---------------------------------------------------------------------------------------------
// Trips and routes
// ---------------------------------------------------------------------------------------------

const PLAN_TABLES = ['Countries', 'Cities', 'Spots', 'Trips', 'TripItems', 'Routes', 'RouteStops'];

/** Everything trips and routes refer to, live rows only. A string back is the error to show. */
async function loadPlans(env) {
  const reply = await backend(env, { action: 'pull', since: 0, tables: PLAN_TABLES });
  if (!reply.ok) return `Backend error: ${reply.error || JSON.stringify(reply)}`;
  const db = {};
  for (const t of PLAN_TABLES) db[t] = (reply.changes[t] || []).filter((r) => r.deleted !== 'true');
  return db;
}

async function pushRows(env, rows) {
  const reply = await backend(env, { action: 'push', rows });
  if (!reply.ok) return `Backend error: ${reply.error || JSON.stringify(reply)}`;
  if (reply.errors && reply.errors.length) return `The backend refused part of it: ${JSON.stringify(reply.errors)}`;
  return null;
}

/**
 * The one row whose name is `wanted`: an exact match (ignoring case) first, else a unique partial
 * one, so "Lion's Head" finds "The Lion's Head Gastro Bar & Brewery". A string back is the reason.
 */
function pick(rows, wanted, label, nameOf, whereOf) {
  const w = text(wanted).toLowerCase();
  if (!w) return `Say which ${label}.`;
  let hits = rows.filter((r) => nameOf(r).toLowerCase() === w);
  if (hits.length === 0) hits = rows.filter((r) => nameOf(r).toLowerCase().includes(w));
  if (hits.length === 1) return hits[0];
  if (hits.length === 0) return `No ${label} called "${wanted}".`;
  const names = hits.slice(0, 10).map((r) => nameOf(r) + (whereOf ? ` (${whereOf(r)})` : ''));
  return `More than one ${label} matches "${wanted}": ${names.join('; ')}. Say which.`;
}

const missing = (found) => typeof found === 'string';
const cityName = (db, id) => (db.Cities.find((c) => c.id === id) || {}).name || '';
const countryName = (db, id) => (db.Countries.find((c) => c.id === id) || {}).name || '';

function findCountry(db, wanted) {
  const w = text(wanted).toLowerCase();
  const byCode = db.Countries.find((c) => c.isoCode.toLowerCase() === w);
  return byCode || pick(db.Countries, wanted, 'country', (c) => c.name);
}

function findCity(db, wanted, country) {
  let cities = db.Cities;
  if (text(country)) {
    const home = findCountry(db, country);
    if (missing(home)) return home;
    cities = cities.filter((c) => c.countryId === home.id);
  }
  return pick(cities, wanted, 'city', (c) => c.name, (c) => countryName(db, c.countryId));
}

/** Spots of one country: those under its cities and those filed on the country itself. */
function spotsInCountry(db, countryId) {
  const cities = new Set(db.Cities.filter((c) => c.countryId === countryId).map((c) => c.id));
  return db.Spots.filter((s) => cities.has(s.parentCityId) || (!s.parentCityId && s.parentCountryId === countryId));
}

const spotHome = (db, s) => cityName(db, s.parentCityId) || countryName(db, s.parentCountryId);

/** A spot for a stop in [city]: one saved in the city itself, else anywhere in its country. */
function findSpot(db, wanted, city) {
  const here = pick(db.Spots.filter((s) => s.parentCityId === city.id), wanted, `spot in ${city.name}`, (s) => s.name);
  if (!missing(here) || !here.startsWith('No ')) return here;
  const near = pick(spotsInCountry(db, city.countryId), wanted, 'spot', (s) => s.name, (s) => spotHome(db, s));
  if (!missing(near) || !near.startsWith('No ')) return near;
  return `No spot called "${wanted}" is saved in ${city.name} or elsewhere in ${countryName(db, city.countryId)}; ` +
    'add it with add_places first.';
}

function findRoute(db, wanted, city) {
  let routes = db.Routes;
  if (text(city)) routes = routes.filter((r) => cityName(db, r.cityId).toLowerCase() === text(city).toLowerCase());
  return pick(routes, wanted, 'route', (r) => r.name, (r) => cityName(db, r.cityId));
}

const findTrip = (db, wanted) => pick(db.Trips, wanted, 'trip', (t) => t.name, (t) => `${t.startDate} to ${t.endDate}`);

/** A route's stops in walking order, which is plain string order of their keys. */
const stopsOf = (db, routeId) => db.RouteStops
  .filter((s) => s.routeId === routeId)
  .sort((a, b) => (a.sortKey < b.sortKey ? -1 : a.sortKey > b.sortKey ? 1 : 0));

/** Mirrors SortKey.after in the app (base-62 fractional keys), so phones order new stops alike. */
const KEY_DIGITS = '0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz';
const validKey = (key) => !!key && !key.endsWith(KEY_DIGITS[0]) && [...key].every((c) => KEY_DIGITS.includes(c));

function keyAfter(last) {
  const step = (before) => {
    const low = before === '' ? 0 : KEY_DIGITS.indexOf(before[0]);
    if (KEY_DIGITS.length - low > 1) return KEY_DIGITS[Math.floor((low + KEY_DIGITS.length + 1) / 2)];
    return KEY_DIGITS[low] + step(before.slice(1));
  };
  return step(validKey(last) ? last : '');
}

const DATE_RE = /^\d{4}-\d{2}-\d{2}$/;

function validDate(s) {
  if (!DATE_RE.test(s)) return false;
  const d = new Date(`${s}T00:00:00Z`);
  return !isNaN(d.getTime()) && d.toISOString().slice(0, 10) === s;
}

/** "9:5" is not a time, "9:05" is; answers "09:05", or null when it cannot be read. */
function readTime(s) {
  const m = /^(\d{1,2}):(\d{2})$/.exec(text(s));
  if (!m || Number(m[1]) > 23 || Number(m[2]) > 59) return null;
  return `${m[1].padStart(2, '0')}:${m[2]}`;
}

/** An arrival as "HH:mm" on [date], or a full "yyyy-MM-ddTHH:mm"; null when it cannot be read. */
function readArrival(value, date) {
  const arrive = text(value).replace(' ', 'T');
  const onlyTime = readTime(arrive);
  if (onlyTime) return `${date}T${onlyTime}`;
  const m = /^(\d{4}-\d{2}-\d{2})T(\d{1,2}:\d{2})$/.exec(arrive);
  return m && validDate(m[1]) && readTime(m[2]) ? `${m[1]}T${readTime(m[2])}` : null;
}

const addDays = (date, days) => {
  const d = new Date(`${date}T00:00:00Z`);
  d.setUTCDate(d.getUTCDate() + days);
  return d.toISOString().slice(0, 10);
};
const daysFrom = (a, b) => Math.round((new Date(`${b}T00:00:00Z`) - new Date(`${a}T00:00:00Z`)) / 86400000);
const dayLabel = (date) => new Date(`${date}T00:00:00Z`)
  .toLocaleDateString('en-GB', { weekday: 'short', day: 'numeric', month: 'short', timeZone: 'UTC' });

const itemDate = (i) => [i.startLocal, i.endLocal].map((v) => text(v).slice(0, 10)).find(validDate) || '';
const itemTime = (i) => (text(i.startLocal).length > 10 ? text(i.startLocal).slice(11, 16) : '');
const timedLocal = (date, time) => (time ? `${date}T${time}` : date);

/** What an item is called: the spot or route it points at if that still exists, else its title. */
function itemName(db, i) {
  if (i.type === 'SPOT') return (db.Spots.find((s) => s.id === i.spotId) || {}).name || i.title || 'Spot';
  if (i.type === 'ROUTE') return (db.Routes.find((r) => r.id === i.routeId) || {}).name || i.title || 'Route';
  const legs = [i.origin, i.destination].map(text).filter(Boolean).join(' → ');
  return legs || i.title || 'Flight';
}

/** The app's order within a day: timed first by time, then in the order they were added. */
const withinDay = (a, b) => {
  const ta = itemTime(a);
  const tb = itemTime(b);
  if (!ta !== !tb) return ta ? -1 : 1;
  if (ta !== tb) return ta < tb ? -1 : 1;
  return Number(a.createdAt) - Number(b.createdAt);
};

function describeItem(db, i) {
  const time = itemTime(i);
  const name = itemName(db, i);
  let line;
  if (i.type === 'ROUTE') {
    const route = db.Routes.find((r) => r.id === i.routeId);
    const stops = route ? stopsOf(db, route.id).map((s) => (db.Spots.find((x) => x.id === s.spotId) || {}).name || '?') : [];
    line = `Route: ${name}${route ? ` (${cityName(db, route.cityId)})` : ''}${route && route.completed === 'true' ? ', walked' : ''}` +
      (stops.length ? ` — ${stops.join(' → ')}` : ' — no stops yet');
  } else if (i.type === 'FLIGHT') {
    const arrives = itemTime({ startLocal: i.endLocal });
    line = `Flight/train: ${name}${time ? `, ${time}` : ''}${arrives ? ` to ${arrives}` : ''}` +
      `${i.title && i.title !== name ? ` (${i.title})` : ''}${i.bookingRef ? `, booking ${i.bookingRef}` : ''}`;
  } else {
    const spot = db.Spots.find((s) => s.id === i.spotId);
    line = `${time ? `${time} ` : ''}${name}${spot ? ` (${spotHome(db, spot)})` : ''}`;
  }
  const note = text(i.notes);
  return note ? `${line}\n      ${note.replace(/\n/g, '\n      ')}` : line;
}

function tripDetail(db, trip) {
  const items = db.TripItems.filter((i) => i.tripId === trip.id);
  const lines = [`${trip.name} — ${countryName(db, trip.countryId) || '?'}, ${trip.startDate || '?'} to ${trip.endDate || '?'}`];
  if (text(trip.notes)) lines.push(`Notes: ${text(trip.notes)}`);
  const days = new Set(items.map(itemDate).filter(Boolean));
  if (validDate(trip.startDate) && validDate(trip.endDate)) {
    for (let d = trip.startDate, n = 0; d <= trip.endDate && n < 120; d = addDays(d, 1), n++) days.add(d);
  }
  for (const day of [...days].sort()) {
    lines.push(`${dayLabel(day)} (${day})`);
    const here = items.filter((i) => itemDate(i) === day).sort(withinDay);
    if (here.length === 0) lines.push('  (nothing planned)');
    for (const i of here) lines.push(`  - ${describeItem(db, i)}`);
  }
  const undated = items.filter((i) => !itemDate(i));
  if (undated.length) {
    lines.push('No day yet');
    for (const i of undated) lines.push(`  - ${describeItem(db, i)}`);
  }
  return lines.join('\n');
}

function routeDetail(db, route) {
  const lines = [`${route.name} — ${cityName(db, route.cityId)}${route.completed === 'true' ? ', walked' : ''}`];
  if (text(route.notes)) lines.push(`Notes: ${text(route.notes)}`);
  const stops = stopsOf(db, route.id);
  if (stops.length === 0) lines.push('No stops yet.');
  stops.forEach((s, n) => {
    const spot = db.Spots.find((x) => x.id === s.spotId);
    lines.push(`  ${n + 1}. ${spot ? spot.name : '(a deleted spot)'}${s.onMaps === 'false' ? ' (left out of the directions)' : ''}`);
  });
  const on = db.TripItems.filter((i) => i.routeId === route.id).map((i) => {
    const trip = db.Trips.find((t) => t.id === i.tripId);
    return trip ? `${trip.name}, ${itemDate(i) ? dayLabel(itemDate(i)) : 'no day yet'}` : null;
  }).filter(Boolean);
  if (on.length) lines.push(`On: ${on.join('; ')}`);
  return lines.join('\n');
}

async function listPlans(args, env) {
  const db = await loadPlans(env);
  if (missing(db)) return toolText(db, true);
  if (text(args.trip)) {
    const trip = findTrip(db, args.trip);
    return missing(trip) ? toolText(trip, true) : toolText(tripDetail(db, trip));
  }
  if (text(args.route)) {
    const route = findRoute(db, args.route, args.city);
    return missing(route) ? toolText(route, true) : toolText(routeDetail(db, route));
  }
  const lines = ['Trips:'];
  const trips = [...db.Trips].sort((a, b) => (a.startDate < b.startDate ? -1 : 1));
  if (trips.length === 0) lines.push('  (none yet)');
  for (const t of trips) {
    const n = db.TripItems.filter((i) => i.tripId === t.id).length;
    lines.push(`  ${t.name} — ${countryName(db, t.countryId) || '?'}, ${t.startDate} to ${t.endDate}, ${n} thing(s) planned`);
  }
  lines.push('Routes:');
  const routes = [...db.Routes].sort((a, b) =>
    (cityName(db, a.cityId) + a.name).localeCompare(cityName(db, b.cityId) + b.name));
  if (routes.length === 0) lines.push('  (none yet)');
  for (const r of routes) {
    const on = db.TripItems.filter((i) => i.routeId === r.id).map((i) => {
      const trip = db.Trips.find((t) => t.id === i.tripId);
      return trip ? `${trip.name} ${itemDate(i) ? dayLabel(itemDate(i)) : ''}`.trim() : null;
    }).filter(Boolean);
    lines.push(`  ${r.name} (${cityName(db, r.cityId)}) — ${stopsOf(db, r.id).length} stop(s)` +
      `${r.completed === 'true' ? ', walked' : ''}${on.length ? `, on ${on.join('; ')}` : ''}`);
  }
  return toolText(lines.join('\n'));
}

const created = (now) => ({
  id: crypto.randomUUID(), createdAt: now, createdBy: 'claude', updatedAt: now, updatedBy: 'claude', deleted: 'false',
});
const touchedRow = (id, now, changes) => ({ id, updatedAt: now, updatedBy: 'claude', ...changes });
const appended = (current, extra) => [text(current), text(extra)].filter(Boolean).join('\n');

function checkDates(start, end) {
  if (!validDate(start)) return `"${start}" is not a date; use yyyy-MM-dd.`;
  if (!validDate(end)) return `"${end}" is not a date; use yyyy-MM-dd.`;
  if (end < start) return `The trip would end (${end}) before it starts (${start}).`;
  return null;
}

/** A new Trips row. Its columns are checked against the sheet's in contract.test.mjs. */
function newTrip(now, fields) {
  return { ...created(now), name: '', countryId: '', startDate: '', endDate: '', notes: '', ...fields };
}

async function createTrip(args, env) {
  const db = await loadPlans(env);
  if (missing(db)) return toolText(db, true);
  const name = text(args.name);
  if (!name) return toolText('Give the trip a name.', true);
  const clash = db.Trips.find((t) => t.name.toLowerCase() === name.toLowerCase());
  if (clash) {
    return toolText(`A trip called ${clash.name} already exists (${clash.startDate} to ${clash.endDate}); change it with update_trip.`, true);
  }
  const country = findCountry(db, args.country);
  if (missing(country)) return toolText(`${country} Save a place there with add_places first, then create the trip.`, true);
  const bad = checkDates(text(args.start_date), text(args.end_date));
  if (bad) return toolText(bad, true);
  const now = String(Date.now());
  const trip = newTrip(now, {
    name, startDate: text(args.start_date), endDate: text(args.end_date), notes: text(args.notes), countryId: country.id,
  });
  const failed = await pushRows(env, { Trips: [trip] });
  if (failed) return toolText(failed, true);
  return toolText(`Created the trip ${name} in ${country.name}, ${dayLabel(trip.startDate)} to ${dayLabel(trip.endDate)}.`);
}

async function updateTrip(args, env) {
  const db = await loadPlans(env);
  if (missing(db)) return toolText(db, true);
  const trip = findTrip(db, args.trip);
  if (missing(trip)) return toolText(trip, true);
  const changes = {};
  if (text(args.new_name)) changes.name = text(args.new_name);
  if (text(args.start_date)) changes.startDate = text(args.start_date);
  if (text(args.end_date)) changes.endDate = text(args.end_date);
  if (args.notes !== undefined && args.notes !== null) changes.notes = text(args.notes);
  if (text(args.append_notes)) {
    changes.notes = appended(changes.notes !== undefined ? changes.notes : trip.notes, args.append_notes);
  }
  if (Object.keys(changes).length === 0) return toolText('Nothing to change was given.', true);
  if (changes.startDate || changes.endDate) {
    const bad = checkDates(changes.startDate || trip.startDate, changes.endDate || trip.endDate);
    if (bad) return toolText(bad, true);
  }
  const failed = await pushRows(env, { Trips: [touchedRow(trip.id, String(Date.now()), changes)] });
  if (failed) return toolText(failed, true);
  return toolText(`Updated the trip ${trip.name}: ${Object.keys(changes).join(', ')}.`);
}

/** The zone flights on this trip already use, which is the best guess for one more. */
function suggestedZone(db, tripId) {
  const zoned = db.TripItems
    .filter((i) => i.tripId === tripId && text(i.startTimeZone))
    .sort((a, b) => Number(b.createdAt) - Number(a.createdAt));
  return zoned.length ? zoned[0].startTimeZone : 'Europe/Amsterdam';
}

function newTripItem(now, tripId, type, startLocal, fields) {
  return {
    ...created(now), tripId, type, title: '', origin: '', destination: '', startLocal, startTimeZone: '',
    endLocal: '', endTimeZone: '', spotId: '', routeId: '', bookingRef: '', notes: '', ...fields,
  };
}

async function addToTrip(args, env) {
  const db = await loadPlans(env);
  if (missing(db)) return toolText(db, true);
  const trip = findTrip(db, args.trip);
  if (missing(trip)) return toolText(trip, true);
  const date = text(args.date);
  if (!validDate(date)) return toolText(`"${args.date}" is not a date; use yyyy-MM-dd.`, true);
  const time = text(args.time) ? readTime(args.time) : '';
  if (time === null) return toolText(`"${args.time}" is not a time; use HH:mm.`, true);
  const kind = text(args.kind).toLowerCase() ||
    (text(args.route) ? 'route' : text(args.origin) || text(args.destination) ? 'flight' : 'spot');
  const now = String(Date.now());
  const sameDay = db.TripItems.filter((i) => i.tripId === trip.id && itemDate(i) === date);
  const common = { notes: text(args.notes), bookingRef: text(args.booking_ref) };
  let item;

  if (kind === 'spot') {
    let spot;
    if (text(args.city)) {
      const city = findCity(db, args.city, '');
      if (missing(city)) return toolText(city, true);
      spot = findSpot(db, args.spot, city);
    } else {
      spot = pick(spotsInCountry(db, trip.countryId), args.spot, 'spot', (s) => s.name, (s) => spotHome(db, s));
      if (missing(spot) && spot.startsWith('No ')) {
        spot = pick(db.Spots, args.spot, 'spot', (s) => s.name, (s) => spotHome(db, s));
      }
    }
    if (missing(spot)) return toolText(spot.startsWith('No ') ? `${spot} Add it with add_places first.` : spot, true);
    if (sameDay.some((i) => i.spotId === spot.id)) return toolText(`${spot.name} is already on ${dayLabel(date)}.`, true);
    item = newTripItem(now, trip.id, 'SPOT', timedLocal(date, time), { ...common, title: spot.name, spotId: spot.id });
  } else if (kind === 'route') {
    const route = findRoute(db, args.route, args.city);
    if (missing(route)) return toolText(route.startsWith('No ') ? `${route} Make it with create_route.` : route, true);
    if (sameDay.some((i) => i.routeId === route.id)) return toolText(`${route.name} is already on ${dayLabel(date)}.`, true);
    item = newTripItem(now, trip.id, 'ROUTE', timedLocal(date, time), { ...common, title: route.name, routeId: route.id });
  } else if (kind === 'flight') {
    if (!text(args.origin) || !text(args.destination)) return toolText('A flight or train needs an origin and a destination.', true);
    const endLocal = text(args.arrive) ? readArrival(args.arrive, date) : '';
    if (endLocal === null) return toolText(`"${args.arrive}" is not an arrival time; use HH:mm or yyyy-MM-ddTHH:mm.`, true);
    const zone = suggestedZone(db, trip.id);
    item = newTripItem(now, trip.id, 'FLIGHT', timedLocal(date, time), {
      ...common,
      title: text(args.number),
      origin: text(args.origin),
      destination: text(args.destination),
      startTimeZone: text(args.depart_zone) || zone,
      endLocal,
      endTimeZone: text(args.arrive_zone) || text(args.depart_zone) || zone,
    });
  } else {
    return toolText(`Unknown kind "${args.kind}". Use spot, route or flight.`, true);
  }

  const failed = await pushRows(env, { TripItems: [item] });
  if (failed) return toolText(failed, true);
  return toolText(`Added to ${trip.name}, ${dayLabel(date)}: ${describeItem(db, item).split('\n')[0]}.`);
}

async function updateTripItem(args, env) {
  const db = await loadPlans(env);
  if (missing(db)) return toolText(db, true);
  const trip = findTrip(db, args.trip);
  if (missing(trip)) return toolText(trip, true);
  let items = db.TripItems.filter((i) => i.tripId === trip.id);
  if (text(args.date)) items = items.filter((i) => itemDate(i) === text(args.date));
  const item = pick(items, args.item, `item on ${trip.name}${text(args.date) ? ` on ${args.date}` : ''}`,
    (i) => itemName(db, i), (i) => (itemDate(i) ? dayLabel(itemDate(i)) : 'no day'));
  if (missing(item)) return toolText(item, true);
  const name = itemName(db, item);
  const now = String(Date.now());

  if (args.remove === true) {
    const failed = await pushRows(env, { TripItems: [touchedRow(item.id, now, { deleted: 'true' })] });
    if (failed) return toolText(failed, true);
    const kept = item.type === 'FLIGHT' ? '' : ` The ${item.type === 'ROUTE' ? 'route' : 'spot'} itself is still saved.`;
    return toolText(`Took ${name} off ${trip.name}.${kept}`);
  }

  const changes = {};
  const oldDate = itemDate(item);
  const newDate = text(args.new_date);
  if (newDate && !validDate(newDate)) return toolText(`"${newDate}" is not a date; use yyyy-MM-dd.`, true);
  let time = itemTime(item);
  if (args.time !== undefined && args.time !== null) {
    time = text(args.time) ? readTime(args.time) : '';
    if (time === null) return toolText(`"${args.time}" is not a time; use HH:mm.`, true);
  }
  if (newDate || (args.time !== undefined && args.time !== null)) {
    const day = newDate || oldDate;
    if (!day) return toolText(`${name} has no day yet; give new_date.`, true);
    changes.startLocal = timedLocal(day, time);
    // A flight's arrival moves with it, keeping the same length.
    if (newDate && oldDate && text(item.endLocal)) {
      const shift = daysFrom(oldDate, newDate);
      changes.endLocal = addDays(item.endLocal.slice(0, 10), shift) + item.endLocal.slice(10);
    }
  }
  if (text(args.arrive)) {
    if (item.type !== 'FLIGHT') return toolText('Only a flight or train has an arrival.', true);
    const day = (changes.startLocal || item.startLocal || '').slice(0, 10);
    changes.endLocal = readArrival(args.arrive, day);
    if (changes.endLocal === null) return toolText(`"${args.arrive}" is not an arrival time; use HH:mm or yyyy-MM-ddTHH:mm.`, true);
  }
  if (args.notes !== undefined && args.notes !== null) changes.notes = text(args.notes);
  if (Object.keys(changes).length === 0) return toolText('Nothing to change was given.', true);
  const failed = await pushRows(env, { TripItems: [touchedRow(item.id, now, changes)] });
  if (failed) return toolText(failed, true);
  const where = changes.startLocal
    ? ` now on ${dayLabel(changes.startLocal.slice(0, 10))}${time ? ` at ${time}` : ''}`
    : '';
  return toolText(`Updated ${name} on ${trip.name}${where}${changes.notes !== undefined ? ', notes changed' : ''}.`);
}

/** Every name resolved to a spot, or every reason one could not be: nothing is half-done. */
function resolveStops(db, names, city) {
  const spots = [];
  const problems = [];
  for (const n of names || []) {
    const found = findSpot(db, n, city);
    if (missing(found)) problems.push(found);
    else if (!spots.some((s) => s.id === found.id)) spots.push(found);
  }
  return { spots, problems };
}

function newStop(now, routeId, spotId, sortKey) {
  return { ...created(now), routeId, spotId, sortKey, onMaps: 'true' };
}

/** A new Routes row. Its columns are checked against the sheet's in contract.test.mjs. */
function newRoute(now, fields) {
  return { ...created(now), cityId: '', name: '', completed: 'false', notes: '', ...fields };
}

async function createRoute(args, env) {
  const db = await loadPlans(env);
  if (missing(db)) return toolText(db, true);
  const city = findCity(db, args.city, args.country);
  if (missing(city)) return toolText(city, true);
  const name = text(args.name);
  if (!name) return toolText('Give the route a name.', true);
  const clash = db.Routes.find((r) => r.cityId === city.id && r.name.toLowerCase() === name.toLowerCase());
  if (clash) return toolText(`${city.name} already has a route called ${clash.name}; change it with update_route.`, true);
  if (!Array.isArray(args.stops) || args.stops.length === 0) return toolText('A route needs at least one stop.', true);
  const { spots, problems } = resolveStops(db, args.stops, city);
  if (problems.length) return toolText(`Nothing was created:\n${problems.join('\n')}`, true);

  let trip = null;
  let date = '';
  let time = '';
  if (text(args.trip)) {
    trip = findTrip(db, args.trip);
    if (missing(trip)) return toolText(trip, true);
    date = text(args.date);
    if (!validDate(date)) return toolText('Give the trip day as `date`, yyyy-MM-dd.', true);
    time = text(args.time) ? readTime(args.time) : '';
    if (time === null) return toolText(`"${args.time}" is not a time; use HH:mm.`, true);
  }

  const now = String(Date.now());
  const route = newRoute(now, { cityId: city.id, name, notes: text(args.notes) });
  let key = '';
  const stops = spots.map((s) => newStop(now, route.id, s.id, (key = keyAfter(key))));
  const rows = { Routes: [route], RouteStops: stops };
  if (trip) rows.TripItems = [newTripItem(now, trip.id, 'ROUTE', timedLocal(date, time), { title: name, routeId: route.id })];
  const failed = await pushRows(env, rows);
  if (failed) return toolText(failed, true);
  return toolText(`Created the route ${name} in ${city.name}: ${spots.map((s) => s.name).join(' → ')}.` +
    (trip ? ` It is on ${trip.name}, ${dayLabel(date)}.` : ''));
}

async function updateRoute(args, env) {
  const db = await loadPlans(env);
  if (missing(db)) return toolText(db, true);
  const route = findRoute(db, args.route, args.city);
  if (missing(route)) return toolText(route, true);
  const city = db.Cities.find((c) => c.id === route.cityId);
  const now = String(Date.now());
  const changes = {};
  if (text(args.new_name)) changes.name = text(args.new_name);
  if (args.notes !== undefined && args.notes !== null) changes.notes = text(args.notes);
  if (text(args.append_notes)) {
    changes.notes = appended(changes.notes !== undefined ? changes.notes : route.notes, args.append_notes);
  }
  if (typeof args.walked === 'boolean') changes.completed = args.walked ? 'true' : 'false';

  const replacing = Array.isArray(args.stops);
  const adding = Array.isArray(args.add_stops) && args.add_stops.length > 0;
  const removing = Array.isArray(args.remove_stops) && args.remove_stops.length > 0;
  if (replacing && (adding || removing)) return toolText('Give either `stops` or add_stops/remove_stops, not both.', true);
  if ((replacing || adding) && !city) return toolText(`The city of ${route.name} no longer exists, so no stops can be added.`, true);

  const current = stopsOf(db, route.id);
  const stopRows = [];
  const notes = [];
  if (replacing) {
    const { spots, problems } = resolveStops(db, args.stops, city);
    if (problems.length) return toolText(`Nothing was changed:\n${problems.join('\n')}`, true);
    let key = '';
    for (const s of spots) {
      key = keyAfter(key);
      const kept = current.find((c) => c.spotId === s.id);
      stopRows.push(kept ? touchedRow(kept.id, now, { sortKey: key }) : newStop(now, route.id, s.id, key));
    }
    for (const c of current) {
      if (!spots.some((s) => s.id === c.spotId)) stopRows.push(touchedRow(c.id, now, { deleted: 'true' }));
    }
  }
  let remaining = current;
  if (removing) {
    const onRoute = (s) => (db.Spots.find((x) => x.id === s.spotId) || {}).name || '';
    for (const n of args.remove_stops) {
      const stop = pick(remaining, n, `stop on ${route.name}`, onRoute);
      if (missing(stop)) return toolText(`Nothing was changed: ${stop}`, true);
      remaining = remaining.filter((s) => s.id !== stop.id);
      stopRows.push(touchedRow(stop.id, now, { deleted: 'true' }));
    }
  }
  if (adding) {
    const { spots, problems } = resolveStops(db, args.add_stops, city);
    if (problems.length) return toolText(`Nothing was changed:\n${problems.join('\n')}`, true);
    let key = remaining.length ? remaining[remaining.length - 1].sortKey : '';
    for (const s of spots) {
      if (remaining.some((r) => r.spotId === s.id)) {
        notes.push(`${s.name} was already on it.`);
        continue;
      }
      key = keyAfter(key);
      stopRows.push(newStop(now, route.id, s.id, key));
    }
  }
  if (Object.keys(changes).length === 0 && stopRows.length === 0) {
    return toolText(notes.length ? notes.join(' ') : 'Nothing to change was given.', !notes.length);
  }

  const rows = {};
  if (Object.keys(changes).length) rows.Routes = [touchedRow(route.id, now, changes)];
  if (stopRows.length) rows.RouteStops = stopRows;
  const failed = await pushRows(env, rows);
  if (failed) return toolText(failed, true);

  // Read the result back from what was sent, so the answer shows the order the phones will see.
  const after = new Map(current.map((s) => [s.id, s]));
  for (const r of stopRows) after.set(r.id, { ...(after.get(r.id) || {}), ...r });
  const order = [...after.values()]
    .filter((s) => s.deleted !== 'true')
    .sort((a, b) => (a.sortKey < b.sortKey ? -1 : a.sortKey > b.sortKey ? 1 : 0))
    .map((s) => (db.Spots.find((x) => x.id === s.spotId) || {}).name || '?');
  return toolText(`Updated the route ${changes.name || route.name}. Stops: ${order.join(' → ') || '(none)'}.` +
    (notes.length ? ` ${notes.join(' ')}` : ''));
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
