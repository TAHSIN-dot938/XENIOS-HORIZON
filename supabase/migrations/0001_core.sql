-- THE HORIZON — core schema
-- Run on a NEW Supabase project (never on the old production project).
-- Not yet executed against a real Postgres: review before applying.

-- ───────────────────────── Profiles ─────────────────────────
-- SAFETY GUARD: this migration revokes grants across the whole public schema, so it must only ever
-- run on a NEW, empty Supabase project. It aborts (changing nothing) if public already has tables.
do $$
begin
  if exists (select 1 from information_schema.tables where table_schema = 'public') then
    raise exception 'Refusing to run 0001_core.sql: the public schema already contains tables. Use a NEW Supabase project (never your old Horizon project).';
  end if;
end $$;

create sequence public.user_number_seq start 1;

create table public.profiles (
  id            uuid primary key references auth.users(id) on delete cascade,
  user_number   text not null unique
                default lpad(nextval('public.user_number_seq')::text, 14, '0'),
  username      text not null,
  display_name  text not null,
  bio           text not null default '',
  avatar_url    text,
  avatar_style  smallint not null default 0,      -- gradient/ring preset when no photo
  show_online   boolean not null default true,
  created_at    timestamptz not null default now(),
  constraint user_number_format check (user_number ~ '^[0-9]{14}$'),
  constraint username_format    check (username ~ '^[A-Za-z0-9_]{3,20}$'),
  constraint display_name_len   check (char_length(display_name) between 1 and 40),
  constraint bio_len            check (char_length(bio) <= 200)
);

create unique index profiles_username_lower_idx on public.profiles (lower(username));

create table public.notification_prefs (
  user_id         uuid primary key references public.profiles(id) on delete cascade,
  messages        boolean not null default true,
  friend_requests boolean not null default true,
  calls           boolean not null default true,
  global_chat     boolean not null default false,
  sound           text    not null default 'horizon_chime',
  vibrate         boolean not null default true
);

-- Signup requires raw_user_meta_data.username; display_name optional.
create function public.handle_new_user() returns trigger
language plpgsql security definer set search_path = public as $$
declare v_username text := new.raw_user_meta_data->>'username';
begin
  if v_username is null then
    raise exception 'username_required';
  end if;
  insert into public.profiles (id, username, display_name)
  values (new.id, v_username,
          coalesce(nullif(trim(new.raw_user_meta_data->>'display_name'), ''), v_username));
  insert into public.notification_prefs (user_id) values (new.id);
  return new;
end $$;

create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function public.handle_new_user();

-- ───────────────────────── Friendships ─────────────────────────
create type public.friendship_status as enum ('pending', 'accepted');

create table public.friendships (
  id            uuid primary key default gen_random_uuid(),
  requester_id  uuid not null references public.profiles(id) on delete cascade,
  addressee_id  uuid not null references public.profiles(id) on delete cascade,
  status        public.friendship_status not null default 'pending',
  created_at    timestamptz not null default now(),
  responded_at  timestamptz,
  constraint no_self_friend check (requester_id <> addressee_id)
);
create unique index friendships_pair_idx
  on public.friendships (least(requester_id, addressee_id), greatest(requester_id, addressee_id));
create index friendships_addressee_idx on public.friendships (addressee_id, status);

create function public.are_friends(a uuid, b uuid) returns boolean
language sql stable security definer set search_path = public as $$
  select exists (
    select 1 from public.friendships f
    where f.status = 'accepted'
      and least(f.requester_id, f.addressee_id) = least(a, b)
      and greatest(f.requester_id, f.addressee_id) = greatest(a, b));
$$;

-- ───────────────────────── Conversations & messages ─────────────────────────
create type public.conversation_kind as enum ('direct', 'global');
create type public.message_kind      as enum ('text', 'image', 'voice');

create table public.conversations (
  id         uuid primary key default gen_random_uuid(),
  kind       public.conversation_kind not null,
  user_a     uuid references public.profiles(id) on delete cascade,
  user_b     uuid references public.profiles(id) on delete cascade,
  created_at timestamptz not null default now(),
  constraint conv_shape check (
    (kind = 'global' and user_a is null and user_b is null) or
    (kind = 'direct' and user_a is not null and user_b is not null and user_a < user_b))
);
create unique index conversations_pair_idx   on public.conversations (user_a, user_b) where kind = 'direct';
create unique index conversations_global_idx on public.conversations (kind) where kind = 'global';
insert into public.conversations (id, kind) values ('00000000-0000-0000-0000-000000000001', 'global');

create function public.is_member(p_conv uuid) returns boolean
language sql stable security definer set search_path = public as $$
  select exists (select 1 from public.conversations c
    where c.id = p_conv and (c.kind = 'global' or auth.uid() in (c.user_a, c.user_b)));
