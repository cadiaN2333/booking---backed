package com.example.booking.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("user")
public class User implements Serializable {

  private static final long serialVersionUID = 1L;

  @TableId(type = IdType.AUTO)
  private Long id;

  private String username;

  /** BCrypt 密文，绝不返回给前端 */
  private String password;

  private String nickname;
  private String phone;

  /** 0 顾客 1 商家 2 管理员 */
  private Integer role;

  /** 1 正常 0 禁用 */
  private Integer status;

  private LocalDateTime createTime;
  private LocalDateTime updateTime;
}
