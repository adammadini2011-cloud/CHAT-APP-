-- Enable Supabase Realtime postgres_changes for server channel messages.
do $$
begin
  if exists (select 1 from pg_publication where pubname='supabase_realtime')
     and not exists (
       select 1 from pg_publication_tables
       where pubname='supabase_realtime'
         and schemaname='public'
         and tablename='server_channel_messages'
     ) then
    execute 'alter publication supabase_realtime add table public.server_channel_messages';
  end if;
end
$$;
