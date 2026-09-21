/**
 * Holiday Planner backend.
 *
 * Container-bound Apps Script (Extensions → Apps Script on the spreadsheet), V8 runtime.
 * One tab per table, columns mapped by header name. See backend/README.md for deploy steps.
 *
 * Requests: POST JSON { secret, action, ... } to the /exec URL.
 *   ping             -> { ok, serverTime }
 *   pull { since, tables? } -> { ok, serverTime, changes: { Table: [row, ...] } }
 *   push { rows: { Table: [row, ...] } } -> { ok, serverTime, accepted, skipped, errors }
 * Every response is HTTP 200; callers must check `ok`.
 */

var COMMON_COLUMNS = ['id', 'createdAt', 'createdBy', 'updatedAt', 'updatedBy', 'syncedAt', 'deleted'];

var SCHEMA = {
  Users: COMMON_COLUMNS.concat(['name']),
  Countries: COMMON_COLUMNS.concat(['name', 'isoCode', 'sortKey', 'priority', 'imageUrl']),
  Cities: COMMON_COLUMNS.concat([
    'countryId', 'name', 'googlePlaceId', 'lat', 'lng', 'sortKey', 'priority', 'imageUrl',
  ]),
  Spots: COMMON_COLUMNS.concat([
    'parentCityId', 'name', 'type', 'googlePlaceId', 'lat', 'lng', 'address', 'mapsUrl', 'notes', 'status',
    'sortKey', 'priority', 'imageUrl',
  ]),
  Links: COMMON_COLUMNS.concat(['spotId', 'url', 'source', 'note']),
  Hearts: COMMON_COLUMNS.concat(['spotId', 'userId']),
  Trips: COMMON_COLUMNS.concat(['name', 'countryId', 'startDate', 'endDate', 'notes']),
  TripItems: COMMON_COLUMNS.concat([
    'tripId', 'type', 'title', 'origin', 'destination', 'startLocal', 'startTimeZone', 'endLocal', 'endTimeZone',
    'spotId', 'routeId', 'bookingRef', 'notes',
  ]),
  Routes: COMMON_COLUMNS.concat(['cityId', 'name', 'completed', 'notes']),
  RouteStops: COMMON_COLUMNS.concat(['routeId', 'spotId', 'sortKey']),
};

/** Whitelist of tabs the API touches. Any other tab in the spreadsheet is ignored. */
var TABLES = Object.keys(SCHEMA);

/**
 * The Inbox tab: a place to type or paste places in plain words, from any device, without ids or
 * timestamps. importInbox() turns each row into a country, a city and a spot, geocoding as it goes.
 * `result` is written back per row: "done", "exists", or why it failed. Not part of the sync.
 */
/**
 * Where photos shared to the app are kept. The sheet holds only their addresses: a cell cannot
 * hold a picture, and Drive can serve one to both phones without another account or key.
 */
var PICTURE_FOLDER = 'Holiday Planner pictures';

/** The form of Drive address that serves the image bytes themselves rather than a viewer page. */
var PICTURE_URL = 'https://lh3.googleusercontent.com/d/';

var INBOX = 'Inbox';
var INBOX_COLUMNS = ['country', 'city', 'spot', 'type', 'priority', 'notes', 'link', 'result'];
var INBOX_AUTHOR = 'sheet';
var SORT_DIGITS = '0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz';

/** Columns onEdit writes; edits limited to these never re-stamp a row. */
var STAMP_COLUMNS = ['updatedAt', 'updatedBy', 'syncedAt'];

var LOCK_TIMEOUT_MS = 30000;

// ---------------------------------------------------------------------------------------------
// Setup (run manually from the editor; idempotent)
// ---------------------------------------------------------------------------------------------

/**
 * Creates missing tabs, appends missing header columns (never reorders or removes existing ones),
 * formats every sheet as plain text and freezes the header row. Re-run whenever SCHEMA changes.
 */
