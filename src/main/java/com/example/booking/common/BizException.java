package com.example.booking.common;

import lombok.Getter;

/** 业务异常：可被用户理解的失败，返回 code=4001，前端直接提示 message */
@Getter
public class BizException extends RuntimeException {

  private final int code;

  public BizException(String message) {
    super(message);
    this.code = 4001;
  }

  public BizException(int code, String message) {
    super(message);
    this.code = code;
  }
}
