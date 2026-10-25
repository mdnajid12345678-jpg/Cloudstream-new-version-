-- Adds explicit season/episode hierarchy to CloudStream imports.
alter table public.media_items
  drop constraint if exists media_items_content_type_check;

alter table public.media_items
  add constraint media_items_content_type_check
  check (content_type = any (array[
    'anime','movie','series','tv','episode','ova','ona','special','other'
  ]));

alter table public.media_items
  add column if not exists parent_media_id uuid references public.media_items(id) on delete cascade,
  add column if not exists season_number integer,
  add column if not exists episode_number integer,
  add column if not exists episode_name text,
  add column if not exists provider_name text;

alter table public.media_sources
  add column if not exists language text;

create index if not exists media_items_parent_episode_idx
  on public.media_items(parent_media_id, season_number, episode_number);
create index if not exists media_items_provider_idx
  on public.media_items(provider_name);

grant select, insert, update, delete on public.media_items to authenticated;
grant select, insert, update, delete on public.media_sources to authenticated;
