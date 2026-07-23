alter table places add column updated_by bigint references users(id);
update places set updated_by = created_by where updated_by is null;
alter table places alter column updated_by set not null;

alter table place_visits add column updated_by bigint references users(id);
update place_visits set updated_by = created_by where updated_by is null;
alter table place_visits alter column updated_by set not null;

create table place_visit_photos (
 id bigserial primary key,
 visit_id bigint not null references place_visits(id) on delete cascade,
 image_base64 text not null,
 thumbnail_base64 text not null,
 width integer not null,
 height integer not null,
 position integer not null check(position >= 0),
 created_by bigint not null references users(id),
 created_at timestamptz not null default now(),
 unique(visit_id, position)
);
create index idx_place_visit_photos_visit_position on place_visit_photos(visit_id, position);

alter table place_visits add column cover_photo_id bigint references place_visit_photos(id) on delete set null;

create table place_visit_reviews (
 id bigserial primary key,
 visit_id bigint not null references place_visits(id) on delete cascade,
 author_id bigint not null references users(id),
 updated_by bigint not null references users(id),
 overall smallint not null check(overall between 1 and 5),
 comment varchar(2000),
 taste smallint check(taste between 1 and 5),
 price smallint check(price between 1 and 5),
 location smallint check(location between 1 and 5),
 heating smallint check(heating between 1 and 5),
 bathrooms smallint check(bathrooms between 1 and 5),
 exterior smallint check(exterior between 1 and 5),
 seating smallint check(seating between 1 and 5),
 service smallint check(service between 1 and 5),
 ambiance smallint check(ambiance between 1 and 5),
 created_at timestamptz not null default now(),
 updated_at timestamptz not null default now(),
 unique(visit_id, author_id)
);
create index idx_place_visit_reviews_visit on place_visit_reviews(visit_id, author_id);

-- Place media was historically a visit-level cover; item media follows it in display order.
with place_targets as (
 select photo.id as source_id,
        coalesce(
          (select visit.id from place_visits visit join places place on place.id = visit.place_id
           where place.id = photo.place_id and place.name = 'Un Churrito Rosario' and visit.visited_on = date '2026-07-17' limit 1),
          (select visit.id from place_visits visit where visit.place_id = photo.place_id order by visit.visited_on desc, visit.id desc limit 1)
        ) as visit_id,
        photo.image_base64, photo.thumbnail_base64, photo.width, photo.height, photo.created_at
 from place_photos photo
)
insert into place_visit_photos(visit_id, image_base64, thumbnail_base64, width, height, position, created_by, created_at)
select target.visit_id, target.image_base64, target.thumbnail_base64, target.width, target.height, 0, visit.created_by, target.created_at
from place_targets target
join place_visits visit on visit.id = target.visit_id;

with item_targets as (
 select photo.*, item.visit_id, item.created_by,
        row_number() over (partition by item.visit_id order by photo.created_at, photo.id) - 1
          + case when exists (select 1 from place_visit_photos existing where existing.visit_id = item.visit_id) then 1 else 0 end as photo_position
 from item_photos photo
 join items item on item.id = photo.item_id
 where item.deleted_at is null and item.id <> 6
)
insert into place_visit_photos(visit_id, image_base64, thumbnail_base64, width, height, position, created_by, created_at)
select visit_id, image_base64, thumbnail_base64, width, height, photo_position, created_by, created_at
from item_targets;

update place_visits visit
set cover_photo_id = photo.id
from place_visit_photos photo
where photo.visit_id = visit.id and photo.position = 0;

