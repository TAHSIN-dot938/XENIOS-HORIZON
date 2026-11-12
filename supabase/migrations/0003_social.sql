-- Additive migration. Lets a user withdraw a request they sent (0001 only let the addressee
-- reject and either side remove an *accepted* friend).
create or replace function public.cancel_friend_request(p_id uuid) returns void
language plpgsql security definer set search_path = public as $$
begin
  delete from public.friendships
   where id = p_id and requester_id = auth.uid() and status = 'pending';
  if not found then raise exception 'request_not_found'; end if;
end $$;

-- New functions get Supabase's default grants (anon, authenticated); lock to signed-in users only.
revoke all on function public.cancel_friend_request(uuid) from public, anon;
grant execute on function public.cancel_friend_request(uuid) to authenticated;
