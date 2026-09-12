package com.example.booking.domain.vo;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

/** 时段视图。展示态由前端依据 available / locked / sold 推导，后端不下发状态 */
@Data
public class SlotVO implements Serializable {

  private static final long serialVersionUID = 1L;

  private Long id;
  private Long courtId;
  private LocalDate bizDate;
  private LocalDateTime startAt;
  private LocalDateTime endAt;

  private Integer total;
  private Integer available;
  private Integer locked;
  private Integer sold;
  private Integer price;
}
