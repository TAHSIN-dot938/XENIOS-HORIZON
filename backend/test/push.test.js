import test from 'node:test';
import assert from 'node:assert/strict';
import { shouldPush, buildNotificationMessage, buildIncomingCallMessage, isDeadTokenError } from '../src/push.js';

test('shouldPush honours per-kind preferences', () => {
  assert.ok(shouldPush('message', { messages: true }));
  assert.ok(!shouldPush('message', { messages: false }));
  assert.ok(!shouldPush('friend_request', { friend_requests: false }));
  assert.ok(!shouldPush('friend_accepted', { friend_requests: false }));
  assert.ok(!shouldPush('call_missed', { calls: false }));
  assert.ok(shouldPush('message', undefined));            // no prefs row yet -> default on
  assert.ok(!shouldPush('unknown_kind', {}));
});

test('message notification payload', () => {
  const m = buildNotificationMessage(
    { id: 'n1', kind: 'message', actor_id: 'u2', payload: { conversation_id: 'c1', preview: 'hey' } },
    { display_name: 'Ava', username: 'ava' }, { sound: 'horizon_chime', vibrate: false });
  assert.equal(m.data.title, 'Ava'); assert.equal(m.data.body, 'hey');
  assert.equal(m.data.conversation_id, 'c1'); assert.equal(m.data.vibrate, 'false');
  assert.equal(m.android.priority, 'high');
  for (const v of Object.values(m.data)) assert.equal(typeof v, 'string');   // FCM data must be strings
});

test('long previews are truncated; missing actor falls back', () => {
  const m = buildNotificationMessage({ id: 1, kind: 'message', payload: { preview: 'x'.repeat(500) } }, null, null);
  assert.equal(m.data.body.length, 120); assert.equal(m.data.title, 'Someone');
});

test('friend request text', () => {
  const m = buildNotificationMessage({ id: 1, kind: 'friend_request', actor_id: 'u', payload: {} }, { username: 'bo' }, null);
  assert.equal(m.data.body, 'bo wants to be your friend');
});

test('incoming call push is high priority with short TTL', () => {
  const m = buildIncomingCallMessage({ id: 'c', kind: 'video', callerId: 'u' }, { display_name: 'Cy' });
  assert.equal(m.data.type, 'call_incoming'); assert.equal(m.data.caller_name, 'Cy'); assert.equal(m.android.ttl, 45000);
});

test('dead token detection', () => {
  assert.ok(isDeadTokenError('messaging/registration-token-not-registered'));
  assert.ok(!isDeadTokenError('messaging/internal-error'));
});
