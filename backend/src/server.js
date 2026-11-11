// THE HORIZON backend: FCM push dispatcher (DB webhook) + WebRTC call signaling (Socket.IO).
// Chat itself does NOT go through this server (Supabase Realtime handles it).
import { createServer } from 'node:http';
import { timingSafeEqual } from 'node:crypto';
import express from 'express';
import { Server } from 'socket.io';
import { createClient } from '@supabase/supabase-js';
import admin from 'firebase-admin';
import { CallRegistry } from './callState.js';
import { buildIceServers } from './iceConfig.js';
import { validateInvite, validateSignal, validateCallId } from './validation.js';
import { shouldPush, buildNotificationMessage, buildIncomingCallMessage, isDeadTokenError } from './push.js';

const env = process.env;
for (const k of ['SUPABASE_URL', 'SUPABASE_SERVICE_ROLE_KEY', 'WEBHOOK_SECRET']) {
  if (!env[k]) { console.error(`Missing required env var ${k}`); process.exit(1); }
}

const db = createClient(env.SUPABASE_URL, env.SUPABASE_SERVICE_ROLE_KEY, { auth: { persistSession: false } });

let messaging = null;
if (env.FIREBASE_SERVICE_ACCOUNT_B64) {
  const cred = JSON.parse(Buffer.from(env.FIREBASE_SERVICE_ACCOUNT_B64, 'base64').toString('utf8'));
  admin.initializeApp({ credential: admin.credential.cert(cred) });
  messaging = admin.messaging();
} else {
  console.warn('FIREBASE_SERVICE_ACCOUNT_B64 not set: push notifications are DISABLED.');
}

async function userFromToken(token) {
  if (!token || typeof token !== 'string') return null;
  const { data, error } = await db.auth.getUser(token);
  return error ? null : data.user;
}

async function sendPush(userId, message) {
  if (!messaging) return { sent: 0, skipped: 'fcm_disabled' };
  const { data: rows } = await db.from('push_tokens').select('fcm_token').eq('user_id', userId);
  const tokens = (rows ?? []).map((r) => r.fcm_token);
  if (!tokens.length) return { sent: 0, skipped: 'no_tokens' };
  const res = await messaging.sendEachForMulticast({ tokens, ...message });
  const dead = [];
  res.responses.forEach((r, i) => { if (!r.success && isDeadTokenError(r.error?.code)) dead.push(tokens[i]); });
  if (dead.length) await db.from('push_tokens').delete().in('fcm_token', dead);
  return { sent: res.successCount, failed: res.failureCount };
}

const profileOf = async (id) =>
  (await db.from('profiles').select('id,username,display_name').eq('id', id).maybeSingle()).data;

// ───────────── HTTP ─────────────
const app = express();
app.disable('x-powered-by');
app.use(express.json({ limit: '100kb' }));

app.get('/health', (_req, res) => res.json({ ok: true, fcm: Boolean(messaging) }));

app.get('/ice', async (req, res) => {
  const user = await userFromToken((req.headers.authorization ?? '').replace(/^Bearer /, ''));
  if (!user) return res.status(401).json({ error: 'unauthorized' });
  res.json(buildIceServers({ turnUrl: env.TURN_URL, turnSecret: env.TURN_SECRET, userId: user.id, nowSec: Math.floor(Date.now() / 1000) }));
});

function secretOk(header) {
  const a = Buffer.from(String(header ?? '')), b = Buffer.from(env.WEBHOOK_SECRET);
  return a.length === b.length && timingSafeEqual(a, b);
}

// Supabase Database Webhook: INSERT on public.notifications
app.post('/webhooks/notifications', async (req, res) => {
  if (!secretOk(req.headers.authorization)) return res.status(401).json({ error: 'unauthorized' });
  const { type, table, record } = req.body ?? {};
  if (type !== 'INSERT' || table !== 'notifications' || !record?.user_id) return res.status(400).json({ error: 'bad_payload' });
  try {
    const [{ data: prefs }, actor] = await Promise.all([
      db.from('notification_prefs').select('*').eq('user_id', record.user_id).maybeSingle(),
      record.actor_id ? profileOf(record.actor_id) : null,
    ]);
    if (!shouldPush(record.kind, prefs)) return res.json({ skipped: 'prefs' });
    res.json(await sendPush(record.user_id, buildNotificationMessage(record, actor, prefs)));
  } catch (e) {
    console.error('webhook failed', e);
    res.status(500).json({ error: 'internal' });
  }
});

