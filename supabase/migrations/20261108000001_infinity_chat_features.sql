-- Infinity Chat feature expansion (applied to the linked Supabase project).
-- Additive migration: existing profiles, conversations, members, messages, receipts and blocks are preserved.
create extension if not exists pgcrypto;
alter table public.messages add column if not exists reply_to_id uuid references public.messages(id) on delete set null;
alter table public.messages add column if not exists forwarded_from_message_id uuid references public.messages(id) on delete set null;
alter table public.messages add column if not exists pinned_at timestamptz;
alter table public.conversation_members drop constraint if exists conversation_members_role_check;
alter table public.conversation_members add constraint conversation_members_role_check check(role in ('owner','deputy','admin','moderator','member'));
alter table public.conversation_members add column if not exists is_muted boolean not null default false;
alter table public.conversation_members add column if not exists is_banned boolean not null default false;

create or replace function private.is_group_manager(p_conversation_id uuid)
returns boolean language sql stable security definer set search_path=public,private as $$
 select exists(select 1 from public.conversation_members cm where cm.conversation_id=p_conversation_id and cm.user_id=auth.uid() and not cm.is_banned and cm.role in ('owner','deputy','admin','moderator'));
$$;

create table if not exists public.message_reactions(id uuid primary key default gen_random_uuid(),message_id uuid not null references public.messages(id) on delete cascade,user_id uuid not null references auth.users(id) on delete cascade,emoji text not null check(char_length(emoji) between 1 and 32),created_at timestamptz not null default now(),unique(message_id,user_id,emoji));
create index if not exists message_reactions_message_idx on public.message_reactions(message_id);
create table if not exists public.message_pins(message_id uuid primary key references public.messages(id) on delete cascade,conversation_id uuid not null references public.conversations(id) on delete cascade,pinned_by uuid not null references auth.users(id) on delete cascade,created_at timestamptz not null default now());
create table if not exists public.group_invites(id uuid primary key default gen_random_uuid(),conversation_id uuid not null references public.conversations(id) on delete cascade,invite_code text not null unique default encode(gen_random_bytes(18),'hex'),created_by uuid not null references auth.users(id) on delete cascade,expires_at timestamptz,max_uses integer check(max_uses is null or max_uses>0),use_count integer not null default 0 check(use_count>=0),revoked_at timestamptz,created_at timestamptz not null default now());
create table if not exists public.polls(id uuid primary key default gen_random_uuid(),conversation_id uuid not null references public.conversations(id) on delete cascade,created_by uuid not null references auth.users(id) on delete cascade,question text not null check(char_length(question) between 1 and 500),is_multiple_choice boolean not null default false,closes_at timestamptz,created_at timestamptz not null default now());
create table if not exists public.poll_options(id uuid primary key default gen_random_uuid(),poll_id uuid not null references public.polls(id) on delete cascade,option_text text not null check(char_length(option_text) between 1 and 200),position integer not null default 0);
create table if not exists public.poll_votes(poll_option_id uuid not null references public.poll_options(id) on delete cascade,user_id uuid not null references auth.users(id) on delete cascade,created_at timestamptz not null default now(),primary key(poll_option_id,user_id));
create table if not exists public.user_privacy(user_id uuid primary key references auth.users(id) on delete cascade,last_seen_visibility text not null default 'everyone' check(last_seen_visibility in ('everyone','contacts','nobody')),photo_visibility text not null default 'everyone' check(photo_visibility in ('everyone','contacts','nobody')),status_visibility text not null default 'contacts' check(status_visibility in ('everyone','contacts','nobody','selected')),read_receipts_enabled boolean not null default true,updated_at timestamptz not null default now());
create table if not exists public.statuses(id uuid primary key default gen_random_uuid(),user_id uuid not null references auth.users(id) on delete cascade,kind text not null check(kind in ('text','image','video')),body text,media_path text,background_color text,created_at timestamptz not null default now(),expires_at timestamptz not null default(now()+interval '24 hours'),check((kind='text' and body is not null) or (kind in ('image','video') and media_path is not null)));
create index if not exists statuses_user_expiry_idx on public.statuses(user_id,expires_at desc);
create table if not exists public.status_audience(status_id uuid not null references public.statuses(id) on delete cascade,viewer_id uuid not null references auth.users(id) on delete cascade,primary key(status_id,viewer_id));
create table if not exists public.status_views(status_id uuid not null references public.statuses(id) on delete cascade,viewer_id uuid not null references auth.users(id) on delete cascade,viewed_at timestamptz not null default now(),primary key(status_id,viewer_id));
create table if not exists public.muted_status_users(owner_id uuid not null references auth.users(id) on delete cascade,muted_user_id uuid not null references auth.users(id) on delete cascade,created_at timestamptz not null default now(),primary key(owner_id,muted_user_id),check(owner_id<>muted_user_id));
create table if not exists public.channels(id uuid primary key default gen_random_uuid(),owner_id uuid not null references auth.users(id) on delete cascade,name text not null check(char_length(name) between 1 and 80),description text not null default '',avatar_path text,is_public boolean not null default true,created_at timestamptz not null default now(),updated_at timestamptz not null default now());
create table if not exists public.channel_members(channel_id uuid not null references public.channels(id) on delete cascade,user_id uuid not null references auth.users(id) on delete cascade,role text not null default 'follower' check(role in ('owner','admin','follower')),joined_at timestamptz not null default now(),primary key(channel_id,user_id));
create table if not exists public.channel_posts(id uuid primary key default gen_random_uuid(),channel_id uuid not null references public.channels(id) on delete cascade,author_id uuid not null references auth.users(id) on delete cascade,body text,media_path text,media_type text check(media_type is null or media_type in ('image','video')),created_at timestamptz not null default now(),edited_at timestamptz);
create table if not exists public.channel_post_reactions(post_id uuid not null references public.channel_posts(id) on delete cascade,user_id uuid not null references auth.users(id) on delete cascade,emoji text not null check(char_length(emoji) between 1 and 32),created_at timestamptz not null default now(),primary key(post_id,user_id,emoji));
create table if not exists public.user_reports(id uuid primary key default gen_random_uuid(),reporter_id uuid not null references auth.users(id) on delete cascade,reported_user_id uuid references auth.users(id) on delete set null,message_id uuid references public.messages(id) on delete set null,reason text not null check(char_length(reason) between 3 and 1000),created_at timestamptz not null default now(),status text not null default 'open' check(status in ('open','reviewing','resolved','dismissed')));
create table if not exists public.sticker_packs(id uuid primary key default gen_random_uuid(),title text not null,creator_id uuid references auth.users(id) on delete set null,is_public boolean not null default true,created_at timestamptz not null default now());
create table if not exists public.stickers(id uuid primary key default gen_random_uuid(),pack_id uuid not null references public.sticker_packs(id) on delete cascade,asset_path text not null,alt_text text not null default '',position integer not null default 0);
create table if not exists public.user_sticker_favorites(user_id uuid not null references auth.users(id) on delete cascade,sticker_id uuid not null references public.stickers(id) on delete cascade,created_at timestamptz not null default now(),primary key(user_id,sticker_id));

