# Status (honest)

Legend: ✅ done and verified here · 🟡 written, NOT yet run/built · ⬜ not started

| Area | State | Notes |
|---|---|---|
| Architecture + docs | ✅ | `docs/ARCHITECTURE.md` |
| Backend pure logic (call state machine, validation, push rules, TURN creds) | ✅ | 20 tests pass (`cd backend && node --test`) |
| Backend server wiring (Express, Socket.IO, FCM, Supabase) | 🟡 | Needs `npm install` + real credentials; never executed |
| Database schema, RLS, RPCs, storage | 🟡 | Written carefully, never run on Postgres. Apply to a scratch Supabase project first |
| Android project, theme, design system, splash, auth UI | 🟡 | Passed static bracket/token checks only. **Not compiled** (no Android SDK in my environment) |
| Android unit tests (validators, auth API/repository/session) | 🟡 | Run with `./gradlew test` in Android Studio; I could not run them |
| **Phase 3: Auth data layer** (GoTrue client, session store w/ Keystore encryption, refresh + offline start, profile load) | 🟡 | Written + 36 JVM unit tests written (fake HTTP). **Not compiled or run** — no Kotlin toolchain here |
| **Phase 3: Auth UI** (login, signup, emailed-code confirm, forgot/reset password, sign out, profile screen with 14-digit ID, not-configured screen) | 🟡 | Real flow, unverified on device |
| `0002_auth_helpers.sql` (`username_available`) | 🟡 | Additive; never executed |
| Profiles, friends, messaging, global chat, receipts, typing | ⬜ | Backend/DB side ready; Android side Phases 3–5 |
| FCM, voice messages, image sharing | ⬜ | Phase 6 |
| Voice/video calling | ⬜ | Signaling backend written; WebRTC client Phase 7; TURN decision needed |
| Customization/settings | ⬜ | Phase 8 |
| Debug APK | ⬜ | Cannot be built in my environment (no Android SDK, no network). Build in Android Studio |

## External requirements
Supabase project (free) + free SMTP for password-reset email (see COSTS.md) · Firebase project + `google-services.json` + service account · a host for `backend/` · (Phase 7) TURN decision.

## Known limitations / decisions
- "Glass" is translucency + borders, not true backdrop blur (performance on Redmi Note 9 Pro).
- Online status will use Realtime Presence (not stored); `show_online=false` is enforced client-side.
- Profiles are readable by all signed-in users (needed for search and global chat).
- Backend has no rate limiting yet; add before public launch.
