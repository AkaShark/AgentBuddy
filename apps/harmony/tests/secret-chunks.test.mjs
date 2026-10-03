import test from 'node:test';
import assert from 'node:assert/strict';
import { SecretChunks } from '../entry/src/main/ets/state/SecretChunks.ts';

function fixture() {
  const data = new Map(); let generation = 0; let failKey = '';
  const codec = { encode: value => new TextEncoder().encode(value), decode: bytes => new TextDecoder().decode(bytes) };
  const backend = {
    read: key => data.get(key),
    write: (key, bytes) => { assert.ok(bytes.length <= 1024); if (key.includes(failKey) && failKey) throw new Error('asset failed'); data.set(key, bytes.slice()); },
    remove: key => data.delete(key)
  };
  return { data, codec, store: new SecretChunks(backend, codec, () => 'generation-' + ++generation), fail: key => { failKey = key; } };
}

test('large credentials and host lists round-trip across encrypted asset limits', () => {
  const f = fixture(); const bytes = f.codec.encode('中文私钥'.repeat(1600));
  f.store.write('secret', bytes); assert.deepEqual(f.store.read('secret'), bytes);
  f.store.write('secret', f.codec.encode('small'));
  assert.equal(f.codec.decode(f.store.read('secret')), 'small');
  assert.equal(f.data.size, 2); // current manifest + current chunk, no previous generation
});

test('failed chunk or manifest write preserves the previous complete value', () => {
  for (const failure of ['generation-2.1', '.chunks.v1']) {
    const f = fixture(); f.store.write('secret', f.codec.encode('old')); f.fail(failure);
    assert.throws(() => f.store.write('secret', new Uint8Array(3000)), /asset failed/);
    assert.equal(f.codec.decode(f.store.read('secret')), 'old'); assert.equal(f.data.size, 2);
  }
});

test('migrates legacy credentials, removes all current secrets and fails closed on partial data', () => {
  const f = fixture(); f.data.set('secret', f.codec.encode('legacy'));
  assert.equal(f.codec.decode(f.store.read('secret')), 'legacy');
  f.store.write('secret', new Uint8Array(2500)); assert.equal(f.data.has('secret'), false);
  f.data.delete('secret.generation-1.1'); assert.throws(() => f.store.read('secret'), /不完整/);
  f.store.remove('secret'); assert.equal(f.store.read('secret'), undefined); assert.equal(f.data.size, 0);
});
