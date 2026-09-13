package com.example.booking.controller;

import com.example.booking.common.BizException;
import com.example.booking.common.Result;
import com.example.booking.domain.dto.MerchantCourtRequest;
import com.example.booking.domain.dto.MerchantVenueRequest;
import com.example.booking.domain.entity.Court;
import com.example.booking.domain.entity.Venue;
import com.example.booking.service.CourtService;
import com.example.booking.service.SlotService;
import com.example.booking.service.VenueService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.PathVariable;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商家端接口。
 *
 * <p>整个 /merchant/** 前缀由 AuthInterceptor 统一校验 role=商家；
 * 「这条数据是不是我的」由各 Service 内部校验，Controller 不做业务判断。
 */
@RestController
@RequestMapping("/merchant")
@RequiredArgsConstructor
public class MerchantController {

  private final VenueService venueService;
  private final CourtService courtService;
  private final SlotService slotService;

  @GetMapping("/venues")
  public Result<List<Venue>> myVenues() {
    return Result.ok(venueService.listMine());
  }

  @PostMapping("/venues")
  public Result<Venue> createVenue(@Valid @RequestBody MerchantVenueRequest request) {
    return Result.ok(venueService.create(request));
  }

  @PutMapping("/venues/{id}")
  public Result<Venue> updateVenue(
      @PathVariable("id") Long venueId, @Valid @RequestBody MerchantVenueRequest request) {
    return Result.ok(venueService.update(venueId, request));
  }

  @PostMapping("/venues/{id}/offline")
  public Result<Void> offlineVenue(@PathVariable("id") Long venueId) {
    venueService.offline(venueId);
    return Result.ok();
  }

  @GetMapping("/courts")
  public Result<List<Court>> myCourts() {
    return Result.ok(courtService.listMine());
  }

  @PostMapping("/courts")
  public Result<Court> createCourt(@Valid @RequestBody MerchantCourtRequest request) {
    return Result.ok(courtService.create(request));
  }

  @PutMapping("/courts/{id}")
  public Result<Court> updateCourt(
      @PathVariable("id") Long courtId, @Valid @RequestBody MerchantCourtRequest request) {
    return Result.ok(courtService.update(courtId, request));
  }

  @PostMapping("/courts/{id}/offline")
  public Result<Void> offlineCourt(@PathVariable("id") Long courtId) {
    courtService.offline(courtId);
    return Result.ok();
  }

  @PostMapping("/slots/generate")
  public Result<Integer> generateSlots(@RequestBody Map<String, Object> body) {
    Object raw = body.get("courtId");
    if (raw == null) {
      throw new BizException(4000, "courtId 必填");
    }
    return Result.ok(slotService.generateForMerchant(Long.valueOf(String.valueOf(raw))));
  }
}
