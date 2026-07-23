create temporary table legacy_home_recipe_roots (legacy_id bigint primary key, root_id bigint not null) on commit drop;

with recursive walk(legacy_id, current_id, repeated_from_id, path) as (
 select id, id, repeated_from_id, array[id]
 from home_recipes
 union all
 select walk.legacy_id, parent.id, parent.repeated_from_id, walk.path || parent.id
 from walk
 join home_recipes parent on parent.id = walk.repeated_from_id
 where not parent.id = any(walk.path)
), resolved as (
 select legacy_id, coalesce(min(current_id) filter (where repeated_from_id is null), min(current_id)) as root_id
 from walk
 group by legacy_id
)
insert into legacy_home_recipe_roots(legacy_id, root_id)
select legacy_id, root_id from resolved;

create table recipes (
 id bigserial primary key,
 legacy_root_id bigint unique,
 name varchar(160) not null,
 source_url varchar(1000),
 created_by bigint not null references users(id),
 updated_by bigint not null references users(id),
 created_at timestamptz not null default now(),
 updated_at timestamptz not null default now()
);

create table recipe_ingredients (
 id bigserial primary key,
 recipe_id bigint not null references recipes(id) on delete cascade,
 name varchar(160) not null,
 quantity numeric(10,2),
 unit varchar(30) not null,
 position integer not null
);
create index idx_recipe_ingredients_recipe_position on recipe_ingredients(recipe_id, position);

create table recipe_steps (
 id bigserial primary key,
 recipe_id bigint not null references recipes(id) on delete cascade,
 instruction varchar(2000) not null,
 position integer not null
);
create index idx_recipe_steps_recipe_position on recipe_steps(recipe_id, position);

create table cookings (
 id bigserial primary key,
 legacy_home_recipe_id bigint unique,
 recipe_id bigint not null references recipes(id) on delete restrict,
 home varchar(10) not null check(home in ('TOMAS', 'AVRIL')),
 servings integer not null check(servings > 0 and servings <= 100),
 cooked_on date not null,
 meal_type varchar(12) not null check(meal_type in ('DESAYUNO', 'ALMUERZO', 'MERIENDA', 'CENA')),
 created_by bigint not null references users(id),
 updated_by bigint not null references users(id),
 created_at timestamptz not null default now(),
 updated_at timestamptz not null default now()
);
create index idx_cookings_home_date on cookings(home, cooked_on desc, id desc);
create index idx_cookings_recipe_date on cookings(recipe_id, cooked_on desc, id desc);

create table cooking_photos (
 id bigserial primary key,
 cooking_id bigint not null references cookings(id) on delete cascade,
 image_base64 text not null,
 thumbnail_base64 text not null,
 width integer not null,
 height integer not null,
 position integer not null check(position >= 0),
 created_by bigint not null references users(id),
 created_at timestamptz not null default now(),
 unique(cooking_id, position)
);
create index idx_cooking_photos_cooking_position on cooking_photos(cooking_id, position);

alter table cookings add column cover_photo_id bigint references cooking_photos(id) on delete set null;

create table cooking_reviews (
 id bigserial primary key,
 cooking_id bigint not null references cookings(id) on delete cascade,
 author_id bigint not null references users(id),
 updated_by bigint not null references users(id),
 rating smallint not null check(rating between 1 and 5),
 comment varchar(1000),
 created_at timestamptz not null default now(),
 updated_at timestamptz not null default now(),
 unique(cooking_id, author_id)
);
create index idx_cooking_reviews_cooking on cooking_reviews(cooking_id, author_id);

insert into recipes(legacy_root_id, name, source_url, created_by, updated_by, created_at, updated_at)
select legacy.id, legacy.name, legacy.recipe_url, legacy.author_id, legacy.author_id, legacy.created_at, legacy.updated_at
from home_recipes legacy
where legacy.id in (select distinct root_id from legacy_home_recipe_roots);

insert into recipe_ingredients(recipe_id, name, quantity, unit, position)
select recipe.id, ingredient.name, ingredient.quantity, ingredient.unit, ingredient.position
from home_recipe_ingredients ingredient
join recipes recipe on recipe.legacy_root_id = ingredient.recipe_id;

insert into recipe_steps(recipe_id, instruction, position)
select recipe.id, step.instruction, step.position
from home_recipe_steps step
join recipes recipe on recipe.legacy_root_id = step.recipe_id;

insert into cookings(legacy_home_recipe_id, recipe_id, home, servings, cooked_on, meal_type, created_by, updated_by, created_at, updated_at)
select legacy.id, recipe.id, legacy.home, legacy.servings, legacy.prepared_on, legacy.meal_type,
       legacy.author_id, legacy.author_id, legacy.created_at, legacy.updated_at
from home_recipes legacy
join legacy_home_recipe_roots roots on roots.legacy_id = legacy.id
join recipes recipe on recipe.legacy_root_id = roots.root_id;

insert into cooking_photos(cooking_id, image_base64, thumbnail_base64, width, height, position, created_by, created_at)
select cooking.id, photo.image_base64, photo.thumbnail_base64, photo.width, photo.height, 0, cooking.created_by, photo.created_at
from home_recipe_photos photo
join cookings cooking on cooking.legacy_home_recipe_id = photo.recipe_id;

update cookings cooking
set cover_photo_id = photo.id
from cooking_photos photo
where photo.cooking_id = cooking.id and photo.position = 0;

insert into cooking_reviews(cooking_id, author_id, updated_by, rating, comment, created_at, updated_at)
select cooking.id, review.author_id, review.author_id, review.rating, review.comment, review.created_at, review.updated_at
from home_recipe_reviews review
join cookings cooking on cooking.legacy_home_recipe_id = review.recipe_id;

alter table recipes drop column legacy_root_id;
alter table cookings drop column legacy_home_recipe_id;
