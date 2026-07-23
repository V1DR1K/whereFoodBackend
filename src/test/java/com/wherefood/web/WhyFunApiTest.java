package com.wherefood.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.wherefood.domain.*;
import com.wherefood.repo.Repositories.*;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class WhyFunApiTest {
 @Test
  void separatesUpcomingAndPastPlans() throws Exception {
   Method matcher = WhyFunApi.class.getDeclaredMethod("matchesTimeline", WhyFunVenue.class, String.class, LocalDate.class);
  matcher.setAccessible(true);
  WhyFunVenue plan = new WhyFunVenue();
   LocalDate now = LocalDate.of(2026, 7, 22);
   plan.scheduledAt = now.plusDays(1);
  assertTrue((Boolean) matcher.invoke(null, plan, "UPCOMING", now));
   assertFalse((Boolean) matcher.invoke(null, plan, "PAST", now));
  }

  @Test
  void exposesTheParentActivityProfilePhoto() {
   WhyFunVenues activities = mock(WhyFunVenues.class); WhyFunVenuePhotos photos = mock(WhyFunVenuePhotos.class);
   User tomas = new User(); tomas.username = "tomas";
   WhyFunCategory category = new WhyFunCategory(); category.id = 1L; category.name = "Arte"; category.slug = "arte"; category.icon = "a";
   WhyFunVenue activity = new WhyFunVenue(); activity.id = 4L; activity.name = "Museo"; activity.address = "Centro"; activity.category = activity.subcategory = category; activity.createdBy = activity.updatedBy = tomas; activity.coverPhotoId = 9L;
   WhyFunVenuePhoto photo = new WhyFunVenuePhoto(); photo.id = 9L; photo.venue = activity; photo.width = 1000; photo.height = 700;
   when(activities.findDetailedById(4L)).thenReturn(Optional.of(activity)); when(photos.findByIdAndVenueId(9L, 4L)).thenReturn(Optional.of(photo));

   ActivityDto result = new WhyFunActivityApi(null, activities, photos, null, null, null, null).getActivity(4L);

   assertEquals("/why-fun/activities/4/photo?v=9", result.profilePhoto().url());
   assertEquals("/why-fun/activities/4/photo?thumbnail=true&v=9", result.profilePhoto().thumbnailUrl());
  }
}
