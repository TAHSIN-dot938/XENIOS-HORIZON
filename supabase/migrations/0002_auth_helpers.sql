-- Additive migration (no drops, no data changes).
-- Lets the signup screen check a username BEFORE creating the auth user, so the user gets a
-- clear "username taken" message instead of a generic database error from the signup trigger.
create or replace function public.username_available(p_username text) returns boolean
language sql stable security definer set search_path = public as $$
  select coalesce(p_username, '') ~ '^[A-Za-z0-9_]{3,20}$'
     and not exists (select 1 from public.profiles where lower(username) = lower(p_username));
$$;

revoke all on function public.username_available(text) from public;
grant execute on function public.username_available(text) to anon, authenticated;
