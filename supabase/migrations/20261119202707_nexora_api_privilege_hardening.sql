-- Explicit least-privilege Data API grants for all NEXORA tables.
-- Supabase default privileges may grant broader table privileges; reset them
-- for the client roles before granting only the operations used by the app.

revoke all on table public.servers, public.server_members, public.server_channels,
  public.server_channel_messages, public.server_invites
  from public, anon, authenticated;

grant select,insert,update,delete on public.servers to authenticated;
grant select,insert,delete on public.server_members to authenticated;
grant select,insert,update,delete on public.server_channels to authenticated;
grant select,insert,update,delete on public.server_channel_messages to authenticated;
grant select,insert,update on public.server_invites to authenticated;

revoke all on table public.call_sessions, public.call_signals
  from public, anon, authenticated;
grant select,insert on public.call_sessions to authenticated;
grant update(status,accepted_at,ended_at) on public.call_sessions to authenticated;
grant select,insert on public.call_signals to authenticated;
grant update(processed_at) on public.call_signals to authenticated;