-- Merge item and place review metrics per visit and author into one experience review.
with place_review_visits as (
 select review.id,
        coalesce(
          (select visit.id from place_visits visit join places place on place.id = visit.place_id
           where place.id = review.place_id and place.name = 'Un Churrito Rosario' and visit.visited_on = date '2026-07-17' limit 1),
          (select visit.id from items item join place_visits visit on visit.id = item.visit_id
           where visit.place_id = review.place_id and item.created_by = review.author_id and item.deleted_at is null and item.id <> 6
           order by visit.visited_on desc, visit.id desc, item.id desc limit 1),
          (select visit.id from place_visits visit where visit.place_id = review.place_id order by visit.visited_on desc, visit.id desc limit 1)
        ) as visit_id,
        review.author_id, review.comment, review.location, review.heating, review.bathrooms, review.exterior, review.seating, review.service, review.ambiance,
        review.created_at, review.updated_at
 from place_reviews review
), source_reviews as (
 select item.visit_id, review.author_id, review.comment, review.taste, review.price,
        null::smallint as location, null::smallint as heating, null::smallint as bathrooms, null::smallint as exterior,
        null::smallint as seating, null::smallint as service, null::smallint as ambiance,
        review.created_at, review.updated_at, 1 as source_rank, review.id as source_id
 from item_reviews review
 join items item on item.id = review.item_id
 where item.deleted_at is null and item.id <> 6
 union all
 select visit_id, author_id, comment, null, null, location, heating, bathrooms, exterior, seating, service, ambiance,
        created_at, updated_at, 2, id
 from place_review_visits
 where visit_id is not null
), source_summary as (
 select source.visit_id, source.author_id,
        string_agg(source.comment, E'\n\n' order by source.created_at, source.source_rank, source.source_id) filter (where source.comment is not null and btrim(source.comment) <> '') as comment,
        round(avg(source.taste::numeric) filter (where source.taste is not null), 0)::smallint as taste,
        round(avg(source.price::numeric) filter (where source.price is not null), 0)::smallint as price,
        round(avg(source.location::numeric) filter (where source.location is not null), 0)::smallint as location,
        round(avg(source.heating::numeric) filter (where source.heating is not null), 0)::smallint as heating,
        round(avg(source.bathrooms::numeric) filter (where source.bathrooms is not null), 0)::smallint as bathrooms,
        round(avg(source.exterior::numeric) filter (where source.exterior is not null), 0)::smallint as exterior,
        round(avg(source.seating::numeric) filter (where source.seating is not null), 0)::smallint as seating,
        round(avg(source.service::numeric) filter (where source.service is not null), 0)::smallint as service,
        round(avg(source.ambiance::numeric) filter (where source.ambiance is not null), 0)::smallint as ambiance,
        min(source.created_at) as created_at, max(source.updated_at) as updated_at
 from source_reviews source
 group by source.visit_id, source.author_id
), metric_values as (
 select visit_id, author_id, taste::numeric as metric from source_reviews where taste is not null
 union all select visit_id, author_id, price::numeric from source_reviews where price is not null
 union all select visit_id, author_id, location::numeric from source_reviews where location is not null
 union all select visit_id, author_id, heating::numeric from source_reviews where heating is not null
 union all select visit_id, author_id, bathrooms::numeric from source_reviews where bathrooms is not null
 union all select visit_id, author_id, exterior::numeric from source_reviews where exterior is not null
 union all select visit_id, author_id, seating::numeric from source_reviews where seating is not null
 union all select visit_id, author_id, service::numeric from source_reviews where service is not null
 union all select visit_id, author_id, ambiance::numeric from source_reviews where ambiance is not null
), metric_summary as (
 select visit_id, author_id, round(avg(metric), 0)::smallint as overall
 from metric_values
 group by visit_id, author_id
), merged as (
 select source.*, metrics.overall
 from source_summary source
 join metric_summary metrics on metrics.visit_id = source.visit_id and metrics.author_id = source.author_id
)
insert into place_visit_reviews(visit_id, author_id, updated_by, overall, comment, taste, price, location, heating, bathrooms, exterior, seating, service, ambiance, created_at, updated_at)
select visit_id, author_id, author_id, overall, comment, taste, price, location, heating, bathrooms, exterior, seating, service, ambiance, created_at, updated_at
from merged;

-- The duplicate place is deleted only after verifying that no legacy or new relation references it.
delete from place_highlight_tags tag
where tag.place_id = 6
  and not exists (select 1 from place_visits visit where visit.place_id = tag.place_id)
  and not exists (select 1 from place_reviews review where review.place_id = tag.place_id)
  and not exists (select 1 from place_photos photo where photo.place_id = tag.place_id);

delete from places place
where place.id = 6
  and not exists (select 1 from items item where item.visit_id in (select visit.id from place_visits visit where visit.place_id = place.id))
  and not exists (select 1 from place_visits visit where visit.place_id = place.id)
  and not exists (select 1 from place_reviews review where review.place_id = place.id)
  and not exists (select 1 from place_photos photo where photo.place_id = place.id)
  and not exists (select 1 from place_highlight_tags tag where tag.place_id = place.id);
