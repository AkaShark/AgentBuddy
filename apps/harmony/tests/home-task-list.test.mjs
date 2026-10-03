import test from 'node:test';
import assert from 'node:assert/strict';
import { homeTaskItems, taskKey, taskRenderKey } from '../entry/src/main/ets/state/HomeTaskList.ts';

const key = (threadId, serverId = 'mac') => ({ serverId, threadId });
const task = (threadId, overrides = {}) => ({ key: key(threadId), title: threadId, preview: '',
  cwd: '/project', serverDisplayName: 'Mac', agentRuntimeKind: 'codex', hasActiveTurn: false, ...overrides });
const prefs = (overrides = {}) => ({ pinnedThreads: [], hiddenThreads: [], homeSelection: {}, ...overrides });
const servers = [{ serverId: 'mac', displayName: 'Mac' }, { serverId: 'other', displayName: 'Other' }];

test('home matches Android recent-ten and saved pin order, excluding hidden pins', () => {
  const tasks = Array.from({ length: 15 }, (_, n) => task(String(n)));
  assert.equal(homeTaskItems(tasks, servers, prefs(), 0, '').length, 10);
  const saved = prefs({ pinnedThreads: [key('12'), key('2'), key('4')], hiddenThreads: [key('2')] });
  assert.deepEqual(homeTaskItems(tasks, servers, saved, 0, '').map(item => item.title), ['12', '4']);
  assert.deepEqual(homeTaskItems(tasks, servers, saved, 2, '').map(item => item.title), ['2']);
});

test('search reaches loaded tasks outside recent-ten and pins, with host/project boundaries', () => {
  const tasks = Array.from({ length: 15 }, (_, n) => task('task ' + n));
  tasks.push(task('task 14', { key: key('14', 'other'), cwd: '/elsewhere' }));
  const saved = prefs({ pinnedThreads: [key('task 1')] });
  assert.equal(homeTaskItems(tasks, servers, saved, 0, ' TASK 14 ').length, 2);
  saved.homeSelection = { selectedServerId: 'other' };
  assert.equal(homeTaskItems(tasks, servers, saved, 1, '14', '/elsewhere').length, 1);
  assert.equal(homeTaskItems(tasks, servers, saved, 1, '14', '/project').length, 0);
});

test('missing pins have removable placeholders; loaded tasks outside a project do not', () => {
  const saved = prefs({ pinnedThreads: [key('loaded'), key('missing'), key('unavailable', 'removed')] });
  const tasks = [task('loaded', { cwd: '/elsewhere' })];
  const rows = homeTaskItems(tasks, servers, saved, 0, '');
  assert.equal(rows.length, 2);
  assert.equal(rows[1].placeholder, true);
  assert.equal(homeTaskItems(tasks, servers, saved, 0, '', '/project').length, 0);
});

test('render identity tracks live cards and server/thread keys cannot collide', () => {
  const first = homeTaskItems([task('same')], servers, prefs(), 1, '')[0];
  const updated = homeTaskItems([task('same', { lastResponsePreview: 'new reply', hasActiveTurn: true })], servers, prefs(), 1, '')[0];
  assert.notEqual(taskRenderKey(first), taskRenderKey(updated));
  assert.notEqual(taskKey(key('c', 'a:b')), taskKey(key('b:c', 'a')));
});
