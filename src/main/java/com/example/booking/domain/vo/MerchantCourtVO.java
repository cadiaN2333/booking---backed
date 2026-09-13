package com.example.booking.domain.vo;

import com.example.booking.domain.entity.Court;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.LocalTime;
import lombok.Data;

/** 商家端场地视图，只暴露商家管理所需字段。 */
@Data
public class MerchantCourtVO implements Serializable {

  private static final long serialVersionUID = 1L;

  private Long id;
  private Long venueId;
  private String name;
  private String type;
  private Integer price;
  private LocalTime openTime;
  private LocalTime closeTime;
  private Integer slotMinutes;
  private Integer status;
  private Integer auditStatus;
  private String auditRemark;
  private LocalDateTime createTime;

  public static MerchantCourtVO from(Court court) {
    MerchantCourtVO view = new MerchantCourtVO();
    view.setId(court.getId());
    view.setVenueId(court.getVenueId());
    view.setName(court.getName());
    view.setType(court.getType());
    view.setPrice(court.getPrice());
    view.setOpenTime(court.getOpenTime());
    view.setCloseTime(court.getCloseTime());
    view.setSlotMinutes(court.getSlotMinutes());
    view.setStatus(court.getStatus());
    view.setAuditStatus(court.getAuditStatus());
    view.setAuditRemark(court.getAuditRemark());
    view.setCreateTime(court.getCreateTime());
    return view;
  }
}
