// Authoritative in-memory call state machine. Pure: no I/O, time is injected.
import { randomUUID } from 'node:crypto';

export const RING_TIMEOUT_MS = 45_000;

export class CallRegistry {
  constructor({ idGen = randomUUID } = {}) {
    this.calls = new Map();      // callId -> call
    this.byUser = new Map();     // userId -> callId (a user is in at most one call)
    this.idGen = idGen;
  }

  isBusy(userId) { return this.byUser.has(userId); }

  invite(callerId, calleeId, kind, now) {
    if (callerId === calleeId) return { error: 'cannot_call_self' };
    if (this.isBusy(callerId)) return { error: 'caller_busy' };
    if (this.isBusy(calleeId)) return { error: 'callee_busy' };
    const call = { id: this.idGen(), callerId, calleeId, kind, status: 'ringing', createdAt: now, answeredAt: null };
    this.calls.set(call.id, call);
    this.byUser.set(callerId, call.id);
    this.byUser.set(calleeId, call.id);
    return { call };
  }

  get(callId) { return this.calls.get(callId) ?? null; }
  _participant(call, userId) { return call && (call.callerId === userId || call.calleeId === userId); }
  peerOf(call, userId) { return call.callerId === userId ? call.calleeId : call.callerId; }

  accept(callId, userId, now) {
    const c = this.get(callId);
    if (!c || c.calleeId !== userId) return { error: 'not_found' };
    if (c.status !== 'ringing') return { error: 'invalid_state' };
    c.status = 'active'; c.answeredAt = now;
    return { call: c };
  }

  decline(callId, userId) {
    const c = this.get(callId);
    if (!c || c.calleeId !== userId) return { error: 'not_found' };
    if (c.status !== 'ringing') return { error: 'invalid_state' };
    return this._finish(c, 'declined', null);
  }

  cancel(callId, userId) {
    const c = this.get(callId);
    if (!c || c.callerId !== userId) return { error: 'not_found' };
    if (c.status !== 'ringing') return { error: 'invalid_state' };
    return this._finish(c, 'cancelled', null);
  }

  end(callId, userId, now) {
    const c = this.get(callId);
    if (!this._participant(c, userId)) return { error: 'not_found' };
    if (c.status !== 'active') return { error: 'invalid_state' };
    return this._finish(c, 'ended', now);
  }

  /** Ends every call a user is in (e.g. socket disconnect). */
  dropUser(userId, now) {
    const id = this.byUser.get(userId);
    if (!id) return null;
    const c = this.get(id);
    return this._finish(c, c.status === 'ringing' ? (c.callerId === userId ? 'cancelled' : 'declined') : 'ended', now).call;
  }

  /** Returns calls whose ringing time ran out; marks them missed. */
  expireRinging(now) {
    const out = [];
    for (const c of this.calls.values()) {
      if (c.status === 'ringing' && now - c.createdAt >= RING_TIMEOUT_MS) {
        out.push(this._finish(c, 'missed', null).call);
      }
    }
    return out;
  }

  /** Can `fromUser` relay WebRTC signaling for this call? Returns the peer id or null. */
  relayTarget(callId, fromUser) {
    const c = this.get(callId);
    if (!this._participant(c, fromUser)) return null;
    if (c.status !== 'ringing' && c.status !== 'active') return null;
    return this.peerOf(c, fromUser);
  }

  _finish(c, status, now) {
    c.status = status; c.endedAt = now;
    this.calls.delete(c.id);
    if (this.byUser.get(c.callerId) === c.id) this.byUser.delete(c.callerId);
    if (this.byUser.get(c.calleeId) === c.id) this.byUser.delete(c.calleeId);
    return { call: c };
  }
}
