import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { stripTypeScriptTypes } from 'node:module';
import vm from 'node:vm';

const source = readFileSync(new URL('../entry/src/main/ets/state/PushController.ts', import.meta.url), 'utf8')
  .replace(/^import .*;\n/gm, '').replace('export class PushController', 'class PushController');
const javascript = stripTypeScriptTypes(source, { mode: 'transform' });
const tick = () => new Promise(resolve => setImmediate(resolve));
function fixture(open) {
  const f = { allowed: true, token: 'token-1', records: [], opened: [], status: [], callback: undefined, off: 0 };
  const pushService = {
    getToken: async () => f.token,
    on: (_name, _ability, callback) => { f.callback = callback; },
    off: (_name, callback) => { assert.equal(callback, f.callback); ++f.off; }
  };
  const notificationManager = {
    isNotificationEnabled: async () => f.allowed,
    requestEnableNotification: async () => {}
  };
  const Controller = vm.runInNewContext(javascript + '\nPushController', {
    pushService, notificationManager, AppPushPlatform: { Harmony: 3 }
  });
  const client = { setPushRegistration: value => f.records.push(value) };
  f.controller = new Controller({ context: {} }, value => f.status.push(value), async key => {
    f.opened.push(key); return open ? await open(key) : true;
  });
  return Object.assign(f, { client, pushService });
}
const want = () => ({ parameters: {
  'agentbuddy.notification.serverId': 'alleycat:' + 'a'.repeat(64),
  'agentbuddy.notification.threadId': 'task-1',
  'agentbuddy.notification.eventId': 'evt_' + 'b'.repeat(32)
} });

test('notification permission denial clears Rust registration and token updates refresh it', async () => {
  const f = fixture(); f.allowed = false;
  f.controller.start(f.client); await tick();
  assert.equal(f.records.at(-1), undefined);
  f.allowed = true; await f.controller.enable();
  assert.equal(f.records.at(-1).token, 'token-1');
  assert.equal(f.records.at(-1).platform, 3);
  f.callback('token-2'); await tick();
  assert.equal(f.records.at(-1).token, 'token-2');
  f.allowed = false; await f.controller.refresh();
  assert.equal(f.records.at(-1), undefined);
});

test('a second tap during navigation drains after the first and failures wait for retry', async () => {
  let resolve;
  const f = fixture(key => key.threadId === 'task-1' ? new Promise(done => { resolve = done; }) : true);
  f.controller.start(f.client); f.controller.receive(want()); await tick();
  const next = want(); next.parameters['agentbuddy.notification.threadId'] = 'task-2';
  next.parameters['agentbuddy.notification.eventId'] = 'evt_' + 'c'.repeat(32);
  f.controller.receive(next); await tick(); assert.equal(f.opened.length, 1);
  resolve(true); await tick(); assert.equal(f.opened.length, 2);
  assert.equal(f.opened[1].threadId, 'task-2');
  const failed = fixture(async () => { throw new Error('private detail'); });
  failed.controller.start(failed.client); failed.controller.receive(want()); await tick();
  assert.equal(failed.opened.length, 1);
  assert.ok(!failed.status.at(-1).includes('private'));
  await failed.controller.drainRoute(); assert.equal(failed.opened.length, 2);
});

test('a delayed getToken cannot overwrite a newer tokenUpdate, and destruction preserves background grants', async () => {
  const f = fixture(); let resolve;
  f.pushService.getToken = () => new Promise(done => { resolve = done; });
  f.controller.start(f.client); await tick();
  f.callback('new-token'); await tick();
  resolve('old-token'); await tick();
  assert.equal(f.records.length, 1);
  assert.equal(f.records[0].token, 'new-token');
  f.controller.detach();
  assert.equal(f.off, 1); assert.equal(f.records.length, 1);
});

test('cold notification tap waits for Rust, routes once and rejects invalid external parameters', async () => {
  const f = fixture(); f.controller.receive(want()); await tick();
  assert.equal(f.opened.length, 0);
  f.controller.start(f.client); await tick();
  assert.equal(f.opened.length, 1); assert.equal(f.opened[0].threadId, 'task-1');
  f.controller.receive(want()); await tick(); assert.equal(f.opened.length, 1);
  const invalid = want(); invalid.parameters['agentbuddy.notification.serverId'] = 'ws://untrusted';
  f.controller.receive(invalid); await tick(); assert.equal(f.opened.length, 1);
});

test('temporary token retrieval failure preserves registration and displays a redacted status', async () => {
  const f = fixture(); f.controller.start(f.client); await tick();
  f.pushService.getToken = async () => { throw {code:1000900011,message:'sensitive token must not appear'}; };
  await f.controller.refresh();
  assert.equal(f.records.length, 1);
  assert.ok(!f.status.at(-1).includes('sensitive'));
  assert.ok(f.status.at(-1).includes('1000900011'));
});
