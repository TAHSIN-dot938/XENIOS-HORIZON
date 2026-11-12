# API reference

## Supabase (PostgREST / RPC) — authenticated users only, all enforced by RLS
| Call | Purpose |
|---|---|
| `rpc/search_users {p_query}` | By username prefix, or exact 14-digit ID. Returns relation: self/friend/outgoing/incoming/none (max 20) |
| `rpc/username_available {p_username}` | **anon allowed.** True if format-valid and unused (signup pre-check) |
| `rpc/send_friend_request {p_target}` | Serialized per user pair. Errors: `cannot_friend_self`, `user_not_found`, `already_friends`, `request_exists`, `too_many_requests` (100 pending). Auto-accepts if they already asked you |
| `rpc/respond_friend_request {p_id, p_accept}` | Addressee only; reject deletes the request |
| `rpc/cancel_friend_request {p_id}` | Requester only, pending only |
| `rpc/remove_friend {p_other}` | Accepted friends only; no-op for strangers |
| `rpc/open_direct_conversation {p_other}` | Friends only; returns conversation id (idempotent) |
| `rpc/mark_delivered / mark_read {p_conversation}` | Recipient-only receipts on direct chats |
| `rpc/register_push_token {p_token, p_platform}` / `unregister_push_token` | |
| `POST /messages` | Columns: conversation_id, client_id (UUID, idempotent), kind, body, media_path, media_duration_ms |
| `GET /friendships?select=…,requester:profiles!friendships_requester_id_fkey(…),addressee:profiles!friendships_addressee_id_fkey(…)` | Own rows + both profiles in one call |
| `GET /messages`, `/profiles`, `/friendships`, `/notifications`, `/calls` | Read per RLS |
| `PATCH /profiles`, `/notification_prefs`, `/notifications (read_at)` | Own rows, whitelisted columns |
| Global chat | conversation id `00000000-0000-0000-0000-000000000001` |
| Storage | `avatars/{userId}/…` (public read), `chat-media/{conversationId}/…` (members only) |

Realtime: Postgres Changes on `messages`, `notifications`, `friendships`; Broadcast/Presence channels for typing and online status (Phase 5).

## Backend
| Endpoint | Auth | |
|---|---|---|
| `GET /health` | none | `{ok, fcm}` |
| `GET /ice` | Bearer Supabase JWT | WebRTC ICE servers (TURN credentials only if configured) |
| `POST /webhooks/notifications` | `Authorization: WEBHOOK_SECRET` | Supabase DB webhook → FCM |

Socket.IO (`auth: {token: <JWT>}`): client→server `call:invite {calleeId, kind}`, `call:accept|decline|cancel|end {callId}`, `call:signal {callId, type?, sdp?, candidate?}`;
server→client `call:incoming`, `call:accepted`, `call:signal`, `call:ended {callId, status, reason}`. Ring timeout 45 s → missed call + notification.

## Supabase Auth (GoTrue) calls the app makes
`POST /auth/v1/signup` (data: username, display_name) · `POST /auth/v1/token?grant_type=password|refresh_token` · `POST /auth/v1/verify` (type signup|recovery, email + code) · `POST /auth/v1/recover` · `PUT /auth/v1/user` (new password) · `POST /auth/v1/logout`.