function setup() {
  var ss = SpreadsheetApp.getActiveSpreadsheet();
  TABLES.forEach(function (name) {
    var sheet = ss.getSheetByName(name) || ss.insertSheet(name);
    var lastCol = sheet.getLastColumn();
    var existing = lastCol > 0 ? sheet.getRange(1, 1, 1, lastCol).getValues()[0].map(String) : [];
    var missing = SCHEMA[name].filter(function (h) { return existing.indexOf(h) === -1; });
    if (missing.length > 0) {
      sheet.getRange(1, existing.length + 1, 1, missing.length).setValues([missing]);
    }
    sheet.getRange(1, 1, sheet.getMaxRows(), sheet.getMaxColumns()).setNumberFormat('@');
    sheet.setFrozenRows(1);
  });
  setupInbox(ss);
}

/** The Inbox tab, with its columns and an example row so it explains itself. */
function setupInbox(ss) {
  var sheet = ss.getSheetByName(INBOX);
  if (!sheet) {
    sheet = ss.insertSheet(INBOX);
    sheet.getRange(1, 1, 1, INBOX_COLUMNS.length).setValues([INBOX_COLUMNS]);
    sheet.getRange(2, 1, 1, INBOX_COLUMNS.length).setValues([[
      'Netherlands', 'Den Haag', 'Vredespaleis', 'poi', 'high', 'Peace Palace, book a tour', '', 'example',
    ]]);
  }
  sheet.getRange(1, 1, sheet.getMaxRows(), sheet.getMaxColumns()).setNumberFormat('@');
  sheet.setFrozenRows(1);
}

/** Adds the Holiday Planner menu when the sheet is opened in a browser. */
function onOpen() {
  SpreadsheetApp.getUi()
    .createMenu('Holiday Planner')
    .addItem('Import inbox rows', 'importInbox')
    .addToUi();
}

/**
 * Run once from the editor to have the inbox imported every ten minutes, so rows typed on a phone
 * (where the menu does not exist) are picked up on their own.
 */
function installInboxTrigger() {
  var already = ScriptApp.getProjectTriggers().some(function (t) { return t.getHandlerFunction() === 'importInbox'; });
  if (already) return;
  ScriptApp.newTrigger('importInbox').timeBased().everyMinutes(10).create();
}

// ---------------------------------------------------------------------------------------------
// Entry points
// ---------------------------------------------------------------------------------------------

/** Health check. Returns no data. */
function doGet() {
  return json({ ok: true, service: 'holiday-planner' });
}

function doPost(e) {
  var body;
  try {
    body = JSON.parse(e.postData.contents);
  } catch (err) {
    return json({ ok: false, error: 'bad_json' });
  }

  var secret = PropertiesService.getScriptProperties().getProperty('SECRET');
  if (!secret || body.secret !== secret) {
    return json({ ok: false, error: 'unauthorized' });
  }

  // One lock around pull and push so every request sees a consistent sheet.
  var lock = LockService.getScriptLock();
  if (!lock.tryLock(LOCK_TIMEOUT_MS)) {
    return json({ ok: false, error: 'busy' });
  }
  try {
    switch (body.action) {
      case 'ping':
        return json({ ok: true, serverTime: Date.now() });
      case 'pull':
        return json(pull(body));
      case 'push':
        return json(push(body));
      case 'inbox':
        return json(inbox(body));
      case 'image':
        return json(saveImage(body));
      case 'deleteImage':
        return json(trashImage(body));
      default:
        return json({ ok: false, error: 'unknown_action' });
    }
  } catch (err) {
    return json({ ok: false, error: String(err && err.message ? err.message : err) });
  } finally {
    lock.releaseLock();
  }
}

// ---------------------------------------------------------------------------------------------
// Pull
// ---------------------------------------------------------------------------------------------

