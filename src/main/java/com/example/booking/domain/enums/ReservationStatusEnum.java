package com.example.booking.domain.enums;

import lombok.Getter;

@Getter
public enum ReservationStatusEnum {

  PENDING(0, "待支付"),
  CONFIRMED(1, "已确认"),
  CANCELLED(2, "已取消"),
  EXPIRED(3, "已超时"),
  COMPLETED(4, "已完成");

  private final int code;
  private final String desc;

  ReservationStatusEnum(int code, String desc) {
    this.code = code;
    this.desc = desc;
  }

  public static ReservationStatusEnum of(Integer code) {
    for (ReservationStatusEnum e : values()) {
      if (e.code == code) {
        return e;
      }
    }
    return null;
  }
}
