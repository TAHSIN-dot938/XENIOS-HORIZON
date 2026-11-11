# Infrastructure costs (nothing paid is used without your approval)

| Service | Used for | Free tier | When it could cost money |
|---|---|---|---|
| Supabase | Auth, DB, Realtime, Storage | 500 MB DB, 1 GB storage, 50k MAU; project pauses after 1 week of inactivity | Pro plan ≈ $25/mo when you outgrow limits or need no-pause |
| Firebase Cloud Messaging | Push | Free, unlimited | — |
| Node host (Render/Fly/Railway) | Push + call signaling | Free tiers exist but sleep when idle | Always-on small instance ≈ $5–7/mo |
| STUN | Call connectivity | Google public STUN, free | — |
| **TURN relay** | Calls across strict NATs / mobile data | **None reliable** | Self-host coturn on a ≈ $4–6/mo VPS, or a managed TURN service (usage-priced). **Needs your approval before Phase 7.** Without TURN, many calls on mobile networks will fail to connect. |