/** Returns every row whose syncedAt >= since. Duplicates are harmless; missed rows are not. */
function pull(body) {
  var serverTime = Date.now();
  var since = Number(body.since) || 0;
  var requested = Array.isArray(body.tables)
    ? body.tables.filter(function (t) { return TABLES.indexOf(t) !== -1; })
    : TABLES;

  var ss = SpreadsheetApp.getActiveSpreadsheet();
  var changes = {};
  requested.forEach(function (name) {
    var table = readTable(ss, name);
    if (!table) return;
    var rows = [];
    for (var r = 1; r < table.values.length; r++) {
      var row = table.values[r];
      if (cellToString(row[table.index.id]) === '') continue;
      var syncedAt = Number(cellToString(row[table.index.syncedAt]));
      if (!(syncedAt >= since)) continue;
      rows.push(rowToObject(table.headers, row));
    }
    if (rows.length > 0) changes[name] = rows;
  });

  return { ok: true, serverTime: serverTime, changes: changes };
}

// ---------------------------------------------------------------------------------------------
// Push
// ---------------------------------------------------------------------------------------------

/**
 * Upserts rows with last-write-wins on updatedAt. Each table is read once and written back with a
 * single setValues call covering existing and appended rows.
 */
function push(body) {
  var now = Date.now();
  var rowsByTable = body.rows || {};
  var ss = SpreadsheetApp.getActiveSpreadsheet();
  var accepted = 0;
  var skipped = [];
  var errors = [];

  Object.keys(rowsByTable).forEach(function (name) {
    if (TABLES.indexOf(name) === -1) {
      errors.push({ table: name, error: 'unknown_table' });
      return;
    }
    var incomingRows = rowsByTable[name];
    if (!Array.isArray(incomingRows) || incomingRows.length === 0) return;

    var table = readTable(ss, name);
    if (!table) {
      errors.push({ table: name, error: 'missing_sheet' });
      return;
    }

    var width = table.headers.length;
    var idToRow = {};
    for (var r = 1; r < table.values.length; r++) {
      var existingId = cellToString(table.values[r][table.index.id]);
      if (existingId !== '' && !(existingId in idToRow)) idToRow[existingId] = r;
    }

    var changed = false;
    incomingRows.forEach(function (incoming) {
      var id = incoming && incoming.id !== undefined && incoming.id !== null ? String(incoming.id) : '';
      var updatedAt = incoming ? Number(incoming.updatedAt) : NaN;
      if (id === '' || incoming.updatedAt === '' || incoming.updatedAt === null || !isFinite(updatedAt)) {
        errors.push({ table: name, id: id, error: 'invalid_row' });
        return;
      }

      var rowIndex = idToRow[id];
      var row;
      if (rowIndex !== undefined) {
        row = table.values[rowIndex];
        if (Number(cellToString(row[table.index.updatedAt])) >= updatedAt) {
          skipped.push(id);
          return;
        }
      } else {
        row = [];
        for (var c = 0; c < width; c++) row.push('');
      }

      table.headers.forEach(function (header, i) {
        if (header === '' || header === 'syncedAt' || !(header in incoming)) return;
        row[i] = cellToString(incoming[header]);
      });
      row[table.index.syncedAt] = String(now);

      if (rowIndex === undefined) {
        table.values.push(row);
        idToRow[id] = table.values.length - 1;
      }
      accepted++;
      changed = true;
    });

    if (changed) writeTable(table);
  });

  return { ok: true, serverTime: now, accepted: accepted, skipped: skipped, errors: errors };
}

// ---------------------------------------------------------------------------------------------
// Pictures shared to the app
// ---------------------------------------------------------------------------------------------

/**
 * Stores one picture in Drive and returns the address that serves it.
 *
 * The phone sends it already scaled down: a full-size photo as base64 is tens of megabytes and
 * would be refused long before it arrived. The file is readable by anyone holding its address,
 * because the phones fetch it without signing in; the address itself is the secret.
 */
