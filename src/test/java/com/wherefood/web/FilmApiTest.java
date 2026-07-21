package com.wherefood.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wherefood.domain.Film;
import com.wherefood.domain.FilmReview;
import com.wherefood.domain.User;
import com.wherefood.repo.Repositories.Films;
import com.wherefood.repo.Repositories.FilmReviews;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FilmApiTest {
  @Test
  void letsEitherAuthenticatedUserCreateAReview() {
    Films films = mock(Films.class);
    FilmReviews reviews = mock(FilmReviews.class);
    User avril = new User();
    avril.id = 6L;
    avril.username = "avril";
    Film film = new Film();
    film.id = 42L;
    when(films.findDetailedById(42L)).thenReturn(Optional.of(film));
    when(reviews.save(any(FilmReview.class))).thenAnswer(invocation -> invocation.getArgument(0));

    FilmReviewDto result = new FilmApi(films, reviews, null, null, null, null, null).saveReview(
      42L,
      new FilmReviewRequest((short) 5, "La volvería a ver", LocalDate.of(2026, 7, 21), Map.of("story", (short) 4)),
      avril
    );

    assertEquals("avril", result.author());
    assertEquals(1, film.watchedCount);
    verify(reviews).save(any(FilmReview.class));
  }

  @Test
  void updatesReviewWithoutReadingLazyFilmId() {
    FilmReviews reviews = mock(FilmReviews.class);
    User author = new User();
    author.id = 7L;
    author.username = "tomas";
    FilmReview review = new FilmReview();
    review.film = new Film();
    review.author = author;
    review.rating = 3;
    review.watchedOn = LocalDate.of(2026, 7, 17);
    review.metrics.put("story", (short) 3);
    when(reviews.findByIdAndFilmId(99L, 42L)).thenReturn(Optional.of(review));
    when(reviews.save(review)).thenReturn(review);

    FilmReviewDto result = new FilmApi(null, reviews, null, null, null, null, null).updateReview(
      42L,
      99L,
      new FilmReviewRequest((short) 5, "Mejor de lo que recordaba", LocalDate.of(2026, 7, 18), Map.of("story", (short) 4)),
      author
    );

    verify(reviews).findByIdAndFilmId(99L, 42L);
    assertEquals(5, result.rating());
    assertEquals(Map.of("story", (short) 4), result.metrics());
  }
}