$$;

create function public.can_post(p_conv uuid) returns boolean
language sql stable security definer set search_path = public as $$
  select exists (select 1 from public.conversations c
    where c.id = p_conv and (c.kind = 'global'
      or (auth.uid() in (c.user_a, c.user_b) and public.are_friends(c.user_a, c.user_b))));
$$;

create table public.messages (
  id                uuid primary key default gen_random_uuid(),
  conversation_id   uuid not null references public.conversations(id) on delete cascade,
  sender_id         uuid not null default auth.uid() references public.profiles(id) on delete cascade,
  client_id         uuid not null,
  kind              public.message_kind not null default 'text',
  body              text not null default '',
  media_path        text,
  media_duration_ms integer,
  created_at        timestamptz not null default now(),
  delivered_at      timestamptz,
  read_at           timestamptz,
  unique (sender_id, client_id),
  constraint body_len check (char_length(body) <= 4000),
  constraint duration_range check (media_duration_ms is null or media_duration_ms between 0 and 300000),
  constraint kind_shape check (
    (kind = 'text' and char_length(body) > 0) or (kind <> 'text' and media_path is not null))
);
create index messages_conv_created_idx on public.messages (conversation_id, created_at desc);

-- Receipts: only the recipient of a direct message may mark it.
create function public.mark_delivered(p_conversation uuid) returns void
language sql security definer set search_path = public as $$
  update public.messages m set delivered_at = now()
  from public.conversations c
  where m.conversation_id = p_conversation and c.id = m.conversation_id
    and c.kind = 'direct' and auth.uid() in (c.user_a, c.user_b)
    and m.sender_id <> auth.uid() and m.delivered_at is null;
$$;

create function public.mark_read(p_conversation uuid) returns void
language sql security definer set search_path = public as $$
  update public.messages m set read_at = now(), delivered_at = coalesce(m.delivered_at, now())
  from public.conversations c
  where m.conversation_id = p_conversation and c.id = m.conversation_id
    and c.kind = 'direct' and auth.uid() in (c.user_a, c.user_b)
    and m.sender_id <> auth.uid() and m.read_at is null;
$$;

-- ───────────────────────── Notifications, push, calls ─────────────────────────
create table public.notifications (
  id         uuid primary key default gen_random_uuid(),
  user_id    uuid not null references public.profiles(id) on delete cascade,
  kind       text not null check (kind in ('message','friend_request','friend_accepted','call_missed')),
  actor_id   uuid references public.profiles(id) on delete set null,
  payload    jsonb not null default '{}',
  created_at timestamptz not null default now(),
  read_at    timestamptz
);
create index notifications_user_idx on public.notifications (user_id, created_at desc);

create table public.push_tokens (
  id         uuid primary key default gen_random_uuid(),
  user_id    uuid not null references public.profiles(id) on delete cascade,
  fcm_token  text not null unique,
  platform   text not null default 'android',
  updated_at timestamptz not null default now()
);

create table public.calls (
  id          uuid primary key default gen_random_uuid(),
  caller_id   uuid not null references public.profiles(id) on delete cascade,
  callee_id   uuid not null references public.profiles(id) on delete cascade,
  kind        text not null check (kind in ('audio','video')),
  status      text not null default 'ringing'
              check (status in ('ringing','active','ended','declined','missed','cancelled')),
  created_at  timestamptz not null default now(),
  answered_at timestamptz,
  ended_at    timestamptz
);
create index calls_caller_idx on public.calls (caller_id, created_at desc);
create index calls_callee_idx on public.calls (callee_id, created_at desc);

-- A device token belongs to whoever logged in last.
create function public.register_push_token(p_token text, p_platform text default 'android') returns void
language plpgsql security definer set search_path = public as $$
begin
  if auth.uid() is null then raise exception 'not_authenticated'; end if;
  insert into public.push_tokens (user_id, fcm_token, platform)
  values (auth.uid(), p_token, p_platform)
  on conflict (fcm_token) do update
    set user_id = excluded.user_id, platform = excluded.platform, updated_at = now();
end $$;

create function public.unregister_push_token(p_token text) returns void
language sql security definer set search_path = public as $$
  delete from public.push_tokens where fcm_token = p_token and user_id = auth.uid();
$$;

-- ───────────────────────── Social RPCs ─────────────────────────
create function public.search_users(p_query text)
returns table (id uuid, user_number text, username text, display_name text,
               avatar_url text, avatar_style smallint, relation text)
language plpgsql stable security definer set search_path = public as $$
declare q text := trim(coalesce(p_query, ''));
        pat text;