function saveImage(body) {
  var data = body.data;
  if (!data) return { ok: false, error: 'no_data' };
  var blob = Utilities.newBlob(
    Utilities.base64Decode(data),
    body.mimeType || 'image/jpeg',
    body.name || 'photo-' + Date.now() + '.jpg'
  );
  var file = pictureFolder().createFile(blob);
  file.setSharing(DriveApp.Access.ANYONE_WITH_LINK, DriveApp.Permission.VIEW);
  return { ok: true, serverTime: Date.now(), fileId: file.getId(), url: PICTURE_URL + file.getId() };
}

/** Moves a picture to the Drive bin once nothing points at it. A missing file is already gone. */
function trashImage(body) {
  var fileId = body.fileId;
  if (!fileId) return { ok: false, error: 'no_file_id' };
  try {
    DriveApp.getFileById(fileId).setTrashed(true);
  } catch (err) {
    return { ok: true, serverTime: Date.now(), trashed: false };
  }
  return { ok: true, serverTime: Date.now(), trashed: true };
}

/** The folder, remembered by id so renaming it in Drive does not start a second one. */
function pictureFolder() {
  var props = PropertiesService.getScriptProperties();
  var id = props.getProperty('PICTURE_FOLDER_ID');
  if (id) {
    try {
      return DriveApp.getFolderById(id);
    } catch (err) {
      // Deleted by hand; fall through and make another.
    }
  }
  var existing = DriveApp.getFoldersByName(PICTURE_FOLDER);
  var folder = existing.hasNext() ? existing.next() : DriveApp.createFolder(PICTURE_FOLDER);
  props.setProperty('PICTURE_FOLDER_ID', folder.getId());
  return folder;
}

// ---------------------------------------------------------------------------------------------
// Inbox: places in plain words become countries, cities and spots
// ---------------------------------------------------------------------------------------------

/**
 * API entry: appends the given rows ({country, city, spot, type, priority, notes, link}) to the
 * Inbox and imports everything waiting there. Returns one result per row imported.
 */
function inbox(body) {
  var rows = Array.isArray(body.rows) ? body.rows : [];
  var ss = SpreadsheetApp.getActiveSpreadsheet();
  var sheet = ss.getSheetByName(INBOX);
  if (!sheet) {
    setupInbox(ss);
    sheet = ss.getSheetByName(INBOX);
  }
  if (rows.length > 0) {
    var values = rows.map(function (r) {
      return INBOX_COLUMNS.map(function (col) { return col === 'result' ? '' : cellToString(r[col]); });
    });
    var first = sheet.getLastRow() + 1;
    sheet.getRange(first, 1, values.length, INBOX_COLUMNS.length).setValues(values.map(function (row) {
      return row.map(escapeCell);
    }));
  }
  return { ok: true, serverTime: Date.now(), results: importInboxRows(ss) };
}

/** Menu and trigger entry: imports every Inbox row that has no result yet. */
function importInbox() {
  var lock = LockService.getScriptLock();
  if (!lock.tryLock(LOCK_TIMEOUT_MS)) return;
  try {
    importInboxRows(SpreadsheetApp.getActiveSpreadsheet());
  } finally {
    lock.releaseLock();
  }
}

/**
 * The work. Each waiting row is geocoded once (spot, city, country together), which also settles
 * which country it is in, then filed under an existing country and city or new ones. A Google
 * Maps link becomes the spot's own link; any other link is attached as a link row.
 */
