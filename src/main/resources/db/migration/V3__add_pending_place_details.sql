alter table places add column address varchar(250);
alter table places add column source_url varchar(1000);
alter table places add column status varchar(20) not null default 'PENDING';
update places set status='REVIEWED' where exists (select 1 from items where items.place_id=places.id);
create index idx_places_status_id on places(status, id desc);
