import test from 'node:test';
import assert from 'node:assert/strict';
import { validateInvite, validateSignal, validateCallId, isUuid } from '../src/validation.js';

const U = '123e4567-e89b-42d3-a456-426614174000';

test('isUuid', () => { assert.ok(isUuid(U)); assert.ok(!isUuid('nope')); assert.ok(!isUuid(5)); });

test('validateInvite', () => {
  assert.equal(validateInvite({ calleeId: U, kind: 'audio' }), null);
  assert.equal(validateInvite({ calleeId: U, kind: 'video' }), null);
  assert.equal(validateInvite({ calleeId: U, kind: 'screen' }), 'invalid_kind');
  assert.equal(validateInvite({ calleeId: 'x', kind: 'audio' }), 'invalid_callee');
  assert.equal(validateInvite(null), 'invalid_payload');
});

test('validateSignal accepts sdp and candidates, rejects junk and oversize', () => {
  assert.equal(validateSignal({ callId: U, type: 'offer', sdp: 'v=0' }), null);
  assert.equal(validateSignal({ callId: U, candidate: 'candidate:1 1 udp ...' }), null);
  assert.equal(validateSignal({ callId: U, type: 'hax', sdp: 'v=0' }), 'invalid_type');
  assert.equal(validateSignal({ callId: U, type: 'offer', sdp: 'x'.repeat(20_001) }), 'invalid_sdp');
  assert.equal(validateSignal({ callId: U }), 'empty_signal');
  assert.equal(validateSignal({ callId: 'bad', sdp: 'v=0', type: 'offer' }), 'invalid_call');
});

test('validateCallId', () => { assert.equal(validateCallId({ callId: U }), null); assert.equal(validateCallId({}), 'invalid_call'); });
