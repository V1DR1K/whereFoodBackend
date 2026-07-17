update film_genre_options
set name = 'Ciencia Ficción'
where name = 'Ciencia ficción'
  and not exists (select 1 from film_genre_options where name = 'Ciencia Ficción');

insert into film_genre_options(name, emoji) values
 ('Misterio', '🧩'), ('Terror', '👻'), ('Romance', '💘'), ('Rom-Com', '💕'),
 ('Sad', '😢'), ('Comedia', '😂'), ('Drama', '🎭'), ('Fantasía', '🪄'),
 ('Familiar', '👪'), ('Acción', '💥'), ('Musical', '🎵'), ('Thriller', '😱'),
 ('Supervivencia', '🏕️')
on conflict(name) do nothing;

insert into film_genre_options(name, emoji)
select min(legacy.name), '🎞️'
from film_genres legacy
where not exists (
 select 1 from film_genre_options option where lower(option.name) = lower(legacy.name)
)
group by lower(legacy.name);

alter table film_genres rename to film_genres_legacy;

create table film_genres (
 film_id bigint not null references films(id) on delete cascade,
 genre_id bigint not null references film_genre_options(id) on delete cascade,
 primary key(film_id, genre_id)
);

insert into film_genres(film_id, genre_id)
select legacy.film_id, option.id
from film_genres_legacy legacy
join film_genre_options option on lower(option.name) = lower(legacy.name);

drop table film_genres_legacy;

create index idx_film_genres_genre_id on film_genres(genre_id);
