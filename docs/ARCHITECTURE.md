# THE HORIZON — Architecture

Package: `com.tahsin.thehorizon` · Android (Kotlin, Jetpack Compose) · minSdk 26

## Principle: use Supabase for what it does well, Node only where needed

| Concern | Technology | Why |
|---|---|---|
| Auth | Supabase Auth (email/password) | No custom auth code to secure |
| Data + security | Postgres + Row Level Security + RPCs | Rules enforced in the DB, not the client |
| Messages, receipts, notification feed | Supabase Realtime (Postgres Changes) | No extra server needed |
| Typing + online status | Supabase Realtime Broadcast / Presence | Ephemeral, never stored |
| Images, voice, avatars | Supabase Storage | Bucket policies per conversation |
| Push (FCM) | Node backend fed by a Database Webhook | Needs the Firebase service account (server-only secret) |
| Call signaling | Node + Socket.IO (JWT-authenticated) | Authoritative call state; `calls` is server-written |
| Media transport | WebRTC (STUN free; TURN needs your approval, see COSTS.md) | |

Socket.IO is used **only** for call signaling. Chat does not depend on the Node server,
so a sleeping free-tier host cannot break messaging.

## Message reliability
1. Client generates `client_id` (UUID) and inserts; `unique(sender_id, client_id)` makes retries idempotent.
2. UI shows *sending* until the insert returns, then *sent*.
3. Recipient app calls `mark_delivered` on receipt and `mark_read` when the chat is open.
4. On every (re)connect the client re-queries messages newer than its last seen `created_at`. Realtime is a hint; the query is the truth.

## Security model
- RLS on every table; clients get only the grants listed in the migration.
- Friendships, conversations and push tokens are written only via `security definer` RPCs.
- Direct chats require an accepted friendship; the global chat is open to authenticated users.
- Backend verifies the Supabase JWT on every socket and webhook call; the service-role key never ships in the app.
- Android holds only the Supabase URL + anon key (public by design), injected from `local.properties`.

## Android layers
`ui/` (Compose screens, design system) → `domain/` (models) → `data/` (Supabase, backend, DataStore).
Single Activity, Navigation-Compose, ViewModel + StateFlow, manual DI container (smaller APK, faster cold start on budget devices).

## Repo layout
`android/` app · `backend/` Node server · `supabase/migrations/` SQL · `docs/` documentation

## Realtime design (Phase 4)
One OkHttp WebSocket to `wss://<project>.supabase.co/realtime/v1/websocket?apikey=<anon>&vsn=1.0.0`, two Phoenix channels:

| Channel | Purpose | Join payload |
|---|---|---|
| `realtime:horizon-db` | Postgres Changes on `public.friendships` (RLS-filtered by the user's JWT) | `config.postgres_changes=[{event:"*",schema:"public",table:"friendships"}]`, `access_token` |
| `realtime:horizon-presence` | Shared presence; `config.presence.key = userId` (multi-device collapses to one user) | same, no tables; after a successful join the client sends `presence/track` unless the user hid their status |

Frames: `{topic,event,payload,ref}`. Heartbeat every 25 s on topic `phoenix`. `access_token` events re-authenticate both channels every 10 min
using a token guaranteed valid for ≥15 min. Join rejection or `phx_close/phx_error` closes the socket and the reconnect loop takes over.

**Lifecycle / battery:** socket exists only while the app is visible (`ON_START`…`ON_STOP` + 8 s grace so rotation/app-switch don't churn).
While the device has no network the loop *sleeps* (no timers firing, no retries); when `ConnectivityManager` reports a network it reconnects
immediately; otherwise backoff 1→30 s with 25% jitter. Every (re)connect re-queries friendships, so missed events never matter ("events are hints").