function importInboxRows(ss) {
  var sheet = ss.getSheetByName(INBOX);
  if (!sheet || sheet.getLastRow() < 2) return [];
  var width = INBOX_COLUMNS.length;
  var values = sheet.getRange(2, 1, sheet.getLastRow() - 1, width).getValues();
  var resultCol = INBOX_COLUMNS.indexOf('result');

  var tables = {
    Countries: readTable(ss, 'Countries'),
    Cities: readTable(ss, 'Cities'),
    Spots: readTable(ss, 'Spots'),
    Links: readTable(ss, 'Links'),
  };
  var changed = { Countries: false, Cities: false, Spots: false, Links: false };
  var results = [];

  values.forEach(function (row, i) {
    var entry = {};
    INBOX_COLUMNS.forEach(function (col, c) { entry[col] = cellToString(row[c]).trim(); });
    if (entry.result !== '' || (entry.spot === '' && entry.city === '' && entry.country === '')) return;
    var outcome;
    try {
      outcome = importOne(entry, tables, changed);
    } catch (err) {
      outcome = 'failed: ' + String(err && err.message ? err.message : err);
    }
    sheet.getRange(i + 2, resultCol + 1).setValue(outcome);
    results.push({ spot: entry.spot, result: outcome });
  });

  Object.keys(changed).forEach(function (name) {
    if (changed[name]) writeTable(tables[name]);
  });
  return results;
}

function importOne(entry, tables, changed) {
  var now = String(Date.now());
  var spotName = entry.spot || entry.city || entry.country;
  var query = [entry.spot, entry.city, entry.country].filter(function (s) { return s !== ''; }).join(', ');
  var geo = geocode(query);
  if (!geo) return 'failed: could not locate "' + query + '"';

  var isoCode = geo.isoCode;
  if (!isoCode) return 'failed: no country found for "' + query + '"';
  var countryName = entry.country || geo.countryName || isoCode;

  var country = findRow(tables.Countries, function (r) { return r.isoCode.toUpperCase() === isoCode; });
  if (!country) {
    country = newRow(tables.Countries, now, {
      name: countryName, isoCode: isoCode, sortKey: nextSortKey(tables.Countries, function () { return true; }),
      priority: 'NORMAL', imageUrl: '',
    });
    changed.Countries = true;
  }

  var cityName = entry.city || geo.cityName || 'General';
  var city = findRow(tables.Cities, function (r) {
    return r.countryId === country.id && r.name.toLowerCase() === cityName.toLowerCase();
  });
  if (!city) {
    var cityGeo = entry.city ? geocode(cityName + ', ' + countryName) : null;
    city = newRow(tables.Cities, now, {
      countryId: country.id, name: cityName, googlePlaceId: '',
      lat: cityGeo ? String(cityGeo.lat) : '', lng: cityGeo ? String(cityGeo.lng) : '',
      sortKey: nextSortKey(tables.Cities, function (r) { return r.countryId === country.id; }),
      priority: 'NORMAL', imageUrl: '',
    });
    changed.Cities = true;
  }

  var existing = findRow(tables.Spots, function (r) {
    return r.parentCityId === city.id && r.name.toLowerCase() === spotName.toLowerCase();
  });
  if (existing) return 'exists';

  var link = entry.link;
  var isMapsLink = /google\.[a-z.]+\/maps|maps\.app\.goo\.gl|goo\.gl\/maps/i.test(link);
  var spot = newRow(tables.Spots, now, {
    parentCityId: city.id, name: spotName, type: parseType(entry.type), googlePlaceId: '',
    lat: entry.spot ? String(geo.lat) : '', lng: entry.spot ? String(geo.lng) : '', address: geo.address || '',
    mapsUrl: isMapsLink ? link : 'https://www.google.com/maps/search/?api=1&query=' + encodeURIComponent(query),
    notes: entry.notes, status: 'UNVISITED',
    sortKey: nextSortKey(tables.Spots, function (r) { return r.parentCityId === city.id; }),
    priority: parsePriority(entry.priority), imageUrl: '',
  });
  changed.Spots = true;

  if (link !== '' && !isMapsLink) {
    newRow(tables.Links, now, { spotId: spot.id, url: link, source: linkSource(link), note: '' });
    changed.Links = true;
  }
  return 'done';
}

