-- V27 copied the one-to-one place profile into a selected visit gallery. Keep
-- the legacy profile and remove only the row with V27's exact target and data.
create temporary table v31_affected_place_visits (visit_id bigint primary key) on commit drop;

with copied_place_profiles as (
 select gallery.id, gallery.visit_id
 from place_visit_photos gallery
 join place_visits visit on visit.id = gallery.visit_id
 join place_photos profile on profile.place_id = visit.place_id
 where gallery.position = 0
   and gallery.created_by = visit.created_by
   and gallery.image_base64 = profile.image_base64
   and gallery.thumbnail_base64 = profile.thumbnail_base64
   and gallery.width = profile.width
   and gallery.height = profile.height
   and gallery.created_at = profile.created_at
   and gallery.visit_id = coalesce(
    (select target.id
     from place_visits target
     join places place on place.id = target.place_id
     where place.id = profile.place_id
       and place.name = 'Un Churrito Rosario'
       and target.visited_on = date '2026-07-17'
     order by target.id
     limit 1),
    (select target.id
     from place_visits target
     where target.place_id = profile.place_id
     order by target.visited_on desc, target.id desc
     limit 1)
   )
), removed as (
 delete from place_visit_photos gallery
 using copied_place_profiles copied
 where gallery.id = copied.id
 returning gallery.visit_id
)
insert into v31_affected_place_visits(visit_id)
select distinct visit_id from removed;

update place_visits visit
set cover_photo_id = (
 select photo.id
 from place_visit_photos photo
 where photo.visit_id = visit.id
 order by photo.position, photo.id
 limit 1
)
where visit.id in (select visit_id from v31_affected_place_visits);

-- Recipes need their own parent profile. The root's immutable legacy values
-- identify the V30 recipe after V30 removed the temporary legacy id column.
create table recipe_photos (
 id bigserial primary key,
 recipe_id bigint not null unique references recipes(id) on delete cascade,
 image_base64 text not null,
 thumbnail_base64 text not null,
 width integer not null,
 height integer not null,
 created_at timestamptz not null default now()
);

with recipe_roots as (
 select legacy.id as legacy_root_id, recipe.id as recipe_id
 from home_recipes legacy
 join recipes recipe on recipe.name = legacy.name
  and recipe.source_url is not distinct from legacy.recipe_url
  and recipe.created_by = legacy.author_id
  and recipe.created_at = legacy.created_at
  and recipe.updated_at = legacy.updated_at
 where legacy.repeated_from_id is null
)
insert into recipe_photos(recipe_id, image_base64, thumbnail_base64, width, height, created_at)
select root.recipe_id, photo.image_base64, photo.thumbnail_base64, photo.width, photo.height, photo.created_at
from recipe_roots root
join home_recipe_photos photo on photo.recipe_id = root.legacy_root_id
on conflict (recipe_id) do nothing;

create temporary table v31_affected_cookings (cooking_id bigint primary key) on commit drop;

with copied_recipe_profiles as (
 select gallery.id, gallery.cooking_id
 from cooking_photos gallery
 join cookings cooking on cooking.id = gallery.cooking_id
 join recipes recipe on recipe.id = cooking.recipe_id
 join home_recipes legacy on legacy.repeated_from_id is null
  and recipe.name = legacy.name
  and recipe.source_url is not distinct from legacy.recipe_url
  and recipe.created_by = legacy.author_id
  and recipe.created_at = legacy.created_at
  and recipe.updated_at = legacy.updated_at
  and cooking.home = legacy.home
  and cooking.servings = legacy.servings
  and cooking.cooked_on = legacy.prepared_on
  and cooking.meal_type = legacy.meal_type
  and cooking.created_by = legacy.author_id
  and cooking.created_at = legacy.created_at
 join home_recipe_photos legacy_photo on legacy_photo.recipe_id = legacy.id
 join recipe_photos profile on profile.recipe_id = recipe.id
  and profile.image_base64 = legacy_photo.image_base64
  and profile.thumbnail_base64 = legacy_photo.thumbnail_base64
  and profile.width = legacy_photo.width
  and profile.height = legacy_photo.height
  and profile.created_at = legacy_photo.created_at
 where gallery.position = 0
   and gallery.created_by = cooking.created_by
   and gallery.image_base64 = legacy_photo.image_base64
   and gallery.thumbnail_base64 = legacy_photo.thumbnail_base64
   and gallery.width = legacy_photo.width
   and gallery.height = legacy_photo.height
   and gallery.created_at = legacy_photo.created_at
), removed as (
 delete from cooking_photos gallery
 using copied_recipe_profiles copied
 where gallery.id = copied.id
 returning gallery.cooking_id
)
insert into v31_affected_cookings(cooking_id)
select distinct cooking_id from removed;

