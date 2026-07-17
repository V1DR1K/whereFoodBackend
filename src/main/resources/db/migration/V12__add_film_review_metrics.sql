create table film_review_metrics (
 review_id bigint not null references film_reviews(id) on delete cascade,
 metric_key varchar(80) not null,
 level smallint not null check(level between 1 and 5),
 primary key(review_id, metric_key)
);
