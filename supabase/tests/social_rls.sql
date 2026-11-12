-- Social security tests. Run in the Supabase SQL editor (as the postgres role) on a SCRATCH or
-- the new project AFTER applying migrations 0001-0004. Everything runs in one transaction that
-- is ROLLED BACK at the end, so no data is kept. If any check fails the script stops with
-- "FAIL: <what>". On success the last output row says ALL SOCIAL RLS TESTS PASSED.
-- (Written without access to a Postgres instance: not yet executed by the author.)
begin;

insert into auth.users (id, email, raw_user_meta_data) values
  ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'a@test.local', '{"username":"alice_t"}'),
  ('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', 'b@test.local', '{"username":"bob_t"}'),
  ('cccccccc-cccc-4ccc-8ccc-cccccccccccc', 'c@test.local', '{"username":"carol_t"}');

-- sanity: the signup trigger created profiles with 14-digit IDs
do $$ begin
  if (select count(*) from public.profiles where user_number ~ '^[0-9]{14}$'
        and username in ('alice_t','bob_t','carol_t')) <> 3 then
    raise exception 'FAIL: profiles/14-digit ids not created by signup trigger';
  end if;
end $$;

-- ───────── ALICE ─────────
select set_config('request.jwt.claims', '{"sub":"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa","role":"authenticated"}', true);
set local role authenticated;

do $$
declare v uuid; ok boolean;
begin
  -- search by username prefix and by exact 14-digit id
  if not exists (select 1 from public.search_users('bob') where username = 'bob_t' and relation = 'none') then
    raise exception 'FAIL: search by username'; end if;
  if not exists (select 1 from public.search_users((select user_number from public.profiles where username = 'bob_t')) where username = 'bob_t') then
    raise exception 'FAIL: search by 14-digit id'; end if;
  if exists (select 1 from public.search_users('b')) then raise exception 'FAIL: 1-char search must return nothing'; end if;

  -- request to Bob, then duplicates / self / unknown are rejected
  perform public.send_friend_request('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb');
  ok := false; begin perform public.send_friend_request('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb');
  exception when others then ok := (sqlerrm = 'request_exists'); end;
  if not ok then raise exception 'FAIL: duplicate request not rejected'; end if;
  ok := false; begin perform public.send_friend_request('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa');
  exception when others then ok := (sqlerrm = 'cannot_friend_self'); end;
  if not ok then raise exception 'FAIL: self request not rejected'; end if;
  ok := false; begin perform public.send_friend_request('dddddddd-dddd-4ddd-8ddd-dddddddddddd');
  exception when others then ok := (sqlerrm = 'user_not_found'); end;
  if not ok then raise exception 'FAIL: unknown user not rejected'; end if;

  -- direct table writes are impossible for clients
  ok := false; begin insert into public.friendships (requester_id, addressee_id)
    values ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'cccccccc-cccc-4ccc-8ccc-cccccccccccc');
  exception when insufficient_privilege then ok := true; end;
  if not ok then raise exception 'FAIL: client could INSERT into friendships'; end if;
  ok := false; begin update public.friendships set status = 'accepted';
  exception when insufficient_privilege then ok := true; end;
  if not ok then raise exception 'FAIL: client could UPDATE friendships'; end if;
  ok := false; begin delete from public.friendships;
  exception when insufficient_privilege then ok := true; end;
  if not ok then raise exception 'FAIL: client could DELETE friendships'; end if;

  -- cannot chat with a non-friend (request still pending)
  ok := false; begin perform public.open_direct_conversation('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb');
  exception when others then ok := (sqlerrm = 'not_friends'); end;
  if not ok then raise exception 'FAIL: chat opened with pending (non-friend)'; end if;
end $$;
reset role;

-- ───────── CAROL (stranger): cannot see or answer Alice's request to Bob ─────────
select set_config('request.jwt.claims', '{"sub":"cccccccc-cccc-4ccc-8ccc-cccccccccccc","role":"authenticated"}', true);
set local role authenticated;
do $$
declare ok boolean; v_req uuid;
begin
  if (select count(*) from public.friendships) <> 0 then raise exception 'FAIL: stranger can read others'' friendships'; end if;
  select id into v_req from (select id from public.friendships limit 1) s;   -- none visible
  ok := false; begin perform public.respond_friend_request('11111111-1111-4111-8111-111111111111', true);
  exception when others then ok := (sqlerrm = 'request_not_found'); end;
  if not ok then raise exception 'FAIL: responding to a nonexistent request'; end if;