-- All new tables are RLS protected; per-user and per-conversation policies are created below.
do $$ declare t text; begin
 foreach t in array array['message_reactions','message_pins','group_invites','polls','poll_options','poll_votes','user_privacy','statuses','status_audience','status_views','muted_status_users','channels','channel_members','channel_posts','channel_post_reactions','user_reports','sticker_packs','stickers','user_sticker_favorites']
 loop execute format('alter table public.%I enable row level security',t); end loop;
end $$;

create policy "Members view reactions" on public.message_reactions for select to authenticated using(exists(select 1 from public.messages m where m.id=message_id and private.is_conversation_member(m.conversation_id)));
create policy "Users add own reactions" on public.message_reactions for insert to authenticated with check(user_id=auth.uid() and exists(select 1 from public.messages m where m.id=message_id and private.is_conversation_member(m.conversation_id)));
create policy "Users remove own reactions" on public.message_reactions for delete to authenticated using(user_id=auth.uid());
create policy "Members view pins" on public.message_pins for select to authenticated using(private.is_conversation_member(conversation_id));
create policy "Managers pin messages" on public.message_pins for insert to authenticated with check(private.is_group_manager(conversation_id) and pinned_by=auth.uid() and exists(select 1 from public.messages m where m.id=message_id and m.conversation_id=conversation_id));
create policy "Managers unpin messages" on public.message_pins for delete to authenticated using(private.is_group_manager(conversation_id));
create policy "Members view invites" on public.group_invites for select to authenticated using(private.is_conversation_member(conversation_id));
create policy "Managers create invites" on public.group_invites for insert to authenticated with check(created_by=auth.uid() and private.is_group_manager(conversation_id));
create policy "Invite creators update invites" on public.group_invites for update to authenticated using(created_by=auth.uid() or private.is_group_manager(conversation_id)) with check(created_by=auth.uid() or private.is_group_manager(conversation_id));
create policy "Members view polls" on public.polls for select to authenticated using(private.is_conversation_member(conversation_id));
create policy "Members create polls" on public.polls for insert to authenticated with check(created_by=auth.uid() and private.is_conversation_member(conversation_id));
create policy "Poll creators update polls" on public.polls for update to authenticated using(created_by=auth.uid()) with check(created_by=auth.uid());
create policy "Members view poll options" on public.poll_options for select to authenticated using(exists(select 1 from public.polls p where p.id=poll_id and private.is_conversation_member(p.conversation_id)));
create policy "Poll creators add options" on public.poll_options for insert to authenticated with check(exists(select 1 from public.polls p where p.id=poll_id and p.created_by=auth.uid()));
create policy "Members view votes" on public.poll_votes for select to authenticated using(exists(select 1 from public.poll_options o join public.polls p on p.id=o.poll_id where o.id=poll_option_id and private.is_conversation_member(p.conversation_id)));
create policy "Users vote" on public.poll_votes for insert to authenticated with check(user_id=auth.uid() and exists(select 1 from public.poll_options o join public.polls p on p.id=o.poll_id where o.id=poll_option_id and private.is_conversation_member(p.conversation_id) and (p.closes_at is null or p.closes_at>now())));
create policy "Own privacy settings" on public.user_privacy for all to authenticated using(user_id=auth.uid()) with check(user_id=auth.uid());
create policy "Own statuses" on public.statuses for all to authenticated using(user_id=auth.uid()) with check(user_id=auth.uid());
create policy "Status audience owner" on public.status_audience for all to authenticated using(exists(select 1 from public.statuses s where s.id=status_id and s.user_id=auth.uid())) with check(exists(select 1 from public.statuses s where s.id=status_id and s.user_id=auth.uid()));
create policy "Own status views" on public.status_views for select to authenticated using(viewer_id=auth.uid() or exists(select 1 from public.statuses s where s.id=status_id and s.user_id=auth.uid()));
create policy "Viewers insert status views" on public.status_views for insert to authenticated with check(viewer_id=auth.uid());
create policy "Own muted statuses" on public.muted_status_users for all to authenticated using(owner_id=auth.uid()) with check(owner_id=auth.uid());
create policy "Public or followed channels" on public.channels for select to authenticated using(is_public or exists(select 1 from public.channel_members cm where cm.channel_id=id and cm.user_id=auth.uid()));
create policy "Create own channels" on public.channels for insert to authenticated with check(owner_id=auth.uid());
create policy "Update own channels" on public.channels for update to authenticated using(owner_id=auth.uid()) with check(owner_id=auth.uid());
create policy "Delete own channels" on public.channels for delete to authenticated using(owner_id=auth.uid());
create policy "View channel memberships" on public.channel_members for select to authenticated using(user_id=auth.uid() or exists(select 1 from public.channels c where c.id=channel_id and c.owner_id=auth.uid()));
create policy "Follow public channels" on public.channel_members for insert to authenticated with check(user_id=auth.uid() and role='follower' and exists(select 1 from public.channels c where c.id=channel_id and c.is_public));
create policy "Unfollow own channels" on public.channel_members for delete to authenticated using(user_id=auth.uid() and role='follower');
create policy "View channel posts" on public.channel_posts for select to authenticated using(exists(select 1 from public.channels c where c.id=channel_id and (c.is_public or exists(select 1 from public.channel_members cm where cm.channel_id=c.id and cm.user_id=auth.uid()))));
create policy "Channel owner posts" on public.channel_posts for insert to authenticated with check(author_id=auth.uid() and exists(select 1 from public.channels c where c.id=channel_id and c.owner_id=auth.uid()));
create policy "Channel authors update" on public.channel_posts for update to authenticated using(author_id=auth.uid()) with check(author_id=auth.uid());
create policy "Channel authors delete" on public.channel_posts for delete to authenticated using(author_id=auth.uid());
create policy "View channel reactions" on public.channel_post_reactions for select to authenticated using(exists(select 1 from public.channel_posts p join public.channels c on c.id=p.channel_id where p.id=post_id and c.is_public));
create policy "Own channel reactions" on public.channel_post_reactions for all to authenticated using(user_id=auth.uid()) with check(user_id=auth.uid());
create policy "Submit own reports" on public.user_reports for insert to authenticated with check(reporter_id=auth.uid());
create policy "View own reports" on public.user_reports for select to authenticated using(reporter_id=auth.uid());
create policy "View public sticker packs" on public.sticker_packs for select to authenticated using(is_public or creator_id=auth.uid());
create policy "Create own sticker packs" on public.sticker_packs for insert to authenticated with check(creator_id=auth.uid());
create policy "View stickers in visible packs" on public.stickers for select to authenticated using(exists(select 1 from public.sticker_packs p where p.id=pack_id and (p.is_public or p.creator_id=auth.uid())));
create policy "Manage own sticker pack stickers" on public.stickers for all to authenticated using(exists(select 1 from public.sticker_packs p where p.id=pack_id and p.creator_id=auth.uid())) with check(exists(select 1 from public.sticker_packs p where p.id=pack_id and p.creator_id=auth.uid()));
create policy "Own sticker favorites" on public.user_sticker_favorites for all to authenticated using(user_id=auth.uid()) with check(user_id=auth.uid());

