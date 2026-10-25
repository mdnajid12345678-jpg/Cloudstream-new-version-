-- Keep canonical keys unique per signed-in Supabase owner.
alter table public.media_items drop constraint if exists media_items_canonical_key_key;
create unique index if not exists media_items_owner_canonical_key_uidx
  on public.media_items(owner_id, canonical_key);
