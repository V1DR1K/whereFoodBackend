package com.wherefood.web;

import com.wherefood.domain.*;
import com.wherefood.repo.Repositories.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.io.IOException;
import java.time.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

record HomeRecipeIngredientRequest(@NotBlank @Size(max = 160) String name, @Min(0) @Max(100000) int grams) {}
record HomeRecipeRequest(@NotNull Home home, @NotBlank @Size(max = 160) String name, @Size(max = 1000) String recipeUrl, @NotNull LocalDate preparedOn, @NotNull MealType mealType, @NotEmpty List<@Valid HomeRecipeIngredientRequest> ingredients, Long copyPhotoFromId) {}
record HomeRecipeReviewRequest(@Min(1) @Max(5) short rating, @Size(max = 1000) String comment) {}
record HomeRecipeIngredientDto(String name, int grams) {}
record HomeRecipeReviewDto(Long id, String author, short rating, String comment, Instant updatedAt) {}
record HomeRecipeDto(Long id, Home home, String name, String recipeUrl, LocalDate preparedOn, MealType mealType, List<HomeRecipeIngredientDto> ingredients, String author, String photoUrl, String thumbnailUrl, Integer photoWidth, Integer photoHeight, List<HomeRecipeReviewDto> reviews, Instant createdAt) {}

@RestController
@RequestMapping("/api/how-cook")
public class HomeRecipeApi {
 private final HomeRecipes recipes;
 private final HomeRecipePhotos photos;
 private final HomeRecipeReviews reviews;
 private final PhotoStorage storage;

 public HomeRecipeApi(HomeRecipes recipes, HomeRecipePhotos photos, HomeRecipeReviews reviews, PhotoStorage storage) {
  this.recipes = recipes; this.photos = photos; this.reviews = reviews; this.storage = storage;
 }

 @GetMapping List<HomeRecipeDto> list(@RequestParam Home home) {
  List<HomeRecipe> values = recipes.findByHomeOrderByPreparedOnDescIdDesc(home);
  Map<Long, HomeRecipePhoto> photoMap = photoMap(values);
  Map<Long, List<HomeRecipeReviewDto>> reviewMap = reviewMap(values);
  return values.stream().map(recipe -> recipe(recipe, photoMap.get(recipe.id), reviewMap.getOrDefault(recipe.id, List.of()))).toList();
 }

 @GetMapping("/{id}") HomeRecipeDto get(@PathVariable Long id) {
  HomeRecipe recipe = findRecipe(id);
  return recipe(recipe, photos.findByRecipeId(id).orElse(null), reviewMap(List.of(recipe)).getOrDefault(id, List.of()));
 }

 @PostMapping @ResponseStatus(HttpStatus.CREATED) @Transactional HomeRecipeDto add(@RequestBody @Valid HomeRecipeRequest request, @AuthenticationPrincipal User author) {
  HomeRecipe recipe = new HomeRecipe(); recipe.author = author; apply(recipe, request); recipe.createdAt = recipe.updatedAt = Instant.now();
  HomeRecipe saved = recipes.save(recipe); HomeRecipePhoto photo = request.copyPhotoFromId() == null ? null : photos.findByRecipeId(request.copyPhotoFromId()).map(source -> copyPhoto(saved, source)).orElse(null);
  return recipe(saved, photo, List.of());
 }

 @PutMapping("/{id}") HomeRecipeDto update(@PathVariable Long id, @RequestBody @Valid HomeRecipeRequest request) {
  HomeRecipe recipe = findRecipe(id); apply(recipe, request); recipe.updatedAt = Instant.now();
  return recipe(recipes.save(recipe), photos.findByRecipeId(id).orElse(null), reviewMap(List.of(recipe)).getOrDefault(id, List.of()));
 }

