package com.example.booking.controller;

import com.example.booking.common.Result;
import com.example.booking.domain.dto.CreateReservationRequest;
import com.example.booking.domain.vo.ReservationVO;
import com.example.booking.service.ReservationService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 顾客端接口。userId 全部取自登录态，不接受前端传入 */
@RestController
@RequestMapping("/reservations")
@RequiredArgsConstructor
public class ReservationController {

  private final ReservationService reservationService;

  /** 进入确认页前先取令牌，用于拦截按钮连点与网关重试 */
  @PostMapping("/token")
  public Result<String> token(@RequestBody Map<String, Object> body) {
    Object raw = body.get("slotId");
    if (raw == null) {
      return Result.fail(4000, "slotId 必填");
    }
    return Result.ok(reservationService.createToken(Long.valueOf(String.valueOf(raw))));
  }

  @PostMapping
  public Result<ReservationVO> create(@Valid @RequestBody CreateReservationRequest request) {
    return Result.ok(reservationService.create(request));
  }

  /** 我的预约 */
  @GetMapping
  public Result<List<ReservationVO>> list() {
    return Result.ok(reservationService.listMine());
  }

  @GetMapping("/{orderNo}")
  public Result<ReservationVO> detail(@PathVariable String orderNo) {
    return Result.ok(reservationService.getByOrderNo(orderNo));
  }

  @PostMapping("/{orderNo}/confirm")
  public Result<Boolean> confirm(@PathVariable String orderNo) {
    reservationService.confirm(orderNo);
    return Result.ok(true);
  }

  @PostMapping("/{orderNo}/cancel")
  public Result<Boolean> cancel(@PathVariable String orderNo) {
    reservationService.cancel(orderNo);
    return Result.ok(true);
  }
}