// ───────────── Socket.IO call signaling ─────────────
const httpServer = createServer(app);
const io = new Server(httpServer, { cors: { origin: false }, maxHttpBufferSize: 64 * 1024 });
const calls = new CallRegistry();
const room = (uid) => `user:${uid}`;
const isOnline = (uid) => (io.sockets.adapter.rooms.get(room(uid))?.size ?? 0) > 0;

io.use(async (socket, next) => {
  const user = await userFromToken(socket.handshake.auth?.token);
  if (!user) return next(new Error('unauthorized'));
  socket.data.userId = user.id;
  next();
});

async function persistEnd(call) {
  await db.from('calls').update({
    status: call.status, answered_at: call.answeredAt ? new Date(call.answeredAt).toISOString() : null,
    ended_at: new Date().toISOString(),
  }).eq('id', call.id);
  if (call.status === 'missed') {
    await db.from('notifications').insert({ user_id: call.calleeId, kind: 'call_missed', actor_id: call.callerId, payload: { call_id: call.id } });
  }
}

function notifyEnded(call, reason) {
  io.to(room(call.callerId)).to(room(call.calleeId)).emit('call:ended', { callId: call.id, status: call.status, reason });
}

io.on('connection', (socket) => {
  const me = socket.data.userId;
  socket.join(room(me));
  const ack = (cb, payload) => typeof cb === 'function' && cb(payload);

  socket.on('call:invite', async (p, cb) => {
    const err = validateInvite(p); if (err) return ack(cb, { error: err });
    const { data: fr } = await db.from('friendships').select('id')
      .eq('status', 'accepted')
      .or(`and(requester_id.eq.${me},addressee_id.eq.${p.calleeId}),and(requester_id.eq.${p.calleeId},addressee_id.eq.${me})`)
      .maybeSingle();
    if (!fr) return ack(cb, { error: 'not_friends' });
    const r = calls.invite(me, p.calleeId, p.kind, Date.now());
    if (r.error) return ack(cb, { error: r.error });
    const call = r.call;
    await db.from('calls').insert({ id: call.id, caller_id: me, callee_id: p.calleeId, kind: p.kind });
    const caller = await profileOf(me);
    io.to(room(p.calleeId)).emit('call:incoming', { callId: call.id, kind: call.kind, caller });
    if (!isOnline(p.calleeId)) sendPush(p.calleeId, buildIncomingCallMessage(call, caller)).catch((e) => console.error('call push failed', e));
    ack(cb, { callId: call.id });
  });

  socket.on('call:accept', async (p, cb) => {
    const err = validateCallId(p); if (err) return ack(cb, { error: err });
    const r = calls.accept(p.callId, me, Date.now()); if (r.error) return ack(cb, { error: r.error });
    await db.from('calls').update({ status: 'active', answered_at: new Date().toISOString() }).eq('id', p.callId);
    io.to(room(r.call.callerId)).emit('call:accepted', { callId: p.callId });
    ack(cb, { ok: true });
  });

  const finisher = (event, fn) => socket.on(event, async (p, cb) => {
    const err = validateCallId(p); if (err) return ack(cb, { error: err });
    const r = fn(p.callId); if (r.error) return ack(cb, { error: r.error });
    await persistEnd(r.call); notifyEnded(r.call, event); ack(cb, { ok: true });
  });
  finisher('call:decline', (id) => calls.decline(id, me));
  finisher('call:cancel', (id) => calls.cancel(id, me));
  finisher('call:end', (id) => calls.end(id, me, Date.now()));

  socket.on('call:signal', (p) => {
    if (validateSignal(p)) return;
    const peer = calls.relayTarget(p.callId, me);
    if (peer) io.to(room(peer)).emit('call:signal', { callId: p.callId, type: p.type, sdp: p.sdp, candidate: p.candidate });
  });

  socket.on('disconnect', async () => {
    if (isOnline(me)) return;                       // user still has another socket
    const call = calls.dropUser(me, Date.now());
    if (call) { await persistEnd(call); notifyEnded(call, 'disconnect'); }
  });
});

setInterval(async () => {
  for (const call of calls.expireRinging(Date.now())) { await persistEnd(call); notifyEnded(call, 'timeout'); }
}, 5000).unref();

const port = Number(env.PORT ?? 3000);
httpServer.listen(port, () => console.log(`horizon-backend listening on :${port}`));
