package com.wherefood.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wherefood.domain.Home;
import com.wherefood.domain.HomeRecipe;
import com.wherefood.domain.MealType;
import com.wherefood.domain.User;
import com.wherefood.repo.Repositories.HomeRecipePhotos;
import com.wherefood.repo.Repositories.HomeRecipes;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class HomeRecipeApiTest {
  @Test
  void letsEitherAuthenticatedUserUpdateARecipe() {
    HomeRecipes recipes = mock(HomeRecipes.class);
    HomeRecipePhotos photos = mock(HomeRecipePhotos.class);
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

    HomeRecipeDto result = new HomeRecipeApi(recipes, photos, null).update(
      2L,
      new HomeRecipeRequest(Home.AVRIL, "Panes rellenos con papas", null, LocalDate.of(2026, 7, 21), MealType.ALMUERZO, List.of(new HomeRecipeIngredientRequest("Papa", 300)), null)
    );

    assertEquals("Panes rellenos con papas", result.name());
    assertEquals("tomas", result.author());
    verify(recipes).save(recipe);
  }
}
