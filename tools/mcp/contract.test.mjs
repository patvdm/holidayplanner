// Checks the claude.ai connector against what the app and the sheet expect: the columns it writes,
// the values it offers, and the sort keys it hands out. Run with
// `node --test tools/mcp/contract.test.mjs`; the app's unit test task runs it too. The app checks the same contract.json from its side
// (SheetContractTest), so a change made on one side only fails one of the two.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import vm from 'node:vm';

const here = (path) => fileURLToPath(new URL(path, import.meta.url));
const contract = JSON.parse(readFileSync(here('./contract.json'), 'utf8'));

/** Runs a script's top level in a sandbox and hands back its globals, functions included. */
function load(path, extra = '') {
  const sandbox = { crypto: { randomUUID: () => 'id' }, console };
  vm.createContext(sandbox);
  const source = readFileSync(here(path), 'utf8').replace(/^export default /m, 'var __default = ');
  vm.runInContext(`${source}\n${extra}`, sandbox);
  return sandbox;
}

// `const` at a script's top level does not land on the sandbox, so the names wanted are copied out.
const worker = load('./worker.js', 'globalThis.TOOLS = TOOLS;');
const backend = load('../../backend/Code.gs', 'globalThis.SCHEMA = SCHEMA;');

function assertColumns(table, row) {
  const columns = backend.SCHEMA[table];
  assert.ok(columns, `the sheet has no ${table} tab`);
  const unknown = Object.keys(row).filter((key) => !columns.includes(key));
  assert.deepEqual(unknown, [], `${table} has no column for ${unknown.join(', ')}`);
}

test('new rows only use columns the sheet has', () => {
  const now = '1';
  assertColumns('Trips', worker.newTrip(now, {}));
  assertColumns('Routes', worker.newRoute(now, {}));
  assertColumns('RouteStops', worker.newStop(now, 'r', 's', 'V'));
  assertColumns('TripItems', worker.newTripItem(now, 't', 'SPOT', '2026-11-07', {}));
});

test('sort keys follow the app', () => {
  for (const [last, next] of contract.sortKeyAfter) {
    assert.equal(worker.keyAfter(last), next, `after "${last}"`);
  }
});

/** Every value a tool offers for one argument, upper-cased as the connector writes it. */
function offered(tool, path) {
  const spec = worker.TOOLS.find((t) => t.name === tool);
  assert.ok(spec, `no tool called ${tool}`);
  let node = spec.inputSchema;
  for (const step of path) node = node[step];
  // Array.from, so the list belongs to this realm rather than the sandbox's, or deepEqual refuses it.
  return Array.from(node.enum, (v) => String(v).toUpperCase());
}

test('the values the tools offer are the ones the app knows', () => {
  const { enums } = contract;
  assert.deepEqual(offered('update_place', ['properties', 'type']).sort(), [...enums.SpotType].sort());
  assert.deepEqual(offered('update_place', ['properties', 'priority']).sort(), [...enums.Priority].sort());
  assert.deepEqual(offered('update_place', ['properties', 'status']).sort(), [...enums.SpotStatus].sort());
  assert.deepEqual(offered('add_to_trip', ['properties', 'kind']).sort(), [...enums.TripItemType].sort());
});
