alter table film_views add column watched_at time;
alter table film_views drop constraint if exists film_views_film_id_watched_on_key;
create unique index uq_film_views_legacy_day on film_views(film_id, watched_on) where watched_at is null;
create unique index uq_film_views_day_time on film_views(film_id, watched_on, watched_at) where watched_at is not null;
drop index if exists idx_film_views_film_watched_on;
create index idx_film_views_film_when on film_views(film_id, watched_on desc, watched_at desc nulls last, id desc);
