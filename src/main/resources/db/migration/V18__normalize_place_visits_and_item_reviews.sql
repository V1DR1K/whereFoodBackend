alter table places drop column if exists zone;
alter table items drop column if exists venue;

create table place_visits (
 id bigserial primary key,
 place_id bigint not null references places(id) on delete cascade,
 visited_on date not null,
 created_by bigint not null references users(id),
 created_at timestamptz not null default now(),
 updated_at timestamptz not null default now(),
 unique(place_id, visited_on)
);

insert into place_visits(place_id, visited_on, created_by, created_at, updated_at)
select distinct on (place_id, visit_date)
 place_id, visit_date, author_id, created_at, updated_at
from items
order by place_id, visit_date, created_at, id;

alter table items add column visit_id bigint references place_visits(id);

update items item
set visit_id = visit.id
from place_visits visit
where visit.place_id = item.place_id and visit.visited_on = item.visit_date;

create temporary table item_merge_targets (
 source_item_id bigint primary key,
 target_item_id bigint not null
) on commit drop;

insert into item_merge_targets(source_item_id, target_item_id)
select source.id, target.id
from items source
join items target on target.place_id = source.place_id and target.visit_date = source.visit_date
join places place on place.id = source.place_id
join users source_author on source_author.id = source.author_id
join users target_author on target_author.id = target.author_id
where place.name = 'Dulce compañía Café y Resto'
 and source.visit_date = date '2026-07-15'
 and source_author.username = 'avril'
 and source.name = 'Croissant de ddl y crema'
 and target_author.username = 'tomas'
 and target.name = 'Café con leche, Croassant DDL y Crema con Frappe y Donas';

create table item_reviews (
 id bigserial primary key,
 item_id bigint not null references items(id) on delete cascade,
 author_id bigint not null references users(id),
 comment varchar(1000),
 taste smallint not null check(taste between 1 and 5),
 price smallint not null check(price between 1 and 5),
 created_at timestamptz not null default now(),
 updated_at timestamptz not null default now(),
 unique(item_id, author_id)
);

insert into item_reviews(item_id, author_id, comment, taste, price, created_at, updated_at)
select coalesce(merge.target_item_id, item.id), item.author_id, item.comment, item.taste, item.price, item.created_at, item.updated_at
from items item
left join item_merge_targets merge on merge.source_item_id = item.id;

delete from items item
using item_merge_targets merge
where item.id = merge.source_item_id;

alter table items rename column author_id to created_by;
alter table items drop column place_id;
alter table items drop column visit_date;
alter table items drop column comment;
alter table items drop column taste;
alter table items drop column price;
alter table items alter column visit_id set not null;

create index idx_place_visits_place_visited_on on place_visits(place_id, visited_on desc, id desc);
create index idx_items_visit_active on items(visit_id, id desc) where deleted_at is null;
create index idx_item_reviews_item_id on item_reviews(item_id);
