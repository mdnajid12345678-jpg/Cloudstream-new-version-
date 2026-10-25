-- Allow explicit season rows in the media hierarchy.
alter table public.media_items drop constraint if exists media_items_content_type_check;
alter table public.media_items add constraint media_items_content_type_check
check (content_type = any (array['anime','movie','series','tv','season','episode','ova','ona','special','other']));
