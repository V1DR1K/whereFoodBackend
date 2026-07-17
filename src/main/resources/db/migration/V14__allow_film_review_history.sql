alter table film_reviews drop constraint if exists film_reviews_film_id_author_id_key;
create index if not exists idx_film_reviews_film_watched_on on film_reviews(film_id, watched_on desc, id desc);
