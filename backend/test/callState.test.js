import test from 'node:test';
import assert from 'node:assert/strict';
import { CallRegistry, RING_TIMEOUT_MS } from '../src/callState.js';

const mk = () => { let n = 0; return new CallRegistry({ idGen: () => `call-${++n}` }); };

test('invite creates a ringing call and marks both users busy', () => {
  const r = mk(); const { call } = r.invite('A', 'B', 'audio', 0);
  assert.equal(call.status, 'ringing');
  assert.ok(r.isBusy('A') && r.isBusy('B'));
});

test('cannot call self, or a busy user, or while busy', () => {
  const r = mk(); r.invite('A', 'B', 'audio', 0);
  assert.equal(r.invite('A', 'A', 'audio', 0).error, 'cannot_call_self');
  assert.equal(r.invite('C', 'B', 'audio', 0).error, 'callee_busy');
  assert.equal(r.invite('A', 'C', 'audio', 0).error, 'caller_busy');
});

test('only the callee can accept; call becomes active', () => {
  const r = mk(); const { call } = r.invite('A', 'B', 'video', 0);
  assert.equal(r.accept(call.id, 'A', 1).error, 'not_found');
  assert.equal(r.accept(call.id, 'B', 5).call.status, 'active');
  assert.equal(r.accept(call.id, 'B', 6).error, 'invalid_state');
});

test('decline / cancel free both users and only the right party may do it', () => {
  const r = mk(); let { call } = r.invite('A', 'B', 'audio', 0);
  assert.equal(r.decline(call.id, 'A').error, 'not_found');
  assert.equal(r.decline(call.id, 'B').call.status, 'declined');
  assert.ok(!r.isBusy('A') && !r.isBusy('B'));
  ({ call } = r.invite('A', 'B', 'audio', 0));
  assert.equal(r.cancel(call.id, 'B').error, 'not_found');
  assert.equal(r.cancel(call.id, 'A').call.status, 'cancelled');
});

test('end requires an active call and a participant', () => {
  const r = mk(); const { call } = r.invite('A', 'B', 'audio', 0);
  assert.equal(r.end(call.id, 'A', 1).error, 'invalid_state');
  r.accept(call.id, 'B', 2);
  assert.equal(r.end(call.id, 'X', 3).error, 'not_found');
  assert.equal(r.end(call.id, 'A', 3).call.status, 'ended');
  assert.ok(!r.isBusy('A'));
});

test('ringing calls expire to missed after the timeout, active calls do not', () => {
  const r = mk(); const { call } = r.invite('A', 'B', 'audio', 0);
  assert.equal(r.expireRinging(RING_TIMEOUT_MS - 1).length, 0);
  const expired = r.expireRinging(RING_TIMEOUT_MS);
  assert.equal(expired[0].status, 'missed');
  assert.ok(!r.isBusy('B'));
  const { call: c2 } = r.invite('A', 'B', 'audio', 0); r.accept(c2.id, 'B', 1);
  assert.equal(r.expireRinging(RING_TIMEOUT_MS * 10).length, 0);
});

test('dropUser ends the right status depending on role/state', () => {
  const r = mk(); let { call } = r.invite('A', 'B', 'audio', 0);
  assert.equal(r.dropUser('A', 1).status, 'cancelled');
  ({ call } = r.invite('A', 'B', 'audio', 0));
  assert.equal(r.dropUser('B', 1).status, 'declined');
  ({ call } = r.invite('A', 'B', 'audio', 0)); r.accept(call.id, 'B', 1);
  assert.equal(r.dropUser('A', 2).status, 'ended');
  assert.equal(r.dropUser('nobody', 2), null);
});

test('relayTarget only allows participants of live calls', () => {
  const r = mk(); const { call } = r.invite('A', 'B', 'audio', 0);
  assert.equal(r.relayTarget(call.id, 'A'), 'B');
  assert.equal(r.relayTarget(call.id, 'B'), 'A');
  assert.equal(r.relayTarget(call.id, 'EVIL'), null);
  assert.equal(r.relayTarget('nope', 'A'), null);
  r.cancel(call.id, 'A');
  assert.equal(r.relayTarget(call.id, 'A'), null);
});
