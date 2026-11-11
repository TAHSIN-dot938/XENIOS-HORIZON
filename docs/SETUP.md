# Setup

## 1. Supabase (NEW project — do not reuse the old production project)
1. Create a project at supabase.com (free tier is enough to start).
2. SQL Editor → run `supabase/migrations/0001_core.sql`. Review it first; it has not been run against a real database yet.
3. Auth → Providers: keep Email enabled. Decide whether to require email confirmation (recommended on; the app handles both from Phase 3).
4. Database → Webhooks → create: table `public.notifications`, event INSERT, HTTP POST to
   `https://<your-backend>/webhooks/notifications`, header `Authorization: <WEBHOOK_SECRET>`.
5. Settings → API: copy the project URL and **anon** key (app) and the **service_role** key (backend only).

## 2. Backend
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
