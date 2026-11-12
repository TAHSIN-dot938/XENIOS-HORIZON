import test from 'node:test';
import assert from 'node:assert/strict';
import {
  encode, decode, joinPayload, accessTokenPayload, presenceTrackPayload, PresenceTracker,
  DB_TOPIC, PRESENCE_TOPIC,
} from '../../tools/protocol.mjs';

test('encode/decode round trip, ref is a string', () => {
  const text = encode('realtime:x', 'phx_join', { a: 1 }, '7');
  assert.deepEqual(JSON.parse(text), { topic: 'realtime:x', event: 'phx_join', payload: { a: 1 }, ref: '7' });
  assert.deepEqual(decode(text), { topic: 'realtime:x', event: 'phx_join', payload: { a: 1 }, ref: '7' });
});

test('decode tolerates null ref / missing payload and rejects junk', () => {
  assert.equal(decode('{"topic":"t","event":"e","payload":{},"ref":null}').ref, null);
  assert.deepEqual(decode('{"topic":"t","event":"e"}').payload, {});
  assert.equal(decode('not json'), null);
  assert.equal(decode('{"event":"e"}'), null);
});

test('db join payload: postgres_changes for each table, presence key, jwt, not private', () => {
  const p = joinPayload(['friendships'], 'user-1', 'jwt');
  assert.equal(p.access_token, 'jwt');
  assert.deepEqual(p.config.postgres_changes, [{ event: '*', schema: 'public', table: 'friendships' }]);
  assert.equal(p.config.presence.key, 'user-1');
  assert.equal(p.config.private, false);
  assert.deepEqual(p.config.broadcast, { ack: false, self: false });
});

test('presence join payload has no postgres_changes entries', () => {
  assert.deepEqual(joinPayload([], 'u', 't').config.postgres_changes, []);
});

test('the two channels use different topics, both namespaced realtime:', () => {
  assert.notEqual(DB_TOPIC, PRESENCE_TOPIC);
  assert.ok(DB_TOPIC.startsWith('realtime:') && PRESENCE_TOPIC.startsWith('realtime:'));
});

test('track and access_token payload shapes', () => {
  assert.deepEqual(presenceTrackPayload('2026-01-01T00:00:00Z'), { type: 'presence', event: 'track', payload: { online_at: '2026-01-01T00:00:00Z' } });
  assert.deepEqual(accessTokenPayload('t'), { access_token: 't' });
});

const metas = (...refs) => ({ metas: refs.map((r) => ({ phx_ref: r })) });

test('presence_state sets who is online, and replaces earlier state', () => {
  const t = new PresenceTracker();
  t.setState({ a: metas('1'), b: metas('2') });
  assert.deepEqual([...t.online()].sort(), ['a', 'b']);
  t.setState({ c: metas('3') });
  assert.deepEqual([...t.online()], ['c']);
});

test('joins and leaves update presence', () => {
  const t = new PresenceTracker();
  t.setState({ a: metas('1') });
  t.applyDiff({ joins: { b: metas('2') }, leaves: {} });
  assert.deepEqual([...t.online()].sort(), ['a', 'b']);
  t.applyDiff({ joins: {}, leaves: { a: metas('1') } });
  assert.deepEqual([...t.online()], ['b']);
});

test('multi-device: user stays online until the LAST session leaves', () => {
  const t = new PresenceTracker();
  t.setState({ a: metas('phone', 'tablet') });
  t.applyDiff({ joins: {}, leaves: { a: metas('phone') } });
  assert.ok(t.online().has('a'));
  t.applyDiff({ joins: {}, leaves: { a: metas('tablet') } });
  assert.ok(!t.online().has('a'));
});

test('leave for unknown user is ignored; leave+join in one diff (reconnect) keeps user online', () => {
  const t = new PresenceTracker();
  t.applyDiff({ joins: {}, leaves: { ghost: metas('9') } });
  assert.equal(t.online().size, 0);
  t.setState({ a: metas('old') });
  t.applyDiff({ joins: { a: metas('new') }, leaves: { a: metas('old') } });
  assert.ok(t.online().has('a'));
});
