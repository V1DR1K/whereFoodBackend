alter table why_fun_venues add column scheduled_at timestamp;
alter table why_fun_venues add column cover_photo_id bigint references why_fun_venue_photos(id) on delete set null;
create index idx_why_fun_venues_scheduled_at on why_fun_venues(scheduled_at);
