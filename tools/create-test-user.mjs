#!/usr/bin/env node
// Creates a test account through the public signup endpoint (the same call the app makes), including the
// username the database trigger requires. (Dashboard "Add user" cannot set it, so it would fail.)
//   SUPABASE_URL=https://xxx.supabase.co SUPABASE_ANON_KEY=... EMAIL=alice@example.com PASSWORD=horizon-test-1 USERNAME=alice_t node tools/create-test-user.mjs
const { SUPABASE_URL, SUPABASE_ANON_KEY, EMAIL, PASSWORD, USERNAME } = process.env;
if (!SUPABASE_URL || !SUPABASE_ANON_KEY || !EMAIL || !PASSWORD || !USERNAME) { console.error('Set SUPABASE_URL, SUPABASE_ANON_KEY, EMAIL, PASSWORD, USERNAME'); process.exit(2); }
const res = await fetch(`${SUPABASE_URL}/auth/v1/signup`, {
  method: 'POST', headers: { apikey: SUPABASE_ANON_KEY, 'Content-Type': 'application/json' },
  body: JSON.stringify({ email: EMAIL, password: PASSWORD, data: { username: USERNAME, display_name: USERNAME } }),
});
const body = await res.json();
if (!res.ok) { console.error('FAILED', res.status, JSON.stringify(body)); process.exit(1); }
console.log(body.access_token ? `OK: created and signed in. user id = ${body.user.id}` : `OK: created, but email confirmation is ON (turn it off, see docs/SETUP.md). user id = ${body.id ?? body.user?.id}`);
