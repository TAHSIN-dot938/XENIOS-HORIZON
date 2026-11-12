// JavaScript mirror of android/.../data/realtime/PhoenixProtocol.kt (Supabase Realtime, vsn 1.0.0).
// Kept line-for-line equivalent so the protocol can be (a) unit-tested here and (b) exercised against
// a real Supabase project with tools/realtime-smoke.mjs. If you change one, change the other.

export const DB_TOPIC = 'realtime:horizon-db';
export const PRESENCE_TOPIC = 'realtime:horizon-presence';
export const HEARTBEAT_TOPIC = 'phoenix';

export const encode = (topic, event, payload, ref) => JSON.stringify({ topic, event, payload, ref });

export function decode(text) {
  try {
    const o = JSON.parse(text);
    if (typeof o.topic !== 'string' || typeof o.event !== 'string') return null;
    return { topic: o.topic, event: o.event, payload: o.payload ?? {}, ref: o.ref == null ? null : String(o.ref) };
  } catch { return null; }
}

export function joinPayload(tables, presenceKey, accessToken) {
  return {
    config: {
      broadcast: { ack: false, self: false },
      presence: { key: presenceKey },
      postgres_changes: tables.map((table) => ({ event: '*', schema: 'public', table })),
      private: false,
    },
    access_token: accessToken,
  };
}

export const accessTokenPayload = (token) => ({ access_token: token });
export const presenceTrackPayload = (onlineAtIso) => ({ type: 'presence', event: 'track', payload: { online_at: onlineAtIso } });

/** A user stays online while at least one session (meta phx_ref) remains. */
export class PresenceTracker {
  constructor() { this.refsByKey = new Map(); }
  #refsOf(entry) { return new Set((entry?.metas ?? []).map((m) => m.phx_ref).filter(Boolean)); }
  online() { return new Set([...this.refsByKey].filter(([, refs]) => refs.size > 0).map(([k]) => k)); }
  setState(state) {
    this.refsByKey.clear();
    for (const key of Object.keys(state ?? {})) this.refsByKey.set(key, this.#refsOf(state[key]));
  }
  applyDiff(diff) {
    for (const key of Object.keys(diff?.leaves ?? {})) {
      const refs = this.refsByKey.get(key);
      if (!refs) continue;
      for (const r of this.#refsOf(diff.leaves[key])) refs.delete(r);
      if (refs.size === 0) this.refsByKey.delete(key);
    }
    for (const key of Object.keys(diff?.joins ?? {})) {
      const refs = this.refsByKey.get(key) ?? new Set();
      for (const r of this.#refsOf(diff.joins[key])) refs.add(r);
      this.refsByKey.set(key, refs);
    }
  }
  clear() { this.refsByKey.clear(); }
}