/** Google's geocoder, which also says which country and city a place is in. Null when it has no idea. */
function geocode(query) {
  var response = Maps.newGeocoder().geocode(query);
  var hit = response && response.results && response.results[0];
  if (!hit) return null;
  var out = { lat: hit.geometry.location.lat, lng: hit.geometry.location.lng, address: hit.formatted_address };
  (hit.address_components || []).forEach(function (part) {
    var types = part.types || [];
    if (types.indexOf('country') !== -1) {
      out.isoCode = String(part.short_name).toUpperCase();
      out.countryName = part.long_name;
    }
    if (!out.cityName && (types.indexOf('locality') !== -1 || types.indexOf('postal_town') !== -1)) {
      out.cityName = part.long_name;
    }
  });
  if (!out.cityName) {
    (hit.address_components || []).forEach(function (part) {
      if (!out.cityName && (part.types || []).indexOf('administrative_area_level_2') !== -1) out.cityName = part.long_name;
    });
  }
  return out;
}

function parseType(text) {
  var t = text.toLowerCase();
  if (/rest|food|eat|cafe|café|bar/.test(t)) return 'RESTAURANT';
  if (/acc|hotel|stay|sleep|hostel|camp|b&b/.test(t)) return 'ACCOMMODATION';
  return 'POI';
}

function parsePriority(text) {
  var t = text.toLowerCase();
  return (t === 'high' || t === 'must' || t === 'yes' || t === 'x' || t === '1' || t === '!') ? 'HIGH' : 'NORMAL';
}

function linkSource(url) {
  var u = url.toLowerCase();
  if (u.indexOf('instagram.com') !== -1) return 'INSTAGRAM';
  if (u.indexOf('tiktok.com') !== -1) return 'TIKTOK';
  if (u.indexOf('youtube.com') !== -1 || u.indexOf('youtu.be') !== -1) return 'YOUTUBE';
  return 'WEB';
}

/** The first live row of a table matching the predicate, as an object with an `id`. */
function findRow(table, predicate) {
  for (var r = 1; r < table.values.length; r++) {
    var obj = rowToObject(table.headers, table.values[r]);
    if (obj.id === '' || obj.deleted === 'true') continue;
    if (predicate(obj)) return obj;
  }
  return null;
}

/** Appends a row with fresh id and stamps, returns it as an object. Written to the sheet later. */
function newRow(table, now, fields) {
  var obj = {
    id: Utilities.getUuid(), createdAt: now, createdBy: INBOX_AUTHOR, updatedAt: now, updatedBy: INBOX_AUTHOR,
    syncedAt: now, deleted: 'false',
  };
  Object.keys(fields).forEach(function (k) { obj[k] = fields[k]; });
  table.values.push(table.headers.map(function (h) { return h in obj ? cellToString(obj[h]) : ''; }));
  return obj;
}

/** A sort key after the last one among the rows the predicate selects, the way the app makes them. */
function nextSortKey(table, predicate) {
  var last = '';
  for (var r = 1; r < table.values.length; r++) {
    var obj = rowToObject(table.headers, table.values[r]);
    if (obj.id === '' || obj.deleted === 'true' || !predicate(obj)) continue;
    if (obj.sortKey > last) last = obj.sortKey;
  }
  return sortKeyMidpoint(last, null);
}

/** Fractional base-62 keys: a port of the app's SortKey.between(before, null). */
function sortKeyMidpoint(before, after) {
  if (after !== null) {
    var shared = 0;
    while (shared < after.length && (shared < before.length ? before[shared] : SORT_DIGITS[0]) === after[shared]) shared++;
    if (shared > 0) return after.substring(0, shared) + sortKeyMidpoint(before.substring(shared), after.substring(shared));
  }
  var low = before === '' ? 0 : SORT_DIGITS.indexOf(before[0]);
  var high = after === null ? SORT_DIGITS.length : SORT_DIGITS.indexOf(after[0]);
  if (high - low > 1) return SORT_DIGITS[Math.floor((low + high + 1) / 2)];
  if (after !== null && after.length > 1) return after.substring(0, 1);
  return SORT_DIGITS[low] + sortKeyMidpoint(before.substring(1), null);
}

