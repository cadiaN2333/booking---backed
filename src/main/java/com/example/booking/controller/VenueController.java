package com.example.booking.controller;

import com.example.booking.common.Result;
import com.example.booking.domain.entity.Venue;
import com.example.booking.service.VenueService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/venues")
@RequiredArgsConstructor
public class VenueController {

  private final VenueService venueService;

  @GetMapping
  public Result<List<Venue>> list() {
    return Result.ok(venueService.listOnline());
  }
}
