package com.wherefood.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wherefood.domain.WhyFunVenue;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class WhyFunApiTest {
 @Test
 void separatesUpcomingAndPastPlans() throws Exception {
  Method matcher = WhyFunApi.class.getDeclaredMethod("matchesTimeline", WhyFunVenue.class, String.class, LocalDateTime.class);
  matcher.setAccessible(true);
  WhyFunVenue plan = new WhyFunVenue();
  LocalDateTime now = LocalDateTime.of(2026, 7, 22, 18, 0);
  plan.scheduledAt = now.plusHours(2);
  assertTrue((Boolean) matcher.invoke(null, plan, "UPCOMING", now));
  assertFalse((Boolean) matcher.invoke(null, plan, "PAST", now));
 }
}
