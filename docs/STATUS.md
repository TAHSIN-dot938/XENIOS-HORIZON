# Status (honest)

## BUILD STATUS: APK NOT BUILT — environment blocker (not a code failure)
Probe run in my workspace (`java` 21 only): no Android SDK (`ANDROID_HOME` empty, no `android.jar`/`aapt2`/`d8`/`apksigner`),
no Gradle, no Kotlin compiler, no Gradle/Maven caches, and every outbound connection fails (dl.google.com, maven.google.com,
repo.maven.apache.org, services.gradle.org, github.com all return no response). Android Gradle builds need all of these, so
**an APK cannot be produced here, and none is claimed.** Zero-cost fix: `.github/workflows/android-build.yml` builds it on GitHub's free
runners (see `docs/BUILD_APK.md`). Nothing about the app code has been observed compiling yet.

Legend: ✅ verified here by execution · 🟡 written, NOT executed (needs your Android Studio / Supabase) · ⬜ not started

## Verification environment limits
My cloud environment has **no Android SDK, Kotlin compiler, Postgres or network**. So Kotlin and SQL can only be statically
checked here (balanced syntax, every project import resolves, no used-but-unimported project symbols). Anything marked 🟡
must be confirmed by you with the steps in `docs/SETUP.md` ("Verify it works").

| Area | State | Evidence |
|---|---|---|
| Backend logic: call state machine, validation, push rules, TURN creds | ✅ | 20 Node tests pass |
| **Realtime protocol logic** (frames, join payloads, presence fold, multi-device) via the JS mirror `tools/protocol.mjs` | ✅ | 10 Node tests pass (30 total, `cd backend && node --test`) |
| Kotlin `PhoenixProtocol` / `PresenceTracker` (same logic as the JS mirror) | 🟡 | 13 Kotlin tests written, not run |
| Real Supabase Realtime compatibility (auth, joins, heartbeat, presence, change events) | 🟡 | **Unverified against a live server.** Run `tools/realtime-smoke.mjs` (free) |
| Phase 3 auth (data layer + UI) | 🟡 | 33 Kotlin tests; unchanged by Phase 4 except the optional `minValiditySec` argument (default = old behavior, new test added) |
| **Phase 4** social data layer (search, friendships, requests, parsing, friendly errors) | 🟡 | 23 Kotlin tests written, not run |
| Phase 4 UI: Friends (online/offline sections), Discover (debounced search), Requests, bottom navigation, toasts, connection banner | 🟡 | Statically checked only |
| Phase 4 DB: `0003_social.sql` (cancel request), `0004_social_hardening.sql` (per-pair advisory lock, `already_friends`, spam cap) | 🟡 | Never executed |
| Phase 4 DB security test `supabase/tests/social_rls.sql` (25 checks; rolls back) | 🟡 | Written to be run by you in the SQL editor |
| Phase 4 realtime lifecycle (foreground-only, 8 s stop grace, network-aware sleep/reconnect, jittered backoff, 15-min token margin) | 🟡 | `Backoff` + token margin unit-tested (Kotlin, unrun); rest is design |
| Chat, voice/image messages, push, calls, customization, release APK | ⬜ | Phases 5–9 |

**Phase 4 verdict: implemented; available tests pass (30/30 Node); NOT yet verified end to end.** I will not call it complete
until you confirm: (1) it builds, (2) `./gradlew test` is green, (3) `social_rls.sql` prints ALL SOCIAL RLS TESTS PASSED,
(4) `realtime-smoke.mjs` prints ALL REALTIME CHECKS PASSED, (5) two phones can friend each other and see presence.

## Phase 4 verification review (latest changes)
| Item | Result |
|---|---|
| ZIP vs commit `29293bc` (the previously delivered ZIP) | ✅ 99/99 files byte-identical, none missing/extra |
| Migrations 0001-0004 static lint | ✅ no DROP/TRUNCATE/data deletes; 17 SECURITY DEFINER functions all pin `search_path`; every GRANT names a defined function; no duplicate object creation; balanced quotes/parens/`$$` |
| Migration safety | 🔧 Fixed: `0001` now aborts if `public` already has tables (it has sweeping REVOKEs) |
| Cross-account data leak | 🔧 Fixed: stale refresh/search results could be written into another account's state after sign-out; added identity/epoch guards and Discover reset on sign-out |
| Unused dependency | 🔧 Removed `navigation-compose` (not used anywhere) |
| Gradle wrapper config | 🔧 Added `gradle-wrapper.properties` (Gradle 8.9; AGP 8.6.1 needs ≥ 8.7) |
| Kotlin compile risk | Static only: balanced syntax, all project imports resolve, no used-but-unimported project symbols, 50 test→main member calls cross-checked. **Not compiled.** |
| Node tests | ✅ 30/30 (executed) |
| CI workflow | ✅ valid YAML; its test-summary script executed against fake results and counted correctly; the build itself not yet run |

## Security properties (enforced in PostgreSQL, not the app)
- `friendships`: clients have SELECT only (own rows via RLS). Every write goes through `security definer` functions that use `auth.uid()`.
- Duplicate/crossed requests: unique index on the unordered pair + per-pair advisory lock; crossed requests auto-accept.
- Only the addressee can accept/decline; only the requester can cancel; either side can remove an accepted friend; strangers affect nothing.
- Direct chats/messages require an accepted friendship (`can_post`); unfriending closes the chat.
- `anon` cannot call any social function or read profiles (only `username_available`).

## Known limitations (documented, accepted for zero cost)
- Presence uses one shared channel: any signed-in client can see which user **ids** are online (ids only; usernames still need a profile lookup). Fine at small scale; Supabase free tier limits concurrent connections (~200) and presence payload grows with users.
- Realtime DELETE events are not RLS-filtered by Supabase (they carry only the row id). We treat events as hints and re-query, so nothing sensitive is exposed.
- `search_users` allows prefix enumeration (min 2 chars, 20 results). No per-user rate limit yet.
- "Online" means "app visible", by design (socket closes ~8 s after backgrounding). Background delivery will be push (Phase 6).
- `show_online = false` is honored by the client (it simply does not track presence); a modified client could ignore it.
- "Glass" = translucency + borders, not backdrop blur (performance on Redmi Note 9 Pro).

## External requirements
Free Supabase project · free SMTP for password-reset email (see COSTS.md) · Android Studio to build.
