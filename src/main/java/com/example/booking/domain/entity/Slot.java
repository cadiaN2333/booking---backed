package com.example.booking.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 时段库存。
 *
 * <p>库存三段式：available + locked + sold = total，由对账任务校验。
 * 状态不由字段记录，而是从三段数值推导，避免状态与数值不一致。
 */
@Data
@TableName("slot")
public class Slot implements Serializable {

  private static final long serialVersionUID = 1L;

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long courtId;
  private LocalDate bizDate;
  private LocalDateTime startAt;
  private LocalDateTime endAt;

  private Integer total;
  private Integer available;
  private Integer locked;
  private Integer sold;

  private Integer version;
  private LocalDateTime createTime;
  private LocalDateTime updateTime;
}
