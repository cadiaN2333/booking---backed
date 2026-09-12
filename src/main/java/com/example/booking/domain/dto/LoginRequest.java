package com.example.booking.domain.dto;

import jakarta.validation.constraints.NotBlank;
import java.io.Serializable;
import lombok.Data;

@Data
public class LoginRequest implements Serializable {

  private static final long serialVersionUID = 1L;

  @NotBlank(message = "用户名不能为空")
  private String username;

  @NotBlank(message = "密码不能为空")
  private String password;
}
