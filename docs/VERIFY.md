# Verification runbook (fresh Supabase project, all free)

Run these in order. Each lists the **expected** result. Anything else: copy the exact output to me.
Items below are *expected* results; none of them has been observed by me yet (no Postgres/Android/network in my environment).

## 0. Never touch the old project
Create a **new** Supabase project. Migration `0001` contains a guard: if the `public` schema already has any table it aborts with
`Refusing to run 0001_core.sql: the public schema already contains tables...` and changes nothing.

## 1. Database (Supabase → SQL Editor)
Run each file's full contents, in order: `0001_core.sql`, `0002_auth_helpers.sql`, `0003_social.sql`, `0004_social_hardening.sql`.
*(expected, each)* `Success. No rows returned`.
Settings: Authentication → Providers → Email → **Confirm email OFF**; minimum password length 8.

## 2. Offline logic tests (any machine with Node 22+)
```
cd backend && node --test
```
*(observed in my environment)* `# tests 30` `# pass 30` `# fail 0`.

## 3. Create two test users
```
SUPABASE_URL=https://xxx.supabase.co SUPABASE_ANON_KEY=<anon> EMAIL=alice@example.com PASSWORD=horizon-test-1 USERNAME=alice_t node tools/create-test-user.mjs
SUPABASE_URL=... EMAIL=bob@example.com PASSWORD=horizon-test-1 USERNAME=bob_t node tools/create-test-user.mjs
```
*(expected)* `OK: created and signed in. user id = <uuid>` for each. (Windows PowerShell: set variables with `$env:NAME="value"` first.)

## 4. Database security tests
SQL Editor → paste `supabase/tests/social_rls.sql` → Run. *(expected)* result row `ALL SOCIAL RLS TESTS PASSED`; nothing is kept (rolled back).
A failure shows `FAIL: <check>`.

## 5. Live Realtime protocol test
```
SUPABASE_URL=... SUPABASE_ANON_KEY=... EMAIL=alice@example.com PASSWORD=horizon-test-1 node tools/realtime-smoke.mjs
```
*(expected)*
```
PASS  sign in (GoTrue)
PASS  db channel joined (JWT accepted, postgres_changes configured)
PASS  presence channel joined
PASS  heartbeat acknowledged
PASS  presence_state received after join
PASS  own presence tracked and visible (key = user id)
ALL REALTIME CHECKS PASSED
```

## 6. Android unit tests and APK
See `docs/BUILD_APK.md` (Option A builds the APK and runs the 74 Kotlin tests for you).

## 7. Two-device functional test (the real proof)
| Do | Expect |
|---|---|
| Phone A signs in as alice, Phone B as bob | Both land on Friends (empty state) with the Profile tab showing a 14-digit ID |
| A → Discover → type `bob` | bob appears with **Add** |
| B → Discover → paste alice's 14-digit ID | alice appears with **Add** |
| A taps **Add** | Button becomes **Pending**; B's Requests tab shows a red dot within ~1–2 s |
| B → Requests → **Accept** | Both Friends lists show each other within ~1–2 s |
| Close the app on B | A shows bob under **Offline** within ~10–60 s |
| A → Friends → ⋮ → Remove | Gone from both lists |
| Turn airplane mode on/off on A | "Connecting…" banner, then it recovers without restarting the app |
