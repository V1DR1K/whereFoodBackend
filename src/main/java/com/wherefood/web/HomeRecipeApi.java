package com.wherefood.web;

import com.wherefood.domain.*;
import com.wherefood.repo.Repositories.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

record RecipeIngredientRequest(@NotBlank @Size(max = 160) String name, @DecimalMin(value = "0.0", inclusive = false) BigDecimal quantity, @NotBlank @Size(max = 30) String unit) {}
record RecipeStepRequest(@NotBlank @Size(max = 2000) String instruction) {}
record RecipeRequest(@NotBlank @Size(max = 160) String name, @Size(max = 1000) String sourceUrl, @NotEmpty List<@Valid RecipeIngredientRequest> ingredients, @NotEmpty List<@Valid RecipeStepRequest> steps) {}
record CookingRequest(@NotNull Home home, @Min(1) @Max(100) int servings, @NotNull LocalDate cookedOn, @NotNull MealType mealType) {}
record RecipeIngredientDto(String name, BigDecimal quantity, String unit) {}
record RecipeStepDto(String instruction) {}
record RecipeDto(Long id, String name, String sourceUrl, String photoUrl, String thumbnailUrl, Integer photoWidth, Integer photoHeight, List<RecipeIngredientDto> ingredients, List<RecipeStepDto> steps, String createdBy, String updatedBy, Instant createdAt, Instant updatedAt) {}
record CookingPhotoDto(Long id, String url, String thumbnailUrl, int width, int height, int position, String createdBy, Instant createdAt) {}
record CookingReviewRequest(@Min(1) @Max(5) short rating, @Size(max = 1000) String comment) {}
record CookingReviewDto(Long id, String author, String updatedBy, short rating, String comment, Instant createdAt, Instant updatedAt) {}
record CookingDto(Long id, RecipeDto recipe, Home home, int servings, LocalDate cookedOn, MealType mealType, String createdBy, String updatedBy, CookingPhotoDto coverPhoto, List<CookingPhotoDto> photos, List<CookingReviewDto> reviews, Instant createdAt, Instant updatedAt) {}

/**
 * Active WhoCook contract: recipes hold reusable definitions; cookings are
 * dated executions at a home. Recipes and cookings each own their profile and
 * gallery media respectively.
 */
@RestController
@RequestMapping("/api/how-cook")
public class HomeRecipeApi {
 private static final int MAX_PHOTOS = 12;
 private final Recipes recipes;
 private final RecipePhotos recipePhotos;
 private final Cookings cookings;
 private final CookingPhotos photos;
 private final CookingReviews reviews;
 private final PhotoStorage storage;

 public HomeRecipeApi(Recipes recipes, RecipePhotos recipePhotos, Cookings cookings, CookingPhotos photos, CookingReviews reviews, PhotoStorage storage) {
  this.recipes = recipes; this.recipePhotos = recipePhotos; this.cookings = cookings; this.photos = photos; this.reviews = reviews; this.storage = storage;
 }

