package com.example.booking.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("venue")
public class Venue implements Serializable {

  private static final long serialVersionUID = 1L;

  @TableId(type = IdType.AUTO)
  private Long id;

  private String name;
  private String address;

  /** 归属商家 user.id */
  private Long merchantId;

  /** 1已上架 0下架；新建资源默认下架 */
  private Integer status = 0;

  /** 0待审核 1审核通过 2已驳回 */
  private Integer auditStatus = 0;

  /** 审核驳回原因 */
  private String auditRemark;

  /** 审核时间 */
  private LocalDateTime auditTime;

  /** 审核人 user.id */
  private Long auditBy;

  private LocalDateTime createTime;
}