create or replace function public.set_group_member_role(p_conversation_id uuid,p_user_id uuid,p_role text)
returns void language plpgsql security definer set search_path=public,private as $$
declare actor_role text; begin
 if p_role not in ('deputy','admin','moderator','member') then raise exception 'Invalid role'; end if;
 select role into actor_role from public.conversation_members where conversation_id=p_conversation_id and user_id=auth.uid() and not is_banned;
 if actor_role not in ('owner','deputy','admin') then raise exception 'Insufficient permissions'; end if;
 if actor_role<>'owner' and p_role in ('deputy','admin') then raise exception 'Only owner can assign deputy or admin'; end if;
 update public.conversation_members set role=p_role where conversation_id=p_conversation_id and user_id=p_user_id and role<>'owner';
 if not found then raise exception 'Member not found or is owner'; end if;
end $$;
create or replace function public.set_group_member_restriction(p_conversation_id uuid,p_user_id uuid,p_action text,p_enabled boolean)
returns void language plpgsql security definer set search_path=public,private as $$
declare actor_role text; target_role text; begin
 if p_action not in ('mute','ban') then raise exception 'Invalid action'; end if;
 select role into actor_role from public.conversation_members where conversation_id=p_conversation_id and user_id=auth.uid() and not is_banned;
 select role into target_role from public.conversation_members where conversation_id=p_conversation_id and user_id=p_user_id;
 if actor_role is null or target_role is null or target_role='owner' or p_user_id=auth.uid() then raise exception 'Invalid target'; end if;
 if p_action='ban' and actor_role not in ('owner','deputy','admin') then raise exception 'Insufficient permissions'; end if;
 if p_action='mute' and actor_role not in ('owner','deputy','admin','moderator') then raise exception 'Insufficient permissions'; end if;
 if actor_role='moderator' and target_role<>'member' then raise exception 'Insufficient permissions'; end if;
 if p_action='mute' then update public.conversation_members set is_muted=p_enabled where conversation_id=p_conversation_id and user_id=p_user_id;
 else update public.conversation_members set is_banned=p_enabled where conversation_id=p_conversation_id and user_id=p_user_id; end if;
