package com.example.booking.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/** 持久化的预约超时释放任务。 */
@Data
@TableName("reservation_release_task")
public class ReleaseTask implements Serializable {

  private static final long serialVersionUID = 1L;

  @TableId(type = IdType.AUTO)
  private Long id;

  private String orderNo;
  private LocalDateTime executeAt;

  /** 0待执行，1处理中，2完成。 */
  private Integer status;
  private Integer attempts;
  private String lastError;
  private LocalDateTime createTime;
  private LocalDateTime updateTime;
}