// ---------------------------------------------------------------------------------------------
// Manual edits in the sheet
// ---------------------------------------------------------------------------------------------

/**
 * Simple trigger. A human edit to a data row in a schema tab stamps updatedAt/syncedAt with the
 * current time and updatedBy with "sheet", so the change reaches the phones on the next pull.
 * Script writes (push) do not fire onEdit.
 */
function onEdit(e) {
  var range = e && e.range;
  if (!range) return;
  var sheet = range.getSheet();
  var name = sheet.getName();
  if (TABLES.indexOf(name) === -1) return;
  if (range.getRow() <= 1 && range.getLastRow() <= 1) return; // header only

  var headers = sheet.getRange(1, 1, 1, sheet.getLastColumn()).getValues()[0].map(String);
  var stampCols = STAMP_COLUMNS.map(function (h) { return headers.indexOf(h) + 1; });
  if (stampCols.indexOf(0) !== -1) return; // run setup() first

  var firstCol = range.getColumn();
  var lastCol = range.getLastColumn();
  var onlyStampColumns = true;
  for (var c = firstCol; c <= lastCol; c++) {
    if (stampCols.indexOf(c) === -1) { onlyStampColumns = false; break; }
  }
  if (onlyStampColumns) return; // avoid loops

  var now = String(Date.now());
  var firstRow = Math.max(range.getRow(), 2);
  for (var r = firstRow; r <= range.getLastRow(); r++) {
    sheet.getRange(r, stampCols[0]).setValue(now);      // updatedAt
    sheet.getRange(r, stampCols[1]).setValue('sheet');  // updatedBy
    sheet.getRange(r, stampCols[2]).setValue(now);      // syncedAt
  }
}

// ---------------------------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------------------------

/** Reads a whole tab once. Returns { sheet, values, headers, index } or null if the tab is missing. */
function readTable(ss, name) {
  var sheet = ss.getSheetByName(name);
  if (!sheet) return null;
  var values = sheet.getDataRange().getValues();
  var headers = values[0].map(function (v) { return String(v); });
  var index = {};
  headers.forEach(function (h, i) {
    if (h !== '' && !(h in index)) index[h] = i;
  });
  ['id', 'updatedAt', 'syncedAt'].forEach(function (required) {
    if (index[required] === undefined) throw new Error('Tab ' + name + ' is missing column ' + required + '. Run setup().');
  });
  return { sheet: sheet, values: values, headers: headers, index: index };
}

/** One setValues over the full range (existing + appended rows), formatted as text first. */
function writeTable(table) {
  var rows = table.values.length;
  var width = table.headers.length;
  var maxRows = table.sheet.getMaxRows();
  if (rows > maxRows) table.sheet.insertRowsAfter(maxRows, rows - maxRows);
  var range = table.sheet.getRange(1, 1, rows, width);
  range.setNumberFormat('@');
  range.setValues(table.values.map(function (row) { return row.map(escapeCell); }));
}

function rowToObject(headers, row) {
  var obj = {};
  headers.forEach(function (h, i) {
    if (h !== '') obj[h] = cellToString(row[i]);
  });
  return obj;
}

function cellToString(value) {
  if (value === null || value === undefined) return '';
  return String(value);
}

/**
 * Formula-injection guard. Sheets would evaluate a cell starting with "=" or "+" as a formula;
 * a leading apostrophe makes it text and is not part of the stored value. A legitimate leading
 * apostrophe is doubled for the same reason.
 */
function escapeCell(value) {
  if (typeof value !== 'string' || value === '') return value;
  var first = value.charAt(0);
  if (first === '=' || first === '+' || first === "'") return "'" + value;
  return value;
}

function json(obj) {
  return ContentService.createTextOutput(JSON.stringify(obj)).setMimeType(ContentService.MimeType.JSON);
}
