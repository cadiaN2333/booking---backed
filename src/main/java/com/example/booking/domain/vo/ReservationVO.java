package com.example.booking.domain.vo;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class ReservationVO implements Serializable {

  private static final long serialVersionUID = 1L;

  private Long id;
  private String orderNo;
  private Long userId;
  private Long courtId;
  private String courtName;
  private Long slotId;
  private LocalDate bizDate;
  private LocalDateTime startAt;
  private LocalDateTime endAt;
  private Integer amount;
  private Integer status;
  private LocalDateTime expireAt;
  private LocalDateTime createTime;
}
