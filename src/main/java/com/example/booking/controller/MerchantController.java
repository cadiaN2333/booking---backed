package com.example.booking.controller;

import com.example.booking.common.BizException;
import com.example.booking.common.Result;
import com.example.booking.domain.entity.Court;
import com.example.booking.domain.entity.Venue;
import com.example.booking.service.CourtService;
import com.example.booking.service.SlotService;
import com.example.booking.service.VenueService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
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

  @GetMapping("/courts")
  public Result<List<Court>> myCourts() {
    return Result.ok(courtService.listMine());
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
