package com.example.booking.service;

import com.example.booking.domain.vo.AdminReviewVO;
import java.util.List;

/** 管理员审核场馆和场地的服务。 */
public interface AdminAuditService {

  /** 查询指定审核状态的场馆或场地。 */
  List<AdminReviewVO> listReviews(String type, Integer status);

  /** 审核通过场馆并自动上架。 */
  void approveVenue(Long venueId);

  /** 驳回场馆并保持下架。 */
  void rejectVenue(Long venueId, String reason);

  /** 审核通过场地并自动上架。 */
  void approveCourt(Long courtId);

  /** 驳回场地并保持下架。 */
  void rejectCourt(Long courtId, String reason);
}
