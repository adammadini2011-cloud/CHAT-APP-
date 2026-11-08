-- Enforce bans in membership checks and prevent public presence/photo disclosure.
create or replace function private.is_conversation_member(cid uuid, uid uuid default auth.uid())
returns boolean language sql stable security definer set search_path='' as $$
  select exists(
    select 1 from public.conversation_members cm
    where cm.conversation_id=cid and cm.user_id=(select auth.uid()) and cm.is_banned=false
  );
$$;

create or replace view public.profile_directory as
select p.id,p.username,p.display_name,p.bio,
 case when coalesce(pr.photo_visibility,'everyone')='everyone'
   or (coalesce(pr.photo_visibility,'everyone')='contacts' and exists(
     select 1 from public.conversation_members a join public.conversation_members b on b.conversation_id=a.conversation_id
     join public.conversations c on c.id=a.conversation_id and c.is_group=false
     where a.user_id=auth.uid() and b.user_id=p.id
   )) then p.avatar_path else null end as avatar_path,
 case when coalesce(pr.last_seen_visibility,'everyone')='everyone'
   or (coalesce(pr.last_seen_visibility,'everyone')='contacts' and exists(
     select 1 from public.conversation_members a join public.conversation_members b on b.conversation_id=a.conversation_id
     join public.conversations c on c.id=a.conversation_id and c.is_group=false
     where a.user_id=auth.uid() and b.user_id=p.id
   )) then p.is_online else false end as is_online,
 case when coalesce(pr.last_seen_visibility,'everyone')='everyone'
   or (coalesce(pr.last_seen_visibility,'everyone')='contacts' and exists(
     select 1 from public.conversation_members a join public.conversation_members b on b.conversation_id=a.conversation_id
     join public.conversations c on c.id=a.conversation_id and c.is_group=false
     where a.user_id=auth.uid() and b.user_id=p.id
   )) then p.last_seen_at else null end as last_seen_at
from public.profiles p left join public.user_privacy pr on pr.user_id=p.id;
grant select on public.profile_directory to authenticated;
revoke select (avatar_path,is_online,last_seen_at) on public.profiles from authenticated,anon;