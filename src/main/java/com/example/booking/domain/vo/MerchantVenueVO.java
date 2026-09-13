package com.example.booking.domain.vo;

import com.example.booking.domain.entity.Venue;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/** 商家端场馆视图，只暴露商家管理所需字段。 */
@Data
public class MerchantVenueVO implements Serializable {

  private static final long serialVersionUID = 1L;

  private Long id;
  private String name;
  private String address;
  private Integer status;
  private Integer auditStatus;
  private String auditRemark;
  private LocalDateTime createTime;

  public static MerchantVenueVO from(Venue venue) {
    MerchantVenueVO view = new MerchantVenueVO();
    view.setId(venue.getId());
    view.setName(venue.getName());
    view.setAddress(venue.getAddress());
    view.setStatus(venue.getStatus());
    view.setAuditStatus(venue.getAuditStatus());
    view.setAuditRemark(venue.getAuditRemark());
    view.setCreateTime(venue.getCreateTime());
    return view;
  }
}