begin
  if auth.uid() is null then raise exception 'not_authenticated'; end if;
  if char_length(q) < 2 then return; end if;
  pat := replace(replace(replace(q, '\', '\\'), '%', '\%'), '_', '\_') || '%';
  return query
    select p.id, p.user_number, p.username, p.display_name, p.avatar_url, p.avatar_style,
      case when p.id = auth.uid() then 'self'
           when f.status = 'accepted' then 'friend'
           when f.status = 'pending' and f.requester_id = auth.uid() then 'outgoing'
           when f.status = 'pending' then 'incoming'
           else 'none' end
    from public.profiles p
    left join public.friendships f
      on least(f.requester_id, f.addressee_id) = least(p.id, auth.uid())
     and greatest(f.requester_id, f.addressee_id) = greatest(p.id, auth.uid())
    where (q ~ '^[0-9]{14}$' and p.user_number = q)
       or (q !~ '^[0-9]{14}$' and lower(p.username) like lower(pat))
    order by p.username
    limit 20;
end $$;

create function public.send_friend_request(p_target uuid) returns uuid
language plpgsql security definer set search_path = public as $$
declare v_me uuid := auth.uid(); v_existing public.friendships; v_id uuid;
begin
  if v_me is null then raise exception 'not_authenticated'; end if;
  if v_me = p_target then raise exception 'cannot_friend_self'; end if;
  if not exists (select 1 from public.profiles where id = p_target) then
    raise exception 'user_not_found';
  end if;
  select * into v_existing from public.friendships f
   where least(f.requester_id, f.addressee_id) = least(v_me, p_target)
     and greatest(f.requester_id, f.addressee_id) = greatest(v_me, p_target);
  if found then
    if v_existing.status = 'pending' and v_existing.addressee_id = v_me then
      update public.friendships set status = 'accepted', responded_at = now()
       where id = v_existing.id;                       -- they already asked: auto-accept
      return v_existing.id;
    end if;
    raise exception 'request_exists';
  end if;
  insert into public.friendships (requester_id, addressee_id) values (v_me, p_target)
  returning id into v_id;
  return v_id;
end $$;

create function public.respond_friend_request(p_id uuid, p_accept boolean) returns void
language plpgsql security definer set search_path = public as $$
begin
  if p_accept then
    update public.friendships set status = 'accepted', responded_at = now()
     where id = p_id and addressee_id = auth.uid() and status = 'pending';
  else
    delete from public.friendships
     where id = p_id and addressee_id = auth.uid() and status = 'pending';
  end if;
  if not found then raise exception 'request_not_found'; end if;
end $$;

create function public.remove_friend(p_other uuid) returns void
language sql security definer set search_path = public as $$
  delete from public.friendships f
   where f.status = 'accepted'
     and least(f.requester_id, f.addressee_id) = least(auth.uid(), p_other)
     and greatest(f.requester_id, f.addressee_id) = greatest(auth.uid(), p_other);
$$;

create function public.open_direct_conversation(p_other uuid) returns uuid
language plpgsql security definer set search_path = public as $$
declare v_me uuid := auth.uid(); v_a uuid; v_b uuid; v_id uuid;
begin
  if v_me is null then raise exception 'not_authenticated'; end if;
  if not public.are_friends(v_me, p_other) then raise exception 'not_friends'; end if;
  v_a := least(v_me, p_other); v_b := greatest(v_me, p_other);
  select id into v_id from public.conversations where kind = 'direct' and user_a = v_a and user_b = v_b;
  if v_id is null then
    insert into public.conversations (kind, user_a, user_b) values ('direct', v_a, v_b)
    on conflict (user_a, user_b) where kind = 'direct' do nothing;
    select id into v_id from public.conversations where kind = 'direct' and user_a = v_a and user_b = v_b;
  end if;
  return v_id;
end $$;

-- ───────────────────────── Notification triggers (feed → webhook → FCM) ─────────────────────────
create function public.notify_friendship() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  if tg_op = 'INSERT' then
    insert into public.notifications (user_id, kind, actor_id, payload)
    values (new.addressee_id, 'friend_request', new.requester_id, jsonb_build_object('friendship_id', new.id));
  elsif new.status = 'accepted' and old.status = 'pending' then
    insert into public.notifications (user_id, kind, actor_id, payload)
    values (new.requester_id, 'friend_accepted', new.addressee_id, jsonb_build_object('friendship_id', new.id));
  end if;
  return new;
end $$;
create trigger friendships_notify after insert or update on public.friendships
  for each row execute function public.notify_friendship();

create function public.notify_message() returns trigger
language plpgsql security definer set search_path = public as $$
declare v_conv public.conversations; v_to uuid; v_preview text;
begin
  select * into v_conv from public.conversations where id = new.conversation_id;
  if v_conv.kind <> 'direct' then return new; end if;
  v_to := case when new.sender_id = v_conv.user_a then v_conv.user_b else v_conv.user_a end;
  v_preview := case new.kind when 'text' then left(new.body, 120)
                             when 'image' then 'Photo' else 'Voice message' end;
  insert into public.notifications (user_id, kind, actor_id, payload)
  values (v_to, 'message', new.sender_id,
          jsonb_build_object('conversation_id', new.conversation_id, 'message_id', new.id, 'preview', v_preview));
  return new;
end $$;
create trigger messages_notify after insert on public.messages
  for each row execute function public.notify_message();

-- ───────────────────────── Row Level Security ─────────────────────────
alter table public.profiles           enable row level security;
alter table public.notification_prefs enable row level security;
alter table public.friendships        enable row level security;
alter table public.conversations      enable row level security;
alter table public.messages           enable row level security;
alter table public.notifications      enable row level security;
alter table public.push_tokens        enable row level security;
alter table public.calls              enable row level security;

revoke all on all tables    in schema public from anon, authenticated;
revoke all on all sequences in schema public from anon, authenticated;
revoke all on all functions in schema public from public, anon, authenticated;

-- profiles: readable by signed-in users (needed for search/global chat); edit own safe columns only
grant select on public.profiles to authenticated;
grant update (display_name, bio, avatar_url, avatar_style, show_online) on public.profiles to authenticated;
create policy profiles_read   on public.profiles for select to authenticated using (true);
create policy profiles_update on public.profiles for update to authenticated
  using (id = auth.uid()) with check (id = auth.uid());

grant select, update on public.notification_prefs to authenticated;
create policy prefs_read   on public.notification_prefs for select to authenticated using (user_id = auth.uid());
create policy prefs_update on public.notification_prefs for update to authenticated
  using (user_id = auth.uid()) with check (user_id = auth.uid());

grant select on public.friendships to authenticated;
create policy friendships_read on public.friendships for select to authenticated
  using (auth.uid() in (requester_id, addressee_id));

grant select on public.conversations to authenticated;
create policy conversations_read on public.conversations for select to authenticated
  using (public.is_member(id));

grant select on public.messages to authenticated;
grant insert (conversation_id, client_id, kind, body, media_path, media_duration_ms)
  on public.messages to authenticated;
create policy messages_read   on public.messages for select to authenticated using (public.is_member(conversation_id));
create policy messages_insert on public.messages for insert to authenticated
  with check (sender_id = auth.uid() and public.can_post(conversation_id));

grant select on public.notifications to authenticated;
grant update (read_at) on public.notifications to authenticated;
create policy notif_read   on public.notifications for select to authenticated using (user_id = auth.uid());
create policy notif_update on public.notifications for update to authenticated
  using (user_id = auth.uid()) with check (user_id = auth.uid());

grant select on public.calls to authenticated;
create policy calls_read on public.calls for select to authenticated
  using (auth.uid() in (caller_id, callee_id));
-- push_tokens: no client policies at all (RPC-only). calls: written by the backend (service role) only.

-- RPCs & RLS helpers callable by signed-in users
grant execute on function
  public.are_friends(uuid, uuid), public.is_member(uuid), public.can_post(uuid),
  public.mark_delivered(uuid), public.mark_read(uuid),
  public.register_push_token(text, text), public.unregister_push_token(text),
  public.search_users(text), public.send_friend_request(uuid),
  public.respond_friend_request(uuid, boolean), public.remove_friend(uuid),
  public.open_direct_conversation(uuid)
to authenticated;

-- ───────────────────────── Realtime ─────────────────────────
alter publication supabase_realtime add table public.messages, public.notifications, public.friendships;

-- ───────────────────────── Storage ─────────────────────────
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types) values
  ('avatars',    'avatars',    true,  2097152,  array['image/jpeg','image/png','image/webp']),
  ('chat-media', 'chat-media', false, 10485760, array['image/jpeg','image/png','image/webp','audio/mp4','audio/aac','audio/mpeg','audio/ogg']);

-- avatars/{user_id}/file
create policy avatars_write on storage.objects for insert to authenticated
  with check (bucket_id = 'avatars' and (storage.foldername(name))[1] = auth.uid()::text);
create policy avatars_update on storage.objects for update to authenticated
  using (bucket_id = 'avatars' and (storage.foldername(name))[1] = auth.uid()::text);
create policy avatars_delete on storage.objects for delete to authenticated
  using (bucket_id = 'avatars' and (storage.foldername(name))[1] = auth.uid()::text);

-- chat-media/{conversation_id}/file
create policy chatmedia_read on storage.objects for select to authenticated
  using (bucket_id = 'chat-media' and public.is_member(((storage.foldername(name))[1])::uuid));
create policy chatmedia_write on storage.objects for insert to authenticated
  with check (bucket_id = 'chat-media' and public.can_post(((storage.foldername(name))[1])::uuid));
