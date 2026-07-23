package com.wherefood.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wherefood.domain.Place;
import com.wherefood.domain.PlaceVisitPhoto;
import com.wherefood.domain.PlaceVisitReview;
import com.wherefood.domain.PlaceStatus;
import com.wherefood.domain.PlaceVisit;
import com.wherefood.domain.User;
import com.wherefood.repo.Repositories.PlacePhotos;
import com.wherefood.repo.Repositories.PlaceVisitPhotos;
import com.wherefood.repo.Repositories.PlaceVisitReviews;
import com.wherefood.repo.Repositories.PlaceVisits;
import com.wherefood.repo.Repositories.Places;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ApiVisitTest {
  @Test
  void returnsThePlaceToPendingAfterDeletingItsLastVisit() {
    Places places = mock(Places.class);
    PlaceVisits visits = mock(PlaceVisits.class);
    User tomas = new User();
    tomas.id = 7L;
    Place place = new Place();
    place.id = 4L;
    place.status = PlaceStatus.REVIEWED;
    PlaceVisit visit = new PlaceVisit();
    visit.id = 9L;
    visit.place = place;
    visit.createdBy = tomas;
    when(visits.findDetailedById(9L)).thenReturn(Optional.of(visit));
    when(visits.existsByPlaceId(4L)).thenReturn(false);

    new Api(null, null, null, places, visits, null, null, null, null, null, null, null, null).deleteVisit(9L, tomas);

    assertEquals(PlaceStatus.PENDING, place.status);
    verify(visits).delete(visit);
    verify(places).save(place);
  }

  @Test
  void derivesPlaceCardsFromVisitReviewsAndTheLatestVisitCover() {
    Places places = mock(Places.class);
    PlaceVisits visits = mock(PlaceVisits.class);
    PlaceVisitPhotos visitPhotos = mock(PlaceVisitPhotos.class);
    PlaceVisitReviews visitReviews = mock(PlaceVisitReviews.class);
    PlacePhotos placePhotos = mock(PlacePhotos.class);
    User tomas = user(7L, "tomas");
    User avril = user(6L, "avril");
    Place place = new Place();
    place.id = 4L; place.name = "Lugar"; place.status = PlaceStatus.REVIEWED; place.createdBy = tomas; place.category = new com.wherefood.domain.Category();
    PlaceVisit recent = visit(10L, place, tomas, LocalDate.of(2026, 7, 22));
    PlaceVisit older = visit(9L, place, tomas, LocalDate.of(2026, 7, 15));
    PlaceVisitPhoto photo = new PlaceVisitPhoto();
    photo.id = 99L; photo.visit = recent; photo.createdBy = tomas; photo.width = 1200; photo.height = 800; recent.coverPhotoId = photo.id;
    PlaceVisitReview first = review(recent, tomas, (short) 4, (short) 3, null, (short) 2, null, null, null, null, (short) 4, null);
    PlaceVisitReview second = review(older, avril, (short) 5, null, (short) 4, null, null, (short) 5, null, null, null, null);
    when(places.findDetailedById(4L)).thenReturn(Optional.of(place));
    when(places.findAll()).thenReturn(List.of(place));
    when(visits.findByPlaceIdInOrderByPlaceIdAscVisitedOnDescIdDesc(List.of(4L))).thenReturn(List.of(recent, older));
    when(visitPhotos.findByVisitIdInOrderByVisitIdAscPositionAscIdAsc(List.of(10L, 9L))).thenReturn(List.of(photo));
    when(visitReviews.findByVisitIdInOrderByVisitIdAscAuthorUsername(List.of(10L, 9L))).thenReturn(List.of(first, second));
    when(placePhotos.findByPlaceIdIn(List.of(4L))).thenReturn(List.of());

    Api api = new Api(null, null, null, places, visits, null, null, null, null, placePhotos, visitPhotos, visitReviews, null, null, null);
    PlaceDto result = api.getPlace(4L);
    PlaceDto listed = api.list(null, null, null, null, 12).content().getFirst();

    assertEquals(4.5, result.rating());
    assertEquals(3, result.tasteAverage());
    assertEquals(4, result.priceAverage());
    assertEquals(3.7, result.venueAverage());
    assertEquals(2, result.itemCount());
    assertEquals("/place-visit-photos/99", result.photoUrl());
    assertEquals("/place-visit-photos/99?thumbnail=true", result.thumbnailUrl());
   assertEquals(result.rating(), listed.rating());
  }

  @Test
  void givesTheParentPlaceProfilePriorityOverTheVisitCover() {
   Places places = mock(Places.class); PlaceVisits visits = mock(PlaceVisits.class); PlaceVisitPhotos visitPhotos = mock(PlaceVisitPhotos.class); PlaceVisitReviews visitReviews = mock(PlaceVisitReviews.class); PlacePhotos placePhotos = mock(PlacePhotos.class);
   User tomas = user(7L, "tomas"); Place place = new Place(); place.id = 4L; place.name = "Lugar"; place.status = PlaceStatus.REVIEWED; place.createdBy = tomas; place.category = new com.wherefood.domain.Category();
   PlaceVisit visit = visit(10L, place, tomas, LocalDate.of(2026, 7, 22)); PlaceVisitPhoto cover = new PlaceVisitPhoto(); cover.id = 99L; cover.visit = visit; cover.createdBy = tomas; cover.width = 1200; cover.height = 800; visit.coverPhotoId = cover.id;
   com.wherefood.domain.PlacePhoto profile = new com.wherefood.domain.PlacePhoto(); profile.id = 88L; profile.place = place; profile.width = 900; profile.height = 600;
   when(places.findDetailedById(4L)).thenReturn(Optional.of(place)); when(visits.findByPlaceIdInOrderByPlaceIdAscVisitedOnDescIdDesc(List.of(4L))).thenReturn(List.of(visit)); when(visitPhotos.findByVisitIdInOrderByVisitIdAscPositionAscIdAsc(List.of(10L))).thenReturn(List.of(cover)); when(visitReviews.findByVisitIdInOrderByVisitIdAscAuthorUsername(List.of(10L))).thenReturn(List.of()); when(placePhotos.findByPlaceIdIn(List.of(4L))).thenReturn(List.of(profile));

   PlaceDto result = new Api(null, null, null, places, visits, null, null, null, null, placePhotos, visitPhotos, visitReviews, null, null, null).getPlace(4L);

   assertEquals("/places/4/photo?v=88", result.photoUrl());
   assertEquals("/places/4/photo?thumbnail=true&v=88", result.thumbnailUrl());
   assertEquals(900, result.photoWidth());
   assertEquals(600, result.photoHeight());
  }

  @Test
  void keepsPhotoDimensionsEmptyWhenAPlaceHasNoMedia() {
    Places places = mock(Places.class);
    PlaceVisits visits = mock(PlaceVisits.class);
    PlaceVisitPhotos visitPhotos = mock(PlaceVisitPhotos.class);
    PlaceVisitReviews visitReviews = mock(PlaceVisitReviews.class);
    PlacePhotos placePhotos = mock(PlacePhotos.class);
    Place place = new Place();
    place.id = 5L; place.name = "Sin foto"; place.status = PlaceStatus.PENDING; place.createdBy = user(7L, "tomas"); place.category = new com.wherefood.domain.Category();
    when(places.findDetailedById(5L)).thenReturn(Optional.of(place));
    when(visits.findByPlaceIdInOrderByPlaceIdAscVisitedOnDescIdDesc(List.of(5L))).thenReturn(List.of());
    when(placePhotos.findByPlaceIdIn(List.of(5L))).thenReturn(List.of());

    PlaceDto result = new Api(null, null, null, places, visits, null, null, null, null, placePhotos, visitPhotos, visitReviews, null, null, null).getPlace(5L);

    assertNull(result.photoUrl());
    assertNull(result.photoWidth());
    assertNull(result.photoHeight());
  }

  private static PlaceVisit visit(Long id, Place place, User author, LocalDate visitedOn) {
    PlaceVisit visit = new PlaceVisit();
    visit.id = id; visit.place = place; visit.createdBy = visit.updatedBy = author; visit.visitedOn = visitedOn; return visit;
  }

  private static PlaceVisitReview review(PlaceVisit visit, User author, short overall, Short taste, Short price, Short location, Short heating, Short bathrooms, Short exterior, Short seating, Short service, Short ambiance) {
    PlaceVisitReview review = new PlaceVisitReview();
    review.visit = visit; review.author = review.updatedBy = author; review.overall = overall; review.taste = taste; review.price = price; review.location = location; review.heating = heating; review.bathrooms = bathrooms; review.exterior = exterior; review.seating = seating; review.service = service; review.ambiance = ambiance; return review;
  }

  private static User user(Long id, String username) { User user = new User(); user.id = id; user.username = username; return user; }
}
