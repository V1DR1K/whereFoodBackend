-- Experiences are date-based. Opening hours remain on the WhyFun venue.
-- Retain one date-only film view when older records differ only by time.
with duplicate_reviews as (
 select review.id
 from film_reviews review
 join film_views duplicate on duplicate.id = review.view_id
 join lateral (
  select retained.id
  from film_views retained
  where retained.film_id = duplicate.film_id
    and retained.watched_on = duplicate.watched_on
  order by retained.id
  limit 1
 ) retained on retained.id <> duplicate.id
 join film_reviews existing on existing.view_id = retained.id and existing.author_id = review.author_id
)
delete from film_reviews where id in (select id from duplicate_reviews);

with duplicate_views as (
 select duplicate.id, retained.id as retained_id
 from film_views duplicate
 join lateral (
  select retained.id
  from film_views retained
  where retained.film_id = duplicate.film_id
    and retained.watched_on = duplicate.watched_on
  order by retained.id
  limit 1
 ) retained on retained.id <> duplicate.id
)
update film_reviews review
set view_id = duplicate.retained_id
from duplicate_views duplicate
where review.view_id = duplicate.id;

delete from film_views duplicate
using film_views retained
where duplicate.film_id = retained.film_id
  and duplicate.watched_on = retained.watched_on
  and duplicate.id > retained.id;

drop index if exists uq_film_views_legacy_day;
drop index if exists uq_film_views_day_time;
drop index if exists idx_film_views_film_when;
alter table film_views drop column watched_at;
create unique index uq_film_views_day on film_views(film_id, watched_on);
create index idx_film_views_film_day on film_views(film_id, watched_on desc, id desc);

alter table why_fun_venues alter column scheduled_at type date using scheduled_at::date;
alter table why_fun_visits alter column scheduled_at type date using scheduled_at::date;

alter table film_reviews add column favorite_character varchar(300);

-- A view image becomes a film profile only if the parent does not have one.
with candidates as (
 select distinct on (view.film_id)
  view.film_id, photo.image_base64, photo.thumbnail_base64, photo.width, photo.height, photo.created_at
 from film_views view
 join film_view_photos photo on photo.view_id = view.id
 left join film_photos profile on profile.film_id = view.film_id
 where profile.id is null
 order by view.film_id, view.watched_on desc, view.id desc, photo.position, photo.id
)
insert into film_photos(film_id, image_base64, thumbnail_base64, width, height, created_at)
select film_id, image_base64, thumbnail_base64, width, height, created_at
from candidates
on conflict (film_id) do nothing;

drop table film_view_photos;

-- Cooking galleries are retired. Preserve their first image only when the
-- reusable recipe does not yet have its own profile.
with candidates as (
 select distinct on (cooking.recipe_id)
  cooking.recipe_id, photo.image_base64, photo.thumbnail_base64, photo.width, photo.height, photo.created_at
 from cookings cooking
 join cooking_photos photo on photo.cooking_id = cooking.id
 left join recipe_photos profile on profile.recipe_id = cooking.recipe_id
 where profile.id is null
 order by cooking.recipe_id, cooking.cooked_on desc, cooking.id desc, photo.position, photo.id
)
insert into recipe_photos(recipe_id, image_base64, thumbnail_base64, width, height, created_at)
select recipe_id, image_base64, thumbnail_base64, width, height, created_at
from candidates
on conflict (recipe_id) do nothing;

alter table cookings drop column cover_photo_id;
drop table cooking_photos;

-- Venue metrics belong to the place itself, not to one visit.
with latest_visit_metrics as (
 select distinct on (visit.place_id, review.author_id)
  visit.place_id, review.author_id, review.comment, review.location, review.heating,
  review.bathrooms, review.exterior, review.seating, review.service, review.ambiance,
  review.created_at, review.updated_at
 from place_visit_reviews review
 join place_visits visit on visit.id = review.visit_id
 where review.location is not null or review.heating is not null or review.bathrooms is not null
    or review.exterior is not null or review.seating is not null or review.service is not null
    or review.ambiance is not null
 order by visit.place_id, review.author_id, review.updated_at desc, review.id desc
)
insert into place_reviews(place_id, author_id, comment, location, heating, bathrooms, exterior, seating, service, ambiance, created_at, updated_at)
select place_id, author_id, comment, location, heating, bathrooms, exterior, seating, service, ambiance, created_at, updated_at
from latest_visit_metrics
on conflict (place_id, author_id) do update
set comment = coalesce(place_reviews.comment, excluded.comment),
    location = coalesce(excluded.location, place_reviews.location),
    heating = coalesce(excluded.heating, place_reviews.heating),
    bathrooms = coalesce(excluded.bathrooms, place_reviews.bathrooms),
    exterior = coalesce(excluded.exterior, place_reviews.exterior),
    seating = coalesce(excluded.seating, place_reviews.seating),
    service = coalesce(excluded.service, place_reviews.service),
    ambiance = coalesce(excluded.ambiance, place_reviews.ambiance),
    updated_at = greatest(place_reviews.updated_at, excluded.updated_at);

alter table place_visit_reviews
 drop column location,
 drop column heating,
 drop column bathrooms,
 drop column exterior,
 drop column seating,
 drop column service,
 drop column ambiance;
