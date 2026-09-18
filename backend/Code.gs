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
    'spotId', 'routeId', 'bookingRef', 'costMinor', 'currency', 'docUrl', 'notes',
  ]),
  Routes: COMMON_COLUMNS.concat(['cityId', 'name', 'completed', 'notes']),
  RouteStops: COMMON_COLUMNS.concat(['routeId', 'spotId', 'sortKey']),
};

/** Whitelist of tabs the API touches. Any other tab in the spreadsheet is ignored. */
var TABLES = Object.keys(SCHEMA);

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
