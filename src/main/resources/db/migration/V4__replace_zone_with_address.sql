update places set address=zone where address is null and zone is not null;
alter table places drop column zone;