end $$;
create or replace function public.accept_group_invite(p_invite_code text)
returns uuid language plpgsql security definer set search_path=public,private as $$
declare inv public.group_invites%rowtype; begin
 if auth.uid() is null then raise exception 'Authentication required'; end if;
 select * into inv from public.group_invites where invite_code=p_invite_code for update;
 if inv.id is null or inv.revoked_at is not null or (inv.expires_at is not null and inv.expires_at<=now()) or (inv.max_uses is not null and inv.use_count>=inv.max_uses) then raise exception 'Invite invalid or expired'; end if;
 if exists(select 1 from public.conversation_members where conversation_id=inv.conversation_id and user_id=auth.uid() and is_banned) then raise exception 'Banned from group'; end if;
 insert into public.conversation_members(conversation_id,user_id,role) values(inv.conversation_id,auth.uid(),'member') on conflict(conversation_id,user_id) do update set is_banned=false;
 update public.group_invites set use_count=use_count+1 where id=inv.id; return inv.conversation_id;
end $$;
create or replace function public.transfer_group_ownership(p_conversation_id uuid,p_new_owner uuid)
returns void language plpgsql security definer set search_path=public,private as $$
begin
 if not exists(select 1 from public.conversation_members where conversation_id=p_conversation_id and user_id=auth.uid() and role='owner') then raise exception 'Only owner can transfer'; end if;
 if not exists(select 1 from public.conversation_members where conversation_id=p_conversation_id and user_id=p_new_owner and not is_banned) then raise exception 'New owner must be a member'; end if;
 update public.conversation_members set role='admin' where conversation_id=p_conversation_id and user_id=auth.uid();
 update public.conversation_members set role='owner' where conversation_id=p_conversation_id and user_id=p_new_owner;
 update public.conversations set created_by=p_new_owner where id=p_conversation_id and is_group=true;
