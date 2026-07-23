alter table films add column updated_by bigint references users(id);
update films set updated_by = created_by where updated_by is null;
alter table films alter column updated_by set not null;

alter table film_views add column updated_by bigint references users(id);
update film_views set updated_by = created_by where updated_by is null;
alter table film_views alter column updated_by set not null;

alter table film_reviews add column updated_by bigint references users(id);
update film_reviews set updated_by = author_id where updated_by is null;
alter table film_reviews alter column updated_by set not null;

create table film_view_photos (
 id bigserial primary key,
 view_id bigint not null references film_views(id) on delete cascade,
 image_base64 text not null,
 thumbnail_base64 text not null,
 width integer not null,
 height integer not null,
 position integer not null check(position >= 0),
 created_by bigint not null references users(id),
 created_at timestamptz not null default now(),
 unique(view_id, position)
);
create index idx_film_view_photos_view_position on film_view_photos(view_id, position);

with targets as (
 select photo.id,
        (select view.id from film_views view where view.film_id = photo.film_id order by view.watched_on desc, view.watched_at desc nulls last, view.id desc limit 1) as view_id,
        photo.image_base64, photo.thumbnail_base64, photo.width, photo.height, photo.created_at
 from film_photos photo
)
insert into film_view_photos(view_id, image_base64, thumbnail_base64, width, height, position, created_by, created_at)
select target.view_id, target.image_base64, target.thumbnail_base64, target.width, target.height, 0, view.created_by, target.created_at
from targets target
join film_views view on view.id = target.view_id;

delete from film_photos photo
where exists (select 1 from film_views view where view.film_id = photo.film_id);
