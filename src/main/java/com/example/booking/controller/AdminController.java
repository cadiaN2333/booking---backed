package com.example.booking.controller;

import com.example.booking.common.BizException;
import com.example.booking.common.Result;
import com.example.booking.domain.dto.AuditDecisionRequest;
import com.example.booking.domain.vo.AdminReviewVO;
import com.example.booking.service.AdminAuditService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 管理员审核接口。 */
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

  private final AdminAuditService adminAuditService;

  @GetMapping("/reviews")
  public Result<List<AdminReviewVO>> listReviews(
      @RequestParam(defaultValue = "venue") String type,
      @RequestParam(defaultValue = "0") String status) {
    return Result.ok(adminAuditService.listReviews(type, parseAuditStatus(status)));
  }

  private int parseAuditStatus(String status) {
    if (status == null) {
      return 0;
    }
    try {
      int auditStatus = Integer.parseInt(status);
      if (auditStatus >= 0 && auditStatus <= 2) {
        return auditStatus;
      }
    } catch (NumberFormatException ignored) {
      // 统一转换为审核状态业务错误，避免落入5000兜底异常。
    }
    throw new BizException(4000, "审核状态必须是0、1或2");
  }

  @PostMapping("/reviews/venues/{id}/approve")
  public Result<Void> approveVenue(@PathVariable("id") Long venueId) {
    adminAuditService.approveVenue(venueId);
    return Result.ok();
  }

  @PostMapping("/reviews/venues/{id}/reject")
  public Result<Void> rejectVenue(
      @PathVariable("id") Long venueId, @Valid @RequestBody AuditDecisionRequest request) {
    adminAuditService.rejectVenue(venueId, request.reason());
    return Result.ok();
  }

  @PostMapping("/reviews/courts/{id}/approve")
  public Result<Void> approveCourt(@PathVariable("id") Long courtId) {
    adminAuditService.approveCourt(courtId);
    return Result.ok();
  }

  @PostMapping("/reviews/courts/{id}/reject")
  public Result<Void> rejectCourt(
      @PathVariable("id") Long courtId, @Valid @RequestBody AuditDecisionRequest request) {
    adminAuditService.rejectCourt(courtId, request.reason());
    return Result.ok();
  }
}