end $$;
create or replace function private.is_channel_manager(p_channel_id uuid)
returns boolean language sql stable security definer set search_path=public,private as $$
 select exists(select 1 from public.channel_members cm where cm.channel_id=p_channel_id and cm.user_id=auth.uid() and cm.role in ('owner','admin')) or exists(select 1 from public.channels c where c.id=p_channel_id and c.owner_id=auth.uid());
$$;
create or replace function private.add_channel_owner_member()
returns trigger language plpgsql security definer set search_path=public,private as $$
begin insert into public.channel_members(channel_id,user_id,role) values(new.id,new.owner_id,'owner') on conflict(channel_id,user_id) do update set role='owner';return new;end $$;
drop trigger if exists channel_owner_member_after_insert on public.channels;
create trigger channel_owner_member_after_insert after insert on public.channels for each row execute function private.add_channel_owner_member();
create or replace function private.enforce_single_choice_poll()
returns trigger language plpgsql security definer set search_path=public,private as $$
declare multi boolean;begin select p.is_multiple_choice into multi from public.poll_options o join public.polls p on p.id=o.poll_id where o.id=new.poll_option_id;
if coalesce(multi,false)=false then delete from public.poll_votes v using public.poll_options o where v.poll_option_id=o.id and o.poll_id=(select poll_id from public.poll_options where id=new.poll_option_id) and v.user_id=new.user_id and v.poll_option_id<>new.poll_option_id;end if;return new;end $$;
drop trigger if exists poll_single_choice_guard on public.poll_votes;
create trigger poll_single_choice_guard before insert on public.poll_votes for each row execute function private.enforce_single_choice_poll();
grant execute on function public.set_group_member_role(uuid,uuid,text) to authenticated;
grant execute on function public.set_group_member_restriction(uuid,uuid,text,boolean) to authenticated;
grant execute on function public.accept_group_invite(text) to authenticated;
grant execute on function public.transfer_group_ownership(uuid,uuid) to authenticated;