 @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) void delete(@PathVariable Long id) {
  recipes.delete(findRecipe(id));
 }

 @PostMapping(value = "/{id}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE) @Transactional HomeRecipeDto uploadPhoto(@PathVariable Long id, @RequestPart("file") MultipartFile file) throws IOException {
  HomeRecipe recipe = findRecipe(id); photos.findByRecipeId(id).ifPresent(photos::delete); photos.flush(); HomeRecipePhoto photo = photos.save(storage.store(recipe, file));
  return recipe(recipe, photo, reviewMap(List.of(recipe)).getOrDefault(id, List.of()));
 }

 @PutMapping("/{id}/reviews/me") @Transactional HomeRecipeReviewDto saveReview(@PathVariable Long id, @RequestBody @Valid HomeRecipeReviewRequest request, @AuthenticationPrincipal User author) {
  HomeRecipe recipe = findRecipe(id);
  HomeRecipeReview review = reviews.findByRecipeIdAndAuthorId(id, author.id).orElseGet(() -> { HomeRecipeReview value = new HomeRecipeReview(); value.recipe = recipe; value.author = author; value.createdAt = Instant.now(); return value; });
  review.rating = request.rating(); review.comment = blankToNull(request.comment()); review.updatedAt = Instant.now();
  return review(reviews.save(review));
 }

 @GetMapping(value = "/{id}/photo", produces = "image/webp") ResponseEntity<byte[]> photo(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean thumbnail) {
  HomeRecipePhoto photo = photos.findByRecipeId(id).orElseThrow(() -> notFound("Foto"));
  return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic()).contentType(MediaType.valueOf("image/webp")).body(storage.bytes(thumbnail ? photo.thumbnailBase64 : photo.imageBase64));
 }

 private HomeRecipe findRecipe(Long id) { return recipes.findById(id).orElseThrow(() -> notFound("Receta")); }
 private Map<Long, HomeRecipePhoto> photoMap(List<HomeRecipe> values) {
  if (values.isEmpty()) return Map.of();
  return photos.findByRecipeIdIn(values.stream().map(recipe -> recipe.id).toList()).stream().collect(java.util.stream.Collectors.toMap(photo -> photo.recipe.id, photo -> photo));
 }
 private Map<Long, List<HomeRecipeReviewDto>> reviewMap(List<HomeRecipe> values) {
  if (values.isEmpty()) return Map.of();
  return reviews.findByRecipeIdInOrderByAuthorUsername(values.stream().map(recipe -> recipe.id).toList()).stream().collect(java.util.stream.Collectors.groupingBy(review -> review.recipe.id, java.util.stream.Collectors.mapping(HomeRecipeApi::review, java.util.stream.Collectors.toList())));
 }
 private static void apply(HomeRecipe recipe, HomeRecipeRequest request) {
  recipe.home = request.home(); recipe.name = request.name().trim(); recipe.recipeUrl = blankToNull(request.recipeUrl()); recipe.preparedOn = request.preparedOn(); recipe.mealType = request.mealType(); recipe.ingredients.clear();
  for (int index = 0; index < request.ingredients().size(); index++) { HomeRecipeIngredientRequest source = request.ingredients().get(index); HomeRecipeIngredient ingredient = new HomeRecipeIngredient(); ingredient.recipe = recipe; ingredient.name = source.name().trim(); ingredient.grams = source.grams(); ingredient.position = index; recipe.ingredients.add(ingredient); }
 }
 private static HomeRecipeDto recipe(HomeRecipe value, HomeRecipePhoto photo, List<HomeRecipeReviewDto> reviews) {
  return new HomeRecipeDto(value.id, value.home, value.name, value.recipeUrl, value.preparedOn, value.mealType, value.ingredients.stream().map(ingredient -> new HomeRecipeIngredientDto(ingredient.name, ingredient.grams)).toList(), value.author.username, photo == null ? null : "/how-cook/" + value.id + "/photo", photo == null ? null : "/how-cook/" + value.id + "/photo?thumbnail=true", photo == null ? null : photo.width, photo == null ? null : photo.height, reviews, value.createdAt);
 }
 private HomeRecipePhoto copyPhoto(HomeRecipe recipe, HomeRecipePhoto source) {
  HomeRecipePhoto copy = new HomeRecipePhoto(); copy.recipe = recipe; copy.imageBase64 = source.imageBase64; copy.thumbnailBase64 = source.thumbnailBase64; copy.width = source.width; copy.height = source.height; copy.createdAt = Instant.now();
  return photos.save(copy);
 }
 private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
 private static HomeRecipeReviewDto review(HomeRecipeReview value) { return new HomeRecipeReviewDto(value.id, value.author.username, value.rating, value.comment, value.updatedAt); }
 private static ResponseStatusException notFound(String type) { return new ResponseStatusException(HttpStatus.NOT_FOUND, type + " no encontrada"); }
}
