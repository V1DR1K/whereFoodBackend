alter table home_recipes add column servings integer not null default 2 check(servings > 0 and servings <= 100);
alter table home_recipes add column repeated_from_id bigint references home_recipes(id);
create index idx_home_recipes_repeated_from on home_recipes(repeated_from_id);

alter table home_recipe_ingredients rename column grams to quantity;
alter table home_recipe_ingredients alter column quantity type numeric(10,2) using quantity::numeric;
alter table home_recipe_ingredients alter column quantity drop not null;
alter table home_recipe_ingredients add column unit varchar(30) not null default 'g';

create table home_recipe_steps (
 id bigserial primary key,
 recipe_id bigint not null references home_recipes(id) on delete cascade,
 instruction varchar(2000) not null,
 position integer not null
);
create index idx_home_recipe_steps_recipe on home_recipe_steps(recipe_id, position);
