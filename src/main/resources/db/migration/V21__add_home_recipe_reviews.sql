create table home_recipe_reviews (
 id bigserial primary key,
 recipe_id bigint not null references home_recipes(id) on delete cascade,
 author_id bigint not null references users(id),
 rating smallint not null check(rating between 1 and 5),
 comment varchar(1000),
 created_at timestamptz not null default now(),
 updated_at timestamptz not null default now(),
 unique(recipe_id, author_id)
);

create index idx_home_recipe_reviews_recipe_id on home_recipe_reviews(recipe_id);
