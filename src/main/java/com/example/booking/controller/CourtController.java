package com.example.booking.controller;

import com.example.booking.common.Result;
import com.example.booking.domain.entity.Court;
import com.example.booking.domain.vo.SlotVO;
import com.example.booking.service.CourtService;
import com.example.booking.service.SlotService;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 顾客端场地查询。
 *
 * <p>时段的 URL 是 /courts/{courtId}/slots —— 时段是场地的子资源，
 * 所以查时段的接口挂在这里而不是另开 SlotController。这决定了 SlotService
 * 没有同名 Controller：它的调用方按 REST 资源划分，而不是按 Service 名字划分。
 */
@RestController
@RequestMapping("/courts")
@RequiredArgsConstructor
public class CourtController {

  private final CourtService courtService;
  private final SlotService slotService;

  @GetMapping
  public Result<List<Court>> list(@RequestParam(required = false) Long venueId) {
    return Result.ok(courtService.listOnlineByVenue(venueId));
  }

  /** GET /api/courts/{courtId}/slots?date=2026-09-10 */
  @GetMapping("/{courtId}/slots")
  public Result<List<SlotVO>> slots(@PathVariable Long courtId, @RequestParam String date) {
    return Result.ok(slotService.listByCourtAndDate(courtId, LocalDate.parse(date)));
  }
}
