package com.example.booking.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.LocalTime;
import lombok.Data;

@Data
@TableName("court")
public class Court implements Serializable {

  private static final long serialVersionUID = 1L;

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long venueId;
  private String name;
  private String type;

  /** 每时段价格，单位：分 */
  private Integer price;

  private LocalTime openTime;
  private LocalTime closeTime;

  /** 单时段时长（分钟） */
  private Integer slotMinutes;

  private Integer status;
  private LocalDateTime createTime;
}