update cookings cooking
set cover_photo_id = (
 select photo.id
 from cooking_photos photo
 where photo.cooking_id = cooking.id
 order by photo.position, photo.id
 limit 1
)
where cooking.id in (select cooking_id from v31_affected_cookings);

-- V29 moved a film's profile to position zero of its most recent view.
-- Recreate the profile before deleting exactly that selected gallery row.
create temporary table v31_film_profile_copies (film_id bigint primary key, photo_id bigint not null) on commit drop;

with candidates as (
 select distinct on (view.film_id)
  view.film_id, gallery.id as photo_id, gallery.image_base64, gallery.thumbnail_base64,
  gallery.width, gallery.height, gallery.created_at
 from film_views view
 join film_view_photos gallery on gallery.view_id = view.id and gallery.position = 0
 left join film_photos profile on profile.film_id = view.film_id
 where profile.id is null
 order by view.film_id, view.watched_on desc, view.watched_at desc nulls last, view.id desc, gallery.id
), restored as (
 insert into film_photos(film_id, image_base64, thumbnail_base64, width, height, created_at)
 select film_id, image_base64, thumbnail_base64, width, height, created_at
 from candidates
 on conflict (film_id) do nothing
 returning film_id, image_base64, thumbnail_base64, width, height, created_at
)
insert into v31_film_profile_copies(film_id, photo_id)
select candidate.film_id, candidate.photo_id
from candidates candidate
join restored profile on profile.film_id = candidate.film_id
 and profile.image_base64 = candidate.image_base64
 and profile.thumbnail_base64 = candidate.thumbnail_base64
 and profile.width = candidate.width
 and profile.height = candidate.height
 and profile.created_at = candidate.created_at;

delete from film_view_photos gallery
using v31_film_profile_copies copied
where gallery.id = copied.photo_id;

-- V28 duplicated each parent venue image into its initial visit gallery.
create temporary table v31_affected_why_fun_visits (visit_id bigint primary key) on commit drop;

with copied_activity_profiles as (
 select gallery.id, gallery.visit_id
 from why_fun_visit_photos gallery
 join why_fun_visits visit on visit.id = gallery.visit_id
 join why_fun_venues venue on venue.id = visit.venue_id
 join why_fun_venue_photos profile on profile.venue_id = venue.id
 where visit.created_by = venue.created_by
   and visit.created_at = venue.created_at
   and gallery.created_by = visit.created_by
   and gallery.image_base64 = profile.image_base64
   and gallery.thumbnail_base64 = profile.thumbnail_base64
   and gallery.width = profile.width
   and gallery.height = profile.height
   and gallery.created_at = profile.created_at
   and gallery.position = (
    select count(*) - 1
    from why_fun_venue_photos prior
    where prior.venue_id = profile.venue_id
      and prior.id <= profile.id
   )
), removed as (
 delete from why_fun_visit_photos gallery
 using copied_activity_profiles copied
 where gallery.id = copied.id
 returning gallery.visit_id
)
insert into v31_affected_why_fun_visits(visit_id)
select distinct visit_id from removed;

update why_fun_visits visit
set cover_photo_id = (
 select photo.id
 from why_fun_visit_photos photo
 where photo.visit_id = visit.id
 order by photo.position, photo.id
 limit 1
)
where visit.id in (select visit_id from v31_affected_why_fun_visits);

update why_fun_venues venue
set cover_photo_id = (
 select photo.id
 from why_fun_venue_photos photo
 where photo.venue_id = venue.id
 order by photo.id
 limit 1
)
where venue.id in (
 select visit.venue_id
 from why_fun_visits visit
 where visit.id in (select visit_id from v31_affected_why_fun_visits)
);
