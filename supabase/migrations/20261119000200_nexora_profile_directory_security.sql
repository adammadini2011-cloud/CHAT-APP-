-- Make the profile directory an invoker-security view.
-- A narrowly-scoped private helper reads each target user's privacy preference
-- without exposing the user_privacy table through the Data API.

create or replace function private.can_view_profile_field(p_target_user uuid,p_field text)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select case
    when (select auth.uid()) is null then false
    when p_target_user = (select auth.uid()) then true
    when p_field = 'photo' then
      case coalesce(
        (select up.photo_visibility from public.user_privacy up where up.user_id=p_target_user),
        'everyone'
      )
        when 'everyone' then true
        when 'contacts' then exists (
          select 1
          from public.conversation_members a
          join public.conversation_members b on b.conversation_id=a.conversation_id
          join public.conversations c on c.id=a.conversation_id and c.is_group=false
          where a.user_id=(select auth.uid()) and b.user_id=p_target_user
        )
        else false
      end
    when p_field = 'presence' then
      case coalesce(
        (select up.last_seen_visibility from public.user_privacy up where up.user_id=p_target_user),
        'everyone'
      )
        when 'everyone' then true
        when 'contacts' then exists (
          select 1
          from public.conversation_members a
          join public.conversation_members b on b.conversation_id=a.conversation_id
          join public.conversations c on c.id=a.conversation_id and c.is_group=false
          where a.user_id=(select auth.uid()) and b.user_id=p_target_user
        )
        else false
      end
    else false
  end;
$$;

revoke all on function private.can_view_profile_field(uuid,text) from public,anon;
grant execute on function private.can_view_profile_field(uuid,text) to authenticated;

create or replace view public.profile_directory
with (security_invoker=true)
as
select
  p.id,
  p.username,
  p.display_name,
  p.bio,
  case when private.can_view_profile_field(p.id,'photo') then p.avatar_path else null end as avatar_path,
  case when private.can_view_profile_field(p.id,'presence') then p.is_online else false end as is_online,
  case when private.can_view_profile_field(p.id,'presence') then p.last_seen_at else null end as last_seen_at
from public.profiles p;

grant select on public.profile_directory to authenticated;
