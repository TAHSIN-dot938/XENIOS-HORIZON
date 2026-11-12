-- Additive/replacing migration (no drops, no data changes).
-- Fixes a race in send_friend_request: two people requesting each other at the same moment could
-- both pass the existence check and one would hit the unique index with a raw database error.
-- Now every operation on a user PAIR is serialized with a transaction-scoped advisory lock, and
-- the outcomes are explicit: auto-accept, already_friends, request_exists, too_many_requests.

create index if not exists friendships_requester_idx on public.friendships (requester_id, status);

create or replace function public.send_friend_request(p_target uuid) returns uuid
language plpgsql security definer set search_path = public as $$
declare
  v_me uuid := auth.uid();
  v_existing public.friendships;
  v_id uuid;
  v_pending integer;
begin
  if v_me is null then raise exception 'not_authenticated'; end if;
  if p_target is null or v_me = p_target then raise exception 'cannot_friend_self'; end if;
  if not exists (select 1 from public.profiles where id = p_target) then
    raise exception 'user_not_found';
  end if;

  -- One transaction at a time per pair (released automatically at commit/rollback).
  perform pg_advisory_xact_lock(
    hashtextextended(least(v_me, p_target)::text || ':' || greatest(v_me, p_target)::text, 0));

  select * into v_existing from public.friendships f
   where least(f.requester_id, f.addressee_id) = least(v_me, p_target)
     and greatest(f.requester_id, f.addressee_id) = greatest(v_me, p_target);

  if found then
    if v_existing.status = 'accepted' then
      raise exception 'already_friends';
    elsif v_existing.addressee_id = v_me then
      -- They already asked you: sending a request back means "yes".
      update public.friendships set status = 'accepted', responded_at = now() where id = v_existing.id;
      return v_existing.id;
    else
      raise exception 'request_exists';
    end if;
  end if;

  -- Basic spam guard: at most 100 unanswered outgoing requests.
  select count(*) into v_pending from public.friendships
   where requester_id = v_me and status = 'pending';
  if v_pending >= 100 then raise exception 'too_many_requests'; end if;

  insert into public.friendships (requester_id, addressee_id) values (v_me, p_target)
  returning id into v_id;
  return v_id;
end $$;

create or replace function public.respond_friend_request(p_id uuid, p_accept boolean) returns void
language plpgsql security definer set search_path = public as $$
begin
  if auth.uid() is null then raise exception 'not_authenticated'; end if;
  if p_accept is null then raise exception 'invalid_argument'; end if;
  if p_accept then
    update public.friendships set status = 'accepted', responded_at = now()
     where id = p_id and addressee_id = auth.uid() and status = 'pending';
  else
    delete from public.friendships
     where id = p_id and addressee_id = auth.uid() and status = 'pending';
  end if;
  if not found then raise exception 'request_not_found'; end if;   -- also covers "not yours"
end $$;

-- create or replace keeps the grants from 0001/0003 (authenticated only).
