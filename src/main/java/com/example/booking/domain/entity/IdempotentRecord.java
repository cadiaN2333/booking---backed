package com.example.booking.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/** 幂等记录：token + biz_type 唯一，result 为 NULL 表示尚未消费 */
@Data
@TableName("idempotent_record")
public class IdempotentRecord implements Serializable {

  private static final long serialVersionUID = 1L;

  @TableId(type = IdType.AUTO)
  private Long id;

  private String token;
  private Long userId;
  private String bizType;

  /** 首次执行结果（订单号）。NULL=未消费，PROCESSING=处理中，其余为已完成 */
  private String result;

  private LocalDateTime createTime;
}
