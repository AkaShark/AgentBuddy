import test from 'node:test';
import assert from 'node:assert/strict';
import { presentationRevision } from '../entry/src/main/ets/state/MessageIdentity.ts';

const message = (overrides = {}) => ({ title: '搭子', text: 'H', role: 'assistant',
  code: false, images: [], renderRevision: 1, ...overrides });

test('same message id receives fresh UI identity for streamed tokens', () => {
  const previous = message();
  const completed = message({ text: 'HARMONY_OK' });
  assert.equal(presentationRevision(previous, completed), 2);
  completed.renderRevision = 2;
  assert.equal(presentationRevision(completed, { ...completed, images: [] }), 2);
});

test('equal-length corrections and image changes also invalidate the row', () => {
  assert.equal(presentationRevision(message({ text: 'old' }), message({ text: 'new' })), 2);
  assert.equal(presentationRevision(message({ images: ['one'] }), message({ images: ['two'] })), 2);
  assert.equal(presentationRevision(message(), message({ code: true })), 2);
  assert.equal(presentationRevision(message({ remoteImagePath: '/old.png' }), message({ remoteImagePath: '/new.png' })), 2);
});

test('new messages start a render identity without relying on array index', () => {
  assert.equal(presentationRevision(undefined, message()), 1);
});
