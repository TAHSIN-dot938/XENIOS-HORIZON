# Setup

## 1. Supabase (NEW project — do not reuse the old production project)
1. Create a project at supabase.com (free tier is enough to start).
2. SQL Editor → run, in order, `supabase/migrations/0001_core.sql`, `0002_auth_helpers.sql`, `0003_social.sql`, `0004_social_hardening.sql`. Review them first; they have not been run against a real database yet.
3. Authentication settings (all free):
   - Providers → Email: enabled. **Turn OFF "Confirm email"** (zero-cost setup; the app also supports the code flow if you leave it on and configure SMTP).
   - Minimum password length: 8.
   - **Password reset by emailed code** (the app has no web link flow): Authentication → Email Templates → *Reset Password*, make the body contain the code, e.g. `<p>Your Horizon reset code: <b>{{ .Token }}</b></p>`. Do the same in *Confirm signup* if you keep confirmation on.
   - Email delivery: Supabase's built-in sender only suits testing. For real users, set Authentication → SMTP Settings to a free SMTP account (e.g. Gmail + app password). See `docs/COSTS.md`.
4. (Phase 6) Database → Webhooks → create: table `public.notifications`, event INSERT, HTTP POST to
   `https://<your-backend>/webhooks/notifications`, header `Authorization: <WEBHOOK_SECRET>`.
5. Settings → API: copy the project URL and **anon** key (app) and the **service_role** key (backend only).

## 1b. Keep the free project awake (optional, free)
Push this repo to GitHub, add secrets `SUPABASE_URL` and `SUPABASE_ANON_KEY` (Settings → Secrets → Actions). The workflow in `.github/workflows/keepalive.yml` then prevents the 7-day inactivity pause.

## 2. Backend (needed only from Phase 6/7, and may be replaced by free serverless functions)
```
cd backend && cp .env.example .env   # fill in values
npm install && npm test && npm start
```
Free hosting options (Render, Fly.io, Railway free tiers) can sleep when idle; chat does not depend on this server, but incoming-call push and call signaling do.

## 3. Firebase (free)
Create a Firebase project → add Android app `com.tahsin.thehorizon` → download `google-services.json` into `android/app/` (git-ignored) → Project settings → Service accounts → generate a key → `base64 -w0 key.json` into `FIREBASE_SERVICE_ACCOUNT_B64`.
The FCM plugin and code are added in Phase 6.

## 4. Android
1. Install Android Studio (Ladybug or newer). Open the `android/` folder; let it sync (it generates the Gradle wrapper).
2. `cp local.properties.example local.properties` and fill in `SUPABASE_URL`, `SUPABASE_ANON_KEY`, `BACKEND_URL`.
3. Run on a device/emulator, or `./gradlew assembleDebug` → `app/build/outputs/apk/debug/`.
4. Unit tests: `./gradlew test`.

## Verify it works (all free; this is how "Phase 4 complete" gets confirmed)
1. **Database security:** SQL Editor → paste `supabase/tests/social_rls.sql` → Run. Expect the final row `ALL SOCIAL RLS TESTS PASSED`
   (it rolls back; no data is kept). Any failure prints `FAIL: <what>` — send it to me.
2. **Realtime protocol against your real project** (needs Node 22+ and an existing test account):
   `SUPABASE_URL=… SUPABASE_ANON_KEY=… EMAIL=… PASSWORD=… node tools/realtime-smoke.mjs` → expect `ALL REALTIME CHECKS PASSED`.
   Add `TARGET_USER_ID=<another user's uuid>` to also verify friendship change events (creates one pending request; cancel it in the app).
3. **Offline logic tests:** `cd backend && node --test` (30 tests) and, in Android Studio, `./gradlew test`.
4. **Two-device test:** sign up two accounts on two phones → search by username on one, by 14-digit ID on the other → send/accept →
   both appear in Friends; kill the app on one phone and watch the other flip it to Offline within ~10–60 s.
