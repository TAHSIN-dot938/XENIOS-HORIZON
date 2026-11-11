import test from 'node:test';
import assert from 'node:assert/strict';
import { createHmac } from 'node:crypto';
import { buildIceServers } from '../src/iceConfig.js';

test('STUN only when TURN is not configured', () => {
  const r = buildIceServers({ userId: 'u', nowSec: 1000 });
  assert.equal(r.iceServers.length, 1); assert.equal(r.turnConfigured, false);
});

test('TURN credentials are coturn-compatible and expire', () => {
  const r = buildIceServers({ turnUrl: 'turn:t.example.com:3478, turns:t.example.com:5349', turnSecret: 's3', userId: 'u1', nowSec: 1000, ttlSec: 60 });
  const turn = r.iceServers[1];
  assert.equal(turn.username, '1060:u1');
  assert.equal(turn.credential, createHmac('sha1', 's3').update('1060:u1').digest('base64'));
  assert.deepEqual(turn.urls, ['turn:t.example.com:3478', 'turns:t.example.com:5349']);
});
