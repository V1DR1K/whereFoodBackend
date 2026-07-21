package com.wherefood.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wherefood.domain.Home;
import com.wherefood.domain.HomeRecipe;
import com.wherefood.domain.HomeRecipeReview;
import com.wherefood.domain.MealType;
import com.wherefood.domain.User;
import com.wherefood.repo.Repositories.HomeRecipePhotos;
import com.wherefood.repo.Repositories.HomeRecipeReviews;
import com.wherefood.repo.Repositories.HomeRecipes;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class HomeRecipeApiTest {
  @Test
  void letsEitherAuthenticatedUserUpdateARecipe() {
    HomeRecipes recipes = mock(HomeRecipes.class);
    HomeRecipePhotos photos = mock(HomeRecipePhotos.class);
    HomeRecipeReviews reviews = mock(HomeRecipeReviews.class);
    User tomas = new User();
    tomas.id = 7L;
    tomas.username = "tomas";
    HomeRecipe recipe = new HomeRecipe();
    recipe.id = 2L;
    recipe.author = tomas;
    recipe.home = Home.TOMAS;
    recipe.name = "Panes rellenos";
    recipe.preparedOn = LocalDate.of(2026, 7, 18);
    recipe.mealType = MealType.CENA;
    when(recipes.findById(2L)).thenReturn(Optional.of(recipe));
    when(recipes.save(recipe)).thenReturn(recipe);
    when(photos.findByRecipeId(2L)).thenReturn(Optional.empty());
    when(reviews.findByRecipeIdInOrderByAuthorUsername(List.of(2L))).thenReturn(List.of());

    HomeRecipeDto result = new HomeRecipeApi(recipes, photos, reviews, null).update(
      2L,
      new HomeRecipeRequest(Home.AVRIL, "Panes rellenos con papas", null, LocalDate.of(2026, 7, 21), MealType.ALMUERZO, List.of(new HomeRecipeIngredientRequest("Papa", 300)), null)
    );

    assertEquals("Panes rellenos con papas", result.name());
    assertEquals("tomas", result.author());
    verify(recipes).save(recipe);
  }

  @Test
  void savesOneReviewPerUserForEachRecipe() {
    HomeRecipes recipes = mock(HomeRecipes.class);
    HomeRecipePhotos photos = mock(HomeRecipePhotos.class);
    HomeRecipeReviews reviews = mock(HomeRecipeReviews.class);
    User avril = new User();
    avril.id = 6L;
    avril.username = "avril";
    HomeRecipe recipe = new HomeRecipe();
    recipe.id = 2L;
    when(recipes.findById(2L)).thenReturn(Optional.of(recipe));
    when(reviews.findByRecipeIdAndAuthorId(2L, 6L)).thenReturn(Optional.empty());
    when(reviews.save(any(HomeRecipeReview.class))).thenAnswer(invocation -> invocation.getArgument(0));

    HomeRecipeReviewDto result = new HomeRecipeApi(recipes, photos, reviews, null).saveReview(
      2L,
      new HomeRecipeReviewRequest((short) 5, "Una cena espectacular"),
      avril
    );

    assertEquals("avril", result.author());
    assertEquals(5, result.rating());
    verify(reviews).save(any(HomeRecipeReview.class));
  }

  @Test
  void returnsReviewsWithTheRecipe() {
    HomeRecipes recipes = mock(HomeRecipes.class);
    HomeRecipePhotos photos = mock(HomeRecipePhotos.class);
    HomeRecipeReviews reviews = mock(HomeRecipeReviews.class);
    User tomas = new User();
    tomas.username = "tomas";
    HomeRecipe recipe = new HomeRecipe();
    recipe.id = 2L;
    recipe.author = tomas;
    HomeRecipeReview review = new HomeRecipeReview();
    review.recipe = recipe;
    review.author = tomas;
    review.rating = 5;
    review.updatedAt = Instant.parse("2026-07-21T17:24:00Z");
    when(recipes.findById(2L)).thenReturn(Optional.of(recipe));
    when(photos.findByRecipeId(2L)).thenReturn(Optional.empty());
    when(reviews.findByRecipeIdInOrderByAuthorUsername(List.of(2L))).thenReturn(List.of(review));

    HomeRecipeDto result = new HomeRecipeApi(recipes, photos, reviews, null).get(2L);

    assertEquals(1, result.reviews().size());
    assertEquals("tomas", result.reviews().getFirst().author());
  }
}