end $$;
reset role;

-- ───────── BOB: only the addressee can accept; reverse request auto-accepts ─────────
select set_config('request.jwt.claims', '{"sub":"bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb","role":"authenticated"}', true);
set local role authenticated;
do $$
declare v_req uuid; ok boolean;
begin
  select id into v_req from public.friendships where status = 'pending';
  if v_req is null then raise exception 'FAIL: bob cannot see his incoming request'; end if;
  perform public.respond_friend_request(v_req, true);
  if not public.are_friends('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb') then
    raise exception 'FAIL: accept did not create friendship'; end if;
  ok := false; begin perform public.send_friend_request('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa');
  exception when others then ok := (sqlerrm = 'already_friends'); end;
  if not ok then raise exception 'FAIL: already_friends not reported'; end if;
end $$;
reset role;

-- ───────── ALICE & BOB can chat; CAROL cannot read or post ─────────
select set_config('request.jwt.claims', '{"sub":"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa","role":"authenticated"}', true);
set local role authenticated;
do $$
declare v_conv uuid;
begin
  v_conv := public.open_direct_conversation('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb');
  insert into public.messages (conversation_id, client_id, body) values (v_conv, gen_random_uuid(), 'hello bob');
  perform set_config('horizon.test_conv', v_conv::text, true);
end $$;
reset role;

select set_config('request.jwt.claims', '{"sub":"cccccccc-cccc-4ccc-8ccc-cccccccccccc","role":"authenticated"}', true);
set local role authenticated;
do $$
declare ok boolean; v_conv uuid := current_setting('horizon.test_conv')::uuid;
begin
  if (select count(*) from public.messages where conversation_id = v_conv) <> 0 then
    raise exception 'FAIL: stranger can read a private conversation'; end if;
  ok := false; begin insert into public.messages (conversation_id, client_id, body) values (v_conv, gen_random_uuid(), 'intrude');
  exception when insufficient_privilege then ok := true; end;
  if not ok then raise exception 'FAIL: stranger could post in a private conversation'; end if;
  -- stranger cannot remove someone else's friendship (silently affects nothing)
  perform public.remove_friend('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa');
end $$;
reset role;

do $$ begin
  if not public.are_friends('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb') then
    raise exception 'FAIL: a stranger removed someone else''s friendship'; end if;
end $$;

-- ───────── ALICE: cancel her own request; remove Bob; chat is then closed ─────────
select set_config('request.jwt.claims', '{"sub":"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa","role":"authenticated"}', true);
set local role authenticated;
do $$
declare v_req uuid; ok boolean; v_conv uuid := current_setting('horizon.test_conv')::uuid;
begin
  v_req := public.send_friend_request('cccccccc-cccc-4ccc-8ccc-cccccccccccc');
  perform public.cancel_friend_request(v_req);
  ok := false; begin perform public.cancel_friend_request(v_req);
  exception when others then ok := (sqlerrm = 'request_not_found'); end;
  if not ok then raise exception 'FAIL: cancelling twice should fail'; end if;

  perform public.remove_friend('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb');
  ok := false; begin insert into public.messages (conversation_id, client_id, body) values (v_conv, gen_random_uuid(), 'after unfriend');
  exception when insufficient_privilege then ok := true; end;
  if not ok then raise exception 'FAIL: could still post after unfriending'; end if;
end $$;
reset role;

-- ───────── ANON: nothing is callable ─────────
set local role anon;
do $$
declare ok boolean;
begin
  ok := false; begin perform public.send_friend_request('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb');
  exception when insufficient_privilege then ok := true; end;
  if not ok then raise exception 'FAIL: anon can call send_friend_request'; end if;
  ok := false; begin perform * from public.search_users('bob');
  exception when insufficient_privilege then ok := true; end;
  if not ok then raise exception 'FAIL: anon can call search_users'; end if;
  ok := false; begin perform * from public.profiles;
  exception when insufficient_privilege then ok := true; end;
  if not ok then raise exception 'FAIL: anon can read profiles'; end if;
end $$;
reset role;

select 'ALL SOCIAL RLS TESTS PASSED' as result;
rollback;
