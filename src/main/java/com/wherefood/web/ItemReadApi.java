package com.wherefood.web;

import java.time.LocalDate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read route kept separate from the place route so proxies can retrieve item cards reliably. */
@RestController
@RequestMapping("/api")
public class ItemReadApi {
 private final Api api;

 public ItemReadApi(Api api) { this.api = api; }

 @GetMapping("/items")
 Slice<ItemDto> list(@RequestParam Long placeId, @RequestParam(required = false) LocalDate visitDate) {
  return api.itemSlice(placeId, visitDate);
 }
}
