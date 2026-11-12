# Zero-cost plan (budget: $0, no card required)

Rule: nothing here may require payment, a card, or a trial that converts to paid. Where free is not
reliable, the limitation is stated. Limits below are from my knowledge of providers' public free
tiers and **change over time — verify on each provider's pricing page before relying on them.**

| Need | Free solution | Honest limitation |
|---|---|---|
| Auth, database, realtime, storage | Supabase **Free** plan | ~500 MB DB, ~1 GB file storage, limited bandwidth/connections. **Pauses after ~7 days of inactivity** → `.github/workflows/keepalive.yml` pings it every 3 days (GitHub Actions is free) |
| Signup confirmation / password-reset email | Supabase built-in email is for testing only: tiny rate limit and, as far as I know, delivers only to your own team's addresses | **Zero-cost setup: turn email confirmation OFF.** Password reset needs real email delivery: connect a free SMTP account (e.g. a Gmail address with an app password, or Brevo's permanent free plan). Without it, "Forgot password" will not reach ordinary users |
| Push notifications | Firebase Cloud Messaging (free, unlimited) | Android may delay pushes for apps in aggressive battery-saver modes (common on Xiaomi/MIUI) |
| Sending pushes (server side) | Phase 6 decision: Supabase Edge Function (free tier) preferred over a hosted Node server | A Node host with no card required (e.g. Render free) sleeps when idle, which makes calls/pushes unreliable |
| Call signaling | Phase 7 plan: Supabase Realtime broadcast (no server to host) | — |
| Call media (WebRTC) | Free public STUN | **No reliable free TURN exists.** Calls between two devices behind strict NATs (some mobile carriers) can fail to connect; expect most but not all calls to work. Self-hosted coturn needs an always-on public server, which is not free |
| App distribution | Debug/release APK sideloaded | Google Play needs a one-time fee, so it is not part of this plan |

Nothing in the app calls any paid API. The only optional item that could cost money later is a TURN relay, and it is **not** enabled.
