package com.wherefood.web;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ExperienceMigrationTest {
 @Test
 void foodMigrationProtectsDeletedLegacyDataAndMapsTheSpecifiedVisit() throws IOException {
  String sql = migration("V27__unify_place_visit_experiences.sql");
  assertTrue(sql.contains("item.deleted_at is null and item.id <> 6"));
  assertTrue(sql.contains("Un Churrito Rosario"));
  assertTrue(sql.contains("date '2026-07-17'"));
  assertTrue(sql.contains("round(avg(metric), 0)::smallint as overall"));
 }

 @Test
 void laterMigrationsCreateChildExperienceTables() throws IOException {
  assertTrue(migration("V28__split_why_fun_activities_and_visits.sql").contains("create table why_fun_visits"));
  assertTrue(migration("V29__add_film_view_photos.sql").contains("create table film_view_photos"));
  assertTrue(migration("V30__split_recipes_and_cookings.sql").contains("create table cookings"));
 }

 private static String migration(String name) throws IOException {
  try (InputStream stream = ExperienceMigrationTest.class.getResourceAsStream("/db/migration/" + name)) { return new String(stream.readAllBytes(), StandardCharsets.UTF_8); }
 }
}
