alter table items add column visit_date date;
update items set visit_date = created_at::date where visit_date is null;
alter table items alter column visit_date set not null;
alter table items add column deleted_at timestamptz;
create index idx_items_place_visit_date_active on items(place_id, visit_date desc, id desc) where deleted_at is null;
