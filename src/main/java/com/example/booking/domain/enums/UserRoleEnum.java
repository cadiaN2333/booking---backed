package com.example.booking.domain.enums;

import lombok.Getter;

@Getter
public enum UserRoleEnum {

  CUSTOMER(0, "顾客"),
  MERCHANT(1, "商家"),
  ADMIN(2, "管理员");

  private final int code;
  private final String desc;

  UserRoleEnum(int code, String desc) {
    this.code = code;
    this.desc = desc;
  }

  public static boolean isMerchant(Integer role) {
    return role != null && role == MERCHANT.code;
  }

  public static boolean isAdmin(Integer role) {
    return role != null && role == ADMIN.code;
  }
}
