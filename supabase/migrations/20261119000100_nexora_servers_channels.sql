-- NEXORA community servers, text channels, and channel messages.
-- Public-schema tables are explicitly granted to authenticated because newer
-- Supabase projects no longer expose newly-created tables automatically.

create table if not exists public.servers (
  id uuid primary key default gen_random_uuid(),
  owner_id uuid not null references auth.users(id) on delete cascade,
  name text not null check (char_length(trim(name)) between 2 and 80),
  description text not null default '' check (char_length(description) <= 500),
  is_public boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.server_members (
  server_id uuid not null references public.servers(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  role text not null default 'member' check (role in ('owner','admin','moderator','member')),
  is_muted boolean not null default false,
  is_banned boolean not null default false,
  joined_at timestamptz not null default now(),
  primary key (server_id,user_id)
);

create table if not exists public.server_channels (
  id uuid primary key default gen_random_uuid(),
  server_id uuid not null references public.servers(id) on delete cascade,
  name text not null check (char_length(trim(name)) between 1 and 80),
  topic text not null default '' check (char_length(topic) <= 240),
  channel_type text not null default 'text' check (channel_type in ('text','announcement')),
  position integer not null default 0,
  created_by uuid not null references auth.users(id),
  created_at timestamptz not null default now(),
  unique(server_id,name)
);

create table if not exists public.server_channel_messages (
  id uuid primary key default gen_random_uuid(),
  server_id uuid not null references public.servers(id) on delete cascade,
  channel_id uuid not null references public.server_channels(id) on delete cascade,
  author_id uuid not null references auth.users(id) on delete cascade,
  content text not null check (char_length(trim(content)) between 1 and 8000),
  created_at timestamptz not null default now(),
  edited_at timestamptz,
  deleted_at timestamptz,
  constraint server_channel_message_channel_server_fk
    foreign key(channel_id,server_id) references public.server_channels(id,server_id) on delete cascade
);

create unique index if not exists server_channels_id_server_unique on public.server_channels(id,server_id);
create index if not exists servers_public_created_idx on public.servers(is_public,created_at desc);
create index if not exists server_members_user_idx on public.server_members(user_id,server_id);
create index if not exists server_channels_server_position_idx on public.server_channels(server_id,position);
create index if not exists server_channel_messages_channel_created_idx on public.server_channel_messages(channel_id,created_at desc);
create index if not exists server_channel_messages_author_idx on public.server_channel_messages(author_id);

create or replace function private.is_active_server_member(p_server_id uuid)
returns boolean
language sql stable security definer
set search_path = ''
as $$
  select exists (
    select 1 from public.server_members sm
    where sm.server_id = p_server_id
      and sm.user_id = (select auth.uid())
      and sm.is_banned = false
  );
$$;

create or replace function private.is_server_manager(p_server_id uuid)
returns boolean
language sql stable security definer
set search_path = ''
as $$
  select exists (
    select 1 from public.server_members sm
    where sm.server_id = p_server_id
      and sm.user_id = (select auth.uid())
      and sm.role in ('owner','admin')
      and sm.is_banned = false
  );
$$;

create or replace function private.add_server_owner_member()
returns trigger
language plpgsql security definer
set search_path = ''
as $$
begin
  insert into public.server_members(server_id,user_id,role)
  values(new.id,new.owner_id,'owner')
  on conflict(server_id,user_id) do update set role='owner',is_banned=false;
  return new;
end;
$$;

drop trigger if exists server_owner_member_after_insert on public.servers;
create trigger server_owner_member_after_insert
after insert on public.servers
for each row execute function private.add_server_owner_member();

alter table public.servers enable row level security;
alter table public.server_members enable row level security;
alter table public.server_channels enable row level security;
alter table public.server_channel_messages enable row level security;

drop policy if exists "Servers discoverable or member-visible" on public.servers;
create policy "Servers discoverable or member-visible"
on public.servers for select to authenticated
using (is_public or private.is_active_server_member(id));

drop policy if exists "Users create own servers" on public.servers;
create policy "Users create own servers"
on public.servers for insert to authenticated
with check (owner_id = (select auth.uid()));

drop policy if exists "Server managers update servers" on public.servers;
create policy "Server managers update servers"
on public.servers for update to authenticated
using (private.is_server_manager(id))
with check (private.is_server_manager(id));

drop policy if exists "Server owners delete servers" on public.servers;
create policy "Server owners delete servers"
on public.servers for delete to authenticated
using (owner_id = (select auth.uid()));

drop policy if exists "Members see server membership" on public.server_members;
create policy "Members see server membership"
on public.server_members for select to authenticated
using (user_id = (select auth.uid()) or private.is_server_manager(server_id));

drop policy if exists "Users join public servers" on public.server_members;
create policy "Users join public servers"
on public.server_members for insert to authenticated
with check (
  user_id = (select auth.uid())
  and role = 'member'
  and is_banned = false
  and exists(select 1 from public.servers s where s.id=server_id and s.is_public)
);

drop policy if exists "Members leave or managers remove members" on public.server_members;
create policy "Members leave or managers remove members"
on public.server_members for delete to authenticated
using (
  (user_id = (select auth.uid()) and role <> 'owner')
  or private.is_server_manager(server_id)
);

drop policy if exists "Members see server channels" on public.server_channels;
create policy "Members see server channels"
on public.server_channels for select to authenticated
using (private.is_active_server_member(server_id));

drop policy if exists "Managers create server channels" on public.server_channels;
create policy "Managers create server channels"
on public.server_channels for insert to authenticated
with check (
  created_by = (select auth.uid())
  and private.is_server_manager(server_id)
);

drop policy if exists "Managers update server channels" on public.server_channels;
create policy "Managers update server channels"
on public.server_channels for update to authenticated
using (private.is_server_manager(server_id))
with check (private.is_server_manager(server_id));

drop policy if exists "Managers delete server channels" on public.server_channels;
create policy "Managers delete server channels"
on public.server_channels for delete to authenticated
using (private.is_server_manager(server_id));

drop policy if exists "Members read channel messages" on public.server_channel_messages;
create policy "Members read channel messages"
on public.server_channel_messages for select to authenticated
using (private.is_active_server_member(server_id));

drop policy if exists "Unmuted members send channel messages" on public.server_channel_messages;
create policy "Unmuted members send channel messages"
on public.server_channel_messages for insert to authenticated
with check (
  author_id = (select auth.uid())
  and private.is_active_server_member(server_id)
  and exists (
    select 1 from public.server_members sm
    where sm.server_id=server_id and sm.user_id=(select auth.uid())
      and sm.is_muted=false and sm.is_banned=false
  )
  and exists (
    select 1 from public.server_channels c
    where c.id=channel_id and c.server_id=server_id
  )
);

drop policy if exists "Authors edit own channel messages" on public.server_channel_messages;
create policy "Authors edit own channel messages"
on public.server_channel_messages for update to authenticated
using (author_id=(select auth.uid()) and private.is_active_server_member(server_id))
with check (author_id=(select auth.uid()) and private.is_active_server_member(server_id));

drop policy if exists "Authors or managers delete channel messages" on public.server_channel_messages;
create policy "Authors or managers delete channel messages"
on public.server_channel_messages for delete to authenticated
using (author_id=(select auth.uid()) or private.is_server_manager(server_id));

grant select, insert, update, delete on public.servers to authenticated;
grant select, insert, update, delete on public.server_members to authenticated;
grant select, insert, update, delete on public.server_channels to authenticated;
grant select, insert, update, delete on public.server_channel_messages to authenticated;
revoke all on function private.is_active_server_member(uuid) from public, anon;
revoke all on function private.is_server_manager(uuid) from public, anon;
grant execute on function private.is_active_server_member(uuid) to authenticated;
grant execute on function private.is_server_manager(uuid) to authenticated;
