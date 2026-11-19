-- WebRTC direct-call signaling. Media is peer-to-peer; this database stores
-- only call state and SDP/ICE signaling payloads, never audio/video media.
create table if not exists public.call_sessions (
  id uuid primary key default gen_random_uuid(),
  conversation_id uuid not null references public.conversations(id) on delete cascade,
  caller_id uuid not null references auth.users(id) on delete cascade,
  callee_id uuid not null references auth.users(id) on delete cascade,
  call_type text not null check (call_type in ('audio','video')),
  status text not null default 'ringing' check (status in ('ringing','accepted','rejected','ended','missed')),
  created_at timestamptz not null default now(),
  accepted_at timestamptz,
  ended_at timestamptz,
  check (caller_id <> callee_id)
);
create index if not exists call_sessions_callee_status_idx on public.call_sessions(callee_id,status,created_at desc);
create index if not exists call_sessions_conversation_idx on public.call_sessions(conversation_id,created_at desc);

create table if not exists public.call_signals (
  id uuid primary key default gen_random_uuid(),
  call_id uuid not null references public.call_sessions(id) on delete cascade,
  sender_id uuid not null references auth.users(id) on delete cascade,
  recipient_id uuid not null references auth.users(id) on delete cascade,
  signal_type text not null check (signal_type in ('offer','answer','ice','hangup')),
  payload jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  processed_at timestamptz,
  check (sender_id <> recipient_id)
);
create index if not exists call_signals_recipient_pending_idx on public.call_signals(call_id,recipient_id,created_at) where processed_at is null;

alter table public.call_sessions enable row level security;
alter table public.call_signals enable row level security;

drop policy if exists "Call participants view sessions" on public.call_sessions;
create policy "Call participants view sessions" on public.call_sessions
for select to authenticated
using (caller_id=(select auth.uid()) or callee_id=(select auth.uid()));

drop policy if exists "Conversation members start direct calls" on public.call_sessions;
create policy "Conversation members start direct calls" on public.call_sessions
for insert to authenticated
with check (
  caller_id=(select auth.uid())
  and status='ringing'
  and exists (
    select 1 from public.conversations c
    where c.id=conversation_id and c.is_group=false
  )
  and private.is_conversation_member(conversation_id)
  and exists (
    select 1 from public.conversation_members cm
    where cm.conversation_id=call_sessions.conversation_id
      and cm.user_id=call_sessions.callee_id
      and cm.is_banned=false
  )
  and not exists (
    select 1 from public.user_blocks ub
    where (ub.blocker_id=call_sessions.caller_id and ub.blocked_id=call_sessions.callee_id)
       or (ub.blocker_id=call_sessions.callee_id and ub.blocked_id=call_sessions.caller_id)
  )
);

drop policy if exists "Call participants update call status" on public.call_sessions;
create policy "Call participants update call status" on public.call_sessions
for update to authenticated
using (caller_id=(select auth.uid()) or callee_id=(select auth.uid()))
with check (caller_id=(select auth.uid()) or callee_id=(select auth.uid()));

drop policy if exists "Call participants receive signals" on public.call_signals;
create policy "Call participants receive signals" on public.call_signals
for select to authenticated
using (recipient_id=(select auth.uid()));

drop policy if exists "Call participants send signals" on public.call_signals;
create policy "Call participants send signals" on public.call_signals
for insert to authenticated
with check (
  sender_id=(select auth.uid())
  and exists (
    select 1 from public.call_sessions cs
    where cs.id=call_signals.call_id
      and ((cs.caller_id=call_signals.sender_id and cs.callee_id=call_signals.recipient_id)
        or (cs.callee_id=call_signals.sender_id and cs.caller_id=call_signals.recipient_id))
      and cs.status in ('ringing','accepted')
  )
);

drop policy if exists "Recipients mark signals processed" on public.call_signals;
create policy "Recipients mark signals processed" on public.call_signals
for update to authenticated
using (recipient_id=(select auth.uid()) and processed_at is null)
with check (recipient_id=(select auth.uid()));

grant select,insert on public.call_sessions to authenticated;
grant update(status,accepted_at,ended_at) on public.call_sessions to authenticated;
grant select,insert on public.call_signals to authenticated;
grant update(processed_at) on public.call_signals to authenticated;
