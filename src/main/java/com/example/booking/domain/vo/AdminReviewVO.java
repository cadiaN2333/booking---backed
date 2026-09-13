package com.example.booking.domain.vo;

import com.example.booking.domain.entity.Court;
import com.example.booking.domain.entity.Venue;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.LocalTime;
import lombok.Data;

/** 管理员审核视图，不直接暴露场馆或场地实体。 */
@Data
public class AdminReviewVO implements Serializable {

  private static final long serialVersionUID = 1L;

  private String resourceType;
  private Long id;
  private Long venueId;
  private String venueName;
  private String name;
  private String address;
  private Long merchantId;
  private String type;
  private Integer price;
  private LocalTime openTime;
  private LocalTime closeTime;
  private Integer slotMinutes;
  private Integer status;
  private Integer auditStatus;
  private String auditRemark;
  private LocalDateTime auditTime;
  private Long auditBy;
  private LocalDateTime createTime;

  public static AdminReviewVO fromVenue(Venue venue) {
    AdminReviewVO view = new AdminReviewVO();
    view.setResourceType("venue");
    view.setId(venue.getId());
    view.setName(venue.getName());
    view.setAddress(venue.getAddress());
    view.setMerchantId(venue.getMerchantId());
    view.setStatus(venue.getStatus());
    view.setAuditStatus(venue.getAuditStatus());
    view.setAuditRemark(venue.getAuditRemark());
    view.setAuditTime(venue.getAuditTime());
    view.setAuditBy(venue.getAuditBy());
    view.setCreateTime(venue.getCreateTime());
    return view;
  }

  public static AdminReviewVO fromCourt(Court court, Venue venue) {
    AdminReviewVO view = new AdminReviewVO();
    view.setResourceType("court");
    view.setId(court.getId());
    view.setVenueId(court.getVenueId());
    view.setVenueName(venue == null ? null : venue.getName());
    view.setName(court.getName());
    view.setMerchantId(venue == null ? null : venue.getMerchantId());
    view.setType(court.getType());
    view.setPrice(court.getPrice());
    view.setOpenTime(court.getOpenTime());
    view.setCloseTime(court.getCloseTime());
    view.setSlotMinutes(court.getSlotMinutes());
    view.setStatus(court.getStatus());
    view.setAuditStatus(court.getAuditStatus());
    view.setAuditRemark(court.getAuditRemark());
    view.setAuditTime(court.getAuditTime());
    view.setAuditBy(court.getAuditBy());
    view.setCreateTime(court.getCreateTime());
    return view;
  }
}
