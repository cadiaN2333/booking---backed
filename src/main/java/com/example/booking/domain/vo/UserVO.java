package com.example.booking.domain.vo;

import com.example.booking.domain.entity.User;
import java.io.Serializable;
import lombok.Data;

/** 对外暴露的用户信息，绝不包含密码 */
@Data
public class UserVO implements Serializable {

  private static final long serialVersionUID = 1L;

  private Long id;
  private String username;
  private String nickname;
  private String phone;
  private Integer role;

  public static UserVO from(User u) {
    UserVO v = new UserVO();
    v.setId(u.getId());
    v.setUsername(u.getUsername());
    v.setNickname(u.getNickname());
    v.setPhone(u.getPhone());
    v.setRole(u.getRole());
    return v;
  }
}
