import test from 'node:test';
import assert from 'node:assert/strict';
import { loadedUserIndex } from '../entry/src/main/ets/state/MessageSelection.ts';

const item = (id, tag = 'User', boundary = true) => ({ id, content: { tag }, isFromUserTurnBoundary: boundary });
test('message selection follows identity when an older page is prepended', () => {
  const tail = [item('selected'), item('reply', 'Assistant'), item('next')];
  assert.equal(loadedUserIndex(tail, 'selected'), 0);
  assert.equal(loadedUserIndex([item('old'), item('tool', 'Note'), ...tail], 'selected'), 1);
  assert.equal(loadedUserIndex(tail, 'next'), 1);
});
test('stale, non-user and non-boundary selections never fall back to another prompt', () => {
  const items = [item('one'), item('answer', 'Assistant'), item('overlay', 'User', false)];
  for (const id of ['removed', 'answer', 'overlay']) assert.equal(loadedUserIndex(items, id), -1);
});
