package com.wherefood.web;

import java.time.LocalDate;
import java.time.ZoneId;

final class RosarioClock {
 private static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");

 private RosarioClock() {}

 static LocalDate today() { return LocalDate.now(ZONE); }
}