 @GetMapping("/recipes") @Transactional(readOnly = true) List<RecipeDto> listRecipes(@RequestParam(required = false) String search) {
  return recipes.findAll().stream().filter(recipe -> search == null || search.isBlank() || recipe.name.toLowerCase(Locale.ROOT).contains(search.trim().toLowerCase(Locale.ROOT))).sorted(Comparator.comparing((Recipe recipe) -> recipe.updatedAt).reversed()).map(this::recipe).toList();
 }
 @GetMapping("/recipes/{id}") @Transactional(readOnly = true) RecipeDto getRecipe(@PathVariable Long id) { return recipe(findRecipe(id)); }
 @PostMapping("/recipes") @ResponseStatus(HttpStatus.CREATED) @Transactional RecipeDto addRecipe(@RequestBody @Valid RecipeRequest request, @AuthenticationPrincipal User author) {
  Recipe recipe = new Recipe(); recipe.createdBy = recipe.updatedBy = author; recipe.createdAt = recipe.updatedAt = Instant.now(); apply(recipe, request); return recipe(recipes.save(recipe));
 }
 @PutMapping("/recipes/{id}") @Transactional RecipeDto updateRecipe(@PathVariable Long id, @RequestBody @Valid RecipeRequest request, @AuthenticationPrincipal User author) {
  Recipe recipe = findRecipe(id); apply(recipe, request); recipe.updatedBy = author; recipe.updatedAt = Instant.now(); return recipe(recipes.save(recipe));
 }
  @DeleteMapping("/recipes/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) void deleteRecipe(@PathVariable Long id) {
   if (cookings.existsByRecipeId(id)) throw conflict("No podés borrar una receta con preparaciones"); recipes.delete(findRecipe(id));
  }
  @GetMapping(value = "/recipes/{id}/photo", produces = "image/webp") ResponseEntity<byte[]> recipePhoto(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean thumbnail) {
   findRecipe(id); RecipePhoto photo = recipePhotos.findByRecipeId(id).orElseThrow(() -> notFound("Foto"));
   return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic()).contentType(MediaType.valueOf("image/webp")).body(storage.bytes(thumbnail ? photo.thumbnailBase64 : photo.imageBase64));
  }
  @PostMapping(value = "/recipes/{id}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE) @Transactional RecipeDto uploadRecipePhoto(@PathVariable Long id, @RequestPart("file") MultipartFile file, @AuthenticationPrincipal User author) throws IOException {
   Recipe recipe = findRecipe(id); recipePhotos.findByRecipeId(id).ifPresent(recipePhotos::delete); recipePhotos.flush(); recipe.updatedBy = author; recipe.updatedAt = Instant.now(); recipes.save(recipe); recipePhotos.save(storage.store(recipe, file)); return recipe(recipe);
  }

 @GetMapping("/cookings") @Transactional(readOnly = true) List<CookingDto> listCookings(@RequestParam(required = false) Home home, @RequestParam(required = false) Long recipeId) {
  List<Cooking> values = recipeId != null ? cookings.findByRecipeIdOrderByCookedOnDescIdDesc(recipeId) : home != null ? cookings.findByHomeOrderByCookedOnDescIdDesc(home) : cookings.findAll(); return values.stream().map(this::cooking).toList();
 }
 @PostMapping("/recipes/{recipeId}/cookings") @ResponseStatus(HttpStatus.CREATED) @Transactional CookingDto addCooking(@PathVariable Long recipeId, @RequestBody @Valid CookingRequest request, @AuthenticationPrincipal User author) {
  validateCookingDate(request); Cooking cooking = new Cooking(); cooking.recipe = findRecipe(recipeId); cooking.createdBy = cooking.updatedBy = author; cooking.createdAt = cooking.updatedAt = Instant.now(); apply(cooking, request); return cooking(cookings.save(cooking));
 }
 @GetMapping("/cookings/{id}") @Transactional(readOnly = true) CookingDto getCooking(@PathVariable Long id) { return cooking(findCooking(id)); }
 @PutMapping("/cookings/{id}") @Transactional CookingDto updateCooking(@PathVariable Long id, @RequestBody @Valid CookingRequest request, @AuthenticationPrincipal User author) {
  validateCookingDate(request); Cooking cooking = findCooking(id); apply(cooking, request); cooking.updatedBy = author; cooking.updatedAt = Instant.now(); return cooking(cookings.save(cooking));
 }
 @DeleteMapping("/cookings/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) void deleteCooking(@PathVariable Long id) { cookings.delete(findCooking(id)); }

 @PostMapping(value = "/cookings/{id}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE) @Transactional CookingDto uploadPhoto(@PathVariable Long id, @RequestPart("file") MultipartFile file, @AuthenticationPrincipal User author) throws IOException {
  Cooking cooking = findCooking(id); List<CookingPhoto> current = photos.findByCookingIdOrderByPositionAscIdAsc(id); if (current.size() >= MAX_PHOTOS) throw conflict("Cada preparación admite hasta " + MAX_PHOTOS + " fotos");
  CookingPhoto photo = photos.save(storage.store(cooking, author, current.isEmpty() ? 0 : current.getLast().position + 1, file));
  if (cooking.coverPhotoId == null) { cooking.coverPhotoId = photo.id; cooking.updatedBy = author; cooking.updatedAt = Instant.now(); cookings.save(cooking); }
  return cooking(cooking);
 }
 @PutMapping("/cookings/{id}/cover/{photoId}") @Transactional CookingDto setCover(@PathVariable Long id, @PathVariable Long photoId, @AuthenticationPrincipal User author) {
  Cooking cooking = findCooking(id); CookingPhoto photo = photos.findDetailedById(photoId).orElseThrow(() -> notFound("Foto")); if (!photo.cooking.id.equals(cooking.id)) throw badRequest("La foto no pertenece a esta preparación");
  cooking.coverPhotoId = photo.id; cooking.updatedBy = author; cooking.updatedAt = Instant.now(); return cooking(cookings.save(cooking));
 }
 @DeleteMapping("/cooking-photos/{photoId}") @ResponseStatus(HttpStatus.NO_CONTENT) @Transactional void deletePhoto(@PathVariable Long photoId, @AuthenticationPrincipal User author) {
  CookingPhoto photo = photos.findDetailedById(photoId).orElseThrow(() -> notFound("Foto")); Cooking cooking = photo.cooking; boolean wasCover = photo.id.equals(cooking.coverPhotoId); photos.delete(photo); photos.flush();
  if (wasCover) { cooking.coverPhotoId = photos.findByCookingIdOrderByPositionAscIdAsc(cooking.id).stream().findFirst().map(value -> value.id).orElse(null); cooking.updatedBy = author; cooking.updatedAt = Instant.now(); cookings.save(cooking); }
 }
 @GetMapping(value = "/cooking-photos/{photoId}", produces = "image/webp") ResponseEntity<byte[]> photo(@PathVariable Long photoId, @RequestParam(defaultValue = "false") boolean thumbnail) {
  CookingPhoto photo = photos.findById(photoId).orElseThrow(() -> notFound("Foto")); return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic()).contentType(MediaType.valueOf("image/webp")).body(storage.bytes(thumbnail ? photo.thumbnailBase64 : photo.imageBase64));
 }

 @PostMapping("/cookings/{id}/reviews") @ResponseStatus(HttpStatus.CREATED) @Transactional CookingReviewDto addReview(@PathVariable Long id, @RequestBody @Valid CookingReviewRequest request, @AuthenticationPrincipal User author) {
  Cooking cooking = findCooking(id); if (reviews.findByCookingIdAndAuthorId(id, author.id).isPresent()) throw conflict("Ya existe una reseña de este autor para la preparación");
  CookingReview review = new CookingReview(); review.cooking = cooking; review.author = review.updatedBy = author; review.createdAt = review.updatedAt = Instant.now(); apply(review, request); return review(reviews.save(review));
 }
 @PutMapping("/cookings/{id}/reviews/me") @Transactional CookingReviewDto saveOwnReview(@PathVariable Long id, @RequestBody @Valid CookingReviewRequest request, @AuthenticationPrincipal User author) {
  Cooking cooking = findCooking(id); CookingReview review = reviews.findByCookingIdAndAuthorId(id, author.id).orElseGet(() -> { CookingReview value = new CookingReview(); value.cooking = cooking; value.author = author; value.createdAt = Instant.now(); return value; });
  review.updatedBy = author; review.updatedAt = Instant.now(); apply(review, request); return review(reviews.save(review));
 }
 @PutMapping("/cooking-reviews/{reviewId}") @Transactional CookingReviewDto updateReview(@PathVariable Long reviewId, @RequestBody @Valid CookingReviewRequest request, @AuthenticationPrincipal User author) {
  CookingReview review = reviews.findDetailedById(reviewId).orElseThrow(() -> notFound("Reseña")); review.updatedBy = author; review.updatedAt = Instant.now(); apply(review, request); return review(reviews.save(review));
 }
 @DeleteMapping("/cooking-reviews/{reviewId}") @ResponseStatus(HttpStatus.NO_CONTENT) void deleteReview(@PathVariable Long reviewId) { reviews.delete(reviews.findDetailedById(reviewId).orElseThrow(() -> notFound("Reseña"))); }

 private Recipe findRecipe(Long id) { return recipes.findById(id).orElseThrow(() -> notFound("Receta")); }
 private Cooking findCooking(Long id) { return cookings.findDetailedById(id).orElseThrow(() -> notFound("Preparación")); }
 private void apply(Recipe recipe, RecipeRequest request) {
  recipe.name = request.name().trim(); recipe.sourceUrl = blankToNull(request.sourceUrl()); recipe.ingredients.clear(); recipe.steps.clear();
  for (int position = 0; position < request.ingredients().size(); position++) { RecipeIngredientRequest source = request.ingredients().get(position); RecipeIngredient ingredient = new RecipeIngredient(); ingredient.recipe = recipe; ingredient.name = source.name().trim(); ingredient.quantity = source.quantity(); ingredient.unit = source.unit().trim(); ingredient.position = position; recipe.ingredients.add(ingredient); }
  for (int position = 0; position < request.steps().size(); position++) { RecipeStepRequest source = request.steps().get(position); RecipeStep step = new RecipeStep(); step.recipe = recipe; step.instruction = source.instruction().trim(); step.position = position; recipe.steps.add(step); }
 }
 private static void apply(Cooking cooking, CookingRequest request) { cooking.home = request.home(); cooking.servings = request.servings(); cooking.cookedOn = request.cookedOn(); cooking.mealType = request.mealType(); }
 private CookingDto cooking(Cooking value) {
  List<CookingPhotoDto> cookingPhotos = photos.findByCookingIdOrderByPositionAscIdAsc(value.id).stream().map(HomeRecipeApi::photo).toList(); CookingPhotoDto cover = cookingPhotos.stream().filter(photo -> photo.id().equals(value.coverPhotoId)).findFirst().orElse(cookingPhotos.isEmpty() ? null : cookingPhotos.getFirst());
  return new CookingDto(value.id, recipe(value.recipe), value.home, value.servings, value.cookedOn, value.mealType, value.createdBy.username, value.updatedBy.username, cover, cookingPhotos, reviews.findByCookingIdOrderByAuthorUsername(value.id).stream().map(HomeRecipeApi::review).toList(), value.createdAt, value.updatedAt);
 }
 private RecipeDto recipe(Recipe value) {
  RecipePhoto photo = recipePhotos.findByRecipeId(value.id).orElse(null);
  return new RecipeDto(value.id, value.name, value.sourceUrl, photo == null ? null : recipePhotoUrl(value.id, false, photo.id), photo == null ? null : recipePhotoUrl(value.id, true, photo.id), photo == null ? null : Integer.valueOf(photo.width), photo == null ? null : Integer.valueOf(photo.height), value.ingredients.stream().map(ingredient -> new RecipeIngredientDto(ingredient.name, ingredient.quantity, ingredient.unit)).toList(), value.steps.stream().map(step -> new RecipeStepDto(step.instruction)).toList(), value.createdBy.username, value.updatedBy.username, value.createdAt, value.updatedAt);
 }
 private static String recipePhotoUrl(Long recipeId, boolean thumbnail, Long photoId) { return "/how-cook/recipes/" + recipeId + "/photo?" + (thumbnail ? "thumbnail=true&" : "") + "v=" + photoId; }
 private static CookingPhotoDto photo(CookingPhoto value) { return new CookingPhotoDto(value.id, "/how-cook/cooking-photos/" + value.id, "/how-cook/cooking-photos/" + value.id + "?thumbnail=true", value.width, value.height, value.position, value.createdBy.username, value.createdAt); }
 private static CookingReviewDto review(CookingReview value) { return new CookingReviewDto(value.id, value.author.username, value.updatedBy.username, value.rating, value.comment, value.createdAt, value.updatedAt); }
 private static void apply(CookingReview review, CookingReviewRequest request) { review.rating = request.rating(); review.comment = blankToNull(request.comment()); }
 private static void validateCookingDate(CookingRequest request) { if (request.cookedOn().isAfter(LocalDate.now())) throw badRequest("Una preparación no puede quedar en el futuro"); }
 private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
 private static ResponseStatusException notFound(String type) { return new ResponseStatusException(HttpStatus.NOT_FOUND, type + " no encontrada"); }
 private static ResponseStatusException badRequest(String detail) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, detail); }
 private static ResponseStatusException conflict(String detail) { return new ResponseStatusException(HttpStatus.CONFLICT, detail); }
}
