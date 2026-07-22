alter table place_visits add column visited_at time;
alter table place_visits drop constraint if exists place_visits_place_id_visited_on_key;
create unique index uq_place_visits_legacy_day on place_visits(place_id, visited_on) where visited_at is null;
create unique index uq_place_visits_day_time on place_visits(place_id, visited_on, visited_at) where visited_at is not null;
drop index if exists idx_place_visits_place_visited_on;
create index idx_place_visits_place_when on place_visits(place_id, visited_on desc, visited_at desc nulls last, id desc);

alter table items drop constraint if exists items_visit_id_fkey;
alter table items add constraint items_visit_id_fkey foreign key (visit_id) references place_visits(id) on delete cascade;

alter table place_reviews alter column location drop not null;
alter table place_reviews alter column heating drop not null;
alter table place_reviews alter column bathrooms drop not null;
alter table place_reviews alter column exterior drop not null;
alter table place_reviews alter column seating drop not null;
alter table place_reviews alter column service drop not null;
alter table place_reviews alter column ambiance drop not null;
