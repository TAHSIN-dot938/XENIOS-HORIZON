#!/usr/bin/env node
// Verifies, against YOUR real Supabase project, that the app's Realtime protocol works:
// auth, channel joins (db + presence), heartbeat, presence tracking/sync, optional DB-change events.
// Free to run. Requires Node 22+ (built-in WebSocket/fetch). Usage:
//   SUPABASE_URL=https://xxx.supabase.co SUPABASE_ANON_KEY=... EMAIL=a@b.co PASSWORD=... \
//     node tools/realtime-smoke.mjs
// Optional: TARGET_USER_ID=<uuid>  -> also sends a friend request and expects a postgres_changes event
//           (this creates a real pending request; cancel it afterwards in the app).
import { encode, decode, joinPayload, presenceTrackPayload, PresenceTracker, DB_TOPIC, PRESENCE_TOPIC, HEARTBEAT_TOPIC } from './protocol.mjs';

const { SUPABASE_URL, SUPABASE_ANON_KEY, EMAIL, PASSWORD, TARGET_USER_ID } = process.env;
if (!SUPABASE_URL || !SUPABASE_ANON_KEY || !EMAIL || !PASSWORD) {
  console.error('Set SUPABASE_URL, SUPABASE_ANON_KEY, EMAIL, PASSWORD'); process.exit(2);
}
const results = [];
const check = (name, ok, hint = '') => { results.push({ name, ok }); console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${ok ? '' : '  -> ' + hint}`); };

const login = await fetch(`${SUPABASE_URL}/auth/v1/token?grant_type=password`, {
  method: 'POST', headers: { apikey: SUPABASE_ANON_KEY, 'Content-Type': 'application/json' },
  body: JSON.stringify({ email: EMAIL, password: PASSWORD }),
});
const session = await login.json();
check('sign in (GoTrue)', login.ok && !!session.access_token, JSON.stringify(session));
if (!login.ok) process.exit(1);
const token = session.access_token, me = session.user.id;

const wsUrl = SUPABASE_URL.replace(/^http/, 'ws') + `/realtime/v1/websocket?apikey=${SUPABASE_ANON_KEY}&vsn=1.0.0`;
const ws = new WebSocket(wsUrl);
const tracker = new PresenceTracker();
let ref = 2;
const state = { dbJoined: false, presenceJoined: false, gotPresenceState: false, selfOnline: false, heartbeatOk: false, change: false };
const send = (topic, event, payload) => ws.send(encode(topic, event, payload, String(++ref)));

ws.onopen = () => {
  ws.send(encode(DB_TOPIC, 'phx_join', joinPayload(['friendships'], me, token), '1'));
  ws.send(encode(PRESENCE_TOPIC, 'phx_join', joinPayload([], me, token), '2'));
  setTimeout(() => send(HEARTBEAT_TOPIC, 'heartbeat', {}), 1000);
};
ws.onmessage = (m) => {
  const f = decode(String(m.data));
  if (!f) return;
  const ok = f.payload?.status === 'ok';
  if (f.topic === DB_TOPIC && f.event === 'phx_reply' && f.ref === '1') { state.dbJoined = ok; if (!ok) console.log('db join reply:', JSON.stringify(f.payload)); }
  if (f.topic === PRESENCE_TOPIC && f.event === 'phx_reply' && f.ref === '2') {
    state.presenceJoined = ok;
    if (ok) send(PRESENCE_TOPIC, 'presence', presenceTrackPayload(new Date().toISOString()));
    else console.log('presence join reply:', JSON.stringify(f.payload));
  }
  if (f.topic === HEARTBEAT_TOPIC && f.event === 'phx_reply' && f.payload?.status === 'ok') state.heartbeatOk = true;
  if (f.topic === PRESENCE_TOPIC && f.event === 'presence_state') { state.gotPresenceState = true; tracker.setState(f.payload); }
  if (f.topic === PRESENCE_TOPIC && f.event === 'presence_diff') tracker.applyDiff(f.payload);
  if (f.topic === DB_TOPIC && f.event === 'postgres_changes') { state.change = true; console.log('postgres_changes:', JSON.stringify(f.payload?.data?.table), f.payload?.data?.type); }
  if (tracker.online().has(me)) state.selfOnline = true;
};
ws.onerror = (e) => console.log('socket error', e.message ?? e);

await new Promise((r) => setTimeout(r, 6000));
check('db channel joined (JWT accepted, postgres_changes configured)', state.dbJoined, 'join rejected: check token / that friendships is in the supabase_realtime publication (migration 0001)');
check('presence channel joined', state.presenceJoined, 'join rejected');
check('heartbeat acknowledged', state.heartbeatOk, 'no phx_reply to heartbeat');
check('presence_state received after join', state.gotPresenceState, 'server did not send presence_state');
check('own presence tracked and visible (key = user id)', state.selfOnline, 'track payload or presence key mismatch');

if (TARGET_USER_ID) {
  const r = await fetch(`${SUPABASE_URL}/rest/v1/rpc/send_friend_request`, {
    method: 'POST', headers: { apikey: SUPABASE_ANON_KEY, Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    body: JSON.stringify({ p_target: TARGET_USER_ID }),
  });
  console.log('send_friend_request ->', r.status, await r.text());
  await new Promise((res) => setTimeout(res, 4000));
  check('postgres_changes event delivered for friendships', state.change, 'no change event: RLS/publication/replication problem');
}
ws.close();
const failed = results.filter((r) => !r.ok).length;
console.log(failed === 0 ? '\nALL REALTIME CHECKS PASSED' : `\n${failed} CHECK(S) FAILED`);
process.exit(failed === 0 ? 0 : 1);
