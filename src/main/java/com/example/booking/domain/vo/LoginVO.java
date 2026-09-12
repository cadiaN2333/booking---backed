package com.example.booking.domain.vo;

import java.io.Serializable;
import lombok.Data;

@Data
public class LoginVO implements Serializable {

  private static final long serialVersionUID = 1L;

  /** 会话令牌，前端存 localStorage，后续请求放 Authorization: Bearer <token> */
  private String token;

  private UserVO user;

  public LoginVO(String token, UserVO user) {
    this.token = token;
    this.user = user;
  }
}
