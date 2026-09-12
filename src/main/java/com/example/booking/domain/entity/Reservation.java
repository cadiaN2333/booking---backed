package com.example.booking.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("reservation")
public class Reservation implements Serializable {

  private static final long serialVersionUID = 1L;

  @TableId(type = IdType.AUTO)
  private Long id;

  private String orderNo;
  private Long userId;
  private Long courtId;
  private Long slotId;
  private LocalDate bizDate;
  private LocalDateTime startAt;
  private LocalDateTime endAt;

  /** 金额，单位：分 */
  private Integer amount;

  /** 见 ReservationStatusEnum：0待支付 1已确认 2已取消 3已超时 4已完成 */
  private Integer status;

  /** 锁定截止时间，超过后由释放任务归还库存 */
  private LocalDateTime expireAt;

  private Integer version;
  private LocalDateTime createTime;
  private LocalDateTime updateTime;
}
