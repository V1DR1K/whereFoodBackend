alter table why_fun_venues add column updated_by bigint references users(id);
update why_fun_venues set updated_by = created_by where updated_by is null;
alter table why_fun_venues alter column updated_by set not null;

create table why_fun_visits (
 id bigserial primary key,
 venue_id bigint not null references why_fun_venues(id) on delete cascade,
 scheduled_at timestamp,
 created_by bigint not null references users(id),
 updated_by bigint not null references users(id),
 created_at timestamptz not null default now(),
 updated_at timestamptz not null default now()
);
create index idx_why_fun_visits_venue_scheduled on why_fun_visits(venue_id, scheduled_at desc, id desc);

create table why_fun_visit_photos (
 id bigserial primary key,
 visit_id bigint not null references why_fun_visits(id) on delete cascade,
 image_base64 text not null,
 thumbnail_base64 text not null,
 width integer not null,
 height integer not null,
 position integer not null check(position >= 0),
 created_by bigint not null references users(id),
 created_at timestamptz not null default now(),
 unique(visit_id, position)
);
create index idx_why_fun_visit_photos_visit_position on why_fun_visit_photos(visit_id, position);

alter table why_fun_visits add column cover_photo_id bigint references why_fun_visit_photos(id) on delete set null;

create table why_fun_visit_reviews (
 id bigserial primary key,
 visit_id bigint not null references why_fun_visits(id) on delete cascade,
 author_id bigint not null references users(id),
 updated_by bigint not null references users(id),
 rating smallint not null check(rating between 1 and 5),
 comment varchar(1000),
 created_at timestamptz not null default now(),
 updated_at timestamptz not null default now(),
 unique(visit_id, author_id)
);
create index idx_why_fun_visit_reviews_visit on why_fun_visit_reviews(visit_id, author_id);

insert into why_fun_visits(venue_id, scheduled_at, created_by, updated_by, created_at, updated_at)
select id, scheduled_at, created_by, created_by, created_at, updated_at
from why_fun_venues;

insert into why_fun_visit_photos(visit_id, image_base64, thumbnail_base64, width, height, position, created_by, created_at)
select visit.id, photo.image_base64, photo.thumbnail_base64, photo.width, photo.height,
       row_number() over (partition by photo.venue_id order by photo.id) - 1,
       visit.created_by, photo.created_at
from why_fun_venue_photos photo
join why_fun_visits visit on visit.venue_id = photo.venue_id;

update why_fun_visits visit
set cover_photo_id = photo.id
from why_fun_venues venue, why_fun_venue_photos legacy, why_fun_visit_photos photo
where visit.venue_id = venue.id
  and legacy.id = venue.cover_photo_id
  and photo.visit_id = visit.id
  and photo.position = (select count(*) - 1 from why_fun_venue_photos prior where prior.venue_id = venue.id and prior.id <= legacy.id);

insert into why_fun_visit_reviews(visit_id, author_id, updated_by, rating, comment, created_at, updated_at)
select visit.id, review.author_id, review.author_id, review.rating, review.comment, review.created_at, review.updated_at
from why_fun_venue_reviews review
join why_fun_visits visit on visit.venue_id = review.venue_id;
