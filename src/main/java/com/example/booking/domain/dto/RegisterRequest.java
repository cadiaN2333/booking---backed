package com.example.booking.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.io.Serializable;
import lombok.Data;

@Data
public class RegisterRequest implements Serializable {

  private static final long serialVersionUID = 1L;

  @NotBlank(message = "用户名不能为空")
  @Size(min = 3, max = 20, message = "用户名长度需在 3-20 位之间")
  private String username;

  @NotBlank(message = "密码不能为空")
  @Size(min = 6, max = 32, message = "密码长度需在 6-32 位之间")
  private String password;

  @NotBlank(message = "昵称不能为空")
  @Size(max = 20, message = "昵称最长 20 位")
  private String nickname;

  private String phone;

  /** 0 顾客 1 商家；管理员由初始化器创建，不开放普通注册 */
  private Integer role = 0;
}
