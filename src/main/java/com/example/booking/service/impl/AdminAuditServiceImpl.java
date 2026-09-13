package com.example.booking.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.booking.common.BizException;
import com.example.booking.common.UserContext;
import com.example.booking.domain.entity.Court;
import com.example.booking.domain.entity.Venue;
import com.example.booking.domain.vo.AdminReviewVO;
import com.example.booking.mapper.CourtMapper;
import com.example.booking.mapper.VenueMapper;
import com.example.booking.service.AdminAuditService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 管理员审核业务实现。 */
@Service
@RequiredArgsConstructor
public class AdminAuditServiceImpl implements AdminAuditService {

  private static final String INVALID_AUDIT_STATUS_MESSAGE = "审核状态必须是0、1或2";

  private final VenueMapper venueMapper;
  private final CourtMapper courtMapper;

  @Override
  public List<AdminReviewVO> listReviews(String type, Integer status) {
    String reviewType = type == null ? "venue" : type;
    int auditStatus = validateAuditStatus(status);
    if ("venue".equals(reviewType)) {
      return venueMapper
          .selectList(
              new LambdaQueryWrapper<Venue>()
                  .eq(Venue::getAuditStatus, auditStatus)
                  .orderByAsc(Venue::getId))
          .stream()
          .map(AdminReviewVO::fromVenue)
          .toList();
    }
    if ("court".equals(reviewType)) {
      return courtMapper
          .selectList(
              new LambdaQueryWrapper<Court>()
                  .eq(Court::getAuditStatus, auditStatus)
                  .orderByAsc(Court::getId))
          .stream()
          .map(court -> AdminReviewVO.fromCourt(court, venueMapper.selectById(court.getVenueId())))
          .toList();
    }
    throw new BizException("审核资源类型只能是venue或court");
  }

  private int validateAuditStatus(Integer status) {
    int auditStatus = status == null ? 0 : status;
    if (auditStatus < 0 || auditStatus > 2) {
      throw new BizException(4000, INVALID_AUDIT_STATUS_MESSAGE);
    }
    return auditStatus;
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public void approveVenue(Long venueId) {
    Venue venue = requirePendingVenue(venueId);
    markApproved(venue);
    venueMapper.updateById(venue);
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public void rejectVenue(Long venueId, String reason) {
    requireReason(reason);
    Venue venue = requirePendingVenue(venueId);
    markRejected(venue, reason);
    venueMapper.updateById(venue);
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public void approveCourt(Long courtId) {
    Court court = requirePendingCourt(courtId);
    Venue venue = venueMapper.selectById(court.getVenueId());
    if (venue == null || !Objects.equals(venue.getAuditStatus(), 1) || !Objects.equals(venue.getStatus(), 1)) {
      throw new BizException("场地所属场馆尚未审核通过并上架，不能审核场地");
    }
    markApproved(court);
    courtMapper.updateById(court);
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public void rejectCourt(Long courtId, String reason) {
    requireReason(reason);
    Court court = requirePendingCourt(courtId);
    markRejected(court, reason);
    courtMapper.updateById(court);
  }

  private Venue requirePendingVenue(Long venueId) {
    Venue venue = venueMapper.selectById(venueId);
    if (venue == null) {
      throw new BizException("场馆不存在");
    }
    if (!Objects.equals(venue.getAuditStatus(), 0)) {
      throw new BizException("场馆当前不是待审核状态，不能重复审核");
    }
    return venue;
  }

  private Court requirePendingCourt(Long courtId) {
    Court court = courtMapper.selectById(courtId);
    if (court == null) {
      throw new BizException("场地不存在");
    }
    if (!Objects.equals(court.getAuditStatus(), 0)) {
      throw new BizException("场地当前不是待审核状态，不能重复审核");
    }
    return court;
  }

  private void markApproved(Venue venue) {
    venue.setAuditStatus(1);
    venue.setStatus(1);
    venue.setAuditRemark(null);
    venue.setAuditTime(LocalDateTime.now());
    venue.setAuditBy(UserContext.userId());
  }

  private void markApproved(Court court) {
    court.setAuditStatus(1);
    court.setStatus(1);
    court.setAuditRemark(null);
    court.setAuditTime(LocalDateTime.now());
    court.setAuditBy(UserContext.userId());
  }

  private void markRejected(Venue venue, String reason) {
    venue.setAuditStatus(2);
    venue.setStatus(0);
    venue.setAuditRemark(reason);
    venue.setAuditTime(LocalDateTime.now());
    venue.setAuditBy(UserContext.userId());
  }

  private void markRejected(Court court, String reason) {
    court.setAuditStatus(2);
    court.setStatus(0);
    court.setAuditRemark(reason);
    court.setAuditTime(LocalDateTime.now());
    court.setAuditBy(UserContext.userId());
  }

  private void requireReason(String reason) {
    if (reason == null || reason.isBlank()) {
      throw new BizException(4000, "驳回原因不能为空");
    }
    if (reason.length() > 500) {
      throw new BizException(4000, "驳回原因不能超过500个字符");
    }
  }
}
